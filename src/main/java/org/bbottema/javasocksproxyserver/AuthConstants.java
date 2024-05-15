package org.bbottema.javasocksproxyserver;

public class AuthConstants {
    static final byte AUTH_VERSION = 0x01;
    static final byte[] AUTH_USER_PASS_SUCCESS = new byte[]{AUTH_VERSION, 0x00};
    // 0x00 denotes success, any other value denotes failure
    static final byte[] AUTH_USER_PASS_FAILED = new byte[]{AUTH_VERSION, 0x01};

    public static final byte TYPE_NO_AUTH = 0x00;
    public static final byte TYPE_USER_PASS_AUTH = 0x02;

    public static final byte NONE_ACCEPTED = (byte) 0xff;
}
