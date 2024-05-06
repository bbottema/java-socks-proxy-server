package org.bbottema.javasocksproxyserver.auth;

import org.bbottema.javasocksproxyserver.AuthConstants;

public class DefaultAuthenticator extends Authenticator {
  @Override
  public byte accept(byte[] authTypes) {
    for (byte type : authTypes) {
      if (type == AuthConstants.TYPE_NO_AUTH) {
        return type;
      }
    }
    return AuthConstants.NONE_ACCEPTED;
  }

  @Override
  public boolean validate(byte[] username, byte[] password) {
    throw new RuntimeException("Calling validate() on Default Authenticator");
  }
}
