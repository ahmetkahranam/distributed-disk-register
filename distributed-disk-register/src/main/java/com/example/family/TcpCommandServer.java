package com.example.family;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpCommandServer {
    private final int port;
    private final CommandParser parser;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public TcpCommandServer(int port, MessageStore messageStore) {
        this.port = port;
        this.parser = new CommandParser(messageStore);
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("[TCP] Command server listening on port " + port);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        new Thread(() -> handleClient(client)).start();
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("[TCP] Error accepting client: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[TCP] Failed to start server: " + e.getMessage());
            }
        }, "TcpCommandServer").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[TCP] Error stopping server: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        System.out.println("[TCP] Client connected: " + client.getRemoteSocketAddress());
        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                System.out.println("[TCP] Received: " + line);

                Command command = parser.parse(line);
                
                String response;
                if (command == null) {
                    response = "ERROR: Invalid command";
                } else {
                    if (command instanceof SetCommand) {
                        SetCommand setCmd = (SetCommand) command;
                        response = handleSetCommand(setCmd.getId(), setCmd.getMessage());
                    } else if (command instanceof GetCommand) {
                        GetCommand getCmd = (GetCommand) command;
                        response = handleGetCommand(getCmd.getId());
                    } else {
                        response = command.execute();
                    }
                }

                out.println(response);
                System.out.println("[TCP] Response: " + response);
            }
        } catch (IOException e) {
            System.err.println("[TCP] Client handler error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    protected String handleSetCommand(int id, String message) {
        Command cmd = new SetCommand(id, message, parser.getMessageStore());
        return cmd.execute();
    }

    protected String handleGetCommand(int id) {
        Command cmd = new GetCommand(id, parser.getMessageStore());
        return cmd.execute();
    }
}
