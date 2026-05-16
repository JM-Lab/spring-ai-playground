(function () {
  function wire() {
    document.querySelectorAll(".md-typeset .grid.cards > ul > li, .md-typeset .grid.cards > .card").forEach(function (card) {
      if (card.dataset.cardWired === "1") return;
      var titleLink = card.querySelector("p:first-child a[href]");
      if (!titleLink) return;
      card.dataset.cardWired = "1";
      card.addEventListener("click", function (e) {
        var clickedLink = e.target.closest("a");
        if (clickedLink) return;
        if (window.getSelection && String(window.getSelection())) return;
        var href = titleLink.getAttribute("href");
        if (!href) return;
        var target = titleLink.getAttribute("target");
        if (target === "_blank" || e.metaKey || e.ctrlKey) {
          window.open(titleLink.href, "_blank", "noopener");
        } else {
          window.location.assign(titleLink.href);
        }
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", wire);
  } else {
    wire();
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(wire);
  }
})();
