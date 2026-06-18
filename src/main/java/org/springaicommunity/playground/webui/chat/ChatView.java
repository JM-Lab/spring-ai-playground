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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.chat.ChatExportService;
import org.springaicommunity.playground.service.chat.ChatExtraOptions;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetService;
import org.springaicommunity.playground.service.chat.ChatSystemPromptTemplateRenderer;
import org.springaicommunity.playground.service.chat.ChatToolPreferences;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService;
import org.springaicommunity.playground.service.chat.ReasoningEffort;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;
import org.springaicommunity.playground.webui.tool.ExposedToolsSelector;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springaicommunity.playground.webui.VaadinUtils.styledButton;

@SpringComponent
@UIScope
@AnonymousAllowed
@CssImport("./playground/chat-styles.css")
@PageTitle("Agentic Chat")
@Route(value = "agentic-chat", layout = SpringAiPlaygroundAppLayout.class)
public class ChatView extends ContentWorkspaceView implements BeforeEnterObserver {

    public static final String CHAT_HISTORY_CHANGE_EVENT = "CHAT_HISTORY_CHANGE_EVENT";
    public static final String CHAT_HISTORY_SELECT_EVENT = "CHAT_HISTORY_SELECT_EVENT";
    public static final String CHAT_HISTORY_EMPTY_EVENT = "CHAT_HISTORY_EMPTY_EVENT";

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final ChatService chatService;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private final ChatHistoryService chatHistoryService;
    private final McpClientService mcpClientService;
    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final ToolActivationCalculator toolActivationCalculator;
    private final McpServerInfoService mcpServerInfoService;
    private final ChatExportService chatExportService;
    private final ChatSystemPromptPresetService chatSystemPromptPresetService;
    private final ChatSystemPromptTemplateRenderer chatSystemPromptTemplateRenderer;
    private final OllamaModelDownloadService ollamaModelDownloadService;
    private final ChatHistoryView chatHistoryView;
    private final WorkspaceSettingsDrawer settingsDrawer;
    private ChatModelSettingView chatModelSettingView;
    private ChatContentView chatContentView;

    public ChatView(PersistentUiDataStorage persistentUiDataStorage, ChatService chatService,
            ChatHistoryService chatHistoryService, McpClientService mcpClientService,
            ToolSpecService toolSpecService, ToolSpecPersistenceService toolSpecPersistenceService,
            ToolActivationCalculator toolActivationCalculator,
            McpServerInfoService mcpServerInfoService, ChatExportService chatExportService,
            ChatSystemPromptPresetService chatSystemPromptPresetService,
            ChatSystemPromptTemplateRenderer chatSystemPromptTemplateRenderer,
            OllamaModelDownloadService ollamaModelDownloadService) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
        this.mcpClientService = mcpClientService;
        this.toolSpecService = toolSpecService;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.toolActivationCalculator = toolActivationCalculator;
        this.mcpServerInfoService = mcpServerInfoService;
        this.chatExportService = chatExportService;
        this.chatSystemPromptPresetService = chatSystemPromptPresetService;
        this.chatSystemPromptTemplateRenderer = chatSystemPromptTemplateRenderer;
        this.ollamaModelDownloadService = ollamaModelDownloadService;

