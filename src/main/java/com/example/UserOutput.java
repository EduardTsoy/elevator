package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

class UserOutput implements AutoCloseable {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final PrintWriter writer;

    public UserOutput(final PrintStream out) {
        this.writer = new PrintWriter(new OutputStreamWriter(out));
    }

    @Override
    public void close() {
        writer.flush();
        writer.close();
    }

    public void writeString(final String message) {
        writer.flush();
        writer.write("[" + LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + "] " + message + "\n");
        writer.flush();
    }

    public void writeException(final Throwable e) {
        log.trace("writeException() started...");
        writer.flush();
        if (e != null) {
            if (e instanceof ElevatorException) {
                writeString(e.getMessage());
            } else {
                log.error("", e);
                writeString(e.getMessage());
            }
        } else {
            log.error("null");
        }
        writer.flush();
        log.trace("...writeException() finished");
    }
}
