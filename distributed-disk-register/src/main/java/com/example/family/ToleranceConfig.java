package com.example.family;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ToleranceConfig {
    private static final String CONFIG_FILE = "tolerance.conf";
    private static final int DEFAULT_TOLERANCE = 1;
    
    private int tolerance;

    public ToleranceConfig() {
        this.tolerance = loadTolerance();
    }

    private int loadTolerance() {
        Path configPath = Paths.get(CONFIG_FILE);
        
        if (!Files.exists(configPath)) {
            System.out.println("[CONFIG] tolerance.conf not found, using default: " + DEFAULT_TOLERANCE);
            return DEFAULT_TOLERANCE;
        }

        try {
            String content = Files.readString(configPath).trim();
            
            if (content.startsWith("TOLERANCE=")) {
                String value = content.substring("TOLERANCE=".length()).trim();
                int t = Integer.parseInt(value);
                
                if (t < 1 || t > 7) {
                    System.err.println("[CONFIG] Invalid tolerance value: " + t + ", using default");
                    return DEFAULT_TOLERANCE;
                }
                
                System.out.println("[CONFIG] Loaded tolerance: " + t);
                return t;
            } else {
                System.err.println("[CONFIG] Invalid format in tolerance.conf, using default");
                return DEFAULT_TOLERANCE;
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[CONFIG] Error reading tolerance.conf: " + e.getMessage());
            return DEFAULT_TOLERANCE;
        }
    }

    public int getTolerance() {
        return tolerance;
    }

    public void reload() {
        this.tolerance = loadTolerance();
    }
}
