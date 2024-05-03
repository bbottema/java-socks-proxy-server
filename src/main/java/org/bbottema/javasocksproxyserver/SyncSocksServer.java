package org.bbottema.javasocksproxyserver;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This implementation has some additional properties vs SocksServer
 * - start method does several retries to open server socket
 * - start method is not returned until server socket is opened and ready to accept connection
 * - stop method tries to close all server and client sockets
 * <p>
 * These properties make usage of SocksProxy server more reliable in dynamic test environment
 * and more predictable, during several restarts
 */
public class SyncSocksServer {

    private static final Logger LOGGER = new Logger("SyncSocksServer");
    private static final long DEFAULT_SERVER_SOCKET_OPEN_TIMEOUT_MILLIS = 5000;
    private static final long DEFAULT_SERVER_SOCKET_OPEN_RETRY_INTERVAL_MILLIS = 200;
    private static final long DEFAULT_CLOSE_CONNECTION_TIMEOUT_MILLIS = 5000;


    private final long serverSocketOpenTimeoutMillis;
    private final long serverSocketOpenRetryIntervalMillis;
    private final long closeConnectionTimeoutMillis;

    protected volatile boolean stopping = false;

    protected final Map<Integer, Thread> servers = new HashMap<>(); // port -> thread map

    public SyncSocksServer() {
        this(DEFAULT_SERVER_SOCKET_OPEN_TIMEOUT_MILLIS, DEFAULT_SERVER_SOCKET_OPEN_RETRY_INTERVAL_MILLIS, DEFAULT_CLOSE_CONNECTION_TIMEOUT_MILLIS);
    }

    public SyncSocksServer(long serverSocketOpenTimeoutMillis, long serverSocketOpenRetryIntervalMillis, long closeConnectionTimeoutMillis) {
        this.serverSocketOpenTimeoutMillis = serverSocketOpenTimeoutMillis;
        this.serverSocketOpenRetryIntervalMillis = serverSocketOpenRetryIntervalMillis;
        this.closeConnectionTimeoutMillis = closeConnectionTimeoutMillis;
    }

    public synchronized void start(int listenPort) {
        start(listenPort, ServerSocketFactory.getDefault());
    }

    public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
        stopping = false;
        if (servers.containsKey(listenPort)) {
            LOGGER.error("SOCKS server already started on port '" + listenPort + "'");
            return;
        }
        ServerProcess serverProcess = new ServerProcess(listenPort, serverSocketFactory);
        Thread thread = new Thread(serverProcess);
        servers.put(listenPort, thread);
        thread.start();
        if (!serverProcess.waitServerSocketOpened(serverSocketOpenTimeoutMillis)) {
            throw new RuntimeException("Timeout waiting socket to be opened");
        }
    }

    public synchronized void stop() {
        stopping = true;
        waitAllServersToJoin();
    }

    private class ServerProcess implements Runnable {

        protected final int port;
        private final ServerSocketFactory serverSocketFactory;
        private final List<ProxyClient> clients = new ArrayList<>();
        private final CountDownLatch serverSocketOpenLatch = new CountDownLatch(1);

        public ServerProcess(int port, ServerSocketFactory serverSocketFactory) {
            this.port = port;
            this.serverSocketFactory = serverSocketFactory;
        }

        @Override
        public void run() {
            LOGGER.debug("SOCKS server started...");
            try {
                handleClients(port);
                LOGGER.debug("SOCKS server stopped...");
            } catch (IOException | InterruptedException e) {
                LOGGER.debug("SOCKS server crashed...");
                Thread.currentThread().interrupt();
            } finally {
                waitAllClientsToJoinOrTimeout();
            }
        }

        protected void handleClients(int port) throws IOException, InterruptedException {
            while (!stopping) {
                try (ServerSocket listenSocket = serverSocketFactory.createServerSocket(port)) {
                    listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);

                    LOGGER.debug("SOCKS server listening at port: " + listenSocket.getLocalPort());
                    serverSocketOpenLatch.countDown();

                    while (!stopping) {
                        handleNextClient(listenSocket);
                        removeDisconnectedClients();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.debug("Can't handle clients on port " + port + ", message: " + e.getMessage());
                }
                if (!stopping) {
                    Thread.sleep(serverSocketOpenRetryIntervalMillis);
                }
            }
        }

        private boolean waitServerSocketOpened(long timeoutMillis) {
            try {
                return serverSocketOpenLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Timeout while waiting for server socket to opened " + port);
                throw new RuntimeException(e);
            }
        }

        private void handleNextClient(ServerSocket listenSocket) {
            try {
                final Socket clientSocket = listenSocket.accept();
                clientSocket.setSoTimeout(SocksConstants.DEFAULT_SERVER_TIMEOUT);
                LOGGER.debug("Connection from : " + Utils.getSocketInfo(clientSocket));
                ProxyHandler handler = new ProxyHandler(clientSocket);
                Thread thread = new Thread(handler);
                clients.add(new ProxyClient(clientSocket, handler, thread));
                thread.start();
            } catch (InterruptedIOException e) {
                //	This exception is thrown when accept timeout is expired
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error(e.getMessage());
            }
        }

        private void waitAllClientsToJoinOrTimeout() {
            for (ProxyClient client : clients) {
                LOGGER.debug("Waiting client connection '" + Utils.getSocketInfo(client.socket) + "' to close");
                client.handler.close();
                try {
                    client.thread.join(closeConnectionTimeoutMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (client.thread.isAlive()) {
                    LOGGER.error("Can't stop client connection '" + Utils.getSocketInfo(client.socket) + "' to close");
                }
            }
            clients.clear();
        }

        private void removeDisconnectedClients() {
            clients.removeIf(client -> !client.thread.isAlive());
        }
    }

    private void waitAllServersToJoin() {
        servers.forEach((port, thread) -> {
            LOGGER.debug("Waiting server on port " + port + " to close");
            try {
                thread.join(closeConnectionTimeoutMillis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (thread.isAlive()) {
                LOGGER.error("Can't stop server on port " + port + " to close");
            }
        });
        servers.clear();
    }


    static class ProxyClient {
        Socket socket;
        ProxyHandler handler;
        Thread thread;

        public ProxyClient(Socket socket, ProxyHandler handler, Thread thread) {
            this.socket = socket;
            this.handler = handler;
            this.thread = thread;
        }
    }

}