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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.analytics.UsageAnalyticsService;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class GoogleAnalyticsNavigationListener implements VaadinServiceInitListener {

    public static final String MEASUREMENT_ID = "G-52TGT1G9B3";

    private static final Logger logger = LoggerFactory.getLogger(GoogleAnalyticsNavigationListener.class);

    private final UsageAnalyticsService usageAnalyticsService;
    private final UsageEventTracker usageEventTracker;

    public GoogleAnalyticsNavigationListener(UsageAnalyticsService usageAnalyticsService,
            UsageEventTracker usageEventTracker) {
        this.usageAnalyticsService = usageAnalyticsService;
        this.usageEventTracker = usageEventTracker;
    }

    public static boolean isTelemetryOptedOut() {
        String value = System.getenv("SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED");
        if (value == null) value = System.getProperty("spring.ai.playground.telemetry.enabled");
        return value != null && "false".equalsIgnoreCase(value.trim());
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        boolean productionMode = event.getSource().getDeploymentConfiguration().isProductionMode();
        boolean active = productionMode && !isTelemetryOptedOut();
        if (active) {
            logger.info("Anonymous usage telemetry is enabled (GA4 {}). Collected events are documented in "
                    + "docs/getting-started/configuration.md#telemetry; opt out with "
                    + "SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false.", MEASUREMENT_ID);
        } else {
            logger.info("Anonymous usage telemetry is disabled ({}).", productionMode ? "opted out" : "dev mode");
        }
        event.getSource().addUIInitListener(uiInitEvent -> {
            UI ui = uiInitEvent.getUI();
            if (active) initUsageTracking(ui);
            ui.addAfterNavigationListener(navigationEvent -> {
                String path = "/" + navigationEvent.getLocation().getPath();
                Class<?> routeTarget = navigationEvent.getActiveChain().stream().findFirst()
                        .map(Object::getClass).orElse(null);
                ui.getPage().executeJs(
                        "if(window.gtag){window.gtag('set',{page_path:$0,"
                                + "page_location:document.location.origin+$0,page_title:$1});"
                                + "window.gtag('event','page_view');}",
                        path, routeLabel(routeTarget, path));
            });
        });
    }

    static String routeLabel(Class<?> routeTarget, String path) {
        PageTitle pageTitle = routeTarget == null ? null
                : AnnotationUtils.findAnnotation(routeTarget, PageTitle.class);
        if (pageTitle != null && !pageTitle.value().isBlank()) return pageTitle.value();
        String slug = path.startsWith("/") ? path.substring(1) : path;
        if (slug.isBlank()) return "Home";
        return Arrays.stream(slug.split("[/-]")).filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private void initUsageTracking(UI ui) {
        this.usageEventTracker.setUserProperties(ui, this.usageAnalyticsService.userProperties(isDesktopSurface()));
        this.usageAnalyticsService.dailyUsage().ifPresent(daily -> {
            this.usageEventTracker.track(ui, "usage_snapshot", daily.snapshot());
            daily.mcpServers().forEach(params -> this.usageEventTracker.track(ui, "mcp_server_active", params));
            daily.models().forEach(params -> this.usageEventTracker.track(ui, "model_usage", params));
        });
    }

    private static boolean isDesktopSurface() {
        VaadinRequest request = VaadinService.getCurrentRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        return userAgent != null && userAgent.contains("Electron");
    }
}
