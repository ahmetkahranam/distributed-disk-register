package com.example.family;

import family.ChatMessage;

public class VersionedMessage {
    
    private final ChatMessage message;
    private final VectorClock vectorClock;
    private final String originNodeId;
    
    public VersionedMessage(ChatMessage message, VectorClock vectorClock, String originNodeId) {
        this.message = message;
        this.vectorClock = vectorClock;
        this.originNodeId = originNodeId;
    }
    
    public ChatMessage getMessage() {
        return message;
    }
    
    public VectorClock getVectorClock() {
        return vectorClock;
    }
    
    public String getOriginNodeId() {
        return originNodeId;
    }
    
    public static VersionedMessage resolveConflict(VersionedMessage msg1, VersionedMessage msg2) {
        VectorClock clock1 = msg1.getVectorClock();
        VectorClock clock2 = msg2.getVectorClock();
        
        if (clock1.happenedBefore(clock2)) {
            System.out.println("[CONFLICT] Resolved: msg2 is newer (causality)");
            return msg2;
        }
        
        if (clock2.happenedBefore(clock1)) {
            System.out.println("[CONFLICT] Resolved: msg1 is newer (causality)");
            return msg1;
        }
        
        long ts1 = msg1.getMessage().getTimestamp();
        long ts2 = msg2.getMessage().getTimestamp();
        
        if (ts1 != ts2) {
            System.out.println("[CONFLICT] Resolved: using timestamp tie-breaker");
            return ts1 > ts2 ? msg1 : msg2;
        }
        
        System.out.println("[CONFLICT] Resolved: using nodeId tie-breaker");
        return msg1.getOriginNodeId().compareTo(msg2.getOriginNodeId()) > 0 ? msg1 : msg2;
    }
    
    @Override
    public String toString() {
        return String.format("VersionedMessage{origin=%s, clock=%s, text=%s}", 
                originNodeId, vectorClock, message.getText());
    }
}
