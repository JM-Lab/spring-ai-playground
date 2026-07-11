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
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.analytics.UsageAnalyticsService;
import org.springframework.stereotype.Component;

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
                String path = "/" + navigationEvent.getLocation().getPathWithQueryParameters();
                ui.getPage().executeJs(
                        "if(window.gtag){window.gtag('event','page_view',"
                                + "{page_path:$0,page_location:document.location.href,page_title:document.title});}",
                        path);
            });
        });
    }

    private void initUsageTracking(UI ui) {
        this.usageEventTracker.setUserProperties(ui, this.usageAnalyticsService.userProperties(isDesktopSurface()));
        this.usageAnalyticsService.dailyUsageSnapshot()
                .ifPresent(params -> this.usageEventTracker.track(ui, "usage_snapshot", params));
    }

    private static boolean isDesktopSurface() {
        VaadinRequest request = VaadinService.getCurrentRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        return userAgent != null && userAgent.contains("Electron");
    }
}
