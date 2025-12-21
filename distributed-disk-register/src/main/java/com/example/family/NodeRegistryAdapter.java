package com.example.family;

import java.util.Collection;
import java.util.List;

import family.NodeInfo;

public class NodeRegistryAdapter extends NodeRegistry {
    
    private final RedisNodeRegistry redisRegistry;
    
    public NodeRegistryAdapter(RedisNodeRegistry redisRegistry) {
        this.redisRegistry = redisRegistry;
    }
    
    @Override
    public void add(NodeInfo node) {
        redisRegistry.add(node);
    }
    
    @Override
    public void addAll(Collection<NodeInfo> others) {
        redisRegistry.addAll(others);
    }
    
    @Override
    public List<NodeInfo> snapshot() {
        return redisRegistry.snapshot();
    }
    
    @Override
    public void remove(NodeInfo node) {
        redisRegistry.remove(node);
    }
    
    public void heartbeat(NodeInfo node) {
        redisRegistry.heartbeat(node);
    }
    
    public void close() {
        redisRegistry.close();
    }
}
