package com.example.family;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import family.ChatMessage;
import family.MessageId;
import family.NodeInfo;
import family.StorageServiceGrpc;
import family.StoreResult;
import family.StoredMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class TcpListener {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private final BroadcastQueue queue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private final MessageStore messageStore;
    private final CommandParser commandParser;
    private final ToleranceConfig toleranceConfig;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final Map<Integer, List<String>> messageLocations = new HashMap<>();

    public TcpListener(NodeRegistry registry, NodeInfo self, BroadcastQueue queue, MessageStore messageStore) {
        this.registry = registry;
        this.self = self;
        this.queue = queue;
        this.messageStore = messageStore;
        this.commandParser = new CommandParser(messageStore);
        this.toleranceConfig = new ToleranceConfig();
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() {
        if (running.get()) {
            System.out.println("TCP listener already running");
            return;
        }

        listenerThread = new Thread(() -> {
            int maxRetries = 5;
            int retryCount = 0;
            boolean bound = false;
            
            while (retryCount < maxRetries && !bound) {
                try {
                    serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new java.net.InetSocketAddress(6666));
                    bound = true;
                    running.set(true);
                    System.out.printf("Leader listening for text on TCP %s:%d%n", self.getHost(), 6666);
                } catch (IOException e) {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        System.out.printf("Port 6666 in use, retrying in 1 second... (attempt %d/%d)%n", 
                                        retryCount, maxRetries);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } else {
                        System.err.println("Error in TCP listener after " + maxRetries + " attempts: " + e.getMessage());
                        running.set(false);
                        return;
                    }
                }
            }
            
            try {
                while (running.get()) {
                    try {
                        Socket client = serverSocket.accept();
                        new Thread(() -> handleClient(client)).start();
                    } catch (IOException e) {
                        if (running.get()) {
                            System.err.println("Error accepting client: " + e.getMessage());
                        }
                    }
                }
            } finally {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error closing server socket in finally: " + e.getMessage());
                }
            }
        }, "TcpListener");

        listenerThread.start();
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        System.out.println("TCP listener stopped");
    }

    private void handleClient(Socket client) {
        System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()));
             PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty()) continue;

                System.out.println("Received from TCP: " + text);

                Command command = commandParser.parse(text);
                
                if (command != null) {
                    String response;
                    if (command instanceof SetCommand) {
                        SetCommand setCmd = (SetCommand) command;
                        response = handleDistributedSet(setCmd.getId(), setCmd.getMessage());
                    } else if (command instanceof GetCommand) {
                        GetCommand getCmd = (GetCommand) command;
                        response = handleDistributedGet(getCmd.getId());
                    } else {
                        response = command.execute();
                    }
                    writer.println(response);
                    System.out.println("Response: " + response);
                } else {
                    long ts = System.currentTimeMillis();
                    ChatLogger.logMessage(self.getHost(), self.getPort(), text);

                    ChatMessage msg = ChatMessage.newBuilder()
                            .setText(text)
                            .setFromHost(self.getHost())
                            .setFromPort(self.getPort())
                            .setTimestamp(ts)
                            .build();

                    broadcastToFamily(msg);
                }
            }

        } catch (IOException e) {
            System.err.println("TCP client handler error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String handleDistributedSet(int id, String message) {
        try {
            messageStore.store(id, message);
            
            int tolerance = toleranceConfig.getTolerance();
            List<NodeInfo> members = getOtherMembers();
            
            if (members.isEmpty()) {
                System.out.println("[LEADER] No members available, stored locally only");
                return "OK (local only)";
            }
            
            List<NodeInfo> selectedMembers = selectMembers(members, tolerance);
            List<String> successfulMembers = new ArrayList<>();
            
            for (NodeInfo member : selectedMembers) {
                if (replicateToMember(member, id, message)) {
                    successfulMembers.add(member.getHost() + ":" + member.getPort());
                }
            }
            
            messageLocations.put(id, successfulMembers);
            
            System.out.println("[LEADER] Message " + id + " stored on: local + " + successfulMembers);
            
            if (id % 100 == 0) {
                printStatistics();
            }
            
            if (successfulMembers.size() < tolerance) {
                return "OK (WARNING: only " + successfulMembers.size() + "/" + tolerance + " replicas created)";
            }
            
            return "OK";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    private void printStatistics() {
        Map<String, Integer> memberStats = new HashMap<>();
        
        for (List<String> locations : messageLocations.values()) {
            for (String location : locations) {
                memberStats.put(location, memberStats.getOrDefault(location, 0) + 1);
            }
        }
        
        System.out.println("\n========== MESSAGE DISTRIBUTION ==========");
        System.out.println("Leader (local): " + messageLocations.size() + " messages");
        for (Map.Entry<String, Integer> entry : memberStats.entrySet()) {
            System.out.println("Member " + entry.getKey() + ": " + entry.getValue() + " messages");
        }
        System.out.println("==========================================\n");
    }

    private String handleDistributedGet(int id) {
        try {
            String text = messageStore.get(id);
            if (text != null) {
                System.out.println("[LEADER] Message " + id + " found locally");
                return text;
            }
            
            List<String> locations = messageLocations.get(id);
            
            // Eğer messageLocations'da kayıt varsa önce onlara bak
            if (locations != null && !locations.isEmpty()) {
                for (String location : locations) {
                    String[] parts = location.split(":");
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    
                    System.out.println("[GET] Trying member " + location + "...");
                    String retrieved = retrieveFromMember(host, port, id);
                    if (retrieved != null) {
                        System.out.println("[GET] SUCCESS - Message " + id + " retrieved from " + location);
                        return retrieved;
                    } else {
                        System.out.println("[GET] FAILED - Member " + location + " didn't have message " + id);
                    }
                }
            } else {
                // messageLocations boş ise TÜM üyelere bak
                System.out.println("[GET] No location info, trying all members...");
                List<NodeInfo> allMembers = getOtherMembers();
                
                for (NodeInfo member : allMembers) {
                    String location = member.getHost() + ":" + member.getPort();
                    System.out.println("[GET] Trying member " + location + "...");
                    
                    String retrieved = retrieveFromMember(member.getHost(), member.getPort(), id);
                    if (retrieved != null) {
                        System.out.println("[GET] SUCCESS - Message " + id + " retrieved from " + location);
                        return retrieved;
                    } else {
                        System.out.println("[GET] FAILED - Member " + location + " didn't have message " + id);
                    }
                }
            }
            
            return "NOT_FOUND";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private List<NodeInfo> getOtherMembers() {
        List<NodeInfo> members = new ArrayList<>();
        for (NodeInfo n : registry.snapshot()) {
            if (!(n.getHost().equals(self.getHost()) && n.getPort() == self.getPort())) {
                members.add(n);
            }
        }
        return members;
    }

    private List<NodeInfo> selectMembers(List<NodeInfo> members, int tolerance) {
        List<NodeInfo> selected = new ArrayList<>();
        int count = Math.min(tolerance, members.size());
        int start = roundRobinCounter.getAndAdd(count) % members.size();
        
        for (int i = 0; i < count; i++) {
            selected.add(members.get((start + i) % members.size()));
        }
        
        return selected;
    }

    private boolean replicateToMember(NodeInfo member, int id, String text) {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            try {
                StorageServiceGrpc.StorageServiceBlockingStub stub = 
                        StorageServiceGrpc.newBlockingStub(channel);

                StoredMessage message = StoredMessage.newBuilder()
                        .setId(id)
                        .setText(text)
                        .build();

                StoreResult result = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .store(message);

                return result.getSuccess();

            } finally {
                channel.shutdown();
                channel.awaitTermination(1, TimeUnit.SECONDS);
            }

        } catch (StatusRuntimeException e) {
            System.err.println("[LEADER] Failed to replicate to " + member.getHost() + ":" + member.getPort() + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[LEADER] Error replicating to " + member.getHost() + ":" + member.getPort() + ": " + e.getMessage());
            return false;
        }
    }

    private String retrieveFromMember(String host, int port, int id) {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();

            try {
                StorageServiceGrpc.StorageServiceBlockingStub stub = 
                        StorageServiceGrpc.newBlockingStub(channel);

                MessageId messageId = MessageId.newBuilder()
                        .setId(id)
                        .build();

                StoredMessage result = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .retrieve(messageId);

                return result.getText();

            } finally {
                channel.shutdown();
                channel.awaitTermination(1, TimeUnit.SECONDS);
            }

        } catch (StatusRuntimeException e) {
            System.err.println("[LEADER] Failed to retrieve from " + host + ":" + port + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("[LEADER] Error retrieving from " + host + ":" + port + ": " + e.getMessage());
            return null;
        }
    }

    private void broadcastToFamily(ChatMessage msg) {
        for (NodeInfo n : registry.snapshot()) {
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                continue;
            }

            queue.enqueue(n, msg, self);
            System.out.printf("Enqueued message for %s:%d%n", n.getHost(), n.getPort());
        }
    }
}
