package com.nyk;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;

final class EncryptoCli {
    private static final int EXPOSURE_SECONDS = 12;
    private static final String CLEAR_SCREEN = "\033[H\033[2J";
    private static final String HELP = """
            Usage:
              encrypto encrypt [--text VALUE | --in FILE] [--out FILE | --clipboard]
              encrypto decrypt [--text VALUE | --in FILE] [--out FILE | --clipboard]
              encrypto                         Interactive mode

            Options:
              -t, --text VALUE   Read the token/payload from the argument
              -i, --in FILE      Read it from a UTF-8 file
              -o, --out FILE     Write the result to a file (owner-only permissions where supported)
              -c, --clipboard    Copy the result without displaying it; clear it after 12 seconds
              -h, --help         Show this help

            The password is read securely from the terminal. If no console is available,
            it is read from the first stdin line; do not pass passwords as command arguments.
            """;

    private final BufferedReader input;
    private final PrintStream out;
    private final PrintStream err;
    private final ClipboardService clipboard;

    EncryptoCli(InputStream input, PrintStream out, PrintStream err) {
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = out;
        this.err = err;
        this.clipboard = new ClipboardService();
    }

    int run(String[] args) {
        try {
            if (args.length == 0) return interactive();
            if (isHelp(args[0])) {
                out.print(HELP);
                return 0;
            }
            return execute(parse(args), false);
        } catch (UsageException e) {
            err.println("Error: " + e.getMessage());
            err.println("Run with --help for usage.");
            return 2;
        } catch (SecureCrypto.CryptoException | IOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int interactive() throws IOException, SecureCrypto.CryptoException, UsageException {
        out.println("ENCRYPTO // small secrets, safely stored");
        out.print("Mode [encrypt/decrypt]: ");
        String mode = input.readLine();
        out.print(mode != null && mode.equalsIgnoreCase("decrypt")
                ? "Encrypted payload: " : "Token or secret: ");
        String text = input.readLine();
        Command command = new Command(normalizeMode(mode), text, null, null, false);
        return execute(command, true);
    }

    private int execute(Command command, boolean interactive)
            throws IOException, SecureCrypto.CryptoException, UsageException {
        String value = command.text;
        if (command.input != null) value = Files.readString(command.input, StandardCharsets.UTF_8).strip();
        if (value == null) {
            out.print(command.mode.equals("encrypt") ? "Token or secret: " : "Encrypted payload: ");
            value = input.readLine();
        }
        if (value == null || value.isEmpty()) throw new UsageException("Input must not be empty.");

        char[] password = readPassword(command.mode.equals("encrypt") ? "Password: " : "Password: ");
        try {
            if (interactive && command.mode.equals("encrypt")) {
                char[] confirmation = readPassword("Confirm password: ");
                try {
                    if (!Arrays.equals(password, confirmation)) throw new UsageException("Passwords do not match.");
                } finally {
                    Arrays.fill(confirmation, '\0');
                }
            }
            String result = command.mode.equals("encrypt")
                    ? SecureCrypto.encrypt(value, password)
                    : SecureCrypto.decrypt(value, password);
            if (command.clipboard) {
                copyTemporarily(result);
            } else if (command.output != null) {
                writeSecurely(command.output, result + System.lineSeparator());
                out.println("Saved to " + command.output.toAbsolutePath());
            } else {
                displayTemporarily(result);
            }
            return 0;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private char[] readPassword(String prompt) throws IOException, UsageException {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword("%s", prompt);
            if (password == null) throw new UsageException("No password provided.");
            return password;
        }
        err.print(prompt);
        String password = input.readLine();
        if (password == null) throw new UsageException("No password provided.");
        return password.toCharArray();
    }

    private static Command parse(String[] args) throws UsageException {
        String mode = normalizeMode(args[0]);
        String text = null;
        Path input = null;
        Path output = null;
        boolean clipboard = false;
        for (int i = 1; i < args.length; i++) {
            String option = args[i];
            if (isHelp(option)) throw new UsageException("Place --help before the command.");
            if (option.equals("-c") || option.equals("--clipboard")) {
                clipboard = true;
                continue;
            }
            if (i + 1 >= args.length) throw new UsageException("Missing value for " + option + '.');
            String value = args[++i];
            switch (option) {
                case "-t", "--text" -> text = value;
                case "-i", "--in" -> input = Path.of(value);
                case "-o", "--out" -> output = Path.of(value);
                default -> throw new UsageException("Unknown option: " + option);
            }
        }
        if (text != null && input != null) throw new UsageException("Use either --text or --in, not both.");
        if (clipboard && output != null) throw new UsageException("Use either --clipboard or --out, not both.");
        return new Command(mode, text, input, output, clipboard);
    }

    private static String normalizeMode(String mode) throws UsageException {
        if (mode == null) throw new UsageException("Mode is required.");
        return switch (mode.toLowerCase()) {
            case "encrypt", "e" -> "encrypt";
            case "decrypt", "d" -> "decrypt";
            default -> throw new UsageException("Mode must be encrypt or decrypt.");
        };
    }

    private static boolean isHelp(String value) {
        return "--help".equals(value) || "-h".equals(value);
    }

    private void displayTemporarily(String result) {
        out.println(result);
        if (System.console() == null) return; // Preserve redirected output and IDE/test streams.
        out.printf("Result will be cleared in %d seconds.%n", EXPOSURE_SECONDS);
        pause();
        out.print(CLEAR_SCREEN);
        out.flush();
    }

    private void copyTemporarily(String result) throws IOException {
        clipboard.copy(result);
        out.printf("Copied to clipboard. It will be cleared in %d seconds.%n", EXPOSURE_SECONDS);
        pause();
        if (clipboard.clearIfUnchanged(result)) out.println("Clipboard cleared.");
        else out.println("Clipboard changed; newer content was preserved.");
    }

    private static void pause() {
        try {
            Thread.sleep(EXPOSURE_SECONDS * 1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeSecurely(Path target, String content) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".encrypto-", ".tmp");
        try {
            try {
                Files.setPosixFilePermissions(temp, Set.of(PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX file systems use their platform defaults.
            }
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private record Command(String mode, String text, Path input, Path output, boolean clipboard) { }
    private static final class UsageException extends Exception {
        private UsageException(String message) { super(message); }
    }
}
