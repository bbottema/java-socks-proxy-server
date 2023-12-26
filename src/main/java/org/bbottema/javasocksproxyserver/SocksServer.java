package org.bbottema.javasocksproxyserver;

import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SocksServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);
	private static final long CLOSE_CONNECTION_TIMEOUT_MILLIS = 5000;
	private static final long SERVER_SOCKET_OPEN_TIMEOUT_MILLIS = 5000;
	private static final long SOCKET_OPEN_RETRY_MILLIS = 200;

	protected volatile boolean stopping = false;

	protected final Map<Integer, Thread> servers = new HashMap<>();

	public synchronized void start(int listenPort) {
		start(listenPort, ServerSocketFactory.getDefault());
	}

	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
		stopping = false;
		if (servers.containsKey(listenPort)) {
			LOGGER.error("SOCKS server already started on port {}", listenPort);
			return;
		}
    	ServerProcess serverProcess = new ServerProcess(listenPort, serverSocketFactory);
		Thread thread = new Thread(serverProcess);
		servers.put(listenPort, thread);
		thread.start();
		serverProcess.waitServerSocketOpened();
	}

	public synchronized void stop() {
		stopping = true;
		waitAllServersToJoin();
	}

	private class ServerProcess implements Runnable {

		protected final int port;
		private final ServerSocketFactory serverSocketFactory;

		private final Map<Integer, ProxyClient> clientMap = new HashMap<>();

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
				waitAllClientsToJoin();
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
					}
				}
				if (!stopping) {
					Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
				}
			}
		}


		private void waitServerSocketOpened() {
			try {
				serverSocketOpenLatch.await(SERVER_SOCKET_OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				LOGGER.error("Timeout while waiting for socket on port to be opened {}", port);
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
				clientMap.put(clientSocket.getPort(), ProxyClient.of(handler, thread));
				thread.start();
			} catch (InterruptedIOException e) {
				//	This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}

		private void waitAllClientsToJoin() {
			clientMap.forEach((port, client) -> {
				LOGGER.debug("Waiting client connection on port {} to close", port);
				client.handler.close();
				try {
					client.thread.join(CLOSE_CONNECTION_TIMEOUT_MILLIS);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				if (client.thread.isAlive()) {
					LOGGER.error("Can't stop client connection on port {} to close", port);
				}
			});
			clientMap.clear();
		}

	}

	private void waitAllServersToJoin() {
		servers.forEach((port, thread) -> {
			LOGGER.debug("Waiting server on port {} to close", port);
			try {
				thread.join(CLOSE_CONNECTION_TIMEOUT_MILLIS);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if (thread.isAlive()) {
				LOGGER.error("Can't stop server on port {} to close", port);
			}
		});
		servers.clear();
	}


	@Value(staticConstructor = "of")
	static class ProxyClient {
		ProxyHandler handler;
		Thread thread;
	}

}