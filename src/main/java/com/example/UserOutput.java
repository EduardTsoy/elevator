package com.example;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;

class UserOutput implements AutoCloseable {
    private final PrintWriter writer;

    public UserOutput(final PrintStream out) {
        this.writer = new PrintWriter(new OutputStreamWriter(out));
    }

    @Override
    public void close() {
        writer.flush();
        writer.close();
    }

    public void println(final String message) {
        if (message != null) {
            writer.flush();
            writer.write(message + "\n");
            writer.flush();
        }
    }

    public void println(final Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace(writer);
            writer.flush();
        }
    }
}
