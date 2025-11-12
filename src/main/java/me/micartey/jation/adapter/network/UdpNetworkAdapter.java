package me.micartey.jation.adapter.network;

import lombok.Setter;
import lombok.SneakyThrows;
import me.micartey.jation.JationObserver;
import me.micartey.jation.adapter.network.packets.PacketAcknowledge;
import me.micartey.jation.adapter.network.packets.PacketInvokeMethod;
import me.micartey.jation.adapter.network.serializer.Serializer;
import me.micartey.jation.annotations.Distribution;
import me.micartey.jation.interfaces.Function;
import me.micartey.jation.interfaces.JationEvent;
import me.micartey.jation.utilities.Base64;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class UdpNetworkAdapter implements NetworkAdapter {

    private static final ExecutorService RETRY_EXECUTOR = Executors.newCachedThreadPool();

    private static final Serializer SERIALIZER = new Serializer();
    private static final int SOCKET_BUFFER_SIZE = 4096;

    private final Map<Integer, Function<DatagramPacket>> tasks = new HashMap<>();

    private final List<InetAddress> interfaceAddress = new ArrayList<>();
    private final AtomicInteger transactionCounter = new AtomicInteger();
    private final DatagramSocket datagramSocket;
    private final int[] targetPorts;

    @Setter private JationObserver observer = JationObserver.DEFAULT_OBSERVER;

    @SneakyThrows
    public UdpNetworkAdapter(int port, int... targetPorts) {
        this.datagramSocket = new DatagramSocket(port);
        this.targetPorts = targetPorts;
    }

    @SuppressWarnings("unused")
    public UdpNetworkAdapter(int port) {
        this(port, port);
    }

    @Override
    public void publish(JationEvent<?> event, Object... additional) {
        /*
         * Recursion anchor to prevent an infinite loop.
         * Once this adapter will publish an event, it will add its instance and will therefore be present in the obejct array
         */
        if (Arrays.stream(additional).filter(Objects::nonNull).anyMatch(object -> object instanceof NetworkAdapter))
            return;

        /*
         * Ignore event if distribution annotation isn't present
         */
        if (!event.getClass().isAnnotationPresent(Distribution.class))
            return;

        Distribution.Guarantee garantee = event.getClass().getAnnotation(Distribution.class).value();

        int id = nextId();
        String serializedPacket = SERIALIZER.serialize(
                new PacketInvokeMethod(id, Base64.toBase64(event).get(), Base64.toBase64(additional).get()),
                PacketInvokeMethod.class
        );

        String ackPacket = SERIALIZER.serialize(
                new PacketAcknowledge(id),
                PacketAcknowledge.class
        );

        switch(garantee) {
            case EXACTLY_ONCE -> {
                tasks.put(id, packet -> send(ackPacket, packet.getAddress(), packet.getPort()));

                /*
                 * Send broadcast as long as defined task above has not been picked up.
                 * Task above will answer directly
                 */
                RETRY_EXECUTOR.submit(() -> {
                    try {
                        while(tasks.containsKey(id)) {
                            for(int port : this.targetPorts) {
                                send(serializedPacket, port);
                            }
                            Thread.sleep(2000);
                        }
                    } catch(InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }

            case AT_LEAST_ONCE -> {
                tasks.put(id, packet -> {
                }); // Simple placeholder

                RETRY_EXECUTOR.submit(() -> {
                    try {
                        while(tasks.containsKey(id)) {
                            for(int port : this.targetPorts) {
                                send(serializedPacket, port);
                                send(ackPacket, port);
                            }
                            Thread.sleep(2000);
                        }
                    } catch(InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        }
    }

    @SneakyThrows
    public void listen() {
        byte[] buffer = new byte[SOCKET_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while(true) {
            this.datagramSocket.receive(packet); // Blocked Waiting for broadcast message
            String message = new String(packet.getData(), 0, packet.getLength());

            Object parsedPacket = SERIALIZER.deserialize(message, PacketInvokeMethod.class, PacketAcknowledge.class);

            /*
             * Whenever the server receives an acknowledgment, it will execute some task
             */
            if (parsedPacket instanceof PacketAcknowledge ack) {
                tasks.getOrDefault(ack.getAckId(), (datagramPacket) -> { }).apply(packet);
                tasks.remove(ack.getAckId());
            }

            /*
             * When invoke method packet is received, send an ack and wait for a confirmation
             */
            else if (parsedPacket instanceof PacketInvokeMethod invoke) {
                int ackId = invoke.getAckId();

                this.tasks.put(ackId, (datagramPacket) -> {
                    JationEvent<?> event = (JationEvent<?>) Base64.fromBase64(invoke.getEventData()).get();
                    Object[] objects = (Object[]) Base64.fromBase64(invoke.getAdditionalObjects()).get();

                    // Add adapter instance as last argument
                    objects = Arrays.copyOf(objects, objects.length + 1);
                    objects[objects.length - 1] = this;

                    this.observer.publish(event, objects);
                });

                this.send(
                        SERIALIZER.serialize(new PacketAcknowledge(ackId), PacketAcknowledge.class),
                        packet.getAddress(),
                        packet.getPort()
                );
            }
        }
    }

    private synchronized int nextId() {
        int id;

        do {
            int randomId = ThreadLocalRandom.current().nextInt(100_000);
            id = transactionCounter.addAndGet(randomId) & Integer.MAX_VALUE;
        } while(tasks.containsKey(id));

        return id;
    }

    @SneakyThrows
    private void send(String message, InetAddress address, int port) {
        byte[] replyBuffer = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(replyBuffer, replyBuffer.length, address, port);
        this.datagramSocket.send(packet);
    }

    @SneakyThrows
    private void send(String message, int port) {
        for(InetAddress address : this.interfaceAddress) {
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            this.datagramSocket.send(packet);
        }
    }

    @SneakyThrows
    public UdpNetworkAdapter useBraodcastInterface() {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        label:
        while(interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();
            for(InterfaceAddress address : addresses) {
                InetAddress broadcast = address.getBroadcast();

                if (broadcast == null)
                    continue;

                this.interfaceAddress.add(broadcast);
                break label;
            }
        }

        return this;
    }

    @SneakyThrows
    public UdpNetworkAdapter useLoopbackInterface() {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while(addresses.hasMoreElements()) {
                InetAddress loopback = addresses.nextElement();

                if (loopback == null)
                    continue;

                this.interfaceAddress.add(loopback);
                return this;
            }
        }

        this.interfaceAddress.add(InetAddress.getLoopbackAddress());
        return this;
    }

    /**
     * Add specific interface if {@link UdpNetworkAdapter#useBraodcastInterface()} and {@link UdpNetworkAdapter#useLoopbackInterface()}
     * are not sufficient
     *
     * @param address InetAddress, if applicable a broadcast address
     * @return current instance to allow chained calles
     */
    public UdpNetworkAdapter addInterface(InetAddress address) {
        this.interfaceAddress.add(address);
        return this;
    }
}