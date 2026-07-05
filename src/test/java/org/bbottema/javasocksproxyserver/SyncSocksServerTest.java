package org.bbottema.javasocksproxyserver;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyncSocksServerTest {


    @Test
    public void simple_start_stop() {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        server.stop();
    }

    @Test
    public void cant_start_on_the_same_port() {
        SyncSocksServer server = new SyncSocksServer();
        SyncSocksServer server2 = new SyncSocksServer(1,100,1);
        int port = Utils.getFreePort();
        server.start(port);
        assertThrows(
                RuntimeException.class,
                () -> server2.start(port)
        );
        server.stop();
    }

    @Test
    public void socksServer_available_to_connect_right_after_start_method_completes() {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        // socket should be available for connection right away
        assertTrue(Utils.isLocalPortAvailableToConnect(port));
        server.stop();
    }

    @Test
    public void start_stop_two_times() {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        assertTrue(Utils.isLocalPortAvailableToConnect(port));
        server.stop();
        // after closing Server Socket, it's not available immediately for new Server Socket
        server.start(port);
        server.stop();
    }

    @Test
    public void hang_connection_doesn_t_prevent_from_stop() throws IOException {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        Socket socket = new Socket("localhost", port);

        server.stop();
        socket.close();
    }

    @Test
    public void startServer_on_dynamic_port_returns_running_server_handle() {
        SyncSocksServer server = new SyncSocksServer();
        RunningSocksServer runningServer = server.startServer(0);

        int port = runningServer.getPort();
        assertTrue(port > 0);
        assertTrue(Utils.isLocalPortAvailableToConnect(port));

        runningServer.stop();
    }

    @Test
    public void startServer_can_start_multiple_dynamic_ports_on_same_instance() {
        SyncSocksServer server = new SyncSocksServer();
        RunningSocksServer firstServer = server.startServer(0);
        RunningSocksServer secondServer = server.startServer(0);

        assertTrue(firstServer.getPort() > 0);
        assertTrue(secondServer.getPort() > 0);
        assertNotEquals(firstServer.getPort(), secondServer.getPort());
        assertTrue(Utils.isLocalPortAvailableToConnect(firstServer.getPort()));
        assertTrue(Utils.isLocalPortAvailableToConnect(secondServer.getPort()));

        server.stop();
    }

    @Test
    public void stopping_one_running_server_keeps_other_server_available() {
        SyncSocksServer server = new SyncSocksServer();
        RunningSocksServer firstServer = server.startServer(0);
        RunningSocksServer secondServer = server.startServer(0);

        firstServer.stop();

        assertTrue(Utils.isLocalPortAvailableToConnect(secondServer.getPort()));

        secondServer.stop();
    }
}
