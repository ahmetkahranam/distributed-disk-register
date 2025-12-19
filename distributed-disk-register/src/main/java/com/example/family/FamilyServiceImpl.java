package com.example.family;

import family.*;
import io.grpc.stub.StreamObserver;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private LeaderElection leaderElection;

    public FamilyServiceImpl(NodeRegistry registry, NodeInfo self) {
        this.registry = registry;
        this.self = self;
        this.registry.add(self);
    }

    public void setLeaderElection(LeaderElection election) {
        this.leaderElection = election;
    }

    @Override
    public void join(NodeInfo request, StreamObserver<FamilyView> responseObserver) {
        registry.add(request);

        FamilyView view = FamilyView.newBuilder()
                .addAllMembers(registry.snapshot())
                .build();

        responseObserver.onNext(view);
        responseObserver.onCompleted();
    }

    @Override
    public void getFamily(Empty request, StreamObserver<FamilyView> responseObserver) {
        FamilyView view = FamilyView.newBuilder()
                .addAllMembers(registry.snapshot())
                .build();

        responseObserver.onNext(view);
        responseObserver.onCompleted();
    }

    // Diğer düğümlerden broadcast mesajı geldiğinde
    @Override
    public void receiveChat(ChatMessage request, StreamObserver<Empty> responseObserver) {
        System.out.println("💬 Incoming message:");
        System.out.println("  From: " + request.getFromHost() + ":" + request.getFromPort());
        System.out.println("  Text: " + request.getText());
        System.out.println("  Timestamp: " + request.getTimestamp());
        System.out.println("--------------------------------------");

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void election(ElectionMessage request, StreamObserver<Empty> responseObserver) {
        System.out.printf("⚡ Received election message from %s:%d%n",
                request.getCandidateHost(), request.getCandidatePort());

        if (leaderElection != null) {
            leaderElection.startElection();
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void coordinator(CoordinatorMessage request, StreamObserver<Empty> responseObserver) {
        System.out.printf("👑 Received coordinator message: Leader is %s:%d%n",
                request.getLeaderHost(), request.getLeaderPort());

        if (leaderElection != null) {
            NodeInfo newLeader = NodeInfo.newBuilder()
                    .setHost(request.getLeaderHost())
                    .setPort(request.getLeaderPort())
                    .build();
            leaderElection.setLeader(newLeader);
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
