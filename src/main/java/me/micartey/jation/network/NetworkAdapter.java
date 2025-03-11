package me.micartey.jation.network;

import me.micartey.jation.interfaces.JationEvent;

import java.net.InetAddress;

public interface NetworkAdapter {

    void listen();

    void send(String message, InetAddress address, int port);

    void broadcast(String message, int port);

    void publish(JationEvent<?> event, Object... additional);

}
