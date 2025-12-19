package com.example.family;

import family.ChatMessage;
import family.FamilyServiceGrpc;
import family.NodeInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class BroadcastQueue {

    private static final int MAX_RETRIES = 3;
    private final BlockingQueue<BroadcastTask> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private volatile boolean running = true;

    public BroadcastQueue() {
        startWorkers();
    }

    public void enqueue(NodeInfo target, ChatMessage message, NodeInfo sender) {
        BroadcastTask task = new BroadcastTask(target, message, sender);
        try {
            queue.put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Failed to enqueue broadcast task: " + e.getMessage());
        }
    }

    private void startWorkers() {
        for (int i = 0; i < 5; i++) {
            executor.submit(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (running) {
            try {
                BroadcastTask task = queue.take();
                processTask(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processTask(BroadcastTask task) {
        NodeInfo target = task.getTarget();
        ChatMessage msg = task.getMessage();
        NodeInfo sender = task.getSender();

        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(target.getHost(), target.getPort())
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                    FamilyServiceGrpc.newBlockingStub(channel);

            stub.receiveChat(msg);

            System.out.printf("✓ Broadcasted to %s:%d%n", target.getHost(), target.getPort());
            ChatLogger.logBroadcast(sender.getHost(), sender.getPort(),
                    target.getHost(), target.getPort(), msg.getText(), true);

        } catch (Exception e) {
            System.err.printf("✗ Failed to send to %s:%d (attempt %d/%d): %s%n",
                    target.getHost(), target.getPort(),
                    task.getRetryCount() + 1, MAX_RETRIES, e.getMessage());

            if (task.getRetryCount() < MAX_RETRIES) {
                task.incrementRetry();
                try {
                    queue.put(task);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } else {
                ChatLogger.logBroadcast(sender.getHost(), sender.getPort(),
                        target.getHost(), target.getPort(), msg.getText(), false);
            }
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    public void shutdown() {
        running = false;
        executor.shutdownNow();
    }
}
