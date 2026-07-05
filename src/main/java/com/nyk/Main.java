package com.nyk;

/** Application entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new EncryptoCli(System.in, System.out, System.err).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
