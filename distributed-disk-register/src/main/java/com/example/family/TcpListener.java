package com.example.family;

import family.ChatMessage;
import family.NodeInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpListener {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private final BroadcastQueue queue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread listenerThread;

    public TcpListener(NodeRegistry registry, NodeInfo self, BroadcastQueue queue) {
        this.registry = registry;
        this.self = self;
        this.queue = queue;
    }

    public synchronized void start() {
        if (running.get()) {
            System.out.println("TCP listener already running");
            return;
        }

        running.set(true);
        listenerThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(6666);
                System.out.printf("Leader listening for text on TCP %s:%d%n", self.getHost(), 6666);

                while (running.get()) {
                    try {
                        Socket client = serverSocket.accept();
                        new Thread(() -> handleClient(client)).start();
                    } catch (IOException e) {
                        if (running.get()) {
                            System.err.println("Error accepting client: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error in TCP listener: " + e.getMessage());
                }
            }
        }, "TcpListener");

        listenerThread.start();
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        System.out.println("TCP listener stopped");
    }

    private void handleClient(Socket client) {
        System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty()) continue;

                long ts = System.currentTimeMillis();

                System.out.println("Received from TCP: " + text);

                ChatLogger.logMessage(self.getHost(), self.getPort(), text);

                ChatMessage msg = ChatMessage.newBuilder()
                        .setText(text)
                        .setFromHost(self.getHost())
                        .setFromPort(self.getPort())
                        .setTimestamp(ts)
                        .build();

                broadcastToFamily(msg);
            }

        } catch (IOException e) {
            System.err.println("TCP client handler error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcastToFamily(ChatMessage msg) {
        for (NodeInfo n : registry.snapshot()) {
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                continue;
            }

            queue.enqueue(n, msg, self);
            System.out.printf("Enqueued message for %s:%d%n", n.getHost(), n.getPort());
        }
    }
}
