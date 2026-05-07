/*
 * Copyright © 2025 Jemin Huh (hjm1980@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.playground.service.oauth;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

@Component
public class OAuthTokenEncryptor {

    private static final String SECURITY_DIR_NAME = ".security";
    private static final String SALT_FILE_NAME = "oauth.salt";

    private final TextEncryptor textEncryptor;

    public OAuthTokenEncryptor(Path springAiPlaygroundHomeDir) throws IOException {
        Path securityDir = springAiPlaygroundHomeDir.resolve(SECURITY_DIR_NAME);
        Files.createDirectories(securityDir);
        Path saltPath = securityDir.resolve(SALT_FILE_NAME);
        String salt;
        if (Files.exists(saltPath)) {
            salt = Files.readString(saltPath, StandardCharsets.UTF_8).trim();
        } else {
            salt = KeyGenerators.string().generateKey();
            Files.writeString(saltPath, salt, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(saltPath,
                        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignore) {
            }
        }
        this.textEncryptor = Encryptors.text(derivePassphrase(), salt);
    }

    private static String derivePassphrase() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown-host";
        }
        return hostname + ":" + System.getProperty("user.home", "");
    }

    public String encrypt(String plaintext) {
        return textEncryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return textEncryptor.decrypt(ciphertext);
    }
}
