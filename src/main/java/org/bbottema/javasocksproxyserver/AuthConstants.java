package org.bbottema.javasocksproxyserver;

public interface AuthConstants {
    byte AUTH_VERSION = 0x01;
    byte[] AUTH_USER_PASS_SUCCESS = new byte[]{AUTH_VERSION, 0x00};
    // 0x00 denotes success, any other value denotes failure
    byte[] AUTH_USER_PASS_FAILED = new byte[]{AUTH_VERSION, 0x01};

    byte TYPE_NO_AUTH = 0x00;
    byte TYPE_USER_PASS_AUTH = 0x02;

    byte NONE_ACCEPTED = (byte) 0xff;
}
