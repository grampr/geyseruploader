package org.geyserupdater.core.logging;

public interface LogAdapter {
    void info(String msg);
    void warn(String msg);
    void error(String msg, Throwable t);
}