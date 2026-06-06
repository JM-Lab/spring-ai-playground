// Sidebar nav UX:
//   1. Default: depth-1 nested sections (Tool Studio, MCP Server, Observability)
//      are expanded; depth-2+ sections (Default Tools, AI Usage, AI Stack, ...)
//      stay collapsed until clicked.
//   2. Persistence: any expand/collapse the user performs is remembered in
//      localStorage so navigating to another page keeps their layout.
//   3. The path to the current page is always force-expanded so the active
//      entry is visible - even if the user previously collapsed that section.
//      (Their saved choice for OTHER pages is unaffected; we only override the
//       active branch.)
//
// Section-header click behavior extends Material's native `navigation.indexes`
// split: the section name <a> both navigates to the hub `index.md` AND toggles
// the section open/closed. Clicking a collapsed header opens it (so the new
// page's siblings are immediately visible); clicking an already-open header
// collapses it. The chevron <label> still toggles independently without
// navigating, for the case where the user wants to inspect the sidebar without
// switching pages.
//
// Toggle IDs (__nav_4, __nav_4_3, ...) are deterministic from nav position so
// the same key works on every page.
(function () {
  var STORAGE_KEY = 'sap-nav-toggle-state';

  function loadState() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (e) {
      return {};
    }
  }

  function saveState(state) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) { /* quota / private mode - ignore */ }
  }

  function applyDefaultsOverridesAndActivePath() {
    var state = loadState();

    // 1. Default: open every depth-1 nested item under the per-tab nav.
    document.querySelectorAll(
      '.md-sidebar--primary nav[data-md-level="1"] > .md-nav__list > .md-nav__item--nested > input.md-nav__toggle'
    ).forEach(function (input) {
      input.checked = true;
    });

    // 2. Override with user's saved choices.
    document.querySelectorAll(
      '.md-sidebar--primary input.md-nav__toggle'
    ).forEach(function (input) {
      if (!input.id) return;
      if (Object.prototype.hasOwnProperty.call(state, input.id)) {
        input.checked = !!state[input.id];
      }
    });

    // 3. Force-expand every nested ancestor of the current page so its
    //    sidebar entry is reachable visually. Material marks the current
    //    leaf with `.md-nav__item--active` on the <li>. Walk up to the
    //    primary nav and flip each `.md-nav__item--nested` toggle on.
    document.querySelectorAll(
      '.md-sidebar--primary .md-nav__item--active'
    ).forEach(function (active) {
      var node = active.parentElement;
      while (node && !node.classList.contains('md-sidebar--primary')) {
        if (node.classList && node.classList.contains('md-nav__item') && node.classList.contains('md-nav__item--nested')) {
          var toggle = node.querySelector(':scope > input.md-nav__toggle');
          if (toggle) toggle.checked = true;
        }
        node = node.parentElement;
      }
    });
  }

  function persistOnChange(input) {
    if (input.dataset.sapPersisted === '1') return;
    input.dataset.sapPersisted = '1';
    input.addEventListener('change', function () {
      if (!input.id) return;
      var state = loadState();
      state[input.id] = input.checked;
      saveState(state);
    });
  }

  function toggleOnHubLinkClick(anchor) {
    if (anchor.dataset.sapHubToggle === '1') return;
    anchor.dataset.sapHubToggle = '1';
    anchor.addEventListener('click', function () {
      // Walk up to the nested <li> that this hub link belongs to and flip its
      // toggle. The chevron <label>'s own click is unaffected - Material still
      // toggles the input via the for=... association, so clicking the chevron
      // continues to expand/collapse without navigating.
      var item = anchor.closest('.md-nav__item--nested');
      if (!item) return;
      var toggle = item.querySelector(':scope > input.md-nav__toggle');
      if (!toggle) return;
      toggle.checked = !toggle.checked;
      toggle.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }

  function run() {
    // Suspend Material's .md-nav transitions while we apply state - otherwise
    // setting input.checked here would animate the new sidebar from server
    // state to localStorage state, visible as a 0.25 s scrollbar flicker on
    // every instant-nav swap.
    document.documentElement.classList.remove('md-nav-anim-ready');
    applyDefaultsOverridesAndActivePath();
    document.querySelectorAll(
      '.md-sidebar--primary input.md-nav__toggle'
    ).forEach(persistOnChange);
    document.querySelectorAll(
      '.md-sidebar--primary .md-nav__link.md-nav__container > a'
    ).forEach(toggleOnHubLinkClick);
    // Re-enable transitions two frames later so the just-applied state has
    // settled into a paint before user-triggered toggles can animate again.
    var armed = false;
    function arm() {
      if (armed) return;
      armed = true;
      document.documentElement.classList.add('md-nav-anim-ready');
    }
    requestAnimationFrame(function () { requestAnimationFrame(arm); });
    setTimeout(arm, 200);
  }

  // Material's instant navigation (navigation.instant) swaps the body via XHR
  // instead of reloading the page; subscribe to document$ so our nav UX
  // applies on every swap, not just the first load. Fall back to DOMContentLoaded
  // for environments where instant nav isn't enabled.
  if (typeof window.document$ !== 'undefined' && typeof window.document$.subscribe === 'function') {
    window.document$.subscribe(run);
  } else if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', run);
  } else {
    run();
  }
})();
