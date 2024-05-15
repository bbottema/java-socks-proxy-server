package org.bbottema.javasocksproxyserver;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.bbottema.javasocksproxyserver.Utils.getSocketInfo;

public class Socks4Impl {

	private static final Logger LOGGER = LoggerFactory.getLogger(Socks4Impl.class);

	final ProxyHandler m_Parent;
	final byte[] DST_Port = new byte[2];
	byte[] DST_Addr = new byte[4];
	byte SOCKS_Version = 0;
	byte socksCommand;

	private InetAddress m_ExtLocalIP = null;
	InetAddress m_ServerIP = null;
	int m_nServerPort = 0;
	InetAddress m_ClientIP = null;
	int m_nClientPort = 0;

	Socks4Impl(ProxyHandler Parent) {
		m_Parent = Parent;
	}

	public byte getSuccessCode() {
		return 90;
	}

	public byte getFailCode() {
		return 91;
	}

	@NotNull
	public String commName(byte code) {
		switch (code) {
			case 0x01:
				return "CONNECT";
			case 0x02:
				return "BIND";
			case 0x03:
				return "UDP Association";
			default:
				return "Unknown Command";
		}
	}

	@NotNull
	public String replyName(byte code) {
		switch (code) {
			case 0:
				return "SUCCESS";
			case 1:
				return "General SOCKS Server failure";
			case 2:
				return "Connection not allowed by ruleset";
			case 3:
				return "Network Unreachable";
			case 4:
				return "HOST Unreachable";
			case 5:
				return "Connection Refused";
			case 6:
				return "TTL Expired";
			case 7:
				return "Command not supported";
			case 8:
				return "Address Type not Supported";
			case 9:
				return "to 0xFF UnAssigned";
			case 90:
				return "Request GRANTED";
			case 91:
				return "Request REJECTED or FAILED";
			case 92:
				return "Request REJECTED - SOCKS server can't connect to Identd on the client";
			case 93:
				return "Request REJECTED - Client and Identd report diff user-ID";
			default:
				return "Unknown Command";
		}
	}

	public boolean isInvalidAddress() {
		// IP v4 Address Type
		m_ServerIP = Utils.calcInetAddress(DST_Addr);
		m_nServerPort = Utils.calcPort(DST_Port[0], DST_Port[1]);

		m_ClientIP = m_Parent.m_ClientSocket.getInetAddress();
		m_nClientPort = m_Parent.m_ClientSocket.getPort();

		return m_ServerIP == null || m_nServerPort < 0;
	}

	protected byte getByte() {
		try {
			return m_Parent.getByteFromClient();
		} catch (Exception e) {
			return 0;
		}
	}

	public void authenticate(byte SOCKS_Ver) throws Exception {
		SOCKS_Version = SOCKS_Ver;
	}


	public void clientAuthResponse() throws Exception {
		// Socks5Impl must implement this
	}

	public void getClientCommand() throws Exception {
		// Version was get in method Authenticate()
		socksCommand = getByte();

		DST_Port[0] = getByte();
		DST_Port[1] = getByte();

		for (int i = 0; i < 4; i++) {
			DST_Addr[i] = getByte();
		}

		//noinspection StatementWithEmptyBody
		while (getByte() != 0x00) {
			// keep reading bytes
		}

		if ((socksCommand < SocksConstants.SC_CONNECT) || (socksCommand > SocksConstants.SC_BIND)) {
			refuseCommand((byte) 91);
			throw new Exception("Socks 4 - Unsupported Command : " + commName(socksCommand));
		}

		if (isInvalidAddress()) {  // Gets the IP Address
			refuseCommand((byte) 92);    // Host Not Exists...
			throw new Exception("Socks 4 - Unknown Host/IP address '" + m_ServerIP.toString());
		}

		LOGGER.debug("Accepted SOCKS 4 Command: \"" + commName(socksCommand) + "\"");
	}

	public void replyCommand(byte ReplyCode) {
		LOGGER.debug("Socks 4 reply: \"" + replyName(ReplyCode) + "\"");

		byte[] REPLY = new byte[8];
		REPLY[0] = 0;
		REPLY[1] = ReplyCode;
		REPLY[2] = DST_Port[0];
		REPLY[3] = DST_Port[1];
		REPLY[4] = DST_Addr[0];
		REPLY[5] = DST_Addr[1];
		REPLY[6] = DST_Addr[2];
		REPLY[7] = DST_Addr[3];

		m_Parent.sendToClient(REPLY);
	}

