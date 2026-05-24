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
package org.springaicommunity.playground.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.PersistenceServiceInterface;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(prefix = "spring.ai.playground.observability", name = "persist",
        havingValue = "true", matchIfMissing = true)
public class ObservabilityPersistenceService implements PersistenceServiceInterface<TraceRecord> {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityPersistenceService.class);
    private static final DateTimeFormatter DATE_DIR_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path baseDir;
    private final PersistenceExecutor executor;
    private final ObservabilityProperties props;

    public ObservabilityPersistenceService(Path springAiPlaygroundHomeDir,
            PersistenceExecutor executor,
            ObservabilityProperties props) {
        this.executor = executor;
        this.baseDir = springAiPlaygroundHomeDir.resolve("observability");
        this.props = props;
    }

    public void saveAsync(TraceRecord trace) {
        executor.submit(() -> {
            try {
                save(trace);
            } catch (IOException e) {
                logger.error("Failed to save trace {}", trace.traceId(), e);
            }
        });
    }

    @Override
    public Path getSaveDir() {
        return baseDir.resolve(LocalDate.now().toString());
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String buildSaveFileName(TraceRecord trace) {
        return trace.traceId();
    }

    @Override
    public TraceRecord convertTo(Map<String, Object> saveObjectMap) {
        return OBJECT_MAPPER.convertValue(saveObjectMap, TraceRecord.class);
    }

    @Override
    public void onStart() throws IOException {
        Files.createDirectories(baseDir);
        cleanupOlderThanRetention();
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledCleanup() {
        cleanupOlderThanRetention();
    }

    private void cleanupOlderThanRetention() {
        if (!Files.isDirectory(baseDir)) return;
        LocalDate cutoff = LocalDate.now().minusDays(props.getRetainDays());
        try (Stream<Path> dirs = Files.list(baseDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                LocalDate date = parseDateDir(dir);
                if (date != null && date.isBefore(cutoff)) {
                    deleteRecursive(dir);
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to list observability base dir {}", baseDir, e);
        }
    }

    private LocalDate parseDateDir(Path dir) {
        try {
            return LocalDate.parse(dir.getFileName().toString(), DATE_DIR_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void deleteRecursive(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    logger.warn("Failed to delete {}", p, e);
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to walk {} for cleanup", dir, e);
        }
    }
}
