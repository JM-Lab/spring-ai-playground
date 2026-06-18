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

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.dom.Element;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag("vaadin-message")
@Uses(MessageList.class)
@JsModule("./playground/chat-markdown-enhance.js")
@NpmPackage(value = "highlight.js", version = "11.11.1")
@NpmPackage(value = "katex", version = "0.17.0")
@NpmPackage(value = "mermaid", version = "11.15.0")
class ChatMessage extends Component implements HasStyle {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Markdown markdown = new Markdown();
    private final Element content = new Element("div");
    private final Element rawView = new Element("pre");
    private final Element plainView = new Element("div");
    private String rawMarkdown = "";
    private boolean showingRaw;
    private boolean collapsed;
    private boolean autoEnhance = true;
    private boolean plainText;
    private LocalDateTime time;
    private String timeDetail = "";

    ChatMessage(String userName, LocalDateTime time, int colorIndex) {
        Element element = getElement();
        element.setProperty("userName", userName);
        setTime(time);
        if (colorIndex >= 0)
            element.setProperty("userColorIndex", colorIndex);
        this.content.getStyle().set("white-space", "normal");
        this.rawView.getStyle().set("display", "none").set("white-space", "pre-wrap")
                .set("word-break", "break-word").set("margin", "0")
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "var(--lumo-font-size-s)");
        this.plainView.getStyle().set("display", "none").set("white-space", "pre-wrap")
                .set("word-break", "break-word");
        this.content.appendChild(this.markdown.getElement());
        this.content.appendChild(this.rawView);
        this.content.appendChild(this.plainView);
        element.appendChild(this.content);
    }

    // User input must appear exactly as typed (newlines kept, no markdown parsing, no enhancers).
    void usePlainText() {
        this.plainText = true;
        this.autoEnhance = false;
        this.markdown.setContent("");
        this.markdown.getElement().getStyle().set("display", "none");
        this.plainView.setText(this.rawMarkdown);
        this.plainView.getStyle().set("display", "block");
    }

    // Hook the rendered light DOM (code-block copy buttons, links to new tabs, highlight/math/diagrams).
    // Static messages enhance on attach; streaming replies disable this and call enhanceNow() once the
    // stream completes, because enhancing mid-stream flickers (every markdown sync strips the injected
    // nodes and the settle pass re-creates them); process panels (think/tools/rag) skip enhancing entirely.
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (this.autoEnhance) enhanceNow();
    }

    void disableAutoEnhance() {
        this.autoEnhance = false;
    }

    void enhanceNow() {
        this.markdown.getElement().executeJs(
                "window.Saip && window.Saip.chatMarkdown && window.Saip.chatMarkdown.enhance(this)");
    }

    void setTime(LocalDateTime time) {
        this.time = time;
        syncTimeProperty();
    }

    // The header name/time live in the vaadin-message shadow DOM, so per-response metrics (elapsed, tokens,
    // model) ride the time property to land right next to the timestamp - mirroring the process panel summaries.
    void setTimeDetail(String timeDetail) {
        this.timeDetail = timeDetail == null ? "" : timeDetail;
        syncTimeProperty();
    }

    private void syncTimeProperty() {
        String text = this.time.format(TIME_FORMATTER);
        getElement().setProperty("time", this.timeDetail.isEmpty() ? text : text + " · " + this.timeDetail);
    }

    void setMarkdown(String content) {
        this.rawMarkdown = content == null ? "" : content;
        if (this.plainText) {
            this.plainView.setText(this.rawMarkdown);
            return;
        }
        this.markdown.setContent(content);
    }

    void appendMarkdown(String content) {
        if (content != null) this.rawMarkdown += content;
        this.markdown.appendContent(content);
    }

    String getRawMarkdown() {
        return this.rawMarkdown;
    }

    void setShowRaw(boolean showRaw) {
        this.showingRaw = showRaw;
        if (showRaw) this.rawView.setText(this.rawMarkdown);
        this.markdown.getElement().getStyle().set("display", showRaw ? "none" : "");
        this.rawView.getStyle().set("display", showRaw ? "block" : "none");
    }

    boolean isShowingRaw() {
        return this.showingRaw;
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        this.content.getStyle().set("display", collapsed ? "none" : "");
    }

    boolean isCollapsed() {
        return this.collapsed;
    }

    void addActionBar(Component bar) {
        getElement().appendChild(bar.getElement());
    }
}
