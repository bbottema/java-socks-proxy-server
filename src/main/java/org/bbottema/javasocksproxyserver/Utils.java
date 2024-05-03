package org.bbottema.javasocksproxyserver;

import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import static java.lang.String.format;

public final class Utils {

	private static final Logger LOGGER = new Logger("Utils");

	public static InetAddress calcInetAddress(byte[] addr) {
		InetAddress IA;
		StringBuilder sIA = new StringBuilder();

		if (addr.length < 4) {
			LOGGER.error("calcInetAddress() - Invalid length of IP v4 - " + addr.length + " bytes");
			return null;
		}

		// IP v4 Address Type
		for (int i = 0; i < 4; i++) {
			sIA.append(byte2int(addr[i]));
			if (i < 3) sIA.append(".");
		}

		try {
			IA = InetAddress.getByName(sIA.toString());
		} catch (UnknownHostException e) {
			return null;
		}

		return IA;
	}

	public static int byte2int(byte b) {
		return (int) b < 0 ? 0x100 + (int) b : b;
	}

	public static int calcPort(byte Hi, byte Lo) {
		return ((byte2int(Hi) << 8) | byte2int(Lo));
	}

	public static String iP2Str(InetAddress IP) {
		return IP == null
				? "NA/NA"
				: format("%s/%s", IP.getHostName(), IP.getHostAddress());
	}

	public static String getSocketInfo(Socket sock) {
		return sock == null
				? "<NA/NA:0>"
				: format("<%s:%d>", Utils.iP2Str(sock.getInetAddress()), sock.getPort());
	}

	public static String getSocketInfo(DatagramPacket DGP) {
		return DGP == null
				? "<NA/NA:0>"
				: format("<%s:%d>", Utils.iP2Str(DGP.getAddress()), DGP.getPort());
	}

	public static int getFreePort() {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			return serverSocket.getLocalPort();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isLocalPortAvailableToConnect(int port) {
		try (Socket ignored = new Socket("localhost", port)) {
			return true;
		} catch (ConnectException e) {
			return false;
		} catch (IOException e) {
			throw new IllegalStateException("Error while trying to check open port", e);
		}
	}
}