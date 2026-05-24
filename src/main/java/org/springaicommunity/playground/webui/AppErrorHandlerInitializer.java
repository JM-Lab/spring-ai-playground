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
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppErrorHandlerInitializer implements VaadinServiceInitListener {

    private static final Logger logger = LoggerFactory.getLogger(AppErrorHandlerInitializer.class);

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInit ->
                sessionInit.getSession().setErrorHandler(this::handle));
    }

    private void handle(ErrorEvent errorEvent) {
        Throwable throwable = errorEvent.getThrowable();
        logger.error("Unhandled UI exception", throwable);
        UI ui = UI.getCurrent();
        if (ui == null) return;
        ui.access(() -> VaadinUtils.showErrorNotification("Internal error: " + summary(throwable)));
    }

    private static String summary(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String name = root.getClass().getSimpleName();
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) return name;
        int lf = msg.indexOf('\n');
        String line = lf < 0 ? msg : msg.substring(0, lf);
        return name + ": " + line;
    }
}
