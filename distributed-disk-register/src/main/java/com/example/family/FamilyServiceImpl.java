package com.example.family;

import family.ChatMessage;
import family.CoordinatorMessage;
import family.ElectionMessage;
import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import io.grpc.stub.StreamObserver;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private LeaderElection leaderElection;
    private final ChatStreamManager streamManager;

    public FamilyServiceImpl(NodeRegistry registry, NodeInfo self) {
        this.registry = registry;
        this.self = self;
        this.registry.add(self);
        this.streamManager = new ChatStreamManager();
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

    @Override
    public void receiveChat(ChatMessage request, StreamObserver<Empty> responseObserver) {
        System.out.println("[MSG] Incoming message:");
        System.out.println("  From: " + request.getFromHost() + ":" + request.getFromPort());
        System.out.println("  Text: " + request.getText());
        System.out.println("  Timestamp: " + request.getTimestamp());
        System.out.println("--------------------------------------");

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void election(ElectionMessage request, StreamObserver<Empty> responseObserver) {
        System.out.printf("[ELECTION] Received election message from %s:%d%n",
                request.getCandidateHost(), request.getCandidatePort());

        if (leaderElection != null) {
            leaderElection.startElection();
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void coordinator(CoordinatorMessage request, StreamObserver<Empty> responseObserver) {
        System.out.printf("[LEADER] Received coordinator message: Leader is %s:%d%n",
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
    
    @Override
    public StreamObserver<ChatMessage> chatStream(StreamObserver<ChatMessage> responseObserver) {
        String streamId = "stream-" + System.currentTimeMillis();
        streamManager.registerStream(streamId, responseObserver);
        
        return new StreamObserver<ChatMessage>() {
            @Override
            public void onNext(ChatMessage message) {
                System.out.println("[STREAM] Received message from stream: " + message.getText());
                
                // Broadcast to all connected streams
                streamManager.broadcastMessage(message);
                
                // Also log the message
                ChatLogger.logMessage(message.getFromHost(), message.getFromPort(), message.getText());
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("[STREAM] Error in stream: " + t.getMessage());
                streamManager.unregisterStream(streamId);
            }
            
            @Override
            public void onCompleted() {
                System.out.println("[STREAM] Stream completed");
                streamManager.unregisterStream(streamId);
                responseObserver.onCompleted();
            }
        };
    }
}
