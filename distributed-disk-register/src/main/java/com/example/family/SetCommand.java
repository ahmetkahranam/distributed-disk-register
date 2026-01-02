package com.example.family;

public class SetCommand implements Command {
    private final int id;
    private final String message;
    private final MessageStore messageStore;

    public SetCommand(int id, String message, MessageStore messageStore) {
        this.id = id;
        this.message = message;
        this.messageStore = messageStore;
    }

    @Override
    public String execute() {
        try {
            messageStore.store(id, message);
            return "OK";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Override
    public CommandType getType() {
        return CommandType.SET;
    }

    public int getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }
}
