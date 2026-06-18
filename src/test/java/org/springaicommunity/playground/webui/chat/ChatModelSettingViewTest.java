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
package org.springaicommunity.playground.webui.chat;

import tools.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.chat.ChatExtraOptions;
import org.springaicommunity.playground.service.chat.ChatProvider;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetService;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatModelSettingViewTest {

    @TempDir Path home;

    private ChatSystemPromptPresetService presetService;
    private OllamaModelDownloadService downloadService;
    private PersistenceExecutor executor;

    @BeforeEach
    void setUp() throws IOException {
        this.executor = new PersistenceExecutor();
        this.presetService = new ChatSystemPromptPresetService(this.home, this.executor, new ObjectMapper(),
                new ChatSystemPromptPresetCatalog());
        @SuppressWarnings("unchecked")
        ObjectProvider<OllamaApi> noOllama = mock(ObjectProvider.class);
        this.downloadService = new OllamaModelDownloadService(noOllama, mock(ChatModel.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        this.executor.awaitCompletion(Duration.ofSeconds(5));
    }

    @Test
    void typedCustomModelTagCommitsToChatOptions() throws Exception {
        ChatModelSettingView view = newView("qwen3.5:2b-mlx");
        fireCustomValue(view, "smollm2:135m");
        assertThat(view.getChatOptions().getModel()).isEqualTo("smollm2:135m");
    }

    @Test
    void blankCustomValueKeepsPreviousModel() throws Exception {
        ChatModelSettingView view = newView("qwen3.5:2b-mlx");
        fireCustomValue(view, "  ");
        assertThat(view.getChatOptions().getModel()).isEqualTo("qwen3.5:2b-mlx");
    }

    @Test
    void appliedPresetToolsSurviveComboClearForAdHocPresets() {
        ChatModelSettingView view = newView("qwen3.5:2b-mlx");
        ChatSystemPromptPresetCatalog.Preset adHoc = new ChatSystemPromptPresetCatalog.Preset(
                "lib-edited", "Edited in library", "", "You are a tool-using agent.",
                ChatSystemPromptPresetCatalog.PresetKind.EXAMPLE, List.of("getWeather", "getCurrentTime"));
        view.applyPreset(adHoc);
        assertThat(view.getSelectedPresetTools()).containsExactly("getWeather", "getCurrentTime");
    }

    @Test
    void selectedPresetToolsEmptyByDefault() {
        assertThat(newView("qwen3.5:2b-mlx").getSelectedPresetTools()).isEmpty();
    }

    @Test
    void memoryWindowRoundTripsThroughDrawer() {
        ChatModelSettingView view = new ChatModelSettingView(List.of("qwen3.5:2b-mlx", "qwen3.5:4b-mlx"), "",
                ChatOptions.builder().model("qwen3.5:2b-mlx").build(), new ChatExtraOptions(null, null, null, 7),
                ChatProvider.OLLAMA, this.presetService, this.downloadService, List.of(), spec -> "L0",
                spec -> "General", 10);
        assertThat(view.getChatExtraOptions().memoryWindow()).isEqualTo(7);
    }

    @Test
    void emptyMemoryWindowFieldYieldsNullForDefaultFallback() {
        assertThat(newView("qwen3.5:2b-mlx").getChatExtraOptions().memoryWindow()).isNull();
    }

    @Test
    void openAiStopCapRejectsMoreThanFourSequences() {
        assertThat(viewWithStop(ChatProvider.OPENAI, List.of("a", "b", "c", "d", "e")).validate()).isFalse();
    }

    @Test
    void openAiStopWithinCapIsValid() {
        assertThat(viewWithStop(ChatProvider.OPENAI, List.of("a", "b", "c", "d")).validate()).isTrue();
    }

    @Test
    void ollamaHasNoStopSequenceCap() {
        assertThat(viewWithStop(ChatProvider.OLLAMA, List.of("a", "b", "c", "d", "e", "f")).validate()).isTrue();
    }

    private ChatModelSettingView viewWithStop(ChatProvider provider, List<String> stop) {
        return new ChatModelSettingView(List.of("model-a"), "",
                ChatOptions.builder().model("model-a").build(), new ChatExtraOptions(null, stop, null, null),
                provider, this.presetService, this.downloadService, List.of(), spec -> "L0", spec -> "General", 10);
    }

    private ChatModelSettingView newView(String model) {
        return new ChatModelSettingView(List.of("qwen3.5:2b-mlx", "qwen3.5:4b-mlx"), "",
                ChatOptions.builder().model(model).build(), ChatExtraOptions.defaults(), ChatProvider.OLLAMA,
                this.presetService, this.downloadService, List.of(), spec -> "L0", spec -> "General", 10);
    }

    private static void fireCustomValue(ChatModelSettingView view, String typed) throws Exception {
        Field field = ChatModelSettingView.class.getDeclaredField("modelComboBox");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ComboBox<String> combo = (ComboBox<String>) field.get(view);
        ComponentUtil.fireEvent(combo, new ComboBoxBase.CustomValueSetEvent<>(combo, true, typed));
    }
}
