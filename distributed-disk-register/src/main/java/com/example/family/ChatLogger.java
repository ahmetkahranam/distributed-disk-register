package com.example.family;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatLogger {

    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = LOG_DIR + "/chat-messages.log";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
    }

    public static synchronized void logMessage(String fromHost, int fromPort, String text) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logEntry = String.format("[%s] FROM=%s:%d TEXT=%s%n",
                timestamp, fromHost, fromPort, text);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(logEntry);
            writer.flush();
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }

    public static synchronized void logBroadcast(String fromHost, int fromPort,
                                                  String toHost, int toPort,
                                                  String text, boolean success) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String status = success ? "SUCCESS" : "FAILED";
        String logEntry = String.format("[%s] BROADCAST FROM=%s:%d TO=%s:%d STATUS=%s TEXT=%s%n",
                timestamp, fromHost, fromPort, toHost, toPort, status, text);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(logEntry);
            writer.flush();
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }
}
