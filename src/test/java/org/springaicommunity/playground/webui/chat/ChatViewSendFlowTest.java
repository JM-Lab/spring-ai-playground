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

import tools.jackson.databind.node.StringNode;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.webui.SttMicButton;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@SpringBootTest
class ChatViewSendFlowTest extends SpringBrowserlessTest {

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @BeforeEach
    void stubStreamedReply() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("mock reply from browserless"))))));
    }

    @Test
    void submittingPromptRendersMockedAssistantReply() throws InterruptedException {
        ChatView view = navigate(ChatView.class);

        TextArea prompt = promptArea(view);
        test(prompt).setValue("hello from browserless");

        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("hello from browserless");

        assertThat(awaitMarkdownContaining(view, "mock reply from browserless")).isTrue();
    }

    @Test
    void streamingLocksAttachAndMicUntilCompletion() throws InterruptedException {
        Sinks.Many<ChatResponse> replies = Sinks.many().unicast().onBackpressureBuffer();
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(replies.asFlux());
        ChatView view = navigate(ChatView.class);

        TextArea prompt = promptArea(view);
        test(prompt).setValue("streaming lock check");
        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("streaming lock check");

        assertThat(attachButton(view).isEnabled()).isFalse();
        assertThat($(SttMicButton.class, view).first().isEnabled()).isFalse();
        assertThat(prompt.isReadOnly()).isTrue();
        assertThat(submit.isEnabled()).isTrue();
        assertThat(submit.getTooltip().getText()).isEqualTo("Stop");

        replies.tryEmitNext(new ChatResponse(List.of(new Generation(new AssistantMessage("done")))));
        replies.tryEmitComplete();

        assertThat(awaitAttachEnabled(view)).isTrue();
        assertThat(prompt.isReadOnly()).isFalse();
        assertThat($(SttMicButton.class, view).first().isEnabled()).isTrue();
        assertThat(submit.getTooltip().getText()).isEqualTo("Submit");
    }

    @Test
    void attachStaysLockedWhenImageArrivesMidStream() throws InterruptedException {
        Sinks.Many<ChatResponse> replies = Sinks.many().unicast().onBackpressureBuffer();
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(replies.asFlux());
        ChatView view = navigate(ChatView.class);

        test(promptArea(view)).setValue("image lands mid-stream");
        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("image lands mid-stream");
        assertThat(attachButton(view).isEnabled()).isFalse();

        $(ChatAttach.class, view).first().receiveImage("late.png", "aGVsbG8=", "image/png", null);
        roundTrip();
        assertThat(attachButton(view).isEnabled()).isFalse();

        replies.tryEmitNext(new ChatResponse(List.of(new Generation(new AssistantMessage("done")))));
        replies.tryEmitComplete();

        assertThat(awaitAttachEnabled(view)).isTrue();
    }

    @Test
    void attachButtonDisablesAtMaxImagesWhileIdle() {
        ChatView view = navigate(ChatView.class);
        ChatAttach attach = $(ChatAttach.class, view).first();

        for (int i = 0; i < 5; i++) {
            attach.receiveImage("img" + i + ".png", "aGVsbG8=", "image/png", null);
        }
        roundTrip();

        assertThat(attachButton(view).isEnabled()).isFalse();
        assertThat(promptArea(view).isReadOnly()).isFalse();
    }

    @Test
    void streamedThinkPanelStaysInsideBoundedScrollerChain() throws InterruptedException {
        AssistantMessage thinking = AssistantMessage.builder().content("")
                .properties(Map.of("reasoningContent", "| a | b |\n|---|---|\n| wide | table |")).build();
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(thinking))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("after thinking"))))));
        ChatView view = navigate(ChatView.class);

        test(promptArea(view)).setValue("trigger think panel");
        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("trigger think panel");

        Details thinkPanel = awaitThinkPanel(view);
        assertThat(thinkPanel.getWidth()).isEqualTo("100%");
        assertThat(thinkPanel.getContent().anyMatch(ChatMessage.class::isInstance)).isTrue();

        List<String> ancestorWidths = new ArrayList<>();
        Component node = thinkPanel;
        Scroller scroller = null;
        while (scroller == null) {
            Component parent = node.getParent().orElseThrow();
            if (parent instanceof Scroller found) {
                scroller = found;
                break;
            }
            assertThat(parent).isInstanceOf(VerticalLayout.class);
            ancestorWidths.add(((VerticalLayout) parent).getWidth());
            node = parent;
        }
        assertThat(ancestorWidths).isNotEmpty().allMatch("100%"::equals);
        assertThat(scroller.getClassNames()).contains("chat-message-scroller");
        assertThat(scroller.getScrollDirection()).isEqualTo(Scroller.ScrollDirection.VERTICAL);
    }

    private Details awaitThinkPanel(ChatView view) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            roundTrip();
            Details found = $(Details.class, view).all().stream()
                    .filter(details -> details.getSummary() != null
                            && details.getSummary().getElement().getText().startsWith("THINK"))
                    .findFirst().orElse(null);
            if (found != null) {
                return found;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("THINK panel did not appear within 10s");
    }

    @Test
    void stoppingStreamReenablesAttach() throws InterruptedException {
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());
        ChatView view = navigate(ChatView.class);

        TextArea prompt = promptArea(view);
        test(prompt).setValue("stop reenables attach");
        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("stop reenables attach");
        assertThat(attachButton(view).isEnabled()).isFalse();

        test(submit).click();

        assertThat(awaitAttachEnabled(view)).isTrue();
        assertThat(prompt.isReadOnly()).isFalse();
    }

    @Test
    void reenteringStreamingConversationLocksComposerAndRendersAnswerOnFinish() throws InterruptedException {
        Sinks.Many<ChatResponse> replies = Sinks.many().unicast().onBackpressureBuffer();
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(replies.asFlux());
        long now = System.currentTimeMillis();
        this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("park-conv-finish", "Parking", now, now,
                "sys", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(new UserMessage("parked question"), new AssistantMessage("parked answer"))));
        Set<String> knownIds = knownConversationIds();
        ChatView view = navigate(ChatView.class);

        test(promptArea(view)).setValue("background stream question");
        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("background stream question");
        assertThat(attachButton(view).isEnabled()).isFalse();
        String streamingConvId = newConversationId(knownIds);

        view = switchToConversation("park-conv-finish");
        assertThat(promptArea(view).isReadOnly()).isFalse();
        assertThat(attachButton(view).isEnabled()).isTrue();

        view = switchToConversation(streamingConvId);
        assertThat(promptArea(view).isReadOnly()).isTrue();
        assertThat(attachButton(view).isEnabled()).isFalse();
        assertThat($(SttMicButton.class, view).first().isEnabled()).isFalse();

        replies.tryEmitNext(new ChatResponse(List.of(new Generation(
                new AssistantMessage("finished while parked elsewhere")))));
        replies.tryEmitComplete();

        assertThat(awaitAttachEnabled(view)).isTrue();
        assertThat(promptArea(view).isReadOnly()).isFalse();
        assertThat(awaitMarkdownContaining(view, "finished while parked elsewhere")).isTrue();
    }

    @Test
    void stopFromReenteredConversationUnlocksComposer() throws InterruptedException {
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());
        long now = System.currentTimeMillis();
        this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("park-conv-stop", "Parking2", now, now,
                "sys", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(new UserMessage("parked q"), new AssistantMessage("parked a"))));
        Set<String> knownIds = knownConversationIds();
        ChatView view = navigate(ChatView.class);

        test(promptArea(view)).setValue("stop me from elsewhere");
        Button submit = $(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first();
        test(submit).click();
        completePendingPromptValueJs("stop me from elsewhere");
        String streamingConvId = newConversationId(knownIds);

        view = switchToConversation("park-conv-stop");
        view = switchToConversation(streamingConvId);
        Button stop = $(Button.class, view)
                .withCondition(button -> "Stop".equals(button.getTooltip().getText()))
                .first();
        test(stop).click();

        assertThat(awaitAttachEnabled(view)).isTrue();
        assertThat(promptArea(view).isReadOnly()).isFalse();
    }

    private Set<String> knownConversationIds() {
        return this.chatHistoryService.getChatHistoryList().stream()
                .map(ChatHistory::conversationId)
                .collect(Collectors.toSet());
    }

    private String newConversationId(Set<String> knownIds) {
        return this.chatHistoryService.getChatHistoryList().stream()
                .map(ChatHistory::conversationId)
                .filter(id -> !knownIds.contains(id))
                .findFirst().orElseThrow();
    }

    private ChatView switchToConversation(String conversationId) {
        UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", conversationId));
        roundTrip();
        return (ChatView) getCurrentView();
    }

    private TextArea promptArea(ChatView view) {
        return $(TextArea.class, view)
                .withCondition(area -> "Ask Spring AI Playground".equals(area.getPlaceholder()))
                .first();
    }

    private Button attachButton(ChatView view) {
        return $(Button.class, view)
                .withCondition(button -> "Attach image".equals(button.getTooltip().getText()))
                .first();
    }

    private boolean awaitAttachEnabled(ChatView view) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            roundTrip();
            if (attachButton(view).isEnabled()) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    // The submit listener reads the prompt through executeJs("return this.value;") to capture
    // IME-composing text; no browser runs here, so resolve that pending invocation by hand.
    private void completePendingPromptValueJs(String typedValue) {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .filter(invocation -> invocation.getInvocation().getExpression().contains("return this.value"))
                .forEach(invocation -> invocation.complete(StringNode.valueOf(typedValue)));
    }

    private boolean awaitMarkdownContaining(ChatView view, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            roundTrip();
            boolean rendered = $(Markdown.class, view).all().stream()
                    .anyMatch(markdown -> markdown.getContent() != null && markdown.getContent().contains(expected));
            if (rendered) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

}
