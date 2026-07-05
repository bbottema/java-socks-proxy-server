package org.bbottema.javasocksproxyserver;

import java.util.concurrent.atomic.AtomicBoolean;

final class RunningSocksServerHandle implements RunningSocksServer {

    private final int port;
    private final Runnable stopAction;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    RunningSocksServerHandle(int port, Runnable stopAction) {
        this.port = port;
        this.stopAction = stopAction;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            stopAction.run();
        }
    }
}
