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

@Tag("chat-attach")
@JsModule("./playground/chat-attach.js")
@NpmPackage(value = "exifr", version = "7.1.3")
public class ChatAttach extends Component {

    public interface ImageSink {
        void accept(String fileName, byte[] bytes, String mimeType, String exifJson);
    }

    private final ImageSink imageSink;
    private final Runnable onImageStart;
    private final Consumer<String> onError;

    public ChatAttach(String acceptTypes, ImageSink imageSink, Runnable onImageStart, Consumer<String> onError) {
        if (StringUtils.hasText(acceptTypes)) getElement().setAttribute("accept-types", acceptTypes);
        this.imageSink = imageSink;
        this.onImageStart = onImageStart;
        this.onError = onError;
    }

    @ClientCallable
    public void imageAttachStarted() {
        this.onImageStart.run();
    }

    @ClientCallable
    public void receiveImage(String fileName, String base64, String mimeType, String exifJson) {
        byte[] bytes = decode(base64);
        if (bytes != null) this.imageSink.accept(fileName, bytes, mimeType, exifJson);
    }

    @ClientCallable
    public void attachFailed(String message) {
        this.onError.accept(StringUtils.hasText(message) ? message : "Attach failed");
    }

    public void openPicker() {
        getElement().callJsFunction("openPicker");
    }

    public void bindTo(Component target) {
        getElement().callJsFunction("bindTo", target.getElement());
    }

    private byte[] decode(String base64) {
        try {
            return base64 == null ? new byte[0] : Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            this.onError.accept("The attachment data was not valid.");
            return null;
        }
    }
}
