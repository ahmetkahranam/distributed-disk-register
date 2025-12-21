package com.example.family;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import family.ChatMessage;

public class MultiLeaderStore {
    
    private final ConcurrentMap<String, VersionedMessage> messages;
    private final VectorClock localClock;
    private final String nodeId;
    
    public MultiLeaderStore(String nodeId) {
        this.messages = new ConcurrentHashMap<>();
        this.localClock = new VectorClock();
        this.nodeId = nodeId;
    }
    
    public VersionedMessage storeLocal(ChatMessage message) {
        localClock.increment(nodeId);
        VectorClock messageClock = localClock.copy();
        
        String messageId = generateMessageId(message);
        VersionedMessage versioned = new VersionedMessage(message, messageClock, nodeId);
        
        messages.put(messageId, versioned);
        System.out.println("[MULTI-LEADER] Stored local: " + messageId + " clock=" + messageClock);
        
        return versioned;
    }
    
    public void storeRemote(VersionedMessage incomingMessage) {
        ChatMessage message = incomingMessage.getMessage();
        String messageId = generateMessageId(message);
        
        localClock.merge(incomingMessage.getVectorClock());
        
        VersionedMessage existing = messages.get(messageId);
        
        if (existing == null) {
            messages.put(messageId, incomingMessage);
            System.out.println("[MULTI-LEADER] Stored remote: " + messageId);
        } else {
            System.out.println("[CONFLICT] Detected for message: " + messageId);
            VersionedMessage resolved = VersionedMessage.resolveConflict(existing, incomingMessage);
            messages.put(messageId, resolved);
            System.out.println("[CONFLICT] Kept version from: " + resolved.getOriginNodeId());
        }
    }
    
    public List<VersionedMessage> getAllMessages() {
        return new ArrayList<>(messages.values());
    }
    
    public VectorClock getCurrentClock() {
        return localClock.copy();
    }
    
    private String generateMessageId(ChatMessage message) {
        return message.getFromHost() + ":" + message.getFromPort() + ":" + message.getTimestamp();
    }
    
    public void clear() {
        messages.clear();
    }
    
    public int size() {
        return messages.size();
    }
}
