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
package org.springaicommunity.playground.service.tool;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolSpecService.ExposureMode;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;

import static org.assertj.core.api.Assertions.assertThat;

class ToolMcpServerSettingTest {

    @Test
    void nullExposedToolIdsDefaultsToEmpty() {
        ToolMcpServerSetting setting = new ToolMcpServerSetting(true, null);
        assertThat(setting.exposedToolIds()).isEmpty();
        assertThat(setting.autoAdd()).isTrue();
    }

    @Test
    void inclusionSemanticsPerMode() {
        assertThat(ExposureMode.BUILTIN_ONLY.includesBuiltin()).isTrue();
        assertThat(ExposureMode.BUILTIN_ONLY.includesComposed()).isFalse();
        assertThat(ExposureMode.COMPOSED_ONLY.includesBuiltin()).isFalse();
        assertThat(ExposureMode.COMPOSED_ONLY.includesComposed()).isTrue();
        assertThat(ExposureMode.BOTH.includesBuiltin()).isTrue();
        assertThat(ExposureMode.BOTH.includesComposed()).isTrue();
    }
}
