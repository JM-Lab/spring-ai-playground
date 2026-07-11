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
package org.springaicommunity.playground.webui.tool;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolSpec.SandboxOverrides;
import org.springaicommunity.playground.service.tool.policy.SandboxPostureCalculator;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SandboxCapabilitiesViewTest {

    private SandboxCapabilitiesView newView() {
        return new SandboxCapabilitiesView(null, new SandboxPostureCalculator());
    }

    private static SandboxOverrides overridesWithDestructive(Boolean destructive) {
        return new SandboxOverrides(Set.of(), Set.of(), Set.of(), Set.of(),
                "blocked", Set.of(), Boolean.TRUE, Boolean.TRUE, null, destructive);
    }

    @Test
    void destructiveSurvivesApplyThenCurrentRoundTrip() {
        SandboxCapabilitiesView view = newView();
        view.applyOverrides(overridesWithDestructive(Boolean.TRUE));
        assertEquals(Boolean.TRUE, view.currentOverrides().destructive());
    }

    @Test
    void destructiveStaysNullWhenNeverApplied() {
        SandboxCapabilitiesView view = newView();
        assertNull(view.currentOverrides().destructive());
    }

    @Test
    void reapplyingNonDestructiveOverridesClearsFlag() {
        SandboxCapabilitiesView view = newView();
        view.applyOverrides(overridesWithDestructive(Boolean.TRUE));
        view.applyOverrides(overridesWithDestructive(null));
        assertNull(view.currentOverrides().destructive());
    }
}
