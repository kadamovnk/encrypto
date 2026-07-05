package com.nyk;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/** Small platform clipboard boundary, kept separate from CLI and crypto code. */
final class ClipboardService {
    void copy(String value) throws IOException {
        try {
            systemClipboard().setContents(new StringSelection(value), null);
        } catch (HeadlessException | IllegalStateException | SecurityException e) {
            throw new IOException("System clipboard is unavailable: " + e.getMessage(), e);
        }
    }

    /** Clears only if Encrypto's value is still present, preserving anything copied afterwards. */
    boolean clearIfUnchanged(String expected) throws IOException {
        try {
            Clipboard clipboard = systemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return false;
            Object current = clipboard.getData(DataFlavor.stringFlavor);
            if (!expected.equals(current)) return false;
            clipboard.setContents(new StringSelection(""), null);
            return true;
        } catch (HeadlessException | IllegalStateException | SecurityException
                 | UnsupportedFlavorException e) {
            throw new IOException("Could not clear the system clipboard: " + e.getMessage(), e);
        }
    }

    private static Clipboard systemClipboard() {
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }
}
