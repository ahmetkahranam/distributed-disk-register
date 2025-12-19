package com.example.family;

import family.ChatMessage;
import family.NodeInfo;

public class BroadcastTask {
    private final NodeInfo target;
    private final ChatMessage message;
    private final NodeInfo sender;
    private int retryCount;

    public BroadcastTask(NodeInfo target, ChatMessage message, NodeInfo sender) {
        this.target = target;
        this.message = message;
        this.sender = sender;
        this.retryCount = 0;
    }

    public NodeInfo getTarget() {
        return target;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public NodeInfo getSender() {
        return sender;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
