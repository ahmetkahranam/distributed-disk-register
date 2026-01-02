package com.example.family;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import family.MessageId;
import family.StorageServiceGrpc;
import family.StoreResult;
import family.StoredMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class DistributedLeaderStore {
    
    private final MessageStore localStore;
    private final ToleranceConfig config;
    private final List<MemberNode> members;
    private final Map<Integer, List<String>> messageLocations;
    private final AtomicInteger roundRobinIndex;

    public DistributedLeaderStore(MessageStore localStore, ToleranceConfig config) {
        this.localStore = localStore;
        this.config = config;
        this.members = new ArrayList<>();
        this.messageLocations = new ConcurrentHashMap<>();
        this.roundRobinIndex = new AtomicInteger(0);
    }

    public void addMember(String host, int port) {
        MemberNode node = new MemberNode(host, port);
        members.add(node);
        System.out.println("[LEADER] Added member: " + host + ":" + port);
    }

    public String storeMessage(int id, String text) {
        try {
            localStore.store(id, text);

            int tolerance = config.getTolerance();
            
            if (members.isEmpty()) {
                System.out.println("[LEADER] No members available, stored locally only");
                return "OK (local only)";
            }

            List<MemberNode> selectedMembers = selectMembers(tolerance);

            List<String> successfulMembers = new ArrayList<>();
            for (MemberNode member : selectedMembers) {
                if (replicateToMember(member, id, text)) {
                    successfulMembers.add(member.getId());
                }
            }

            messageLocations.put(id, successfulMembers);

            System.out.println("[LEADER] Message " + id + " stored on: local + " + successfulMembers);

            if (successfulMembers.size() < tolerance) {
                return "OK (WARNING: only " + successfulMembers.size() + "/" + tolerance + " replicas created)";
            }

            return "OK";

        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String getMessage(int id) {
        try {
            String text = localStore.get(id);
            if (text != null) {
                System.out.println("[LEADER] Message " + id + " found locally");
                return text;
            }

            List<String> locations = messageLocations.get(id);
            if (locations == null || locations.isEmpty()) {
                return null;
            }

            for (String memberId : locations) {
                MemberNode member = findMember(memberId);
                if (member != null && member.isAlive()) {
                    String retrieved = retrieveFromMember(member, id);
                    if (retrieved != null) {
                        System.out.println("[LEADER] Message " + id + " retrieved from " + memberId);
                        return retrieved;
                    }
                }
            }

            return null;

        } catch (IOException e) {
            System.err.println("[LEADER] Error retrieving message: " + e.getMessage());
            return null;
        }
    }

    private List<MemberNode> selectMembers(int count) {
        List<MemberNode> selected = new ArrayList<>();
        
        if (members.isEmpty()) {
            return selected;
        }

        int availableCount = Math.min(count, members.size());
        int startIndex = roundRobinIndex.getAndUpdate(i -> (i + 1) % members.size());

        for (int i = 0; i < availableCount; i++) {
            int index = (startIndex + i) % members.size();
            MemberNode member = members.get(index);
            if (member.isAlive()) {
                selected.add(member);
            }
        }

        return selected;
    }

    private boolean replicateToMember(MemberNode member, int id, String text) {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            try {
                StorageServiceGrpc.StorageServiceBlockingStub stub = 
                        StorageServiceGrpc.newBlockingStub(channel);

                StoredMessage message = StoredMessage.newBuilder()
                        .setId(id)
                        .setText(text)
                        .build();

                StoreResult result = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .store(message);

                return result.getSuccess();

            } finally {
                channel.shutdown();
                channel.awaitTermination(1, TimeUnit.SECONDS);
            }

        } catch (StatusRuntimeException e) {
            System.err.println("[LEADER] Failed to replicate to " + member.getId() + ": " + e.getMessage());
            member.markDead();
            return false;
        } catch (Exception e) {
            System.err.println("[LEADER] Error replicating to " + member.getId() + ": " + e.getMessage());
            return false;
        }
    }

    private String retrieveFromMember(MemberNode member, int id) {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            try {
                StorageServiceGrpc.StorageServiceBlockingStub stub = 
                        StorageServiceGrpc.newBlockingStub(channel);

                MessageId messageId = MessageId.newBuilder()
                        .setId(id)
                        .build();

                StoredMessage message = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .retrieve(messageId);

                return message.getText();

            } finally {
                channel.shutdown();
                channel.awaitTermination(1, TimeUnit.SECONDS);
            }

        } catch (StatusRuntimeException e) {
            System.err.println("[LEADER] Failed to retrieve from " + member.getId() + ": " + e.getMessage());
            member.markDead();
            return null;
        } catch (Exception e) {
            System.err.println("[LEADER] Error retrieving from " + member.getId() + ": " + e.getMessage());
            return null;
        }
    }

    private MemberNode findMember(String memberId) {
        for (MemberNode member : members) {
            if (member.getId().equals(memberId)) {
                return member;
            }
        }
        return null;
    }

    public void printStatistics() {
        System.out.println("\n=== Message Distribution Statistics ===");
        
        Map<String, Integer> memberCounts = new HashMap<>();
        memberCounts.put("local", (int) messageLocations.keySet().stream().count());
        
        for (MemberNode member : members) {
            memberCounts.put(member.getId(), 0);
        }

        for (List<String> locations : messageLocations.values()) {
            for (String memberId : locations) {
                memberCounts.merge(memberId, 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : memberCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " messages");
        }
        
        System.out.println("=======================================\n");
    }

    private static class MemberNode {
        private final String host;
        private final int port;
        private volatile boolean alive;

        public MemberNode(String host, int port) {
            this.host = host;
            this.port = port;
            this.alive = true;
        }

        public String getId() {
            return host + ":" + port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public boolean isAlive() {
            return alive;
        }

        public void markDead() {
            if (alive) {
                System.err.println("[CRASH] Member " + getId() + " marked as dead");
                alive = false;
            }
        }
    }
}
