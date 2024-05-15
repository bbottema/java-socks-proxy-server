package org.bbottema.javasocksproxyserver;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static java.lang.String.format;
import static org.bbottema.javasocksproxyserver.Utils.getSocketInfo;

public class Socks5Impl extends Socks4Impl {

	private static final int[] ADDR_Size = {
			-1, //'00' No such AType
			4, //'01' IP v4 - 4Bytes
			-1, //'02' No such AType
			-1, //'03' First Byte is Len
			16  //'04' IP v6 - 16bytes
	};
	private static final Logger LOGGER = LoggerFactory.getLogger(Socks5Impl.class);

	private static final int MAX_ADDR_LEN = 255;

	private byte acceptedAuthType = 0x00;

	private byte ADDRESS_TYPE;
	private DatagramSocket DGSocket = null;
	private DatagramPacket DGPack = null;
	private InetAddress UDP_IA = null;
	private int UDP_port = 0;

	Socks5Impl(ProxyHandler Parent) {
		super(Parent);
		DST_Addr = new byte[MAX_ADDR_LEN];
	}

	@SuppressWarnings("OctalInteger")
	public byte getSuccessCode() {
		return 00;
	}

	@SuppressWarnings("OctalInteger")
	public byte getFailCode() {
		return 04;
	}

	@Nullable
	public InetAddress calcInetAddress(byte AType, byte[] addr) {
		InetAddress IA;

		switch (AType) {
			// Version IP 4
			case 0x01:
				IA = Utils.calcInetAddress(addr);
				break;
			// Version IP DOMAIN NAME
			case 0x03:
				if (addr[0] <= 0) {
					LOGGER.error("SOCKS 5 - calcInetAddress() : BAD IP in command - size : " + addr[0]);
					return null;
				}
				StringBuilder sIA = new StringBuilder();
				for (int i = 1; i <= addr[0]; i++) {
					sIA.append((char) addr[i]);
				}
				try {
					IA = InetAddress.getByName(sIA.toString());
				} catch (UnknownHostException e) {
					return null;
				}
				break;
			default:
				return null;
		}
		return IA;
	}

	public boolean isInvalidAddress() {
		m_ServerIP = calcInetAddress(ADDRESS_TYPE, DST_Addr);
		m_nServerPort = Utils.calcPort(DST_Port[0], DST_Port[1]);

		m_ClientIP = m_Parent.m_ClientSocket.getInetAddress();
		m_nClientPort = m_Parent.m_ClientSocket.getPort();

		return !((m_ServerIP != null) && (m_nServerPort >= 0));
	}

	public void authenticate(byte SOCKS_Ver) throws Exception {
		super.authenticate(SOCKS_Ver); // Sets SOCKS Version...

		if (SOCKS_Version == SocksConstants.SOCKS5_Version) {
			byte[] authModes = getAuthenticationModes();
			acceptedAuthType = m_Parent.authenticator.accept(authModes);
			LOGGER.debug("Socks 5 - Accepted Auth Type {}", acceptedAuthType);
			sendAuthResponse(acceptedAuthType);
		} else {
			sendAuthResponse(AuthConstants.NONE_ACCEPTED);
			LOGGER.debug("SOCKS 5 - Refuse Authentication: Incorrect SOCKS version: {}", SOCKS_Version);
			throw new Exception("Not Supported SOCKS Version -'" +
					SOCKS_Version + "'");
		}
	}

	@Override
	public void clientAuthResponse() throws Exception {
		if (acceptedAuthType == AuthConstants.TYPE_NO_AUTH) {
			return;
		}
		if (acceptedAuthType != AuthConstants.TYPE_USER_PASS_AUTH) {
            LOGGER.debug("Unknown AUTH TYPE {}", acceptedAuthType);
			throw new RuntimeException("Can only handle NO_AUTH, USER_PASS_AUTH, unrecognized '" + acceptedAuthType + "'");
		}
    	byte version = getByte();
    	if (version != AuthConstants.AUTH_VERSION) {
      		m_Parent.sendToClient(AuthConstants.AUTH_USER_PASS_FAILED);
			LOGGER.debug("Socks 5 - Unknown AUTH_VERSION {}", version);
      		throw new Exception("Not supported SOCKS Username Password Version - '" + version + "'");
    	}
    	byte[] username = readByteString();
    	byte[] password = readByteString();

    	boolean credentialsAccepted = m_Parent.authenticator.validate(username, password);
    	m_Parent.sendToClient(credentialsAccepted ? AuthConstants.AUTH_USER_PASS_SUCCESS : AuthConstants.AUTH_USER_PASS_FAILED);
    }

