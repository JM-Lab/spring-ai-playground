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

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public abstract class BoundedRingBuffer<T> implements RingBuffer<T> {

    protected final int capacity;
    protected final Deque<T> buffer = new ConcurrentLinkedDeque<>();

    protected BoundedRingBuffer(int capacity, int minCapacity) {
        this.capacity = Math.max(minCapacity, capacity);
    }

    protected void addWithEviction(T item) {
        while (buffer.size() >= capacity) {
            buffer.pollFirst();
        }
        buffer.addLast(item);
    }

    @Override
    public int size() {
        return buffer.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public List<T> snapshot() {
        return new ArrayList<>(buffer);
    }
}
