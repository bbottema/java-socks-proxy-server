package org.bbottema.javasocksproxyserver;

import android.util.Log;

public class Logger {

  private final String name;

  public Logger(String name) {
    this.name = name;
  }

  public void debug(String message) {
    Log.d(name, "Debug: " + message);
  }

  public void error(String message) {
    Log.d(name, "Error: " + message);
  }

  public void warn(String message) {
    Log.d(name, "Warn: " + message);
  }
}
