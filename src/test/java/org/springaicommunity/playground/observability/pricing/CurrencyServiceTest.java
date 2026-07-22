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
package org.springaicommunity.playground.observability.pricing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.observability.pricing.CurrencyService.CurrencyRate;
import org.springaicommunity.playground.service.PersistenceExecutor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyServiceTest {

    @TempDir Path home;

    private PersistenceExecutor executor;

    @BeforeEach
    void setUp() {
        this.executor = new PersistenceExecutor();
    }

    @AfterEach
    void tearDown() throws Exception {
        this.executor.awaitCompletion(Duration.ofSeconds(5));
    }

    private CurrencyService newInitializedService() {
        CurrencyService service = new CurrencyService(this.home, this.executor);
        service.init();
        return service;
    }

    @Test
    void initWithoutFileSeedsDefaultsWithUsdActive() {
        CurrencyService service = newInitializedService();
        assertThat(service.getActiveCurrency()).isEqualTo("USD");
        assertThat(service.getActiveRate().rateFromUsd()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.all()).hasSizeGreaterThan(40);
    }

    @Test
    void allPinsKrwFirstThenSortsAlphabetically() {
        List<CurrencyRate> all = newInitializedService().all();
        assertThat(all.getFirst().code()).isEqualTo("KRW");
        List<String> rest = all.stream().skip(1).map(CurrencyRate::code).toList();
        assertThat(rest).isSorted();
    }

    @Test
    void activeCurrencyAndCustomRateSurviveReload() throws Exception {
        CurrencyService service = newInitializedService();
        service.setActiveCurrency("KRW");
        service.upsertRate(new CurrencyRate("KRW", "₩", new BigDecimal("1500")));
        this.executor.awaitCompletion(Duration.ofSeconds(5));

        CurrencyService reloaded = newInitializedService();
        assertThat(reloaded.getActiveCurrency()).isEqualTo("KRW");
        assertThat(reloaded.getActiveRate().rateFromUsd()).isEqualByComparingTo(new BigDecimal("1500"));
    }

    @Test
    void setActiveCurrencyIgnoresUnknownBlankAndNull() {
        CurrencyService service = newInitializedService();
        service.setActiveCurrency("XXX");
        service.setActiveCurrency(" ");
        service.setActiveCurrency(null);
        assertThat(service.getActiveCurrency()).isEqualTo("USD");
    }

    @Test
    void upsertRateIgnoresNullAndBlankCode() {
        CurrencyService service = newInitializedService();
        int before = service.all().size();
        service.upsertRate(null);
        service.upsertRate(new CurrencyRate(" ", "?", BigDecimal.ONE));
        service.upsertRate(new CurrencyRate(null, "?", BigDecimal.ONE));
        assertThat(service.all()).hasSize(before);
    }

    @Test
    void upsertRateAddsNewCurrency() {
        CurrencyService service = newInitializedService();
        service.upsertRate(new CurrencyRate("XAU", "oz", new BigDecimal("0.0004")));
        assertThat(service.all()).extracting(CurrencyRate::code).contains("XAU");
    }

    @Test
    void convertFromUsdUsesActiveRateAtScaleFour() {
        CurrencyService service = newInitializedService();
        service.setActiveCurrency("KRW");
        service.upsertRate(new CurrencyRate("KRW", "₩", new BigDecimal("1380")));
        assertThat(service.convertFromUsd(new BigDecimal("2"))).isEqualByComparingTo(new BigDecimal("2760.0000"));
        assertThat(service.convertFromUsd(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getActiveRateFallsBackToUsdWhenActiveUnknown() throws Exception {
        Path file = this.home.resolve("observability").resolve("currency.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"active\":\"ZZZ\",\"rates\":[]}");
        CurrencyService service = newInitializedService();
        assertThat(service.getActiveCurrency()).isEqualTo("ZZZ");
        assertThat(service.getActiveRate().code()).isEqualTo("USD");
    }

    @Test
    void corruptedStateFileFallsBackToSeededDefaults() throws Exception {
        Path file = this.home.resolve("observability").resolve("currency.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not-json{{{");
        CurrencyService service = newInitializedService();
        assertThat(service.getActiveCurrency()).isEqualTo("USD");
        assertThat(service.all()).isNotEmpty();
    }

    @Test
    void flagForMapsKnownCurrenciesToFlags() {
        assertThat(CurrencyService.flagFor("KRW")).isEqualTo("🇰🇷");
        assertThat(CurrencyService.flagFor("USD")).isEqualTo("🇺🇸");
        assertThat(CurrencyService.flagFor("EUR")).isEqualTo("🇪🇺");
        assertThat(CurrencyService.flagFor("BHD")).isEqualTo("🇧🇭");
    }

    @Test
    void flagForMapsEverySeededCurrencyToASpecificFlag() {
        for (CurrencyRate rate : newInitializedService().all()) {
            assertThat(CurrencyService.flagFor(rate.code())).isNotEqualTo("🏳");
        }
    }

    @Test
    void flagForDerivesRegionalIndicatorsForUnknownCodes() {
        assertThat(CurrencyService.flagFor("BND")).isEqualTo("🇧🇳");
    }

    @Test
    void flagForFallsBackToWhiteFlagOnInvalidInput() {
        assertThat(CurrencyService.flagFor(null)).isEqualTo("🏳");
        assertThat(CurrencyService.flagFor("US")).isEqualTo("🏳");
        assertThat(CurrencyService.flagFor("12X")).isEqualTo("🏳");
    }

}
