package demo;

import org.bbottema.javasocksproxyserver.SocksServer;

import java.io.IOException;

public class ProxyAuthTest {
  public static void main(String[] args) throws IOException {
    new SocksServer(10003).start();
  }
}
