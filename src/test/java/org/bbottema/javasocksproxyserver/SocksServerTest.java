package org.bbottema.javasocksproxyserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocksServerTest {

    @Test
    public void start_on_dynamic_port_exposes_bound_port() {
        SocksServer server = new SocksServer(0);

        server.start();
        assertTrue(server.waitUntilStarted(1000));

        int port = server.getListenPort();
        assertTrue(port > 0);
        assertTrue(Utils.isLocalPortAvailableToConnect(port));

        server.stop();
    }
}
