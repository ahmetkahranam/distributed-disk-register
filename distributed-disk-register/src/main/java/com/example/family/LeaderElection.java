package com.example.family;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import family.CoordinatorMessage;
import family.ElectionMessage;
import family.FamilyServiceGrpc;
import family.NodeInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class LeaderElection {

    private final NodeInfo self;
    private final NodeRegistry registry;
    private final AtomicReference<NodeInfo> currentLeader;
    private final TcpListener tcpListener;

    public LeaderElection(NodeInfo self, NodeRegistry registry, TcpListener tcpListener) {
        this.self = self;
        this.registry = registry;
        this.currentLeader = new AtomicReference<>(null);
        this.tcpListener = tcpListener;
    }

    public void setLeader(NodeInfo leader) {
        NodeInfo oldLeader = currentLeader.get();
        
        if (oldLeader != null && 
            oldLeader.getHost().equals(self.getHost()) && 
            oldLeader.getPort() == self.getPort() &&
            (!leader.getHost().equals(self.getHost()) || leader.getPort() != self.getPort())) {
            
            if (tcpListener.isRunning()) {
                System.out.println("[WARNING] Stepping down as leader, stopping TCP listener");
                tcpListener.stop();
            }
        }
        
        currentLeader.set(leader);
        System.out.printf("[*] Leader is now: %s:%d%n", leader.getHost(), leader.getPort());
        
        if (leader.getHost().equals(self.getHost()) && 
            leader.getPort() == self.getPort() &&
            !tcpListener.isRunning()) {
            tcpListener.start();
        }
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
        System.out.println("[ELECTION] Starting leader election...");

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
            System.out.printf("-> Sent election message to %s:%d%n",
                    target.getHost(), target.getPort());
            return true;

        } catch (Exception e) {
            System.err.printf("[FAIL] Failed to contact %s:%d for election%n",
                    target.getHost(), target.getPort());
            return false;
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    public void becomeLeader() {
        System.out.println("[LEADER] I am the new leader!");
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
            System.out.printf("-> Announced leadership to %s:%d%n",
                    target.getHost(), target.getPort());

        } catch (Exception e) {
            System.err.printf("[FAIL] Failed to announce to %s:%d%n",
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
