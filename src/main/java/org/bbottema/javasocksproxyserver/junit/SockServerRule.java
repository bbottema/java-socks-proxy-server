package org.bbottema.javasocksproxyserver.junit;

import org.bbottema.javasocksproxyserver.SocksServer;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

/**
 * Creates a {@link SocksServer} once, and starts and stops it before and after each test.
 * <p>
 * Can be used both by JUnit's {@code Rule} and {@code ClassRule} (`the latter being the preferred usage).
 */
public class SockServerRule extends ExternalResource {

	private final SocksServer socksServer = new SocksServer();
	private final int port;

	public SockServerRule(@NotNull Integer port) {
		this.port = port;
	}

	@Override
	protected void before() {
		this.socksServer.start(port);
	}

	@Override
	protected void after() {
		this.socksServer.stop();
	}
}