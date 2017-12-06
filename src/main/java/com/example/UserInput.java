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
                    String s = reader.readLine();
                    lines.add(s);
                } catch (final IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
