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
package org.springaicommunity.playground.service.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretMasking {

    private static final Pattern ENV_REF = Pattern.compile("\\$\\{([A-Z_]+[A-Z0-9_]*)}");
    private static final int MIN_MASK_LENGTH = 4;
    private static final String MASK = "***";

    private SecretMasking() {}

    public static Set<String> collectFromTemplate(String template) {
        Set<String> values = new LinkedHashSet<>();
        if (template == null || template.isEmpty()) return values;
        Matcher m = ENV_REF.matcher(template);
        while (m.find()) {
            EnvVarResolver.lookup(m.group(1)).ifPresent(v -> {
                if (v != null && v.length() >= MIN_MASK_LENGTH) values.add(v);
            });
        }
        return values;
    }

    public static String mask(String text, Set<String> secrets) {
        if (text == null || text.isEmpty() || secrets == null || secrets.isEmpty()) return text;
        String out = text;
        for (String secret : secrets) {
            if (secret == null || secret.length() < MIN_MASK_LENGTH) continue;
            out = out.replace(secret, MASK);
        }
        return out;
    }
}
