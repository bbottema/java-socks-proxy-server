package org.bbottema.javasocksproxyserver.junit;

import org.bbottema.javasocksproxyserver.SocksServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.net.ServerSocketFactory;

/**
 * Creates a {@link SocksServer} once, and starts and stops it before and after each test.
 * <p>
 * Can be used both by JUnit's {@code Rule} and {@code ClassRule} (`the latter being the preferred usage).
 */
public class SockServerExtension implements BeforeAllCallback, AfterAllCallback {

    private final SocksServer socksServer;
    private final int port;
    private final ServerSocketFactory serverSocketFactory;

    public SockServerExtension(@NotNull Integer port) {
        this(port, ServerSocketFactory.getDefault());
    }

    public SockServerExtension(@NotNull Integer port, @NotNull ServerSocketFactory serverSocketFactory) {
        this.socksServer = new SocksServer();
        this.port = port;
        this.serverSocketFactory = serverSocketFactory;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        this.socksServer.start(port, serverSocketFactory);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        this.socksServer.stop();
    }
}