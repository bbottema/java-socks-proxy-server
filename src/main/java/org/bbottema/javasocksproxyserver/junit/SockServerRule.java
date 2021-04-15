package org.bbottema.javasocksproxyserver.junit;

import org.bbottema.javasocksproxyserver.SocksServer;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

import javax.net.ServerSocketFactory;

/**
 * Creates a {@link SocksServer} once, and starts and stops it before and after each test.
 * <p>
 * Can be used both by JUnit's {@code Rule} and {@code ClassRule} (`the latter being the preferred usage).
 */
public class SockServerRule extends ExternalResource {

	private final SocksServer socksServer;
	private final int port;
	private final ServerSocketFactory serverSocketFactory;
	
	public SockServerRule(@NotNull Integer port) {
		this(port, ServerSocketFactory.getDefault());
	}
	
	public SockServerRule(@NotNull Integer port, @NotNull ServerSocketFactory serverSocketFactory) {
		this.socksServer = new SocksServer();
		this.port = port;
		this.serverSocketFactory = serverSocketFactory;
	}

	@Override
	protected void before() {
		this.socksServer.start(port, serverSocketFactory);
	}

	@Override
	protected void after() {
		this.socksServer.stop();
	}
}