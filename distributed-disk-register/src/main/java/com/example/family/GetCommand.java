package com.example.family;

public class GetCommand implements Command {
    private final int id;
    private final MessageStore messageStore;

    public GetCommand(int id, MessageStore messageStore) {
        this.id = id;
        this.messageStore = messageStore;
    }

    @Override
    public String execute() {
        try {
            String message = messageStore.get(id);
            return message != null ? message : "NOT_FOUND";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Override
    public CommandType getType() {
        return CommandType.GET;
    }

    public int getId() {
        return id;
    }
}
