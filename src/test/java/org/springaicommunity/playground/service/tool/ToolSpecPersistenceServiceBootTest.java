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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void legacyOverridesFileIsArchivedOnStart() throws IOException {
        Path saveDir = home.resolve("tool").resolve("save");
        Files.createDirectories(saveDir);
        Files.writeString(saveDir.resolve("defaultToolOverrides.json"), "{}");

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService, "",
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator());

        service.onStart();

        assertThat(saveDir.resolve("defaultToolOverrides.json")).doesNotExist();
        assertThat(saveDir.resolve("defaultToolOverrides.json.deprecated")).exists();
    }

    @Test
    void exposeBuiltinToolsAsPreferenceRewritesPreferenceAndSetting() throws IOException {
        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        when(toolSpecService.getToolMcpServerSetting()).thenReturn(new ToolMcpServerSetting(true, Set.of()));
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);
        when(resolver.resolveActiveNames(any(), any())).thenReturn(Set.of("getCurrentTime"));

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService, "",
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator());

        service.exposeBuiltinToolsAsPreference(Set.of("getCurrentTime"));

        verify(preferenceService).update(any());
        verify(toolSpecService).updateToolMcpServerSetting(any(ToolMcpServerSetting.class));
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
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator());

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
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator());

        service.onStart();

        verify(toolSpecService).setToolMcpServerSetting(argThat((ToolMcpServerSetting s) ->
                s != null && !s.autoAdd() && Set.of().equals(s.exposedToolIds())));
    }

    @Test
    void corruptSaveFileIsSkippedAtBootInsteadOfCrashing() throws IOException {
        Path saveDir = home.resolve("tool").resolve("save");
        Files.createDirectories(saveDir);
        Files.writeString(saveDir.resolve("toolSpecsMcpSetting.json"), "{ not valid json ]]");

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);

        // The @Service constructor calls loads() on the corrupt file; it must skip it, not crash boot.
        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService, "",
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator());

        service.onStart();

        verify(toolSpecService, never()).setToolMcpServerSetting(any());
    }

    @Test
    void activeDefaultToolWithUnmetEnvVarBootsAsDraft() throws IOException {
        Path specFile = home.resolve("boot-test-specs.json");
        Files.writeString(specFile, """
                [
                  {"toolId": "id-plain", "name": "plainTool", "staticVariables": []},
                  {"toolId": "id-keyed", "name": "keyedTool",
                   "staticVariables": [{"apiKey": "${BOOT_TEST_MISSING_KEY}"}]}
                ]
                """);

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);
        when(toolSpecService.getToolMcpServerSetting()).thenReturn(new ToolMcpServerSetting(true, Set.of()));
        when(preferenceService.current()).thenReturn(DefaultToolsPreference.forPreset("everything"));
        when(resolver.resolveActiveNames(any(), any())).thenReturn(Set.of("plainTool", "keyedTool"));

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService,
                specFile.toUri().toString(), new ObjectMapper(), new DefaultResourceLoader(),
                this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator(Map.of("UNRELATED", "x")::get));

        service.onApplicationEvent(mock(WebServerInitializedEvent.class));

        Map<String, Boolean> drafts = service.getDefaultToolSpecs().stream()
                .collect(Collectors.toMap(ToolSpec::name, ToolSpec::draft));
        assertThat(drafts).containsEntry("plainTool", false).containsEntry("keyedTool", true);
    }

    @Test
    void activeDefaultToolWithSatisfiedEnvVarBootsPublished() throws IOException {
        Path specFile = home.resolve("boot-test-specs.json");
        Files.writeString(specFile, """
                [
                  {"toolId": "id-keyed", "name": "keyedTool",
                   "staticVariables": [{"apiKey": "${BOOT_TEST_SET_KEY}"}]}
                ]
                """);

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);
        when(toolSpecService.getToolMcpServerSetting()).thenReturn(new ToolMcpServerSetting(true, Set.of()));
        when(preferenceService.current()).thenReturn(DefaultToolsPreference.forPreset("everything"));
        when(resolver.resolveActiveNames(any(), any())).thenReturn(Set.of("keyedTool"));

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService,
                specFile.toUri().toString(), new ObjectMapper(), new DefaultResourceLoader(),
                this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator(Map.of("BOOT_TEST_SET_KEY", "secret")::get));

        service.onApplicationEvent(mock(WebServerInitializedEvent.class));

        assertThat(service.getDefaultToolSpecs().getFirst().draft()).isFalse();
    }

    @Test
    void exposureIsThePresetSetOnlyKeepingCustomsAndExclusions() throws IOException {
        Path specFile = home.resolve("boot-test-specs.json");
        Files.writeString(specFile, """
                [
                  {"toolId": "id-plain", "name": "plainTool", "staticVariables": []},
                  {"toolId": "id-keyed", "name": "keyedTool",
                   "staticVariables": [{"apiKey": "${BOOT_TEST_MISSING_KEY}"}]},
                  {"toolId": "id-unchecked", "name": "uncheckedTool", "staticVariables": []},
                  {"toolId": "id-localpass-only", "name": "localPassOnlyTool", "staticVariables": []}
                ]
                """);

        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        DefaultToolsPreferenceService preferenceService = mock(DefaultToolsPreferenceService.class);
        DefaultToolsPreferenceResolver resolver = mock(DefaultToolsPreferenceResolver.class);
        // persisted state: a user-authored tool, a stale entry for the now-draft keyed tool,
        // and a drawer un-check for uncheckedTool
        when(toolSpecService.getToolMcpServerSetting()).thenReturn(
                new ToolMcpServerSetting(true, Set.of("custom-1", "id-keyed"), Set.of("id-unchecked")));
        when(preferenceService.current()).thenReturn(DefaultToolsPreference.forPreset("starter-5"));
        // localPassOnlyTool is published (Local Pass) but NOT in the preset, so it must not be exposed.
        when(resolver.resolveActiveNames(any(), any()))
                .thenReturn(Set.of("plainTool", "keyedTool", "uncheckedTool"));

        ToolSpecPersistenceService service = new ToolSpecPersistenceService(home, toolSpecService,
                specFile.toUri().toString(), new ObjectMapper(), new DefaultResourceLoader(),
                this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator(Map.of("UNRELATED", "x")::get));

        service.onApplicationEvent(mock(WebServerInitializedEvent.class));

        // localPassOnlyTool is still published in the usable pool even though it is not exposed.
        Map<String, Boolean> drafts = service.getDefaultToolSpecs().stream()
                .collect(Collectors.toMap(ToolSpec::name, ToolSpec::draft));
        assertThat(drafts).containsEntry("localPassOnlyTool", false);
        verify(toolSpecService).setToolMcpServerSetting(argThat((ToolMcpServerSetting s) ->
                s != null && s.autoAdd()
                        && Set.of("custom-1", "id-plain").equals(s.exposedToolIds())
                        && Set.of("id-unchecked").equals(s.excludedToolIds())));
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
                new ObjectMapper(), resourceLoader, this.persistenceExecutor, preferenceService, resolver,
                new ToolActivationCalculator());

        service.onStart();

        verify(toolSpecService, never()).setToolMcpServerSetting(any());
    }
}
