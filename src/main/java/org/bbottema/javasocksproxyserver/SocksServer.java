package org.bbottema.javasocksproxyserver;

import org.bbottema.javasocksproxyserver.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocksServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);
	
	private volatile boolean stopped = false;
	private final int listenPort;

	private final ServerSocketFactory factory;
	private Authenticator authenticator = null;

	public SocksServer(int listenPort) {
		this.listenPort = listenPort;
		factory = ServerSocketFactory.getDefault();
	}

	public SocksServer(int listenPort, ServerSocketFactory factory) {
		this.listenPort = listenPort;
		this.factory = factory;
	}

	public SocksServer setAuthenticator(Authenticator authenticator) {
		this.authenticator = authenticator;
		return this;
	}

	public synchronized SocksServer start() {
		stopped = false;
		new Thread(new ServerProcess(listenPort, factory, authenticator)).start();
		return this;
	}

	public synchronized SocksServer stop() {
		stopped = true;
		return this;
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
			
			LOGGER.debug("SOCKS server listening at port: " + listenSocket.getLocalPort());

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
				LOGGER.debug("Connection from : " + Utils.getSocketInfo(clientSocket));
				new Thread(new ProxyHandler(clientSocket, authenticator)).start();
			} catch (InterruptedIOException e) {
				//	This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}