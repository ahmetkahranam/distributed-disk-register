package com.example.family;

import family.MessageId;
import family.StorageServiceGrpc;
import family.StoreResult;
import family.StoredMessage;
import io.grpc.stub.StreamObserver;

public class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {
    
    private final MessageStore messageStore;

    public StorageServiceImpl(MessageStore messageStore) {
        this.messageStore = messageStore;
    }

    @Override
    public void store(StoredMessage request, StreamObserver<StoreResult> responseObserver) {
        try {
            messageStore.store(request.getId(), request.getText());
            
            StoreResult result = StoreResult.newBuilder()
                    .setSuccess(true)
                    .setMessage("OK")
                    .build();
            
            responseObserver.onNext(result);
            responseObserver.onCompleted();
            
            System.out.println("[gRPC] Stored message " + request.getId());
        } catch (Exception e) {
            StoreResult result = StoreResult.newBuilder()
                    .setSuccess(false)
                    .setMessage("ERROR: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(result);
            responseObserver.onCompleted();
            
            System.err.println("[gRPC] Failed to store message: " + e.getMessage());
        }
    }

    @Override
    public void retrieve(MessageId request, StreamObserver<StoredMessage> responseObserver) {
        try {
            String text = messageStore.get(request.getId());
            
            if (text != null) {
                StoredMessage message = StoredMessage.newBuilder()
                        .setId(request.getId())
                        .setText(text)
                        .build();
                
                responseObserver.onNext(message);
                responseObserver.onCompleted();
                
                System.out.println("[gRPC] Retrieved message " + request.getId());
            } else {
                responseObserver.onError(new Exception("Message not found: " + request.getId()));
            }
        } catch (Exception e) {
            responseObserver.onError(e);
            System.err.println("[gRPC] Failed to retrieve message: " + e.getMessage());
        }
    }
}
