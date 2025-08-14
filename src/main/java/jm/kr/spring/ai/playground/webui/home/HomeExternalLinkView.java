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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class HomeExternalLinkView extends VerticalLayout {

    public HomeExternalLinkView(String url) {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        VerticalLayout contentLayout = new VerticalLayout();
        contentLayout.setSizeFull();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);
        contentLayout.getStyle()
            .set("padding-left", "2rem")
            .set("padding-right", "2rem");

        IFrame iframe = new IFrame();
        iframe.setSizeFull();
        iframe.getElement().getStyle().set("border", "none");

        contentLayout.add(iframe);
        contentLayout.setFlexGrow(1, iframe);

        UI.getCurrent().getPage().executeJs(
            """
            const iframeEl = $0;
            iframeEl.src = $1;
            let loaded = false;
            iframeEl.onload = () => { loaded = true; };
            setTimeout(() => {
                if (!loaded) {
                    window.open($1, '_blank');
                }
            }, 1500);
            """,
            iframe.getElement(), url
        );

        Scroller scroller = new Scroller(contentLayout);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        add(scroller);
    }
}