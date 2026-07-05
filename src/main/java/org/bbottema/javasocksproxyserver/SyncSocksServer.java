package org.bbottema.javasocksproxyserver;

import lombok.Value;
import org.bbottema.javasocksproxyserver.auth.Authenticator;
import org.bbottema.javasocksproxyserver.auth.DefaultAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSocksServer.class);
    private static final long DEFAULT_SERVER_SOCKET_OPEN_TIMEOUT_MILLIS = 5000;
    private static final long DEFAULT_SERVER_SOCKET_OPEN_RETRY_INTERVAL_MILLIS = 200;
    private static final long DEFAULT_CLOSE_CONNECTION_TIMEOUT_MILLIS = 5000;


    private final long serverSocketOpenTimeoutMillis;
    private final long serverSocketOpenRetryIntervalMillis;
    private final long closeConnectionTimeoutMillis;

    protected volatile boolean stopping = false;

    protected final Map<Integer, Thread> servers = new HashMap<>(); // actual port -> thread map
    private final Map<Integer, ServerProcess> serverProcesses = new HashMap<>();

    public SyncSocksServer() {
        this(DEFAULT_SERVER_SOCKET_OPEN_TIMEOUT_MILLIS, DEFAULT_SERVER_SOCKET_OPEN_RETRY_INTERVAL_MILLIS, DEFAULT_CLOSE_CONNECTION_TIMEOUT_MILLIS);
    }

    public SyncSocksServer(long serverSocketOpenTimeoutMillis, long serverSocketOpenRetryIntervalMillis, long closeConnectionTimeoutMillis) {
        this.serverSocketOpenTimeoutMillis = serverSocketOpenTimeoutMillis;
        this.serverSocketOpenRetryIntervalMillis = serverSocketOpenRetryIntervalMillis;
        this.closeConnectionTimeoutMillis = closeConnectionTimeoutMillis;
    }

    public synchronized void start(int listenPort) {
        startServer(listenPort);
    }

    public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
        startServer(listenPort, serverSocketFactory);
    }

    public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory, Authenticator authenticator) {
        startServer(listenPort, serverSocketFactory, authenticator);
    }

    public synchronized RunningSocksServer startServer(int listenPort) {
        return startServer(listenPort, ServerSocketFactory.getDefault());
    }

    public synchronized RunningSocksServer startServer(int listenPort, ServerSocketFactory serverSocketFactory) {
        return startServer(listenPort, serverSocketFactory, new DefaultAuthenticator());
    }

    public synchronized RunningSocksServer startServer(int listenPort, ServerSocketFactory serverSocketFactory, Authenticator authenticator) {
        stopping = false;
        if (listenPort != 0 && servers.containsKey(listenPort)) {
            LOGGER.error("SOCKS server already started on port {}", listenPort);
            return createRunningServerHandle(listenPort);
        }
        ServerProcess serverProcess = new ServerProcess(listenPort, serverSocketFactory, authenticator);
        Thread thread = new Thread(serverProcess);
        thread.start();
        if (!serverProcess.waitServerSocketOpened(serverSocketOpenTimeoutMillis)) {
            serverProcess.stop();
            thread.interrupt();
            waitServerToJoin(thread);
            throw new RuntimeException("Timeout waiting socket to be opened");
        }
        int boundPort = serverProcess.getBoundPort();
        servers.put(boundPort, thread);
        serverProcesses.put(boundPort, serverProcess);
        return createRunningServerHandle(boundPort);
    }

    public synchronized void stop() {
        stopping = true;
        waitAllServersToJoin();
    }

    public synchronized void stop(int port) {
        ServerProcess serverProcess = serverProcesses.remove(port);
        Thread thread = servers.remove(port);
        if (serverProcess != null) {
            serverProcess.stop();
        }
        if (thread != null) {
            waitServerToJoin(thread);
        }
    }

    private RunningSocksServer createRunningServerHandle(final int port) {
        return new RunningSocksServerHandle(port, new Runnable() {
            @Override
            public void run() {
                stop(port);
            }
        });
    }

    private class ServerProcess implements Runnable {

        protected final int port;
        private final ServerSocketFactory serverSocketFactory;
        private final List<ProxyClient> clients = new ArrayList<>();
        private final CountDownLatch serverSocketOpenLatch = new CountDownLatch(1);
        private volatile boolean stopping = false;
        private volatile int boundPort = -1;

        private final Authenticator authenticator;

        public ServerProcess(int port, ServerSocketFactory serverSocketFactory, Authenticator authenticator) {
            this.port = port;
            this.serverSocketFactory = serverSocketFactory;
            this.authenticator = authenticator;
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
            while (!shouldStop()) {
                try (ServerSocket listenSocket = serverSocketFactory.createServerSocket(resolvePortToBind(port))) {
                    listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);
                    boundPort = listenSocket.getLocalPort();

                    LOGGER.debug("SOCKS server listening at port: " + boundPort);
                    serverSocketOpenLatch.countDown();

                    while (!shouldStop()) {
                        handleNextClient(listenSocket);
                        removeDisconnectedClients();
                    }
                } catch (Exception e) {
                    if (!shouldStop()) {
                        LOGGER.debug("Can't handle clients on port {} ", port, e);
                    }
                }
                if (!shouldStop()) {
                    Thread.sleep(serverSocketOpenRetryIntervalMillis);
                }
            }
        }

        private int resolvePortToBind(int port) {
            return boundPort > 0 ? boundPort : port;
        }

        private boolean shouldStop() {
            return stopping || SyncSocksServer.this.stopping;
        }

        private boolean waitServerSocketOpened(long timeoutMillis) {
            try {
                return serverSocketOpenLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Timeout while waiting for server socket to opened {}", port);
                throw new RuntimeException(e);
            }
        }

        private int getBoundPort() {
            return boundPort;
        }

        private void stop() {
            stopping = true;
        }

        private void handleNextClient(ServerSocket listenSocket) {
            try {
                final Socket clientSocket = listenSocket.accept();
                clientSocket.setSoTimeout(SocksConstants.DEFAULT_SERVER_TIMEOUT);
                LOGGER.debug("Connection from : " + Utils.getSocketInfo(clientSocket));
                ProxyHandler handler = new ProxyHandler(clientSocket, authenticator);
                Thread thread = new Thread(handler);
                clients.add(ProxyClient.of(clientSocket, handler, thread));
                thread.start();
            } catch (InterruptedIOException e) {
                //	This exception is thrown when accept timeout is expired
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        private void waitAllClientsToJoinOrTimeout() {
            for (ProxyClient client : clients) {
                LOGGER.debug("Waiting client connection {} to close", Utils.getSocketInfo(client.socket));
                client.handler.close();
                try {
                    client.thread.join(closeConnectionTimeoutMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (client.thread.isAlive()) {
                    LOGGER.error("Can't stop client connection {} to close", Utils.getSocketInfo(client.socket));
                }
            }
            clients.clear();
        }

        private void removeDisconnectedClients() {
            clients.removeIf(client -> !client.thread.isAlive());
        }
    }

    private void waitAllServersToJoin() {
        for (Map.Entry<Integer, ServerProcess> serverProcess : serverProcesses.entrySet()) {
            serverProcess.getValue().stop();
        }
        for (Map.Entry<Integer, Thread> server : servers.entrySet()) {
            LOGGER.debug("Waiting server on port {} to close", server.getKey());
            waitServerToJoin(server.getValue());
            if (server.getValue().isAlive()) {
                LOGGER.error("Can't stop server on port {} to close", server.getKey());
            }
        }
        serverProcesses.clear();
        servers.clear();
    }

    private void waitServerToJoin(Thread thread) {
        try {
            thread.join(closeConnectionTimeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }


    @Value(staticConstructor = "of")
    static class ProxyClient {
        Socket socket;
        ProxyHandler handler;
        Thread thread;
    }

}
