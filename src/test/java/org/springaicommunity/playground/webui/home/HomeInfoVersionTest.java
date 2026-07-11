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
package org.springaicommunity.playground.webui.home;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomeInfoVersionTest {

    @Test
    void newerMilestoneIsAnUpdateButOlderIsNot() {
        assertThat(HomeInfoView.isNewer("0.2.0-M9", "0.2.0-M8")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.0-M7", "0.2.0-M8")).isFalse();
        assertThat(HomeInfoView.isNewer("0.2.0-M8", "0.2.0-M8")).isFalse();
    }

    @Test
    void gaOutranksAnyMilestoneOfSameVersion() {
        assertThat(HomeInfoView.isNewer("0.2.0", "0.2.0-M8")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.0-M8", "0.2.0")).isFalse();
    }

    @Test
    void rcOutranksMilestoneAndGaOutranksRc() {
        assertThat(HomeInfoView.isNewer("0.2.0-RC1", "0.2.0-M11")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.0-RC2", "0.2.0-RC1")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.0", "0.2.0-RC2")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.0-M12", "0.2.0-RC1")).isFalse();
        assertThat(HomeInfoView.isNewer("0.2.0-RC1", "0.2.0-RC1")).isFalse();
        assertThat(HomeInfoView.isNewer("0.2.0-RC1", "0.2.0")).isFalse();
        assertThat(HomeInfoView.isNewer("0.3.0-M1", "0.2.0")).isTrue();
    }

    @Test
    void higherCoreVersionWins() {
        assertThat(HomeInfoView.isNewer("0.3.0-M1", "0.2.0-M8")).isTrue();
        assertThat(HomeInfoView.isNewer("0.1.9", "0.2.0-M8")).isFalse();
        assertThat(HomeInfoView.isNewer("1.0.0-M1", "0.9.9")).isTrue();
    }

    @Test
    void patchReleaseOutranksLowerPatchAtAnyStage() {
        assertThat(HomeInfoView.isNewer("0.2.1", "0.2.0")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.1-M1", "0.2.0")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.1-RC1", "0.2.0")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.0", "0.2.1-M1")).isFalse();
        assertThat(HomeInfoView.isNewer("0.2.10", "0.2.9")).isTrue();
        assertThat(HomeInfoView.isNewer("0.2.1", "0.2.1")).isFalse();
    }

    @Test
    void leadingVPrefixAndUnparseableAreHandled() {
        assertThat(HomeInfoView.isNewer("v0.2.0-M9", "0.2.0-M8")).isTrue();
        assertThat(HomeInfoView.isNewer("garbage", "0.2.0-M8")).isFalse();
        assertThat(HomeInfoView.isNewer("0.2.0-M8", null)).isFalse();
    }

    @Test
    void parseVersionMapsStageAndNumber() {
        assertThat(HomeInfoView.parseVersion("0.2.0-M8")).containsExactly(0, 2, 0, 1, 8);
        assertThat(HomeInfoView.parseVersion("0.2.0-RC1")).containsExactly(0, 2, 0, 2, 1);
        assertThat(HomeInfoView.parseVersion("0.2.0")).containsExactly(0, 2, 0, 3, 0);
        assertThat(HomeInfoView.parseVersion("1.4.2-SNAPSHOT")).containsExactly(1, 4, 2, 0, 0);
    }
}
