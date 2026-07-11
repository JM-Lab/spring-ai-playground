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

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

@Tag("chat-file-upload")
@JsModule("./playground/chat-file-upload.js")
@NpmPackage(value = "xlsx", version = "0.18.5")
public class ChatFileUpload extends Component {

    public interface Sink {
        void accept(String fileName, String mediaType, byte[] bytes);
    }

    private final Sink sink;
    private final Consumer<String> onError;

    public ChatFileUpload(String accept, Sink sink, Consumer<String> onError) {
        this.sink = sink;
        this.onError = onError;
        if (StringUtils.hasText(accept)) getElement().setAttribute("accept-types", accept);
    }

    @ClientCallable
    public void receiveText(String fileName, String content, String mediaType) {
        this.sink.accept(fileName, mediaType,
                content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
    }

    @ClientCallable
    public void receiveBinary(String fileName, String base64, String mediaType) {
        byte[] bytes;
        try {
            bytes = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            this.onError.accept("The uploaded file data was not valid.");
            return;
        }
        this.sink.accept(fileName, mediaType, bytes);
    }

    @ClientCallable
    public void uploadFailed(String message) {
        this.onError.accept(StringUtils.hasText(message) ? message : "Upload failed");
    }
}