	protected void refuseCommand(byte errorCode) {
		LOGGER.debug("Socks 4 - Refuse Command: \"" + replyName(errorCode) + "\"");
		replyCommand(errorCode);
	}

	public void connect() throws Exception {
		LOGGER.debug("Connecting...");
		//	Connect to the Remote Host
		try {
			m_Parent.connectToServer(m_ServerIP.getHostAddress(), m_nServerPort);
		} catch (IOException e) {
			refuseCommand(getFailCode()); // Connection Refused
			throw new Exception("Socks 4 - Can't connect to " +
					getSocketInfo(m_Parent.m_ServerSocket));
		}

		LOGGER.debug("Connected to " + getSocketInfo(m_Parent.m_ServerSocket));
		replyCommand(getSuccessCode());
	}

	public void bindReply(byte ReplyCode, InetAddress IA, int PT) {
		LOGGER.debug("Reply to Client : \"{}\"", replyName(ReplyCode));

		final byte[] REPLY = new byte[8];
		final byte[] IP = IA.getAddress();

		REPLY[0] = 0;
		REPLY[1] = ReplyCode;
		REPLY[2] = (byte) ((PT & 0xFF00) >> 8);
		REPLY[3] = (byte) (PT & 0x00FF);
		REPLY[4] = IP[0];
		REPLY[5] = IP[1];
		REPLY[6] = IP[2];
		REPLY[7] = IP[3];

		if (m_Parent.isActive()) {
			m_Parent.sendToClient(REPLY);
		} else {
			LOGGER.debug("Closed BIND Client Connection");
		}
	}

	@NotNull
	public InetAddress resolveExternalLocalIP() {
		InetAddress IP = null;

		if (m_ExtLocalIP != null) {
			Socket sct;
			try {
				sct = new Socket(m_ExtLocalIP, m_Parent.getPort());
				IP = sct.getLocalAddress();
				sct.close();
				return m_ExtLocalIP;
			} catch (IOException e) {
				LOGGER.debug("WARNING !!! THE LOCAL IP ADDRESS WAS CHANGED !");
			}
		}

		final String[] hosts = {"www.wikipedia.org", "www.google.com", "www.microsoft.com", "www.amazon.com", "www.zombo.com", "www.ebay.com"};

		final List<Exception> bindExceptions = new ArrayList<>();
		for (String host : hosts) {
			try (Socket sct = new Socket(InetAddress.getByName(host), 80)) {
				IP = sct.getLocalAddress();
				break;
			} catch (Exception e) {
				bindExceptions.add(e);
			}
		}

		if (IP == null) {
			LOGGER.error("Error in BIND() - BIND reip Failed on all common hosts to determine external IP's");
			for (Exception bindException : bindExceptions) {
				LOGGER.debug(bindException.getMessage(), bindException);
			}
		}

		m_ExtLocalIP = IP;
		return requireNonNull(IP);
	}

	public void bind() throws IOException {
		int MyPort = 0;

		LOGGER.debug("Binding...");
		// Resolve External IP
		InetAddress MyIP = resolveExternalLocalIP();

		LOGGER.debug("Local IP : " + MyIP.toString());

		ServerSocket ssock = new ServerSocket(0);
		try {
			ssock.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);
			MyPort = ssock.getLocalPort();
		} catch (IOException e) {  // MyIP == null
			LOGGER.debug("Error in BIND() - Can't BIND at any Port");
			bindReply((byte) 92, MyIP, MyPort);
			ssock.close();
			return;
		}

		LOGGER.debug("BIND at : <" + MyIP.toString() + ":" + MyPort + ">");
		bindReply((byte) 90, MyIP, MyPort);

		Socket socket = null;

		while (socket == null) {
			if (m_Parent.checkClientData() >= 0) {
				LOGGER.debug("BIND - Client connection closed");
				ssock.close();
				return;
			}

			try {
				socket = ssock.accept();
				socket.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);
			} catch (InterruptedIOException e) {
				// ignore
			}
			Thread.yield();
		}

		m_ServerIP = socket.getInetAddress();
		m_nServerPort = socket.getPort();

		bindReply((byte) 90, socket.getInetAddress(), socket.getPort());

		m_Parent.m_ServerSocket = socket;
		m_Parent.prepareServer();

		LOGGER.debug("BIND Connection from " + getSocketInfo(m_Parent.m_ServerSocket));
		ssock.close();
	}

	public void udp() throws IOException {
		LOGGER.debug("Error - Socks 4 don't support UDP Association!");
		LOGGER.debug("Check your Software please...");
		refuseCommand((byte) 91);    // SOCKS4 don't support UDP
	}
}