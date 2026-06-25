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
package org.springaicommunity.playground.webui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import tools.jackson.databind.JsonNode;

public class SttMicButton extends Button {

    private static final String BUTTON_ID = "micButton";

    private enum Mode { WEB_SPEECH, LOCAL_WHISPER }

    private final String targetComponentId;
    private final Mode mode;

    public <C extends Component & HasValueAndElement<?, String>> SttMicButton(C targetComponent) {
        super(VaadinUtils.styledLargeIcon(VaadinIcon.MICROPHONE.create()));
        this.targetComponentId = targetComponent.getId().orElse("");
        this.mode = resolveMode();
        addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        setId(BUTTON_ID);
        getElement().setAttribute("data-stt-mode", mode == Mode.WEB_SPEECH ? "webspeech" : "local");
        setTooltipText("Voice input — click to start recording");
        addClickListener(e -> getElement().executeJs("window.STTModule.toggle($0, $1)",
                targetComponentId, getId().orElse(BUTTON_ID)));
        registerJsErrorBridge();
    }

    private static Mode resolveMode() {
        VaadinRequest request = VaadinService.getCurrentRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        if (userAgent == null) return Mode.LOCAL_WHISPER;
        if (userAgent.contains("Electron")) return Mode.LOCAL_WHISPER;
        if (userAgent.contains("Chrome/")) return Mode.WEB_SPEECH;
        return Mode.LOCAL_WHISPER;
    }

    private void registerJsErrorBridge() {
        getElement().addEventListener("stt-error", event -> {
            JsonNode data = event.getEventData().get("event.detail.message");
            String message = (data != null && !data.isNull()) ? data.asText() : null;
            if (message == null || message.isBlank()) message = "Voice input failed.";
            VaadinUtils.showErrorNotification(message);
        }).addEventData("event.detail.message");
    }
}
