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

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AppleSiliconMlxActivatorTest {

    @Test
    void contributesMlxGroupOnAppleSilicon() {
        MockEnvironment environment = new MockEnvironment();
        assertThat(AppleSiliconMlxActivator.shouldActivateMlx(environment, "Mac OS X", "aarch64")).isTrue();
        assertThat(AppleSiliconMlxActivator.shouldActivateMlx(environment, "Mac OS X", "arm64")).isTrue();
    }

    @Test
    void skipsWhenNotAppleSilicon() {
        MockEnvironment environment = new MockEnvironment();
        assertThat(AppleSiliconMlxActivator.shouldActivateMlx(environment, "Mac OS X", "x86_64")).isFalse();
        assertThat(AppleSiliconMlxActivator.shouldActivateMlx(environment, "Linux", "aarch64")).isFalse();
    }

    @Test
    void skipsWhenAutoSelectDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(AppleSiliconMlxActivator.AUTO_SELECT_PROPERTY, "false");
        assertThat(AppleSiliconMlxActivator.shouldActivateMlx(environment, "Mac OS X", "aarch64")).isFalse();
    }

    @Test
    void detectsAppleSiliconByOsAndArch() {
        assertThat(AppleSiliconMlxActivator.isAppleSilicon("Mac OS X", "aarch64")).isTrue();
        assertThat(AppleSiliconMlxActivator.isAppleSilicon("Mac OS X", "arm64")).isTrue();
        assertThat(AppleSiliconMlxActivator.isAppleSilicon("Mac OS X", "x86_64")).isFalse();
        assertThat(AppleSiliconMlxActivator.isAppleSilicon("Windows 11", "aarch64")).isFalse();
    }

}
