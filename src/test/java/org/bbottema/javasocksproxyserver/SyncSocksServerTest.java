package org.bbottema.javasocksproxyserver;

import org.junit.Test;

import java.io.IOException;
import java.net.Socket;

import static org.junit.Assert.*;

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

}