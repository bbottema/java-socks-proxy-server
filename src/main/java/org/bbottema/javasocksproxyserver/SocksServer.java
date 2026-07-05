package org.bbottema.javasocksproxyserver;

import org.bbottema.javasocksproxyserver.auth.Authenticator;
import org.bbottema.javasocksproxyserver.auth.DefaultAuthenticator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SocksServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);
	
	private volatile boolean stopped = false;
	private final int listenPort;
	private volatile int boundPort;
	@NotNull private volatile CountDownLatch serverSocketOpenLatch = new CountDownLatch(0);

	@NotNull private ServerSocketFactory factory;
	@NotNull private Authenticator authenticator;

	public SocksServer() {
		this(1080);
	}

	public SocksServer(int listenPort) {
		this.listenPort = listenPort;
		this.boundPort = listenPort;
		this.factory = ServerSocketFactory.getDefault();
		this.authenticator = new DefaultAuthenticator();
	}

	public synchronized SocksServer setFactory(@NotNull ServerSocketFactory factory) {
		this.factory = factory;
		return this;
	}

	public synchronized SocksServer setAuthenticator(@NotNull Authenticator authenticator) {
		this.authenticator = authenticator;
		return this;
	}

	public synchronized void start() {
		stopped = false;
		boundPort = listenPort;
		serverSocketOpenLatch = new CountDownLatch(1);
		new Thread(new ServerProcess(listenPort, factory, authenticator)).start();
	}

	public synchronized void stop() {
		stopped = true;
	}

	public int getListenPort() {
		return boundPort;
	}

	public boolean waitUntilStarted(long timeoutMillis) {
		try {
			return serverSocketOpenLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for server socket to open", e);
		}
	}

	public RunningSocksServer startAndWait(long timeoutMillis) {
		start();
		if (!waitUntilStarted(timeoutMillis)) {
			stop();
			throw new RuntimeException("Timeout waiting socket to be opened");
		}
		return new RunningSocksServerHandle(getListenPort(), new Runnable() {
			@Override
			public void run() {
				stop();
			}
		});
	}
	
	private class ServerProcess implements Runnable {
		
		protected final int port;
		private final ServerSocketFactory serverSocketFactory;
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
			} catch (IOException e) {
				LOGGER.debug("SOCKS server crashed...");
				Thread.currentThread().interrupt();
			}
		}

		protected void handleClients(int port) throws IOException {
			final ServerSocket listenSocket = serverSocketFactory.createServerSocket(port);
			listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);
			boundPort = listenSocket.getLocalPort();
			serverSocketOpenLatch.countDown();

            LOGGER.debug("SOCKS server listening at port: {}", boundPort);

			while (true) {
				synchronized (SocksServer.this) {
					if (stopped) {
						break;
					}
				}
				handleNextClient(listenSocket);
			}

			try {
				listenSocket.close();
			} catch (IOException e) {
				// ignore
			}
		}

		private void handleNextClient(ServerSocket listenSocket) {
			try {
				final Socket clientSocket = listenSocket.accept();
				clientSocket.setSoTimeout(SocksConstants.DEFAULT_SERVER_TIMEOUT);
                LOGGER.debug("Connection from : {}", Utils.getSocketInfo(clientSocket));
				new Thread(new ProxyHandler(clientSocket, authenticator)).start();
			} catch (InterruptedIOException e) {
				//	This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}
