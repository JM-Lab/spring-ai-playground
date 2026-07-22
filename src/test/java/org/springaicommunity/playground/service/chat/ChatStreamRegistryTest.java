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
package org.springaicommunity.playground.service.chat;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.Disposables;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStreamRegistryTest {

    private final ChatStreamRegistry registry = new ChatStreamRegistry();

    @Test
    void beginMarksConversationStreamingUntilFinish() {
        this.registry.begin("conv-a");
        assertThat(this.registry.isStreaming("conv-a")).isTrue();
        this.registry.finish("conv-a");
        assertThat(this.registry.isStreaming("conv-a")).isFalse();
    }

    @Test
    void stopDisposesAttachedStreamButKeepsEntryUntilFinish() {
        Disposable stream = Disposables.single();
        this.registry.begin("conv-b");
        this.registry.attach("conv-b", stream);
        this.registry.stop("conv-b");
        assertThat(stream.isDisposed()).isTrue();
        assertThat(this.registry.isStreaming("conv-b")).isTrue();
    }

    @Test
    void finishRunsListenersOnceAndClearsEntry() {
        AtomicInteger runs = new AtomicInteger();
        this.registry.begin("conv-c");
        this.registry.onFinish("conv-c", runs::incrementAndGet);
        this.registry.finish("conv-c");
        this.registry.finish("conv-c");
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void failingListenerDoesNotBlockOthers() {
        AtomicInteger runs = new AtomicInteger();
        this.registry.begin("conv-d");
        this.registry.onFinish("conv-d", () -> {
            throw new IllegalStateException("listener boom");
        });
        this.registry.onFinish("conv-d", runs::incrementAndGet);
        this.registry.finish("conv-d");
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void operationsOnUnknownConversationAreNoOps() {
        this.registry.attach("missing", Disposables.single());
        this.registry.stop("missing");
        this.registry.finish("missing");
        this.registry.onFinish("missing", () -> {
            throw new AssertionError("listener for unknown conversation must not run");
        });
        assertThat(this.registry.isStreaming("missing")).isFalse();
    }

}
