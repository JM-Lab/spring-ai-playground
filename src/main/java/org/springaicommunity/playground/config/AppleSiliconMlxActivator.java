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

import java.util.Locale;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class AppleSiliconMlxActivator implements EnvironmentPostProcessor, Ordered {

    static final String MLX_PROFILE = "mlx";

    static final String OLLAMA_PROFILE = "ollama";

    static final String AUTO_SELECT_PROPERTY = "spring.ai.playground.ollama.mlx-auto-select";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!shouldActivateMlx(environment, System.getProperty("os.name", ""), System.getProperty("os.arch", ""))) {
            return;
        }
        // group avoids the default-suppression side effect of addActiveProfile
        environment.getPropertySources().addFirst(new MapPropertySource("appleSiliconMlx",
                Map.of("spring.profiles.group." + OLLAMA_PROFILE, MLX_PROFILE)));
    }

    static boolean shouldActivateMlx(ConfigurableEnvironment environment, String osName, String osArch) {
        return environment.getProperty(AUTO_SELECT_PROPERTY, Boolean.class, true) && isAppleSilicon(osName, osArch);
    }

    static boolean isAppleSilicon(String osName, String osArch) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return normalizedOs.contains("mac") && ("aarch64".equals(osArch) || "arm64".equals(osArch));
    }

    @Override
    public int getOrder() {
        // Must run before ConfigData (HIGHEST_PRECEDENCE + 10) so the profile group is in place.
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

}
