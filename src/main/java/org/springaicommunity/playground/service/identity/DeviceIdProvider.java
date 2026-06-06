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
package org.springaicommunity.playground.service.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeviceIdProvider {

    private static final Logger logger = LoggerFactory.getLogger(DeviceIdProvider.class);

    private static final String SALT = "spring-ai-playground:device-id:v1";
    private static final long PROBE_TIMEOUT_SECONDS = 3;
    private static final Pattern MAC_UUID = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern WIN_GUID = Pattern.compile("MachineGuid\\s+REG_SZ\\s+([\\w-]+)");

    public Optional<String> resolve() {
        return readMachineId().map(DeviceIdProvider::deriveUuid);
    }

    static String deriveUuid(String machineId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((SALT + ':' + machineId.trim()).getBytes(StandardCharsets.UTF_8));
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (hash[i] & 0xFF);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (hash[i] & 0xFF);
            msb &= ~(0xFL << 12);
            msb |= 0x5L << 12;
            lsb &= ~(0xC000000000000000L);
            lsb |= 0x8000000000000000L;
            return new UUID(msb, lsb).toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Optional<String> readMachineId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("linux")) return readLinux();
            if (os.contains("mac") || os.contains("darwin")) return readMac();
            if (os.contains("windows")) return readWindows();
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.debug("machine-id probe failed on os={}: {}", os, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> readLinux() throws IOException {
        for (String candidate : List.of("/etc/machine-id", "/var/lib/dbus/machine-id")) {
            Path path = Path.of(candidate);
            if (Files.isReadable(path)) {
                String id = Files.readString(path).trim();
                if (!id.isBlank()) return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    private Optional<String> readMac() throws IOException, InterruptedException {
        Matcher matcher = MAC_UUID.matcher(runProcess("ioreg", "-rd1", "-c", "IOPlatformExpertDevice"));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> readWindows() throws IOException, InterruptedException {
        Matcher matcher = WIN_GUID.matcher(runProcess("reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private String runProcess(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("machine-id probe timed out");
        }
        return output;
    }
}