	public byte[] readByteString() {
		byte length = getByte();
		byte[] bytes = new byte[length];

		for (int i = 0; i < length; i++) {
			bytes[i] = getByte();
		}
		return bytes;
	}

	private byte[] getAuthenticationModes() {
		byte numberOfAuthModesAvailable = getByte();
		byte[] auths = new byte[numberOfAuthModesAvailable];
		for (byte i = 0; i < numberOfAuthModesAvailable; i++) {
			auths[i] = getByte();
		}
		return auths;
	}


	private void sendAuthResponse(byte result) {
		byte[] authResponse = new byte[2];
		authResponse[0] = SOCKS_Version;
		authResponse[1] = result;
		m_Parent.sendToClient(authResponse);
	}

	public void getClientCommand() throws Exception {
		SOCKS_Version = getByte();
		socksCommand = getByte();
		/*byte RSV =*/ getByte(); // Reserved. Must be'00'
		ADDRESS_TYPE = getByte();

		int Addr_Len = ADDR_Size[ADDRESS_TYPE];
		DST_Addr[0] = getByte();
		if (ADDRESS_TYPE == 0x03) {
			Addr_Len = DST_Addr[0] + 1;
		}

		for (int i = 1; i < Addr_Len; i++) {
			DST_Addr[i] = getByte();
		}
		DST_Port[0] = getByte();
		DST_Port[1] = getByte();

		if (SOCKS_Version != SocksConstants.SOCKS5_Version) {
			LOGGER.debug("SOCKS 5 - Incorrect SOCKS Version of Command: " +
					SOCKS_Version);
			refuseCommand((byte) 0xFF);
			throw new Exception("Incorrect SOCKS Version of Command: " +
					SOCKS_Version);
		}

		if ((socksCommand < SocksConstants.SC_CONNECT) || (socksCommand > SocksConstants.SC_UDP)) {
			LOGGER.error("SOCKS 5 - GetClientCommand() - Unsupported Command : \"" + commName(socksCommand) + "\"");
			refuseCommand((byte) 0x07);
			throw new Exception("SOCKS 5 - Unsupported Command: \"" + socksCommand + "\"");
		}

		if (ADDRESS_TYPE == 0x04) {
			LOGGER.error("SOCKS 5 - GetClientCommand() - Unsupported Address Type - IP v6");
			refuseCommand((byte) 0x08);
			throw new Exception("Unsupported Address Type - IP v6");
		}

		if ((ADDRESS_TYPE >= 0x04) || (ADDRESS_TYPE <= 0)) {
			LOGGER.error("SOCKS 5 - GetClientCommand() - Unsupported Address Type: " + ADDRESS_TYPE);
			refuseCommand((byte) 0x08);
			throw new Exception("SOCKS 5 - Unsupported Address Type: " + ADDRESS_TYPE);
		}

		if (isInvalidAddress()) {  // Gets the IP Address
			refuseCommand((byte) 0x04); // Host Not Exists...
			throw new Exception("SOCKS 5 - Unknown Host/IP address '" + m_ServerIP.toString() + "'");
		}

		LOGGER.debug("SOCKS 5 - Accepted SOCKS5 Command: \"" + commName(socksCommand) + "\"");
	}

	public void replyCommand(byte replyCode) {
		LOGGER.debug("SOCKS 5 - Reply to Client \"" + replyName(replyCode) + "\"");

		final int pt;

		byte[] REPLY = new byte[10];
		byte[] IP = new byte[4];

		if (m_Parent.m_ServerSocket != null) {
			pt = m_Parent.m_ServerSocket.getLocalPort();
		} else {
			IP[0] = 0;
			IP[1] = 0;
			IP[2] = 0;
			IP[3] = 0;
			pt = 0;
		}

		formGenericReply(replyCode, pt, REPLY, IP);

		m_Parent.sendToClient(REPLY);// BND.PORT
	}

