package com.example.family;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import family.NodeInfo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisNodeRegistry {

    private static final String REDIS_KEY_PREFIX = "family:nodes:";
    private static final int EXPIRE_SECONDS = 30;
    
    private final JedisPool jedisPool;
    private final String clusterName;
    
    public RedisNodeRegistry(String redisHost, int redisPort, String clusterName) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        
        this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
        this.clusterName = clusterName;
        
        System.out.println("[REDIS] Connected to Redis at " + redisHost + ":" + redisPort);
    }
    
    public RedisNodeRegistry(String clusterName) {
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        
        this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
        this.clusterName = clusterName;
        
        System.out.println("[REDIS] Connected to Redis at " + redisHost + ":" + redisPort);
    }
    
    public void add(NodeInfo node) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = getNodeKey(node);
            String value = node.getHost() + ":" + node.getPort();
            jedis.setex(key, EXPIRE_SECONDS, value);
            System.out.println("[REDIS] Added node: " + value);
        } catch (Exception e) {
            System.err.println("[REDIS] Failed to add node: " + e.getMessage());
        }
    }
    
    public void addAll(Collection<NodeInfo> others) {
        others.forEach(this::add);
    }
    
    public List<NodeInfo> snapshot() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pattern = REDIS_KEY_PREFIX + clusterName + ":*";
            Set<String> keys = jedis.keys(pattern);
            
            return keys.stream()
                    .map(key -> {
                        String value = jedis.get(key);
                        if (value != null) {
                            String[] parts = value.split(":");
                            return NodeInfo.newBuilder()
                                    .setHost(parts[0])
                                    .setPort(Integer.parseInt(parts[1]))
                                    .build();
                        }
                        return null;
                    })
                    .filter(node -> node != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[REDIS] Failed to get snapshot: " + e.getMessage());
            return List.of();
        }
    }
    
    public void remove(NodeInfo node) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = getNodeKey(node);
            jedis.del(key);
            System.out.println("[REDIS] Removed node: " + node.getHost() + ":" + node.getPort());
        } catch (Exception e) {
            System.err.println("[REDIS] Failed to remove node: " + e.getMessage());
        }
    }
    
    public void heartbeat(NodeInfo node) {
        add(node); // Refresh TTL
    }
    
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            System.out.println("[REDIS] Connection closed");
        }
    }
    
    private String getNodeKey(NodeInfo node) {
        return REDIS_KEY_PREFIX + clusterName + ":" + node.getHost() + ":" + node.getPort();
    }
}
