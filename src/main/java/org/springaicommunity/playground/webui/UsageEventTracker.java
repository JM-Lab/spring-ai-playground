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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.UI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UsageEventTracker {

    private static final Logger logger = LoggerFactory.getLogger(UsageEventTracker.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void track(UI ui, String eventName, Map<String, Object> params) {
        executeGtag(ui, "if(window.gtag){gtag('event',$0,JSON.parse($1));}", eventName, params);
    }

    public void setUserProperties(UI ui, Map<String, Object> properties) {
        executeGtag(ui, "if(window.gtag){gtag('set','user_properties',JSON.parse($0));}", null, properties);
    }

    private void executeGtag(UI ui, String expression, String eventName, Map<String, Object> payload) {
        if (ui == null || payload == null) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.debug("usage event skipped ({}): {}", eventName, e.getMessage());
            return;
        }
        if (eventName == null) ui.getPage().executeJs(expression, json);
        else ui.getPage().executeJs(expression, eventName, json);
    }
}
