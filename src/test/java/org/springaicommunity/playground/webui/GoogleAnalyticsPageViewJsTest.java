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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.internal.UIInternals.JavaScriptInvocation;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.webui.chat.ChatView;
import org.springaicommunity.playground.webui.home.HomeView;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// document.title on the chat route is the conversation title (first 20 chars of the user's prompt),
// so the page_view payload must always override both title and location with route-derived values.
@SpringBootTest
class GoogleAnalyticsPageViewJsTest extends SpringBrowserlessTest {

    private static final String CONVERSATION_ID = "11111111-2222-3333-4444-555555555555";

    @Test
    void pageViewPathParameterCarriesNoQueryString() {
        navigate(HomeView.class);
        drainPageViewInvocations();

        UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", CONVERSATION_ID));

        List<Object> parameters = pageViewParameters();
        assertThat(String.valueOf(parameters.get(0))).isEqualTo("/agentic-chat");
        assertThat(String.valueOf(parameters.get(1))).isEqualTo("Agentic Chat");
        assertThat(parameters).noneMatch(parameter -> String.valueOf(parameter).contains(CONVERSATION_ID));
    }

    @Test
    void pageViewTitleUsesTheRouteLabelNotTheViewTitle() {
        navigate(HomeView.class);
        drainPageViewInvocations();

        navigate(McpServerView.class);

        assertThat(String.valueOf(pageViewParameters().get(1))).isEqualTo("MCP Server");
    }

    @Test
    void pageLocationIsTheOriginPlusTheStrippedPathAndNothingElse() {
        navigate(HomeView.class);
        drainPageViewInvocations();

        UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", CONVERSATION_ID));

        assertThat(pageViewExpression())
                .contains("page_path:$0")
                .contains("page_location:document.location.origin+$0")
                .contains("page_title:$1")
                .doesNotContain("document.title")
                .doesNotContain("document.location.href")
                .doesNotContain("document.location.search")
                .doesNotContain("document.location.pathname");
    }

    @Test
    void pageViewIsEmittedOnlyAfterTheRouteDerivedFieldsAreSet() {
        navigate(HomeView.class);
        drainPageViewInvocations();

        navigate(ChatView.class);

        String expression = pageViewExpression();
        assertThat(expression.indexOf("gtag('set'")).isLessThan(expression.indexOf("'page_view'"));
    }

    private void drainPageViewInvocations() {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations();
    }

    private List<Object> pageViewParameters() {
        return lastPageViewInvocation().getParameters();
    }

    private String pageViewExpression() {
        return lastPageViewInvocation().getExpression();
    }

    private JavaScriptInvocation lastPageViewInvocation() {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .map(pending -> pending.getInvocation())
                .filter(invocation -> invocation.getExpression().contains("page_view"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no page_view invocation was queued"));
    }
}
