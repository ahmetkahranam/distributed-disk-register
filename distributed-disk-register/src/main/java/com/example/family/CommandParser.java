package com.example.family;

public class CommandParser {
    private final MessageStore messageStore;

    public CommandParser(MessageStore messageStore) {
        this.messageStore = messageStore;
    }

    public MessageStore getMessageStore() {
        return messageStore;
    }

    public Command parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String[] parts = line.trim().split("\\s+", 3);
        
        if (parts.length == 0) {
            return null;
        }

        String cmd = parts[0].toUpperCase();

        try {
            switch (cmd) {
                case "SET":
                    if (parts.length < 3) {
                        return null;
                    }
                    int setId = Integer.parseInt(parts[1]);
                    String message = parts[2];
                    return new SetCommand(setId, message, messageStore);

                case "GET":
                    if (parts.length < 2) {
                        return null;
                    }
                    int getId = Integer.parseInt(parts[1]);
                    return new GetCommand(getId, messageStore);

                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
