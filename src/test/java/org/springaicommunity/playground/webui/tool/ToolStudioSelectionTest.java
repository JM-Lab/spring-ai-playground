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
package org.springaicommunity.playground.webui.tool;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.listbox.ListBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ToolStudioSelectionTest extends SpringBrowserlessTest {

    @Autowired
    private ToolSpecPersistenceService toolSpecPersistenceService;

    // Default tool specs register on WebServerInitializedEvent, which a MOCK web environment never fires.
    @BeforeEach
    void loadDefaultToolSpecs() throws IOException {
        this.toolSpecPersistenceService.onStart();
        this.toolSpecPersistenceService.onApplicationEvent(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectingToolFromListOpensBuilder() {
        navigate(ToolStudioView.class);
        ToolListView toolListView = $(ToolListView.class).first();
        toolListView.beforeEnter(null);
        roundTrip();

        ListBox<ToolSpec> listBox = $(ListBox.class, toolListView)
                .withCondition(box -> ((ListBox<ToolSpec>) box).getListDataView().getItems().findAny().isPresent())
                .first();
        ToolSpec firstTool = listBox.getListDataView().getItems().findFirst().orElseThrow();
        listBox.setValue(firstTool);
        roundTrip();

        assertThat($(ToolBuilderView.class).all()).isNotEmpty();
    }

}
