package com.example.family;

import family.ChatMessage;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChatStreamManager {
    
    private final ConcurrentMap<String, StreamObserver<ChatMessage>> activeStreams = new ConcurrentHashMap<>();
    
    public void registerStream(String nodeId, StreamObserver<ChatMessage> observer) {
        activeStreams.put(nodeId, observer);
        System.out.println("[STREAM] Registered stream for node: " + nodeId);
    }
    
    public void unregisterStream(String nodeId) {
        activeStreams.remove(nodeId);
        System.out.println("[STREAM] Unregistered stream for node: " + nodeId);
    }
    
    public void broadcastMessage(ChatMessage message) {
        activeStreams.forEach((nodeId, observer) -> {
            try {
                observer.onNext(message);
                System.out.println("[STREAM] Sent message to: " + nodeId);
            } catch (Exception e) {
                System.err.println("[STREAM] Failed to send to " + nodeId + ": " + e.getMessage());
                activeStreams.remove(nodeId);
            }
        });
    }
    
    public int getActiveStreamCount() {
        return activeStreams.size();
    }
}
