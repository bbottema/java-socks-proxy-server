package org.bbottema.javasocksproxyserver.auth;

public abstract class Authenticator {
  public abstract byte accept(byte[] authTypes);
  public abstract boolean validate(byte[] username, byte[] password);
}
