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
package org.springaicommunity.playground.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ToolSpecPersistenceServiceBootTest {

    @TempDir Path home;

    private PersistenceExecutor persistenceExecutor;

    @BeforeEach
    void setUp() {
        this.persistenceExecutor = new PersistenceExecutor();
    }

    @AfterEach
    void tearDown() throws InterruptedException, TimeoutException {
        this.persistenceExecutor.flushAndShutdown();
    }

    @Test
    void foreignFileInSaveDirDoesNotClobberToolMcpServerSetting() throws IOException {
        Path saveDir = home.resolve("tool").resolve("save");
        Files.createDirectories(saveDir);
        Files.writeString(saveDir.resolve("default-tools-preference.json"),
                "{\"schemaVersion\":3,\"preset\":\"korea-toolkit\",\"include\":{\"names\":[]},"
                        + "\"exclude\":{\"names\":[]}}");

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService, "",
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver);

        service.onStart();

        verify(toolSpecService, never()).setToolMcpServerSetting(isNull());
        verify(toolSpecService, never()).setToolMcpServerSetting(any());
    }

    @Test
    void legitimateSaveFileIsLoaded() throws IOException {
        Path saveDir = home.resolve("tool").resolve("save");
        Files.createDirectories(saveDir);
        Files.writeString(saveDir.resolve("toolSpecsMcpSetting.json"),
                "{\"toolSpecs\":[],\"toolMcpServerSetting\":{\"autoAdd\":false,\"exposedToolIds\":[]}}");

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService, "",
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver);

        service.onStart();

        verify(toolSpecService).setToolMcpServerSetting(argThat((ToolMcpServerSetting s) ->
                s != null && !s.autoAdd() && Set.of().equals(s.exposedToolIds())));
    }

    @Test
    void tmpAndDeprecatedFilesAreIgnored() throws IOException {
        Path saveDir = home.resolve("tool").resolve("save");
        Files.createDirectories(saveDir);
        Files.writeString(saveDir.resolve("toolSpecsMcpSetting.json.tmp"), "{ corrupt");
        Files.writeString(saveDir.resolve("defaultToolOverrides.json.deprecated"), "{ corrupt");

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService, "",
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver);

        service.onStart();

        verify(toolSpecService, never()).setToolMcpServerSetting(any());
    }
}
