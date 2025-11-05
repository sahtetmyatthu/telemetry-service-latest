package org.mdt.telemetry_service.listener;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UdpInputStream extends InputStream {

    private final DatagramSocket socket;
    private final Queue<byte[]> packetQueue = new ConcurrentLinkedQueue<>();
    private byte[] buffer;
    private int position = 0;
    private int length = 0;

    @Getter
    private InetAddress senderAddress;

    @Getter
    private int senderPort;

    public UdpInputStream(DatagramSocket socket) {
        this.socket = socket;
        this.buffer = new byte[4096];
    }

    public UdpInputStream(byte[] data, InetSocketAddress sender) {
        this.socket = null;
        this.packetQueue.offer(data);
        this.buffer = data;
        this.length = data.length;
        this.senderAddress = sender.getAddress();
        this.senderPort = sender.getPort();
    }

    public void appendData(byte[] data, InetSocketAddress sender) {
        if (socket == null) {
            packetQueue.offer(data);
            this.senderAddress = sender.getAddress();
            this.senderPort = sender.getPort();
        }
    }

    @Override
    public int read() throws IOException {
        while (position >= length && !packetQueue.isEmpty()) {
            buffer = packetQueue.poll();
            position = 0;
            length = buffer.length;
        }

        if (position >= length) {
            if (socket != null) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                length = packet.getLength();
                position = 0;
                senderAddress = packet.getAddress();
                senderPort = packet.getPort();
                return buffer[position++] & 0xFF;
            }
            return -1;
        }
        return buffer[position++] & 0xFF;
    }

    @Override
    public int available() throws IOException {
        if (socket != null) {
            return 0;
        }
        return length - position + packetQueue.stream().mapToInt(b -> b.length).sum();
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        packetQueue.clear();
    }
}
