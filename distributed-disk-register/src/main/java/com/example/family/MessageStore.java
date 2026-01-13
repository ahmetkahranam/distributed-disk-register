package com.example.family;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageStore {
    private static final String MESSAGES_DIR_PREFIX = "messages";
    private final Map<Integer, String> cache;
    private final Path messagesPath;
    private final int port;
    private final IOMode ioMode;
    
    public enum IOMode {
        BUFFERED,    // BufferedWriter/BufferedReader (default)
        UNBUFFERED   // FileOutputStream/FileInputStream (direct)
    }

    public MessageStore(int port) {
        this(port, IOMode.BUFFERED);
    }
    
    public MessageStore(int port, IOMode ioMode) {
        this.cache = new ConcurrentHashMap<>();
        this.port = port;
        this.ioMode = ioMode;
        this.messagesPath = Paths.get(MESSAGES_DIR_PREFIX + "-" + port);
        
        try {
            Files.createDirectories(messagesPath);
            System.out.println("[STORE] Using " + ioMode + " IO mode for port " + port);
        } catch (IOException e) {
            System.err.println("Failed to create messages directory: " + e.getMessage());
        }
    }
    
    public void cleanup() {
        try {
            Files.walk(messagesPath)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
            System.out.println("[CLEANUP] Deleted messages for port " + port);
        } catch (IOException e) {
            System.err.println("Failed to cleanup messages: " + e.getMessage());
        }
    }

    public void store(int id, String message) throws IOException {
        Path filePath = messagesPath.resolve(id + ".msg");
        
        if (ioMode == IOMode.UNBUFFERED) {
            // Unbuffered - Direct FileOutputStream
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                byte[] bytes = message.getBytes("UTF-8");
                fos.write(bytes);
                // NO fsync() - let OS handle flushing
            }
        } else {
            // Buffered - BufferedWriter (default)
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                writer.write(message);
            }
        }
        
        cache.put(id, message);
        
        // Log less frequently to avoid overhead
        if (id % 100 == 0) {
            System.out.println("[STORE-" + ioMode + "] Saved message " + id + " to disk");
        }
    }

    public String get(int id) throws IOException {
        // Cache devre dışı - test için
        // if (cache.containsKey(id)) {
        //     return cache.get(id);
        // }
        
        Path filePath = messagesPath.resolve(id + ".msg");
        
        if (!Files.exists(filePath)) {
            return null;
        }
        
        if (ioMode == IOMode.UNBUFFERED) {
            // Unbuffered - Direct FileInputStream
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] bytes = new byte[(int) Files.size(filePath)];
                fis.read(bytes);
                String message = new String(bytes, "UTF-8");
                cache.put(id, message);
                return message;
            }
        } else {
            // Buffered - BufferedReader (default)
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (content.length() > 0) {
                        content.append("\n");
                    }
                    content.append(line);
                }
                String message = content.toString();
                cache.put(id, message);
                return message;
            }
        }
    }

    public boolean exists(int id) {
        if (cache.containsKey(id)) {
            return true;
        }
        
        Path filePath = messagesPath.resolve(id + ".msg");
        return Files.exists(filePath);
    }
}
