package com.example.family;

import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;


import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

public class NodeMain {

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;
    private static BroadcastQueue broadcastQueue;
    private static LeaderElection leaderElection;
    private static TcpListener tcpListener;

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        broadcastQueue = new BroadcastQueue();
        NodeRegistry registry = new NodeRegistry();
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);

        tcpListener = new TcpListener(registry, self, broadcastQueue);
        leaderElection = new LeaderElection(self, registry, tcpListener);
        service.setLeaderElection(leaderElection);

        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .build()
                .start();

                System.out.printf("Node started on %s:%d%n", host, port);

                discoverExistingNodes(host, port, registry, self);

                NodeInfo initialLeader = leaderElection.findInitialLeader();

                if (initialLeader.getHost().equals(self.getHost()) &&
                    initialLeader.getPort() == self.getPort()) {
                    leaderElection.becomeLeader();
                } else {
                    leaderElection.setLeader(initialLeader);
                }

                startFamilyPrinter(registry, self, leaderElection);
                startHealthChecker(registry, self, leaderElection);

                server.awaitTermination();




    }

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self, BroadcastQueue queue) {
    new Thread(() -> {
        try (ServerSocket serverSocket = new ServerSocket(6666)) {
            System.out.printf("Leader listening for text on TCP %s:%d%n",
                    self.getHost(), 6666);

            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClientTextConnection(client, registry, self, queue)).start();
            }

        } catch (IOException e) {
            System.err.println("Error in leader text listener: " + e.getMessage());
        }
    }, "LeaderTextListener").start();
}

private static void handleClientTextConnection(Socket client,
                                               NodeRegistry registry,
                                               NodeInfo self,
                                               BroadcastQueue queue) {
    System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(client.getInputStream()))) {

        String line;
        while ((line = reader.readLine()) != null) {
            String text = line.trim();
            if (text.isEmpty()) continue;

            long ts = System.currentTimeMillis();

            System.out.println("📝 Received from TCP: " + text);

            ChatLogger.logMessage(self.getHost(), self.getPort(), text);

            ChatMessage msg = ChatMessage.newBuilder()
                    .setText(text)
                    .setFromHost(self.getHost())
                    .setFromPort(self.getPort())
                    .setTimestamp(ts)
                    .build();

            broadcastToFamily(registry, self, msg, queue);
        }

    } catch (IOException e) {
        System.err.println("TCP client handler error: " + e.getMessage());
    } finally {
        try { client.close(); } catch (IOException ignored) {}
    }
}

private static void broadcastToFamily(NodeRegistry registry,
                                      NodeInfo self,
                                      ChatMessage msg,
                                      BroadcastQueue queue) {

    List<NodeInfo> members = registry.snapshot();

    for (NodeInfo n : members) {
        if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
            continue;
        }

        queue.enqueue(n, msg, self);
        System.out.printf("→ Enqueued message for %s:%d%n", n.getHost(), n.getPort());
    }
}


    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void discoverExistingNodes(String host,
                                              int selfPort,
                                              NodeRegistry registry,
                                              NodeInfo self) {

        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                FamilyView view = stub.join(self);
                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self, LeaderElection election) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            System.out.println("======================================");
            System.out.printf("Family at %s:%d (me)%n", self.getHost(), self.getPort());
            System.out.println("Time: " + LocalDateTime.now());

            NodeInfo leader = election.getLeader();
            if (leader != null) {
                System.out.printf("Leader: %s:%d%s%n",
                        leader.getHost(), leader.getPort(),
                        election.isLeader() ? " (me)" : "");
            }

            System.out.println("Members:");
            for (NodeInfo n : members) {
                boolean isMe = n.getHost().equals(self.getHost()) && n.getPort() == self.getPort();
                System.out.printf(" - %s:%d%s%n",
                        n.getHost(),
                        n.getPort(),
                        isMe ? " (me)" : "");
            }
            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self,
                                           LeaderElection election) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    scheduler.scheduleAtFixedRate(() -> {
        List<NodeInfo> members = registry.snapshot();
        NodeInfo currentLeader = election.getLeader();
        boolean leaderAlive = false;

        for (NodeInfo n : members) {
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                continue;
            }

            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(n.getHost(), n.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                stub.getFamily(Empty.newBuilder().build());

                if (currentLeader != null &&
                    n.getHost().equals(currentLeader.getHost()) &&
                    n.getPort() == currentLeader.getPort()) {
                    leaderAlive = true;
                }

            } catch (Exception e) {
                System.out.printf("Node %s:%d unreachable, removing from family%n",
                        n.getHost(), n.getPort());
                registry.remove(n);

                if (currentLeader != null &&
                    n.getHost().equals(currentLeader.getHost()) &&
                    n.getPort() == currentLeader.getPort()) {
                    System.out.println("⚠️  Leader is down!");
                    leaderAlive = false;
                }
            } finally {
                if (channel != null) {
                    channel.shutdownNow();
                }
            }
        }

        if (currentLeader != null && !leaderAlive &&
            !election.isLeader()) {
            System.out.println("Leader is dead, starting election...");
            election.startElection();
        }

    }, 5, 10, TimeUnit.SECONDS);
}

}
