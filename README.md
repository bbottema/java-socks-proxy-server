[![APACHE v2 License](https://img.shields.io/badge/license-apachev2-blue.svg?style=flat)](LICENSE-2.0.txt) 
[![Latest Release](https://img.shields.io/maven-central/v/com.github.bbottema/java-socks-proxy-server.svg?style=flat)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.github.bbottema%22%20AND%20a%3A%22java-socks-proxy-server%22) 
[![Javadocs](http://www.javadoc.io/badge/com.github.bbottema/java-socks-proxy-server.svg)](http://www.javadoc.io/doc/com.github.bbottema/java-socks-proxy-server)
[![Codacy](https://img.shields.io/codacy/grade/3d5316af468d4234bf9b783def62b416.svg?style=flat)](https://www.codacy.com/gh/bbottema/java-socks-proxy-server)

# java-socks-proxy-server
*java-socks-proxy-server* is a SOCKS 4/5 server for Java. Includes a JUnit Rule for easy testing with a SOCKS server.

It is a continuation of https://github.com/damico/java-socks-proxy-server.

```
<dependency>
  <groupId>com.github.bbottema</groupId>
  <artifactId>java-socks-proxy-server</artifactId>
  <version>3.0.0</version>
</dependency>
```

## Usage:

```
SocksServer socksServer = new SocksServer();

socksServer.start(100); // start serving clients on port 100
socksServer.start(200); // start serving clients on port 200
socksServer.start(300, myCustomServerSocketFactory); // eg. SSL on port 300

socksServer.stop(); // stops server on all ports
```

For use in junit tests:

```
	@ClassRule
	public static final SockServerRule sockServerRule = new SockServerRule(PROXY_SERVER_PORT);
	
	// or
	
	@ClassRule
	public static final SockServerRule sockServerRule = new SockServerRule(PROXY_SERVER_PORT, myServerSocketFactory);
```

And that's it!

## Change history

v3.0.0 (22-Januray-2024)

- [#12](https://github.com/bbottema/java-socks-proxy-server/issues/12): Added a more robust server adaptation with synchronous startup (including retries), shutdown closes all connections. With thanks to @kllbzz


v2.0.0 (26-December-2021)

- Switched to Java 8 and included fix for recent log4j security issue


v1.1.0 (15-April-2021)

- [#4](https://github.com/bbottema/java-socks-proxy-server/issues/4) added support for custom server socket factory (so you are free to configure SSL)


v1.0.2 (5-July-2020)

- Bumped log4j-core from 2.6.1 to 2.13.2


v1.0.1 (6-December-2019)

- Removed Jacoco instrumentation from production code


v1.0.0 (6-December-2019)

Initial release


4-December-2019

Initial upload