        PropertyChangeSupport chatHistoryChangeSupport = new PropertyChangeSupport(this);
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_SELECT_EVENT,
                event -> this.changeChatContent((ChatHistory) event.getNewValue()));
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_EMPTY_EVENT, event -> {
            if ((boolean) event.getNewValue())
                addNewChatContent();
        });
        this.completeChatHistoryConsumer =
                chatHistory -> chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_CHANGE_EVENT, null,
                        chatHistoryService.updateChatHistory(chatHistory));

        this.chatHistoryView =
                new ChatHistoryView(persistentUiDataStorage, chatHistoryService, chatHistoryChangeSupport);
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_CHANGE_EVENT,
                event -> this.chatHistoryView.changeChatHistoryContent((ChatHistory) event.getNewValue()));

        configureSidebar(this.chatHistoryView, "History");

        Button newChatButton =
                styledButton("New Chat", VaadinIcon.CHAT.create(), event -> addNewChatContent());
        addHeaderAction(newChatButton);

        // Export and Prompts go first so the settings cog stays the right-most header action (app-wide convention).
        installConversationExportMenu();
        installPromptLibraryButton();

        this.settingsDrawer = installSettingsDrawer(VaadinIcon.COG_O, "Agentic Chat Setting",
                "Agentic Chat Setting");
        this.settingsDrawer.setBodyFactory(this::buildChatModelSettingView);
        this.settingsDrawer.setApplyButton("Apply & New Chat", this::applySettingsAndNewChat, false);

        addNewChatContent();
    }

    private void installConversationExportMenu() {
        var exportIcon = VaadinUtils.styledIcon(VaadinIcon.DOWNLOAD.create());
        exportIcon.setTooltipText("Export conversation");
        var exportMenu = getHeaderEndMenuBar().addItem(exportIcon).getSubMenu();
        exportMenu.addItem("Markdown (.md)", event -> exportCurrent("md"));
        exportMenu.addItem("Plain text (.txt)", event -> exportCurrent("txt"));
        exportMenu.addItem("JSON (.json)", event -> exportCurrent("json"));
        exportMenu.addItem("PDF (print)", event -> exportCurrent("pdf"));
    }

    private void exportCurrent(String format) {
        if (Objects.nonNull(this.chatContentView)) this.chatContentView.exportConversation(format);
    }

    private void installPromptLibraryButton() {
        var promptsIcon = VaadinUtils.styledIcon(VaadinIcon.CLIPBOARD_TEXT.create());
        promptsIcon.setTooltipText("Prompt Library");
        getHeaderEndMenuBar().addItem(promptsIcon, event -> new PromptLibraryDialog(
                this.chatSystemPromptPresetService, this.chatSystemPromptTemplateRenderer,
                this::applyPromptFromLibrary, this::toolReadiness, builtinToolSpecs(),
                this.toolSpecService::riskLevelOf, this.toolSpecService::categoryOf).open());
    }

    private List<ToolSpec> builtinToolSpecs() {
        return this.toolSpecPersistenceService.getDefaultToolSpecs().stream()
                .filter(spec -> spec.name() != null).toList();
    }

    // Library detail pages flag each required tool: ready, active-but-not-exposed, missing-key, or not enabled.
    private PromptLibraryDialog.ToolReadiness toolReadiness(String toolName) {
        return this.toolSpecService.getToolSpecAsOpt(toolName)
                .map(this::toolReadiness)
                .orElse(PromptLibraryDialog.ToolReadiness.NOT_ENABLED);
    }

    // READY must mean "Apply & New Chat will pick it up", so it requires the MCP exposure check-mark too.
    private PromptLibraryDialog.ToolReadiness toolReadiness(ToolSpec spec) {
        if (this.toolActivationCalculator.calculate(spec) != ToolActivationCalculator.State.ACTIVE)
            return PromptLibraryDialog.ToolReadiness.NEEDS_SETUP;
        return this.toolSpecService.getToolMcpServerSetting().exposedToolIds().contains(spec.toolId())
                ? PromptLibraryDialog.ToolReadiness.READY
                : PromptLibraryDialog.ToolReadiness.NOT_EXPOSED;
    }

    // Save & apply from the library lands here: open the settings drawer and select the (just-saved) preset, so the
    // user reviews it and commits with the drawer's single Apply & New Chat action.
    private void applyPromptFromLibrary(Preset preset) {
        this.settingsDrawer.open();
        if (Objects.nonNull(this.chatModelSettingView)) this.chatModelSettingView.applyPreset(preset);
    }

    private ChatModelSettingView buildChatModelSettingView() {
        this.chatModelSettingView = new ChatModelSettingView(this.chatService.getModels(),
                this.chatContentView.getSystemPrompt(), this.chatContentView.getChatOption(),
                this.chatContentView.getExtraOptions(), this.chatService.getChatProvider(),
                this.chatSystemPromptPresetService, this.ollamaModelDownloadService, builtinToolSpecs(),
                this.toolSpecService::riskLevelOf, this.toolSpecService::categoryOf,
                this.chatService.getDefaultMemoryWindow());
        return this.chatModelSettingView;
    }

    private void applySettingsAndNewChat() {
        if (Objects.isNull(this.chatModelSettingView)) {
            addNewChatContent();
            this.settingsDrawer.close();
            return;
        }
        if (!this.chatModelSettingView.validate()) {
            Notification.show("Fix the highlighted settings before applying.", 3000, Notification.Position.MIDDLE);
            return;
        }
        String model = this.chatModelSettingView.getChatOptions().getModel();
        if (!this.ollamaModelDownloadService.isDownloaded(model)) {
            // Re-enter the gate after the pull: the drawer stays open meanwhile, so settings (even the
            // model) may have changed and must be re-validated before the new chat starts.
            new ModelDownloadDialog(model, this.ollamaModelDownloadService, () -> {
                if (Objects.nonNull(this.chatModelSettingView)) this.chatModelSettingView.refreshModelItems();
                applySettingsAndNewChat();
            }).open();
            return;
        }
        List<String> presetTools = this.chatModelSettingView.getSelectedPresetTools();
        if (presetTools.isEmpty()) {
            commitSettingsAndNewChat(ChatToolPreferences.defaults());
            return;
        }
        openPresetExposureDialog(resolvePresetTools(presetTools));
    }

    private void commitSettingsAndNewChat(ChatToolPreferences toolPreferences) {
        this.chatHistoryView.clearSelectHistory();
        changeChatContent(this.chatHistoryService.createChatHistory(
                this.chatModelSettingView.getSystemPromptTextArea(), this.chatModelSettingView.getChatOptions(),
                this.chatModelSettingView.getChatExtraOptions(), toolPreferences));
        this.settingsDrawer.close();
    }

    private void applyPresetExposureAndNewChat(List<ToolSpec> matched) {
        syncBuiltinExposureToPreset(matched);
        commitSettingsAndNewChat(presetPreferences(matched));
    }

    private void syncBuiltinExposureToPreset(List<ToolSpec> matched) {
        this.toolSpecPersistenceService.exposeBuiltinToolsAsPreference(
                matched.stream().map(ToolSpec::name).toList());
    }

    private ChatToolPreferences presetPreferences(List<ToolSpec> matched) {
        Set<String> toolIds = matched.stream().map(ToolSpec::toolId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ChatToolPreferences(true, toolIds, List.of(), Map.of(), ReasoningEffort.OFF);
    }

    private record PresetToolMatch(List<ToolSpec> matched, Map<String, String> unmatched) {}

    private PresetToolMatch resolvePresetTools(List<String> presetTools) {
        Set<String> defaultIds = this.toolSpecPersistenceService.getDefaultToolIds();
        Map<String, ToolSpec> exposableBuiltinsByName = ExposedToolsSelector
                .exposableBuiltinsFrom(this.toolSpecService.getToolSpecList(), defaultIds,
                        this.toolActivationCalculator)
                .stream()
                .collect(Collectors.toMap(ToolSpec::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ToolSpec> matched = new ArrayList<>();
        Map<String, String> unmatched = new LinkedHashMap<>();
        for (String name : presetTools) {
            ToolSpec spec = exposableBuiltinsByName.get(name);
            if (spec != null) matched.add(spec);
            else unmatched.put(name, unavailableReason(name));
        }
        return new PresetToolMatch(matched, unmatched);
    }

    private String unavailableReason(String toolName) {
        return switch (toolReadiness(toolName)) {
            case READY -> "not a built-in default tool";
            case NOT_EXPOSED -> "un-checked in MCP exposure";
            case NEEDS_SETUP -> "needs setup (publish or API key)";
            case NOT_ENABLED -> "not enabled";
        };
    }

    private void openPresetExposureDialog(PresetToolMatch match) {
        Dialog dialog = VaadinUtils.headerDialog("Apply preset tools");
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.getStyle().set("gap", "var(--lumo-space-xs)");
        body.setMaxWidth("30rem");
        if (match.matched().isEmpty()) {
            body.add(new Span("This preset has no tools that can be exposed without setup, so the built-in "
                    + "MCP server is left unchanged and the chat starts with the current selection."));
        } else {
            body.add(new Span("Resets the built-in MCP server to expose exactly these "
                    + match.matched().size() + " tool(s):"));
            match.matched().forEach(spec -> {
                Span line = new Span(spec.name());
                line.getStyle().set("font-size", "var(--lumo-font-size-s)");
                body.add(line);
            });
        }
        if (!match.unmatched().isEmpty()) {
            Span skippedHeading = new Span("These need an API key and are skipped:");
            skippedHeading.getStyle().set("margin-top", "var(--lumo-space-s)")
                    .set("font-size", "var(--lumo-font-size-s)");
            body.add(skippedHeading);
            match.unmatched().forEach((name, reason) -> {
                Span line = new Span(name + " - " + reason);
                line.getStyle().set("color", "var(--lumo-error-text-color)")
                        .set("font-size", "var(--lumo-font-size-s)");
                body.add(line);
            });
        }
        dialog.add(body);
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button apply = new Button("Apply", event -> {
            dialog.close();
            if (match.matched().isEmpty()) commitSettingsAndNewChat(ChatToolPreferences.defaults());
            else applyPresetExposureAndNewChat(match.matched());
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, apply);
        dialog.open();
    }

    private void addNewChatContent() {
        addNewChatContent(this.chatService.getSystemPrompt(), this.chatService.getDefaultOptions());
    }

    private void addNewChatContent(String systemPrompt, ChatOptions chatOptions) {
        this.chatHistoryView.clearSelectHistory();
        changeChatContent(this.chatHistoryService.createChatHistory(systemPrompt, chatOptions,
                ChatExtraOptions.defaults()));
    }

    private void changeChatContent(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory))
            return;

        if (Objects.nonNull(this.chatContentView)
                && chatHistory.conversationId().equals(this.chatContentView.getConversationId()))
            return;

        this.chatContentView = new ChatContentView(this.chatService, this.chatHistoryService, chatHistory,
                this.completeChatHistoryConsumer, this.mcpClientService,
                this.toolSpecService, this.toolSpecPersistenceService, this.toolActivationCalculator,
                this.mcpServerInfoService, this.chatExportService);
        ChatOptions chatOptions = chatHistory.chatOptions();
        String label = String.format("%s: %s", this.chatService.getChatModelProvider(), chatOptions.getModel());
        VaadinUtils.getUi(this).access(() -> {
            setHeaderLabel(label);
            setContent(this.chatContentView);
        });
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> convParam = event.getLocation().getQueryParameters().getParameters().get("conv");
        if (convParam == null || convParam.isEmpty()) return;
        String convId = convParam.getFirst();
        if (convId == null || convId.isBlank()) return;
        ChatHistory existing = this.chatHistoryService.getChatHistory(convId);
        if (existing != null) {
            changeChatContent(existing);
        } else {
            // In trace data but not in active chat memory (cleared/restart) — surface it instead of silently starting fresh.
            Notification n = Notification.show(
                    "Conversation " + shortenId(convId) + " is not in active chat history — starting a fresh chat.",
                    6000, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        }
    }

    private static String shortenId(String s) {
        if (s == null) return "";
        return s.length() <= 14 ? s : s.substring(0, 14);
    }
}
