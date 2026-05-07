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
package org.springaicommunity.playground.webui.mcp;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import java.util.List;
import java.util.regex.Pattern;

@SpringComponent
@UIScope
@AnonymousAllowed
@Route("oauth-complete")
@PageTitle("Authorization complete")
public class OAuthCompleteView extends VerticalLayout implements BeforeEnterObserver {

    private static final Pattern SAFE_REG_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private String redirectUrl = "/mcp-server";

    public OAuthCompleteView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        getStyle().set("background-color", "var(--lumo-shade-5pct)");

        Div card = new Div();
        card.getStyle()
                .set("padding", "var(--lumo-space-l) var(--lumo-space-xl)")
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("text-align", "center")
                .set("max-width", "420px");

        H2 title = new H2("✓ Authorization complete");
        title.getStyle()
                .set("color", "var(--lumo-success-color)")
                .set("margin", "0 0 var(--lumo-space-s)");

        Paragraph body = new Paragraph("You can close this tab and return to the Spring AI Playground.");
        body.getStyle().set("margin", "var(--lumo-space-xs) 0");

        Paragraph note = new Paragraph("Returning to the app shortly…");
        note.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin", "var(--lumo-space-m) 0 0");

        card.add(title, body, note);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters().getParameters().get("regId");
        String regId = values != null && !values.isEmpty() ? values.get(0) : "";
        String safe = SAFE_REG_ID.matcher(regId).matches() ? regId : "";
        this.redirectUrl = "/mcp-server" + (safe.isEmpty() ? "" : "?authorized=" + safe);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().getPage().executeJs(
                "setTimeout(() => { try { window.close(); } catch (e) {} }, 250);"
                        + "setTimeout(() => { try { window.location.replace($0); } catch (e) {} }, 1500);",
                this.redirectUrl);
    }
}