	public void bindReply(byte replyCode, InetAddress IA, int PT) {
		byte[] IP = {0, 0, 0, 0};

		LOGGER.debug("BIND Reply to Client \"" + replyName(replyCode) + "\"");

		byte[] REPLY = new byte[10];
		if (IA != null) IP = IA.getAddress();

		formGenericReply((byte) ((int) replyCode - 90), PT, REPLY, IP);

		if (m_Parent.isActive()) {
			m_Parent.sendToClient(REPLY);
		} else {
			LOGGER.debug("BIND - Closed Client Connection");
		}
	}

	public void udpReply(byte replyCode, InetAddress IA, int pt) {
		LOGGER.debug("Reply to Client \"" + replyName(replyCode) + "\"");

		if (m_Parent.m_ClientSocket == null) {
			LOGGER.debug("Error in UDP_Reply() - Client socket is NULL");
		}
		byte[] IP = IA.getAddress();

		byte[] REPLY = new byte[10];

		formGenericReply(replyCode, pt, REPLY, IP);

		m_Parent.sendToClient(REPLY);// BND.PORT
	}

	private void formGenericReply(byte replyCode, int pt, byte[] REPLY, byte[] IP) {
		REPLY[0] = SocksConstants.SOCKS5_Version;
		REPLY[1] = replyCode;
		REPLY[2] = 0x00;        // Reserved	'00'
		REPLY[3] = 0x01;        // DOMAIN NAME Address Type IP v4
		REPLY[4] = IP[0];
		REPLY[5] = IP[1];
		REPLY[6] = IP[2];
		REPLY[7] = IP[3];
		REPLY[8] = (byte) ((pt & 0xFF00) >> 8);// Port High
		REPLY[9] = (byte) (pt & 0x00FF);      // Port Low
	}

	public void udp() throws IOException {
		//	Connect to the Remote Host
		try {
			DGSocket = new DatagramSocket();
			initUdpInOut();
		} catch (IOException e) {
			refuseCommand((byte) 0x05); // Connection Refused
			throw new IOException("Connection Refused - FAILED TO INITIALIZE UDP Association.");
		}

		InetAddress MyIP = m_Parent.m_ClientSocket.getLocalAddress();
		int MyPort = DGSocket.getLocalPort();

		//	Return response to the Client
		// Code '00' - Connection Succeeded,
		// IP/Port where Server will listen
		udpReply((byte) 0, MyIP, MyPort);

		LOGGER.debug("UDP Listen at: <" + MyIP.toString() + ":" + MyPort + ">");

		while (m_Parent.checkClientData() >= 0) {
			processUdp();
			Thread.yield();
		}
		LOGGER.debug("UDP - Closed TCP Master of UDP Association");
	}

