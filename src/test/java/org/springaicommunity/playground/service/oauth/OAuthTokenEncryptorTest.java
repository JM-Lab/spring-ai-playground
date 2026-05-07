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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokenEncryptorTest {

    @Test
    void encryptAndDecryptRoundTrip(@TempDir Path tempDir) throws IOException {
        OAuthTokenEncryptor encryptor = new OAuthTokenEncryptor(tempDir);
        String secret = "{\"refresh_token\":\"abc-123\",\"scopes\":[\"read\",\"write\"]}";
        String encrypted = encryptor.encrypt(secret);
        assertThat(encrypted).isNotEqualTo(secret);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void differentInstancesShareSaltOnRestart(@TempDir Path tempDir) throws IOException {
        OAuthTokenEncryptor first = new OAuthTokenEncryptor(tempDir);
        String secret = "test-payload";
        String encrypted = first.encrypt(secret);

        OAuthTokenEncryptor second = new OAuthTokenEncryptor(tempDir);
        assertThat(second.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void saltFileIsCreatedAtFirstRun(@TempDir Path tempDir) throws IOException {
        new OAuthTokenEncryptor(tempDir);
        Path saltPath = tempDir.resolve(".security").resolve("oauth.salt");
        assertThat(Files.exists(saltPath)).isTrue();
        String saltContent = Files.readString(saltPath, StandardCharsets.UTF_8).trim();
        assertThat(saltContent).isNotBlank();
    }

    @Test
    void encryptIsNonDeterministicForSamePlaintext(@TempDir Path tempDir) throws IOException {
        OAuthTokenEncryptor encryptor = new OAuthTokenEncryptor(tempDir);
        String secret = "the-same-secret";
        String firstCipher = encryptor.encrypt(secret);
        String secondCipher = encryptor.encrypt(secret);
        assertThat(firstCipher).isNotEqualTo(secondCipher);
        assertThat(encryptor.decrypt(firstCipher)).isEqualTo(secret);
        assertThat(encryptor.decrypt(secondCipher)).isEqualTo(secret);
    }
}
