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
package jm.kr.spring.ai.playground.webui.home;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jm.kr.spring.ai.playground.webui.home.HomeItemView.HomeItem;

public class HomeContentView extends VerticalLayout {

    public HomeContentView() {
        setSizeFull();
        setSpacing(false);
        setPadding(false);
        getStyle().set("overflow", "auto");
    }

    public void setContent(HomeItem item) {
        if (item == null)
            return;
        switch (item.actionType()) {
            case MARKDOWN -> setContent(new HomeMarkdownView(item.actionData().toString(), item.displayName()));
            case UI_COMPONENT -> setContent((Component) item.actionData());
            case EXTERNAL_URL -> openExternalLink(item.actionData().toString());
            case EXTERNAL_PAGE -> setContent(new HomeExternalLinkView(item.actionData().toString()));
        }
    }

    private void setContent(Component component) {
        removeAll();
        add(component);
    }

    public void openExternalLink(String url) {
        UI.getCurrent().getPage().open(url, "_blank");
    }

}