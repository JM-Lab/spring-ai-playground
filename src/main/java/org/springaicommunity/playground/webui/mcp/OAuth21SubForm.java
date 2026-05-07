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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OAuth21SubForm extends VerticalLayout {

    private static final String CLIENT_AUTH_PKCE = "none";
    private static final String CLIENT_AUTH_SECRET_BASIC = "client_secret_basic";

    private final TextField issuerUriField = new TextField("Issuer URI");
    private final TextField authorizationUriField = new TextField("Authorization endpoint");
    private final TextField tokenUriField = new TextField("Token endpoint");
    private final TextField clientIdField = new TextField("Client ID");
    private final PasswordField clientSecretField = new PasswordField("Client secret");
    private final TextField scopesField = new TextField("Scopes");
    private final RadioButtonGroup<String> authMethodRadio = new RadioButtonGroup<>();
    private final Span redirectInfo = new Span();
    private final Button authorizeButton = new Button("Authorize", VaadinIcon.UNLOCK.create());
    private final Details advanced = new Details();
    private final Runnable onChange;
    private final Supplier<String> registrationIdSupplier;

    public OAuth21SubForm(Runnable onChange, Supplier<String> registrationIdSupplier) {
        this.onChange = onChange;
        this.registrationIdSupplier = registrationIdSupplier;
        buildLayout();
        wireChangeListeners();
    }

    private void buildLayout() {
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s)")
                .set("margin-bottom", "var(--lumo-space-s)");

        H4 header = new H4("OAuth 2.1 Authorization Code");
        header.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-xs)");

        Span helpText = new Span(
                "Use ${ENV_VAR} in any value. Click Authorize after Save & Connect to grant access in your browser.");
        helpText.getStyle().set("font-size", "0.85em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-s)");

        clientIdField.setLabel("Client ID *");
        clientIdField.setPlaceholder("your-client-id or ${ENV_VAR}");
        clientIdField.setWidthFull();
        issuerUriField.setPlaceholder("https://accounts.example.com");
        issuerUriField.setHelperText(
                "OIDC discovery (.well-known) — auto-resolves the authorization/token endpoints.");
        issuerUriField.setWidthFull();
        scopesField.setPlaceholder("read, write");
        scopesField.setHelperText("Comma-separated. Leave blank to inherit the issuer's defaults.");
        scopesField.setWidthFull();

        authorizationUriField.setPlaceholder("https://example.com/oauth/authorize");
        authorizationUriField.setHelperText(
                "Required only if the issuer above doesn't expose .well-known discovery.");
        authorizationUriField.setWidthFull();
        tokenUriField.setPlaceholder("https://example.com/oauth/token");
        tokenUriField.setHelperText("Same — paired with the authorization endpoint above.");
        tokenUriField.setWidthFull();
        clientSecretField.setPlaceholder("blank for PKCE, or ${ENV_VAR}");
        clientSecretField.setHelperText(
                "Optional — leave blank for PKCE. Plain values land on disk in cleartext, prefer ${ENV_VAR}.");
        clientSecretField.setRevealButtonVisible(true);
        clientSecretField.setWidthFull();

        authMethodRadio.setLabel("Client authentication");
        authMethodRadio.setItems(CLIENT_AUTH_PKCE, CLIENT_AUTH_SECRET_BASIC);
        authMethodRadio.setItemLabelGenerator(value -> CLIENT_AUTH_PKCE.equals(value)
                ? "PKCE (no secret)" : "client_secret_basic");
        authMethodRadio.setValue(CLIENT_AUTH_PKCE);

        redirectInfo.getStyle().set("font-size", "0.85em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block");
        updateRedirectInfo();

        authorizeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        authorizeButton.setEnabled(false);
        authorizeButton.addClickListener(e -> {
            String regId = registrationIdSupplier.get();
            if (regId == null || regId.isBlank()) return;
            UI.getCurrent().getPage().open("/oauth2/authorization/" + regId, "_blank");
        });

        FormLayout primaryForm = new FormLayout(clientIdField, issuerUriField, scopesField);
        primaryForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));

        FormLayout advancedForm = new FormLayout(authorizationUriField, tokenUriField, clientSecretField,
                authMethodRadio);
        advancedForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        advancedForm.setColspan(authMethodRadio, 2);
        advanced.setSummaryText("Advanced (manual URIs · client secret · auth method)");
        advanced.add(advancedForm);
        advanced.setOpened(false);
        advanced.setWidthFull();
        advanced.getStyle().set("margin-top", "var(--lumo-space-s)");

        HorizontalLayout actions = new HorizontalLayout(authorizeButton);
        actions.setSpacing(true);
        actions.getStyle().set("margin-top", "var(--lumo-space-s)");

        add(header, helpText, primaryForm, advanced, redirectInfo, actions);
    }

    private void wireChangeListeners() {
        Arrays.asList(issuerUriField, authorizationUriField, tokenUriField, clientIdField, clientSecretField,
                        scopesField)
                .forEach(field -> field.addValueChangeListener(e -> {
                    onChange.run();
                    updateAuthorizeButtonState();
                }));
        authMethodRadio.addValueChangeListener(e -> {
            onChange.run();
            updateAuthorizeButtonState();
        });
    }

    private void updateAuthorizeButtonState() {
        String regId = registrationIdSupplier.get();
        boolean hasRegId = regId != null && !regId.isBlank();
        boolean hasClientId = hasText(clientIdField.getValue());
        boolean hasIssuer = hasText(issuerUriField.getValue());
        boolean hasManualPair = hasText(authorizationUriField.getValue()) && hasText(tokenUriField.getValue());
        boolean enabled = hasRegId && hasClientId && (hasIssuer || hasManualPair);
        authorizeButton.setEnabled(enabled);
        if (!enabled) {
            authorizeButton.setTooltipText(
                    "Fill the server name, Client ID, and an Issuer URI (or Authorization + Token endpoints) first, then Save & Connect.");
        } else {
            authorizeButton.setTooltipText(
                    "Opens the issuer's consent page in your system browser. Make sure you've clicked Save & Connect first.");
        }
    }

    private void updateRedirectInfo() {
        String regId = registrationIdSupplier.get();
        String redirect = regId == null || regId.isBlank()
                ? "(after save) http://localhost:<port>/login/oauth2/code/<registration-id>"
                : "http://localhost:<port>/login/oauth2/code/" + regId;
        redirectInfo.setText("Redirect URI: " + redirect);
    }

    public void refreshRedirectInfo() {
        updateRedirectInfo();
        updateAuthorizeButtonState();
    }

    public HttpConnectionParametersWithExtras.OAuth toRecord() {
        String issuer = trimToNull(issuerUriField.getValue());
        String authUri = trimToNull(authorizationUriField.getValue());
        String tokenUri = trimToNull(tokenUriField.getValue());
        String clientId = trimToNull(clientIdField.getValue());
        String clientSecret = trimToNull(clientSecretField.getValue());
        List<String> scopes = parseScopes(scopesField.getValue());
        if (issuer == null && authUri == null && tokenUri == null && clientId == null
                && clientSecret == null && scopes.isEmpty()) {
            return null;
        }
        return new HttpConnectionParametersWithExtras.OAuth(issuer, authUri, tokenUri, clientId, clientSecret, scopes,
                authMethodRadio.getValue());
    }

    public void populate(HttpConnectionParametersWithExtras.OAuth oauth) {
        if (oauth == null) {
            clearFields();
            return;
        }
        issuerUriField.setValue(nullToBlank(oauth.issuerUri()));
        authorizationUriField.setValue(nullToBlank(oauth.authorizationUri()));
        tokenUriField.setValue(nullToBlank(oauth.tokenUri()));
        clientIdField.setValue(nullToBlank(oauth.clientId()));
        clientSecretField.setValue(nullToBlank(oauth.clientSecret()));
        scopesField.setValue(String.join(", ", oauth.scopes()));
        String authMethod = oauth.clientAuthenticationMethod();
        authMethodRadio.setValue(authMethod == null ? CLIENT_AUTH_PKCE : authMethod);
        boolean hasAdvancedData = hasText(oauth.authorizationUri()) || hasText(oauth.tokenUri())
                || hasText(oauth.clientSecret())
                || (authMethod != null && !CLIENT_AUTH_PKCE.equals(authMethod));
        if (hasAdvancedData) advanced.setOpened(true);
        updateRedirectInfo();
        updateAuthorizeButtonState();
    }

    public void clearFields() {
        issuerUriField.clear();
        authorizationUriField.clear();
        tokenUriField.clear();
        clientIdField.clear();
        clientSecretField.clear();
        scopesField.clear();
        authMethodRadio.setValue(CLIENT_AUTH_PKCE);
        advanced.setOpened(false);
        updateRedirectInfo();
        updateAuthorizeButtonState();
    }

    public boolean validateForSave() {
        boolean valid = true;
        clientIdField.setInvalid(false);
        issuerUriField.setInvalid(false);
        authorizationUriField.setInvalid(false);
        tokenUriField.setInvalid(false);

        if (!hasText(clientIdField.getValue())) {
            clientIdField.setInvalid(true);
            clientIdField.setErrorMessage("Client ID is required for OAuth.");
            valid = false;
        }
        boolean hasIssuer = hasText(issuerUriField.getValue());
        boolean hasAuth = hasText(authorizationUriField.getValue()) && hasText(tokenUriField.getValue());
        if (!hasIssuer && !hasAuth) {
            authorizationUriField.setInvalid(true);
            authorizationUriField.setErrorMessage("Provide either Issuer URI, or both Authorization and Token URIs.");
            valid = false;
        }
        if (hasIssuer && !issuerUriField.getValue().trim().toLowerCase().startsWith("https://")) {
            issuerUriField.setInvalid(true);
            issuerUriField.setErrorMessage("Issuer URI must use HTTPS.");
            valid = false;
        }
        return valid;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToBlank(String s) {
        return s == null ? "" : s;
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static List<String> parseScopes(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
