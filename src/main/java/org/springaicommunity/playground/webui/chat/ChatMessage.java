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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.dom.Element;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag("vaadin-message")
@Uses(MessageList.class)
class ChatMessage extends Component implements HasStyle {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Markdown markdown = new Markdown();

    ChatMessage(String userName, LocalDateTime time, int colorIndex) {
        Element element = getElement();
        element.setProperty("userName", userName);
        setTime(time);
        if (colorIndex >= 0)
            element.setProperty("userColorIndex", colorIndex);
        Element content = new Element("div");
        content.getStyle().set("white-space", "normal");
        content.appendChild(this.markdown.getElement());
        element.appendChild(content);
    }

    void setTime(LocalDateTime time) {
        getElement().setProperty("time", time.format(TIME_FORMATTER));
    }

    void setMarkdown(String content) {
        this.markdown.setContent(content);
    }

    void appendMarkdown(String content) {
        this.markdown.appendContent(content);
    }
}
