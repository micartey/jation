package me.micartey.jation.network;

import lombok.NonNull;
import lombok.SneakyThrows;
import me.micartey.jation.JationObserver;
import me.micartey.jation.annotations.Distribution;
import me.micartey.jation.interfaces.Function;
import me.micartey.jation.interfaces.JationEvent;
import me.micartey.jation.network.packets.PacketAcknowledge;
import me.micartey.jation.network.packets.PacketInvokeMethod;
import me.micartey.jation.network.serializer.Serializer;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class UdpNetworkAdapter implements NetworkAdapter {

    private static final ExecutorService RETRY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final Serializer SERIALIZER = new Serializer("汉语");
    private static final int BUFFER_SIZE = 1024;

    private final Map<Integer, Function<DatagramPacket>> tasks = new HashMap<>();

    private final AtomicInteger transactionCounter = new AtomicInteger();
    private final DatagramSocket datagramSocket;
    private InetAddress interfaceAddress;
    private final int port;

    @SneakyThrows
    public UdpNetworkAdapter(int port) {
        this.datagramSocket = new DatagramSocket(port);
        this.port = port;
    }

    @Override
    public void publish(JationEvent<?> event, Object... additional) {
        if (Arrays.stream(additional).filter(Objects::nonNull).anyMatch(object -> object == this))
            return;

        Distribution.Garantee garantee = Distribution.Garantee.AT_LEAST_ONCE;
        if (event.getClass().isAnnotationPresent(Distribution.class)) {
            garantee = event.getClass().getAnnotation(Distribution.class).value();
        }

        int id = nextId();
        String serializedPacket = SERIALIZER.serialize(
                new PacketInvokeMethod(id, event.getClass().getName(), SERIALIZER.serialize(event, event.getClass())),
                PacketInvokeMethod.class
        );

        switch (garantee) {
            case EXACTLY_ONCE -> {
                String ackPacket = SERIALIZER.serialize(
                        new PacketAcknowledge(id),
                        PacketAcknowledge.class
                );

                tasks.put(id, packet -> send(ackPacket, packet.getAddress(), packet.getPort()));

                /*
                 * Send broadcast as long as defined task above has not been picked up.
                 * Task above will answer directly
                 */
                RETRY_EXECUTOR.submit(() -> {
                    try {
                        while (tasks.containsKey(id)) {
                            broadcast(serializedPacket, port);
                            Thread.sleep(2000);
                        }
                    } catch(InterruptedException e) {
                        // TODO: Trigger JationEvent if this fails
                    }
                });
            }

            case AT_LEAST_ONCE -> {
                String ackPacket = SERIALIZER.serialize(
                        new PacketAcknowledge(id),
                        PacketAcknowledge.class
                );

                tasks.put(id, packet -> { }); // Simple placeholder

                RETRY_EXECUTOR.submit(() -> {
                    try {
                        while (tasks.containsKey(id)) {
                            System.out.println("WAITING");

                            broadcast(serializedPacket, port);
                            broadcast(ackPacket, port);
                            Thread.sleep(2000);
                        }
                    } catch(InterruptedException e) {
                        // TODO: Trigger JationEvent if this fails
                    }
                });
            }
        }
    }

    /**
     * Listen to incomming socket data in a virtual thread
     */
    public void listen() {
        Runnable task = () -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while(true) {
                try {
                    this.datagramSocket.receive(packet); // Blocked Waiting for broadcast message
                    String message = new String(packet.getData(), 0, packet.getLength());

                    Object parsedPacket = SERIALIZER.deserialize(message, PacketInvokeMethod.class, PacketAcknowledge.class);

                    /*
                     * Whenever the server receives an acknowledgment, it will execute some task
                     */
                    if (parsedPacket instanceof PacketAcknowledge ack) {
                        tasks.getOrDefault(ack.getAckId(), (datagramPacket) -> { }).apply(packet);
                    }

                    /*
                     * When invoke method packet is received, send an ack and wait for a confirmation
                     */
                    if (parsedPacket instanceof PacketInvokeMethod invoke) {
                        int ackId = invoke.getAckId();

                        this.tasks.put(ackId, (datagramPacket) -> {
                            Class<?> eventClass = Class.forName(invoke.getEventClass());
                            Object event = SERIALIZER.deserialize(invoke.getEventData(), eventClass);
                            JationObserver.DEFAULT_OBSERVER.publish((JationEvent<?>) event, this);
                        });

                        this.send(
                                SERIALIZER.serialize(new PacketAcknowledge(ackId), PacketAcknowledge.class),
                                packet.getAddress(),
                                packet.getPort()
                        );
                    }

                } catch(Exception exception) {
                    exception.printStackTrace();
                }
            }
        };

        Thread.ofVirtual().start(task);
    }

    private synchronized int nextId() {
        int randomId = ThreadLocalRandom.current().nextInt(10, 64);
        return transactionCounter.addAndGet(randomId) & Integer.MAX_VALUE;
    }

    @SneakyThrows
    public void send(String message, InetAddress address, int port) {
        byte[] replyBuffer = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(replyBuffer, replyBuffer.length, address, port);
        this.datagramSocket.send(packet);
    }

    @SneakyThrows
    public void broadcast(String message, int port) {
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, this.getBraodcastInterface(), port);
        this.datagramSocket.send(packet);
    }

    private InetAddress getBraodcastInterface() throws SocketException {
        if (this.interfaceAddress != null) {
            return this.interfaceAddress;
        }

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

                this.interfaceAddress = broadcast;
                break label;
            }
        }

        return this.getBraodcastInterface();
    }
}
