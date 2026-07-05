package com.nyk;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Password-based authenticated encryption. This class has no UI or file-system concerns. */
public final class SecureCrypto {
    private static final String PREFIX = "encrypto:v1:";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 600_000;
    private static final int LEGACY_ITERATIONS = 65_536;
    private static final int MIN_CIPHERTEXT_BYTES = SALT_BYTES + IV_BYTES + TAG_BITS / 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureCrypto() {
    }

    public static String encrypt(String plainText, char[] password) throws CryptoException {
        requirePassword(password);
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        byte[] clearBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = null;
        try {
            cipherBytes = crypt(Cipher.ENCRYPT_MODE, clearBytes, password, salt, iv, ITERATIONS);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(join(salt, iv, cipherBytes));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed.", e);
        } finally {
            Arrays.fill(clearBytes, (byte) 0);
            if (cipherBytes != null) Arrays.fill(cipherBytes, (byte) 0);
        }
    }

    /** Decrypts the current version and payloads produced by the original app. */
    public static String decrypt(String payload, char[] password) throws CryptoException {
        requirePassword(password);
        boolean legacy = !payload.startsWith(PREFIX);
        String encoded = legacy ? payload : payload.substring(PREFIX.length());
        try {
            byte[] packed = (legacy ? Base64.getDecoder() : Base64.getUrlDecoder()).decode(encoded.trim());
            if (packed.length < MIN_CIPHERTEXT_BYTES) {
                throw new CryptoException("Encrypted payload is incomplete.");
            }
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherBytes = new byte[buffer.remaining() - SALT_BYTES - IV_BYTES];
            buffer.get(salt).get(iv).get(cipherBytes);
            byte[] clearBytes = crypt(Cipher.DECRYPT_MODE, cipherBytes, password, salt, iv,
                    legacy ? LEGACY_ITERATIONS : ITERATIONS);
            try {
                return new String(clearBytes, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(clearBytes, (byte) 0);
            }
        } catch (AEADBadTagException e) {
            throw new CryptoException("Wrong password or damaged payload.");
        } catch (IllegalArgumentException e) {
            throw new CryptoException("Payload is not valid Base64 data.");
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Decryption failed.", e);
        }
    }

    private static byte[] crypt(int mode, byte[] input, char[] password, byte[] salt, byte[] iv, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        byte[] keyBytes = null;
        try {
            keyBytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(input);
        } finally {
            spec.clearPassword();
            if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] join(byte[] first, byte[] second, byte[] third) {
        return ByteBuffer.allocate(first.length + second.length + third.length)
                .put(first).put(second).put(third).array();
    }

    private static void requirePassword(char[] password) throws CryptoException {
        if (password == null || password.length == 0) {
            throw new CryptoException("Password must not be empty.");
        }
    }

    public static final class CryptoException extends Exception {
        public CryptoException(String message) { super(message); }
        public CryptoException(String message, Throwable cause) { super(message, cause); }
    }
}
