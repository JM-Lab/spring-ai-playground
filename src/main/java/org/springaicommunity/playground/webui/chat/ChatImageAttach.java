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

import java.util.Base64;
import java.util.function.Consumer;

@Tag("chat-image-attach")
@JsModule("./playground/chat-image-attach.js")
@NpmPackage(value = "exifr", version = "7.1.3")
public class ChatImageAttach extends Component {

    public interface Sink {
        void accept(String fileName, byte[] bytes, String mimeType, String exifJson);
    }

    private final Sink sink;
    private final Consumer<String> onError;

    public ChatImageAttach(Sink sink, Consumer<String> onError) {
        this.sink = sink;
        this.onError = onError;
    }

    @ClientCallable
    public void receiveImage(String fileName, String base64, String mimeType, String exifJson) {
        byte[] bytes;
        try {
            bytes = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            this.onError.accept("The image data was not valid.");
            return;
        }
        this.sink.accept(fileName, bytes, mimeType, exifJson);
    }

    @ClientCallable
    public void attachFailed(String message) {
        this.onError.accept(StringUtils.hasText(message) ? message : "Image attach failed");
    }

    public void openPicker() {
        getElement().callJsFunction("openPicker");
    }

    public void bindTo(Component target) {
        getElement().callJsFunction("bindTo", target.getElement());
    }
}
