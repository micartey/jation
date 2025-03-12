package me.micartey.jation.adapter.network;

import me.micartey.jation.interfaces.JationEvent;

import java.net.InetAddress;

public interface NetworkAdapter {

    /**
     * Non-blocking method that will start some kind of listener to receive packets
     */
    void listen();

    /**
     * Event publish hook to be invoked on every event publish.
     * <br />
     * <b>Be careful</b>:
     * If you trigger an event in this method you will end in a recursion if no recursion anchor is present
     */
    void publish(JationEvent<?> event, Object... additional);

}
