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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChipListBindingTest {

    @Test
    void baselineSeedsBothItemsAndSelected() {
        ChipListBinding binding = new ChipListBinding(List.of("a", "b", "c"));
        assertEquals(Set.of("a", "b", "c"), binding.items());
        assertEquals(Set.of("a", "b", "c"), binding.selected());
    }

    @Test
    void nullBaselineYieldsEmpty() {
        ChipListBinding binding = new ChipListBinding(null);
        assertTrue(binding.items().isEmpty());
        assertTrue(binding.selected().isEmpty());
    }

    @Test
    void addExtendsBothItemsAndSelected() {
        ChipListBinding binding = new ChipListBinding(List.of("a"));
        assertTrue(binding.add("b"));
        assertEquals(Set.of("a", "b"), binding.items());
        assertEquals(Set.of("a", "b"), binding.selected(),
                "newly added value should also be selected so the chip is visible");
    }

    @Test
    void addTrimsWhitespace() {
        ChipListBinding binding = new ChipListBinding(List.of());
        binding.add("  foo  ");
        assertEquals(Set.of("foo"), binding.items());
    }

    @Test
    void addRejectsNullAndBlank() {
        ChipListBinding binding = new ChipListBinding(List.of("a"));
        assertFalse(binding.add(null));
        assertFalse(binding.add(""));
        assertFalse(binding.add("   "));
        assertEquals(Set.of("a"), binding.items());
    }

    @Test
    void addDuplicateIsNoOp() {
        ChipListBinding binding = new ChipListBinding(List.of("a"));
        assertFalse(binding.add("a"), "re-adding an already-selected item changes nothing");
        assertEquals(Set.of("a"), binding.items());
        assertEquals(Set.of("a"), binding.selected());
    }

    @Test
    void addReturnsTrueWhenItemNewButSelectedSeparately() {
        ChipListBinding binding = new ChipListBinding(List.of("a"));
        binding.replaceSelected(List.of());
        assertTrue(binding.add("a"), "item exists but was unselected — add re-selects → changed=true");
        assertTrue(binding.selected().contains("a"));
    }

    @Test
    void replaceSelectedReflectsUserRemovingChips() {
        ChipListBinding binding = new ChipListBinding(List.of("a", "b", "c"));
        binding.replaceSelected(List.of("a", "c"));  // user removed "b"
        assertEquals(Set.of("a", "c"), binding.selected());
        assertEquals(Set.of("a", "b", "c"), binding.items(),
                "removing a chip must NOT shrink the dropdown — user can re-select from items");
    }

    @Test
    void replaceSelectedWithNullClearsSelection() {
        ChipListBinding binding = new ChipListBinding(List.of("a", "b"));
        binding.replaceSelected(null);
        assertTrue(binding.selected().isEmpty());
        assertEquals(Set.of("a", "b"), binding.items(), "items survive selection clearing");
    }

    @Test
    void replaceSelectedTrimsAndIgnoresBlanks() {
        ChipListBinding binding = new ChipListBinding(List.of("a", "b"));
        // java.util.Arrays.asList allows null entries; List.of does not.
        binding.replaceSelected(java.util.Arrays.asList(" a ", "", null, "b"));
        assertEquals(Set.of("a", "b"), binding.selected());
    }

    @Test
    void readdAfterRemoveDoesNotDuplicateInItems() {
        ChipListBinding binding = new ChipListBinding(List.of("a"));
        binding.replaceSelected(List.of());  // user removed the chip
        assertTrue(binding.selected().isEmpty());
        binding.add("a");                    // user re-types the same value
        assertEquals(Set.of("a"), binding.items(), "items should still hold one entry");
        assertEquals(Set.of("a"), binding.selected());
    }

    @Test
    void itemsAndSelectedAreImmutableViews() {
        ChipListBinding binding = new ChipListBinding(List.of("a"));
        assertThrows(UnsupportedOperationException.class, () -> binding.items().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> binding.selected().add("x"));
    }

    @Test
    void baselineOrderPreserved() {
        ChipListBinding binding = new ChipListBinding(List.of("c", "a", "b"));
        assertEquals(List.of("c", "a", "b"), List.copyOf(binding.items()));
    }
}
