package me.micartey.jation.adapter.network;

import me.micartey.jation.JationObserver;
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
     *
     * @param event published event
     * @param additional additional classes that should be published
     */
    void publish(JationEvent<?> event, Object... additional);

    /**
     * Called by {@link JationObserver#addAdapter(NetworkAdapter)} to set its own instance.
     * Use this instance for any operations that require the jation observer class.
     *
     * @param observer instance to be used
     */
    void setObserver(JationObserver observer);
}
