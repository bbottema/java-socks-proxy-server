[![APACHE v2 License](https://img.shields.io/badge/license-apachev2-blue.svg?style=flat)](LICENSE-2.0.txt) 
[![Latest Release](https://img.shields.io/maven-central/v/com.github.bbottema/java-socks-proxy-server.svg?style=flat)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.github.bbottema%22%20AND%20a%3A%22java-socks-proxy-server%22) 
[![Javadocs](http://www.javadoc.io/badge/com.github.bbottema/java-socks-proxy-server.svg)](http://www.javadoc.io/doc/com.github.bbottema/java-socks-proxy-server)
[![Codacy](https://img.shields.io/codacy/grade/3d5316af468d4234bf9b783def62b416.svg?style=flat)](https://www.codacy.com/gh/bbottema/java-socks-proxy-server)

# java-socks-proxy-server
*java-socks-proxy-server* is a SOCKS 4/5 server for Java. Includes a JUnit Rule for easy testing with a SOCKS server.

It is a continuation of https://github.com/damico/java-socks-proxy-server.

```xml
<dependency>
  <groupId>com.github.bbottema</groupId>
  <artifactId>java-socks-proxy-server</artifactId>
  <version>3.0.0</version>
</dependency>
```

## Usage:

```java
// start serving clients on port 1234
SocksServer server = new SocksServer(1234).start();
```

Or you can supply your own `ServerSocketFactory`:

```java
// e.g. SSL on port 7132
SocksServer server = new SocksServer(1234, myCustomServerFactory).start();
```

> By default, library uses `NO_AUTH` authentication mode

### Username and Password Authentication

If you want to authenticate the clients, before proxying, you can set a `UsernamePasswordAuthenticator`, library supports standard Username/Password protocol.

```java
    new SocksServer(1234)
        .setAuthenticator(new UsernamePasswordAuthenticator(false) {
          @Override
          public boolean validate(String username, String password) {
            // validate credentials here, e.g. check your local database
            return username.equals("mysecureusername") && password.equals("mysecurepassword");
          }
        }).start();
```

> Supply a `true` value to constructor `UsernamePasswordAuthenticator()`, if you also want to prefer `NO_AUTH` mode over Username and password.

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