package com.example.family;

import family.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LeaderElection {

    private final NodeInfo self;
    private final NodeRegistry registry;
    private final AtomicReference<NodeInfo> currentLeader;

    public LeaderElection(NodeInfo self, NodeRegistry registry) {
        this.self = self;
        this.registry = registry;
        this.currentLeader = new AtomicReference<>(null);
    }

    public void setLeader(NodeInfo leader) {
        currentLeader.set(leader);
        System.out.printf("★ Leader is now: %s:%d%n", leader.getHost(), leader.getPort());
    }

    public NodeInfo getLeader() {
        return currentLeader.get();
    }

    public boolean isLeader() {
        NodeInfo leader = currentLeader.get();
        return leader != null &&
               leader.getHost().equals(self.getHost()) &&
               leader.getPort() == self.getPort();
    }

    public void startElection() {
        System.out.println("⚡ Starting leader election...");

        List<NodeInfo> members = registry.snapshot();
        boolean foundHigher = false;

        for (NodeInfo node : members) {
            if (node.getPort() > self.getPort()) {
                if (sendElectionMessage(node)) {
                    foundHigher = true;
                }
            }
        }

        if (!foundHigher) {
            becomeLeader();
        }
    }

    private boolean sendElectionMessage(NodeInfo target) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(target.getHost(), target.getPort())
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                    FamilyServiceGrpc.newBlockingStub(channel);

            ElectionMessage msg = ElectionMessage.newBuilder()
                    .setCandidatePort(self.getPort())
                    .setCandidateHost(self.getHost())
                    .build();

            stub.election(msg);
            System.out.printf("→ Sent election message to %s:%d%n",
                    target.getHost(), target.getPort());
            return true;

        } catch (Exception e) {
            System.err.printf("✗ Failed to contact %s:%d for election%n",
                    target.getHost(), target.getPort());
            return false;
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    public void becomeLeader() {
        System.out.println("👑 I am the new leader!");
        setLeader(self);
        announceCoordinator();
    }

    private void announceCoordinator() {
        List<NodeInfo> members = registry.snapshot();

        for (NodeInfo node : members) {
            if (node.getHost().equals(self.getHost()) && node.getPort() == self.getPort()) {
                continue;
            }
            sendCoordinatorMessage(node);
        }
    }

    private void sendCoordinatorMessage(NodeInfo target) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(target.getHost(), target.getPort())
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                    FamilyServiceGrpc.newBlockingStub(channel);

            CoordinatorMessage msg = CoordinatorMessage.newBuilder()
                    .setLeaderPort(self.getPort())
                    .setLeaderHost(self.getHost())
                    .build();

            stub.coordinator(msg);
            System.out.printf("→ Announced leadership to %s:%d%n",
                    target.getHost(), target.getPort());

        } catch (Exception e) {
            System.err.printf("✗ Failed to announce to %s:%d%n",
                    target.getHost(), target.getPort());
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    public NodeInfo findInitialLeader() {
        List<NodeInfo> members = registry.snapshot();
        NodeInfo highest = self;

        for (NodeInfo node : members) {
            if (node.getPort() > highest.getPort()) {
                highest = node;
            }
        }

        return highest;
    }
}
