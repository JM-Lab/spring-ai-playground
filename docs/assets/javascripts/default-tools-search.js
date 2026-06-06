(function () {
  // Preset is single-select (mutually exclusive - only one preset can be
  // active at a time). Tag, Category, Transport are multi-select. Within
  // tag/category a card matches if ANY selected value matches (OR); transport
  // is AND because each card has exactly one transport. Search is AND with
  // every other filter; preset is exclusive.
  const MULTI_GROUPS = new Set(['tag', 'category', 'transport']);

  function initDirectory(root) {
    const search = root.querySelector('.tool-directory__search');
    // Click delegation target: include the preset top-section AND any
    // .tool-directory__chips wrapper (tag, category, future groups).
    const chipGroups = root.querySelectorAll('.tool-directory__preset, .tool-directory__chips');
    const list = root.querySelector('.tool-directory__list');
    const countEl = root.querySelector('.tool-directory__count');
    if (!list) return;

    const rows = Array.from(list.querySelectorAll('.tcg-card--directory'));
    const filterState = {
      q: '',
      preset: null,            // single
      tag: new Set(),          // multi
      category: new Set(),     // multi
      transport: new Set(),    // multi (MCP catalog only - empty on tools page)
    };

    function matches(row) {
      // Preset is the exclusive mode: when a preset is active, ignore all other
      // filters and show only the tools in that preset.
      // 'everything' is a synthetic preset = show every card (no curation).
      if (filterState.preset) {
        if (filterState.preset === 'everything') return true;
        const presets = (row.dataset.preset || '').split(',').map((s) => s.trim());
        return presets.includes(filterState.preset);
      }
      // Otherwise: search (AND) + (tag-selected OR category-selected).
      if (filterState.q) {
        const q = filterState.q.toLowerCase();
        const name = (row.dataset.name || '').toLowerCase();
        const desc = (row.dataset.desc || '').toLowerCase();
        if (!name.includes(q) && !desc.includes(q)) return false;
      }
      const tagSel = filterState.tag.size > 0;
      const catSel = filterState.category.size > 0;
      if (tagSel || catSel) {
        const tags = (row.dataset.tags || '').split(',').map((s) => s.trim());
        const cat = (row.dataset.category || '').toLowerCase();
        let any = false;
        if (tagSel) {
          for (const t of filterState.tag) {
            if (tags.includes(t)) { any = true; break; }
          }
        }
        if (!any && catSel) {
          for (const c of filterState.category) {
            if (cat === String(c).toLowerCase()) { any = true; break; }
          }
        }
        if (!any) return false;
      }
      // Transport is an independent AND - each card has exactly one transport,
      // so any selection narrows the visible set to that subset.
      if (filterState.transport.size > 0) {
        const tr = (row.dataset.transport || '').toLowerCase();
        if (!filterState.transport.has(tr)) return false;
      }
      return true;
    }

    function clearNonPresetFilters() {
      filterState.q = '';
      filterState.tag.clear();
      filterState.category.clear();
      filterState.transport.clear();
      if (search) search.value = '';
      refreshGroup('tag');
      refreshGroup('category');
      refreshGroup('transport');
    }
    function clearPreset() {
      if (filterState.preset !== null) {
        filterState.preset = null;
        refreshGroup('preset');
      }
    }

    function apply() {
      let visible = 0;
      for (const row of rows) {
        const show = matches(row);
        row.hidden = !show;
        if (show) visible++;
      }
      if (countEl) {
        countEl.textContent = `Showing ${visible} of ${rows.length} tools`;
      }
    }

    function isPressed(btn) {
      const group = btn.dataset.group;
      const value = btn.dataset.value;
      if (MULTI_GROUPS.has(group)) return filterState[group].has(value);
      return filterState[group] === value;
    }

    function refreshGroup(group) {
      // Look across ALL chip groups in the directory (not just one parent),
      // since preset / tag / category each live in their own .tool-directory__chips
      // wrapper.
      const groupBtns = root.querySelectorAll(`.tool-directory__chip[data-group="${group}"]`);
      groupBtns.forEach((b) => b.setAttribute('aria-pressed', isPressed(b) ? 'true' : 'false'));
    }

    if (search) {
      search.addEventListener('input', (e) => {
        filterState.q = e.target.value || '';
        // Search and tag/category are part of the non-preset mode - typing
        // exits any active preset.
        if (filterState.q) clearPreset();
        apply();
      });
    }

    // Attach a click handler to EVERY chip-group wrapper so preset, tag, and
    // category all toggle correctly. Preset is exclusive: turning a preset on
    // wipes search/tag/category; turning a tag/category on wipes preset.
    chipGroups.forEach((chipsEl) => {
      chipsEl.addEventListener('click', (e) => {
        const btn = e.target.closest('.tool-directory__chip');
        if (!btn) return;
        const group = btn.dataset.group;
        const value = btn.dataset.value;
        if (!group) return;

        if (group === 'preset') {
          // single-select + exclusive
          if (filterState.preset === value) {
            filterState.preset = null;
          } else {
            filterState.preset = value;
            clearNonPresetFilters();
          }
          refreshGroup('preset');
        } else if (MULTI_GROUPS.has(group)) {
          // multi-select; also drop any active preset
          clearPreset();
          const set = filterState[group];
          if (set.has(value)) set.delete(value);
          else set.add(value);
          refreshGroup(group);
        } else {
          // any other single-select group (none today, future-proof)
          clearPreset();
          filterState[group] = (filterState[group] === value) ? null : value;
          refreshGroup(group);
        }
        apply();
      });
    });

    apply();
  }

  // ===== Sub-page card inline-row expand =====
  // Click a card -> insert a full-grid-row detail panel right after it. The
  // grid uses align-items: start so the row 1 cards keep their own heights
  // (no stretching). The next cards reflow into row 3+ naturally.
  // scrollIntoView({block:'start'}) parks the selected card at viewport top
  // so the user sees row 1 (same-row siblings) + row 2 (detail) together.
  let currentCard = null;
  function closeInline() {
    const existing = document.querySelector('.tcg-detail-row');
    if (existing) existing.remove();
    if (currentCard) {
      currentCard.classList.remove('tcg-card--selected');
      currentCard = null;
    }
  }
  function openInline(card) {
    if (currentCard === card) {
      closeInline();
      return;
    }
    closeInline();
    const tmpl = card.querySelector(':scope > .tcg-detail-template');
    if (!tmpl) return;
    const title = card.dataset.toolTitle || card.dataset.toolId || '';
    const row = document.createElement('div');
    row.className = 'tcg-detail-row';
    row.innerHTML =
      '<div class="tcg-detail-row__header">' +
        '<span class="tcg-detail-row__title">' + title + '</span>' +
        '<button class="tcg-detail-row__close" type="button" aria-label="Close">&times;</button>' +
      '</div>' +
      '<div class="tcg-detail-row__content">' + tmpl.innerHTML + '</div>';
    card.insertAdjacentElement('afterend', row);
    card.classList.add('tcg-card--selected');
    currentCard = card;
    row.querySelector('.tcg-detail-row__close').addEventListener('click', closeInline);
    // Scroll so the selected card is just under the sticky header and the
    // detail row right below it claims most of the viewport.
    requestAnimationFrame(() => {
      const headerOffset = 90;
      const top = card.getBoundingClientRect().top + window.pageYOffset - headerOffset;
      window.scrollTo({ top, behavior: 'smooth' });
    });
  }
  function initCardDrawerTriggers() {
    document.querySelectorAll('.tcg-card--clickable').forEach((card) => {
      if (card.dataset.expandWired) return;
      card.dataset.expandWired = '1';
      card.addEventListener('click', (e) => {
        if (e.target.closest('a, button, summary, details')) return;
        openInline(card);
      });
    });
    if (!window.__tcgEscWired) {
      window.__tcgEscWired = true;
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && currentCard) closeInline();
      });
    }
  }

  // Auto-open the card matching window.location.hash when the page loads (or
  // when the hash changes - e.g. user clicks a stretched-link from a directory
  // page that lands on this sub-page with `#<entry-id>`). Without this, the
  // browser only scrolls to the anchor; the detail row stays hidden and the
  // user can't tell whether the navigation arrived.
  function openByHash() {
    if (!window.location.hash || window.location.hash === '#') return;
    let id;
    try { id = decodeURIComponent(window.location.hash.slice(1)); } catch { return; }
    if (!id) return;
    const target = document.getElementById(id);
    if (!target || !target.classList.contains('tcg-card--clickable')) return;
    // Skip if this card is already open (e.g. hashchange fired for the same id).
    if (currentCard === target) return;
    openInline(target);
  }
  if (!window.__tcgHashWired) {
    window.__tcgHashWired = true;
    window.addEventListener('hashchange', openByHash);
  }

  function bootstrap() {
    document.querySelectorAll('.tool-directory').forEach(initDirectory);
    initCardDrawerTriggers();
    // Defer one frame so anchor-scroll settles before we expand + re-scroll.
    requestAnimationFrame(openByHash);
  }

  if (window.document$ && typeof window.document$.subscribe === 'function') {
    window.document$.subscribe(bootstrap);
  } else if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrap);
  } else {
    bootstrap();
  }
})();
