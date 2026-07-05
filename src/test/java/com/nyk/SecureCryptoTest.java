package com.nyk;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureCryptoTest {
    @Test
    void roundTripSupportsUnicode() throws Exception {
        char[] password = "correct horse battery staple".toCharArray();
        String encrypted = SecureCrypto.encrypt("token-🔐-value", password);

        assertTrue(encrypted.startsWith("encrypto:v1:"));
        assertEquals("token-🔐-value", SecureCrypto.decrypt(encrypted, password));
    }

    @Test
    void randomSaltAndIvProduceDifferentPayloads() throws Exception {
        char[] password = "password".toCharArray();
        assertNotEquals(SecureCrypto.encrypt("same", password), SecureCrypto.encrypt("same", password));
    }

    @Test
    void rejectsWrongPasswordAndDamagedInput() throws Exception {
        String encrypted = SecureCrypto.encrypt("secret", "right".toCharArray());

        SecureCrypto.CryptoException wrongPassword = assertThrows(SecureCrypto.CryptoException.class,
                () -> SecureCrypto.decrypt(encrypted, "wrong".toCharArray()));
        assertEquals("Wrong password or damaged payload.", wrongPassword.getMessage());
        assertThrows(SecureCrypto.CryptoException.class,
                () -> SecureCrypto.decrypt("not base64!", "right".toCharArray()));
    }

    @Test
    void decryptsPayloadFromOriginalApplication() throws Exception {
        assertEquals("old token", SecureCrypto.decrypt(legacyEncrypt("old token", "legacy"),
                "legacy".toCharArray()));
    }

    private static String legacyEncrypt(String text, String password) throws Exception {
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(iv);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 65_536, 256)).getEncoded();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(salt.length + iv.length + encrypted.length)
                .put(salt).put(iv).put(encrypted).array());
    }
}