	private void initUdpInOut() throws IOException {
		DGSocket.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);
		m_Parent.m_Buffer = new byte[SocksConstants.DEFAULT_BUF_SIZE];
		DGPack = new DatagramPacket(m_Parent.m_Buffer, SocksConstants.DEFAULT_BUF_SIZE);
	}

	@NotNull
	private byte[] addDgpHead(byte[] buffer) {
		byte[] IABuf = DGPack.getAddress().getAddress();
		int DGport = DGPack.getPort();
		int HeaderLen = 6 + IABuf.length;
		int DataLen = DGPack.getLength();
		int NewPackLen = HeaderLen + DataLen;

		byte[] UB = new byte[NewPackLen];

		UB[0] = (byte) 0x00;    // Reserved 0x00
		UB[1] = (byte) 0x00;    // Reserved 0x00
		UB[2] = (byte) 0x00;    // FRAG '00' - Standalone DataGram
		UB[3] = (byte) 0x01;    // Address Type -->'01'-IP v4
		System.arraycopy(IABuf, 0, UB, 4, IABuf.length);
		UB[4 + IABuf.length] = (byte) ((DGport >> 8) & 0xFF);
		UB[5 + IABuf.length] = (byte) ((DGport) & 0xFF);
		System.arraycopy(buffer, 0, UB, 6 + IABuf.length, DataLen);
		System.arraycopy(UB, 0, buffer, 0, NewPackLen);
		return UB;
	}

	@Nullable
	private byte[] clearDgpHead(byte[] buffer) {
		final int IAlen;
		//int	bl	= Buffer.length;
		int p = 4;    // First byte of IP Address

		byte AType = buffer[3];    // IP Address Type
		switch (AType) {
			case 0x01:
				IAlen = 4;
				break;
			case 0x03:
				IAlen = buffer[p] + 1;
				break; // One for Size Byte
			default:
				LOGGER.debug("Error in ClearDGPhead() - Invalid Destination IP Addres type " + AType);
				return null;
		}

		byte[] IABuf = new byte[IAlen];
		System.arraycopy(buffer, p, IABuf, 0, IAlen);
		p += IAlen;

		UDP_IA = calcInetAddress(AType, IABuf);
		UDP_port = Utils.calcPort(buffer[p++], buffer[p++]);

		if (UDP_IA == null) {
			LOGGER.debug("Error in ClearDGPHead() - Invalid UDP dest IP address: NULL");
			return null;
		}

		int DataLen = DGPack.getLength();
		DataLen -= p; // <p> is length of UDP Header

		byte[] UB = new byte[DataLen];
		System.arraycopy(buffer, p, UB, 0, DataLen);
		System.arraycopy(UB, 0, buffer, 0, DataLen);

		return UB;
	}

	protected void udpSend(DatagramPacket DGP) {
		if (DGP != null) {
			String LogString = DGP.getAddress() + ":" +
					DGP.getPort() + "> : " +
					DGP.getLength() + " bytes";
			try {
				DGSocket.send(DGP);
			} catch (IOException e) {
				LOGGER.debug("Error in ProcessUDPClient() - Failed to Send DGP to " + LogString);
			}
		}
	}

	public void processUdp() {
		// Trying to Receive DataGram
		try {
			DGSocket.receive(DGPack);
		} catch (InterruptedIOException e) {
			return;    // Time Out
		} catch (IOException e) {
			LOGGER.debug("Error in ProcessUDP() - " + e.toString());
			return;
		}

		if (m_ClientIP.equals(DGPack.getAddress())) {
			processUdpClient();
		} else {
			processUdpRemote();
		}

		try {
			initUdpInOut();    // Clean DGPack & Buffer
		} catch (IOException e) {
			LOGGER.debug("IOError in Init_UDP_IO() - " + e.toString());
			m_Parent.close();
		}
	}

	/**
	 * Processing Client's datagram
	 * This Method must be called only from {@link #processUdp()}
	 */
	private void processUdpClient() {
		m_nClientPort = DGPack.getPort();

		// Also calculates UDP_IA & UDP_port ...
		byte[] Buf = clearDgpHead(DGPack.getData());
		if (Buf == null) return;

		if (Buf.length <= 0) return;

		if (UDP_IA == null) {
			LOGGER.debug("Error in ProcessUDPClient() - Invalid Destination IP - NULL");
			return;
		}
		if (UDP_port == 0) {
			LOGGER.debug("Error in ProcessUDPClient() - Invalid Destination Port - 0");
			return;
		}

		if (m_ServerIP != UDP_IA || m_nServerPort != UDP_port) {
			m_ServerIP = UDP_IA;
			m_nServerPort = UDP_port;
		}

		LOGGER.debug("Datagram : " + Buf.length + " bytes : " + getSocketInfo(DGPack) +
				" >> <" + Utils.iP2Str(m_ServerIP) + ":" + m_nServerPort + ">");

		DatagramPacket DGPSend = new DatagramPacket(Buf, Buf.length,
				UDP_IA, UDP_port);

		udpSend(DGPSend);
	}


	public void processUdpRemote() {
		LOGGER.debug(format("Datagram : %d bytes : <%s:%d> << %s",
				DGPack.getLength(), Utils.iP2Str(m_ClientIP), m_nClientPort, getSocketInfo(DGPack)));

		// This Method must be CALL only from <ProcessUDP()>
		// ProcessUDP() Reads a Datagram packet <DGPack>

		InetAddress DGP_IP = DGPack.getAddress();
		int DGP_Port = DGPack.getPort();

		final byte[] Buf = addDgpHead(m_Parent.m_Buffer);

		// SendTo Client
		DatagramPacket DGPSend = new DatagramPacket(Buf, Buf.length,
				m_ClientIP, m_nClientPort);
		udpSend(DGPSend);

		if (DGP_IP != UDP_IA || DGP_Port != UDP_port) {
			m_ServerIP = DGP_IP;
			m_nServerPort = DGP_Port;
		}
	}
}