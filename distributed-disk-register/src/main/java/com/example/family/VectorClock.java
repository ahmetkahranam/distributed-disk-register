package com.example.family;

import java.util.HashMap;
import java.util.Map;

public class VectorClock {
    
    private final Map<String, Long> clocks;
    
    public VectorClock() {
        this.clocks = new HashMap<>();
    }
    
    public VectorClock(Map<String, Long> clocks) {
        this.clocks = new HashMap<>(clocks);
    }
    
    public void increment(String nodeId) {
        clocks.put(nodeId, clocks.getOrDefault(nodeId, 0L) + 1);
    }
    
    public void merge(VectorClock other) {
        for (Map.Entry<String, Long> entry : other.clocks.entrySet()) {
            String nodeId = entry.getKey();
            Long otherValue = entry.getValue();
            clocks.put(nodeId, Math.max(clocks.getOrDefault(nodeId, 0L), otherValue));
        }
    }
    
    public boolean happenedBefore(VectorClock other) {
        boolean anyLess = false;
        
        for (Map.Entry<String, Long> entry : clocks.entrySet()) {
            String nodeId = entry.getKey();
            Long thisValue = entry.getValue();
            Long otherValue = other.clocks.getOrDefault(nodeId, 0L);
            
            if (thisValue > otherValue) {
                return false;
            }
            if (thisValue < otherValue) {
                anyLess = true;
            }
        }
        
        for (Map.Entry<String, Long> entry : other.clocks.entrySet()) {
            String nodeId = entry.getKey();
            if (!clocks.containsKey(nodeId) && entry.getValue() > 0) {
                anyLess = true;
            }
        }
        
        return anyLess;
    }
    
    public boolean happenedAfter(VectorClock other) {
        return other.happenedBefore(this);
    }
    
    public boolean isConcurrentWith(VectorClock other) {
        return !happenedBefore(other) && !happenedAfter(other);
    }
    
    public long get(String nodeId) {
        return clocks.getOrDefault(nodeId, 0L);
    }
    
    public Map<String, Long> getClocks() {
        return new HashMap<>(clocks);
    }
    
    public VectorClock copy() {
        return new VectorClock(this.clocks);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : clocks.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof VectorClock)) return false;
        VectorClock other = (VectorClock) obj;
        return clocks.equals(other.clocks);
    }
    
    @Override
    public int hashCode() {
        return clocks.hashCode();
    }
}
