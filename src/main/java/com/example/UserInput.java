package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentLinkedDeque;

public class UserInput implements AutoCloseable {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BufferedReader reader;
    private ConcurrentLinkedDeque<String> lines;
    private final Thread daemon;

    public UserInput(final InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in));
        this.lines = new ConcurrentLinkedDeque<>();

        daemon = new Thread(new UserInputDaemon());
        daemon.setDaemon(true);
        daemon.start();
    }

    @Override
    public void close() throws Exception {
        daemon.interrupt();
        reader.close();
    }

    public String nextLine() {
        return lines.poll();
    }

    private class UserInputDaemon implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10);
                    if (reader.ready()) {
                        final String s = reader.readLine();
                        log.debug("\n // s: " + s);
                        lines.add(s);
                    }
                } catch (final IOException e) {
                    log.debug("", e);
                    throw new RuntimeException(e);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            log.info("UserInput daemon thread has been interrupted.");
        }
    }
}
