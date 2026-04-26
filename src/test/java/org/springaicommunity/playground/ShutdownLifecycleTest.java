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
package org.springaicommunity.playground;

import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.PersistenceServiceInterface;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShutdownLifecycleTest {

    @Test
    void onShutdown_flushesExecutorBeforeInvokingServiceOnShutdown() throws IOException {
        PersistenceExecutor executor = mock(PersistenceExecutor.class);
        PersistenceServiceInterface<?> service1 = mock(PersistenceServiceInterface.class);
        PersistenceServiceInterface<?> service2 = mock(PersistenceServiceInterface.class);
        when(service1.getLogger()).thenReturn(LoggerFactory.getLogger("svc1"));
        when(service2.getLogger()).thenReturn(LoggerFactory.getLogger("svc2"));

        SpringAiPlaygroundPersistenceManager manager = new SpringAiPlaygroundPersistenceManager(
                new PersistenceServiceInterface[]{service1, service2}, executor);

        manager.onShutdown();

        // Executor must flush queued saveAsync tasks BEFORE services run their onShutdown.
        // Otherwise pending mutations would be silently dropped.
        InOrder inOrder = inOrder(executor, service1, service2);
        inOrder.verify(executor).flushAndShutdown();
        inOrder.verify(service1).onShutdown();
        inOrder.verify(service2).onShutdown();
    }

    @Test
    void onShutdown_continuesServiceChainWhenOneFails() throws IOException {
        PersistenceExecutor executor = mock(PersistenceExecutor.class);
        PersistenceServiceInterface<?> failing = mock(PersistenceServiceInterface.class);
        PersistenceServiceInterface<?> healthy = mock(PersistenceServiceInterface.class);
        when(failing.getLogger()).thenReturn(LoggerFactory.getLogger("failing"));
        when(healthy.getLogger()).thenReturn(LoggerFactory.getLogger("healthy"));

        org.mockito.Mockito.doThrow(new IOException("boom")).when(failing).onShutdown();

        SpringAiPlaygroundPersistenceManager manager = new SpringAiPlaygroundPersistenceManager(
                new PersistenceServiceInterface[]{failing, healthy}, executor);

        // Should not throw — the manager swallows per-service IOExceptions and keeps going.
        manager.onShutdown();

        org.mockito.Mockito.verify(healthy).onShutdown();
    }
}
