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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CurrencyService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<State> STATE_TYPE = new TypeReference<>() {};

    private final Path file;
    private final PersistenceExecutor executor;
    private final Map<String, CurrencyRate> rates = new ConcurrentHashMap<>();
    private volatile String activeCurrency = "USD";

    public CurrencyService(Path springAiPlaygroundHomeDir, PersistenceExecutor executor) {
        this.executor = executor;
        this.file = springAiPlaygroundHomeDir.resolve("observability").resolve("currency.json");
    }

    @PostConstruct
    public void init() {
        try {
            if (Files.exists(file)) {
                State state = MAPPER.readValue(file.toFile(), STATE_TYPE);
                if (state != null) {
                    if (state.rates != null) {
                        for (CurrencyRate r : state.rates) {
                            if (r != null && r.code() != null) rates.put(r.code(), r);
                        }
                    }
                    if (state.active != null && !state.active.isBlank()) {
                        this.activeCurrency = state.active;
                    }
                }
                logger.info("Loaded {} currency rates from {}; active={}",
                        rates.size(), file, activeCurrency);
            } else {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException | JacksonException e) {
            logger.warn("Failed to load currency state from {} — falling back to defaults", file, e);
        }
        // Always ensure defaults are present (so the user can opt-in to extra
        // currencies without touching JSON by hand). Existing rates keep
        // whatever the user set.
        seedDefaults();
    }

    // KRW pinned to the top (local-first ordering for the maintainer);
    // everything else (USD included) sorts alphabetically. Default active
    // currency stays USD as the canonical base.
    private static final List<String> PREFERRED_ORDER = List.of("KRW");

    private void seedDefaults() {
        Map<String, CurrencyRate> defaults = new LinkedHashMap<>();
        // Preferred top-of-list (user-requested order)
        defaults.put("KRW", new CurrencyRate("KRW", "₩", new BigDecimal("1380")));
        defaults.put("USD", new CurrencyRate("USD", "$", new BigDecimal("1.0")));
        defaults.put("EUR", new CurrencyRate("EUR", "€", new BigDecimal("0.92")));
        defaults.put("CNY", new CurrencyRate("CNY", "元", new BigDecimal("7.2")));
        defaults.put("JPY", new CurrencyRate("JPY", "¥", new BigDecimal("150")));
        defaults.put("INR", new CurrencyRate("INR", "₹", new BigDecimal("84")));
        // Remaining major fiat currencies — rough mid-2026 USD pegs; user can
        // override via the dialog. Grouped by region for the seed pass, then
        // sorted alphabetically by all() for display.
        defaults.put("GBP", new CurrencyRate("GBP", "£", new BigDecimal("0.79")));
        defaults.put("CHF", new CurrencyRate("CHF", "Fr", new BigDecimal("0.88")));
        defaults.put("CAD", new CurrencyRate("CAD", "C$", new BigDecimal("1.36")));
        defaults.put("AUD", new CurrencyRate("AUD", "A$", new BigDecimal("1.52")));
        defaults.put("NZD", new CurrencyRate("NZD", "NZ$", new BigDecimal("1.66")));
        defaults.put("HKD", new CurrencyRate("HKD", "HK$", new BigDecimal("7.82")));
        defaults.put("SGD", new CurrencyRate("SGD", "S$", new BigDecimal("1.34")));
        defaults.put("TWD", new CurrencyRate("TWD", "NT$", new BigDecimal("32")));
        defaults.put("THB", new CurrencyRate("THB", "฿", new BigDecimal("36")));
        defaults.put("IDR", new CurrencyRate("IDR", "Rp", new BigDecimal("15800")));
        defaults.put("VND", new CurrencyRate("VND", "₫", new BigDecimal("24600")));
        defaults.put("PHP", new CurrencyRate("PHP", "₱", new BigDecimal("57")));
        defaults.put("MYR", new CurrencyRate("MYR", "RM", new BigDecimal("4.7")));
        defaults.put("MXN", new CurrencyRate("MXN", "Mex$", new BigDecimal("17")));
        defaults.put("BRL", new CurrencyRate("BRL", "R$", new BigDecimal("5.2")));
        defaults.put("ARS", new CurrencyRate("ARS", "$", new BigDecimal("990")));
        defaults.put("CLP", new CurrencyRate("CLP", "$", new BigDecimal("970")));
        defaults.put("COP", new CurrencyRate("COP", "$", new BigDecimal("3900")));
        defaults.put("PEN", new CurrencyRate("PEN", "S/", new BigDecimal("3.75")));
        defaults.put("ZAR", new CurrencyRate("ZAR", "R", new BigDecimal("18.7")));
        defaults.put("EGP", new CurrencyRate("EGP", "£E", new BigDecimal("48")));
        defaults.put("NGN", new CurrencyRate("NGN", "₦", new BigDecimal("1530")));
        defaults.put("KES", new CurrencyRate("KES", "KSh", new BigDecimal("129")));
        defaults.put("TRY", new CurrencyRate("TRY", "₺", new BigDecimal("32")));
        defaults.put("RUB", new CurrencyRate("RUB", "₽", new BigDecimal("93")));
        defaults.put("UAH", new CurrencyRate("UAH", "₴", new BigDecimal("40")));
        defaults.put("PLN", new CurrencyRate("PLN", "zł", new BigDecimal("4.0")));
        defaults.put("CZK", new CurrencyRate("CZK", "Kč", new BigDecimal("23")));
        defaults.put("HUF", new CurrencyRate("HUF", "Ft", new BigDecimal("360")));
        defaults.put("SEK", new CurrencyRate("SEK", "kr", new BigDecimal("10.7")));
        defaults.put("NOK", new CurrencyRate("NOK", "kr", new BigDecimal("10.8")));
        defaults.put("DKK", new CurrencyRate("DKK", "kr", new BigDecimal("6.86")));
        defaults.put("ISK", new CurrencyRate("ISK", "kr", new BigDecimal("140")));
        defaults.put("ILS", new CurrencyRate("ILS", "₪", new BigDecimal("3.7")));
        defaults.put("AED", new CurrencyRate("AED", "د.إ", new BigDecimal("3.67")));
        defaults.put("SAR", new CurrencyRate("SAR", "﷼", new BigDecimal("3.75")));
        defaults.put("QAR", new CurrencyRate("QAR", "﷼", new BigDecimal("3.64")));
        defaults.put("KWD", new CurrencyRate("KWD", "د.ك", new BigDecimal("0.31")));
        defaults.put("BHD", new CurrencyRate("BHD", "د.ب", new BigDecimal("0.38")));
        boolean changed = false;
        for (CurrencyRate d : defaults.values()) {
            if (rates.putIfAbsent(d.code(), d) == null) changed = true;
        }
        if (changed) saveAsync();
    }

    public String getActiveCurrency() {
        return activeCurrency;
    }

    public CurrencyRate getActiveRate() {
        return rates.getOrDefault(activeCurrency,
                new CurrencyRate("USD", "$", new BigDecimal("1.0")));
    }

    public List<CurrencyRate> all() {
        List<CurrencyRate> list = new ArrayList<>(rates.values());
        list.sort((a, b) -> {
            int ai = PREFERRED_ORDER.indexOf(a.code());
            int bi = PREFERRED_ORDER.indexOf(b.code());
            if (ai >= 0 && bi >= 0) return Integer.compare(ai, bi);
            if (ai >= 0) return -1;
            if (bi >= 0) return 1;
            return a.code().compareTo(b.code());
        });
        return list;
    }

    public void setActiveCurrency(String code) {
        if (code == null || code.isBlank() || !rates.containsKey(code)) return;
        this.activeCurrency = code;
        saveAsync();
    }

    public void upsertRate(CurrencyRate rate) {
        if (rate == null || rate.code() == null || rate.code().isBlank()) return;
        rates.put(rate.code(), rate);
        saveAsync();
    }

    public BigDecimal convertFromUsd(BigDecimal usd) {
        if (usd == null) return BigDecimal.ZERO;
        return usd.multiply(getActiveRate().rateFromUsd()).setScale(4, RoundingMode.HALF_UP);
    }

    private void saveAsync() {
        State state = new State(activeCurrency, new ArrayList<>(rates.values()));
        executor.submit(() -> {
            try {
                Files.createDirectories(file.getParent());
                MAPPER.writeValue(file.toFile(), state);
            } catch (IOException | JacksonException e) {
                logger.error("Failed to save currency state to {}", file, e);
            }
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class State {
        public String active;
        public List<CurrencyRate> rates;

        public State() {}
        public State(String active, List<CurrencyRate> rates) {
            this.active = active;
            this.rates = rates;
        }
    }

    public record CurrencyRate(String code, String symbol, BigDecimal rateFromUsd) {}

    public static String flagFor(String currencyCode) {
        if (currencyCode == null || currencyCode.length() < 3) return "🏳";
        return switch (currencyCode) {
            case "KRW" -> "🇰🇷";
            case "USD" -> "🇺🇸";
            case "EUR" -> "🇪🇺";
            case "CNY" -> "🇨🇳";
            case "JPY" -> "🇯🇵";
            case "INR" -> "🇮🇳";
            case "GBP" -> "🇬🇧";
            case "CHF" -> "🇨🇭";
            case "CAD" -> "🇨🇦";
            case "AUD" -> "🇦🇺";
            case "NZD" -> "🇳🇿";
            case "HKD" -> "🇭🇰";
            case "SGD" -> "🇸🇬";
            case "TWD" -> "🇹🇼";
            case "THB" -> "🇹🇭";
            case "IDR" -> "🇮🇩";
            case "VND" -> "🇻🇳";
            case "PHP" -> "🇵🇭";
            case "MYR" -> "🇲🇾";
            case "MXN" -> "🇲🇽";
            case "BRL" -> "🇧🇷";
            case "ARS" -> "🇦🇷";
            case "CLP" -> "🇨🇱";
            case "COP" -> "🇨🇴";
            case "PEN" -> "🇵🇪";
            case "ZAR" -> "🇿🇦";
            case "EGP" -> "🇪🇬";
            case "NGN" -> "🇳🇬";
            case "KES" -> "🇰🇪";
            case "TRY" -> "🇹🇷";
            case "RUB" -> "🇷🇺";
            case "UAH" -> "🇺🇦";
            case "PLN" -> "🇵🇱";
            case "CZK" -> "🇨🇿";
            case "HUF" -> "🇭🇺";
            case "SEK" -> "🇸🇪";
            case "NOK" -> "🇳🇴";
            case "DKK" -> "🇩🇰";
            case "ISK" -> "🇮🇸";
            case "ILS" -> "🇮🇱";
            case "AED" -> "🇦🇪";
            case "SAR" -> "🇸🇦";
            case "QAR" -> "🇶🇦";
            case "KWD" -> "🇰🇼";
            case "BHD" -> "🇧🇭";
            default -> regionalIndicator(currencyCode.substring(0, 2));
        };
    }

    private static String regionalIndicator(String iso2) {
        if (iso2 == null || iso2.length() != 2) return "🏳";
        StringBuilder out = new StringBuilder();
        for (char c : iso2.toUpperCase().toCharArray()) {
            if (c < 'A' || c > 'Z') return "🏳";
            out.appendCodePoint(0x1F1E6 + (c - 'A'));
        }
        return out.toString();
    }
}
