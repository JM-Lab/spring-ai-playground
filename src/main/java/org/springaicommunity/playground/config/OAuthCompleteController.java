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
package org.springaicommunity.playground.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.regex.Pattern;

@Controller
public class OAuthCompleteController {

    private static final Pattern SAFE_REG_ID = Pattern.compile("[A-Za-z0-9._-]+");

    @GetMapping("/login/oauth2/code/{registrationId}")
    public String oauthCallbackComplete(@PathVariable String registrationId) {
        if (!SAFE_REG_ID.matcher(registrationId).matches()) {
            return "redirect:/oauth-complete";
        }
        return "redirect:/oauth-complete?regId=" + registrationId;
    }
}
