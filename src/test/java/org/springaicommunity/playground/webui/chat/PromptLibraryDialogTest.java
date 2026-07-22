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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetService;
import org.springaicommunity.playground.service.chat.ChatSystemPromptTemplateRenderer;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PromptLibraryDialogTest extends SpringBrowserlessTest {

    @Autowired
    private ChatSystemPromptPresetService presetService;

    @Autowired
    private ChatSystemPromptTemplateRenderer templateRenderer;

    @Autowired
    private ToolSpecService toolSpecService;

    @Autowired
    private ToolSpecPersistenceService toolSpecPersistenceService;

    @Test
    void browsingEveryPresetRendersDetailAndApplyDeliversSelection() {
        AtomicReference<Preset> applied = new AtomicReference<>();
        PromptLibraryDialog dialog = new PromptLibraryDialog(this.presetService, this.templateRenderer,
                applied::set, name -> PromptLibraryDialog.ToolReadiness.READY,
                this.toolSpecPersistenceService.getDefaultToolSpecs().stream()
                        .filter(spec -> spec.name() != null).toList(),
                this.toolSpecService::riskLevelOf, this.toolSpecService::categoryOf);
        dialog.open();
        roundTrip();

        for (Preset preset : this.presetService.presets()) {
            HorizontalLayout row = presetRow(dialog, preset.displayName());
            ComponentUtil.fireEvent(row, new ClickEvent<>(row));
            roundTrip();
        }

        Button apply = $(Button.class, dialog)
                .withCondition(button -> "Apply to chat".equals(button.getText()))
                .first();
        test(apply).click();
        roundTrip();

        assertThat(applied.get()).isNotNull();
        assertThat(dialog.isOpened()).isFalse();
    }

    private HorizontalLayout presetRow(PromptLibraryDialog dialog, String displayName) {
        return $(Span.class, dialog)
                .withCondition(span -> displayName.equals(span.getText()))
                .all().stream()
                .map(Component::getParent)
                .flatMap(Optional::stream)
                .filter(HorizontalLayout.class::isInstance)
                .map(HorizontalLayout.class::cast)
                .filter(layout -> "pointer".equals(layout.getStyle().get("cursor")))
                .findFirst().orElseThrow();
    }

}
