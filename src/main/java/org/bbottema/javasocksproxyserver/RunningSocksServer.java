package org.bbottema.javasocksproxyserver;

public interface RunningSocksServer extends AutoCloseable {

    int getPort();

    void stop();

    @Override
    default void close() {
        stop();
    }
}
