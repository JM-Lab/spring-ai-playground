// Replace <VERSION> tokens inside <code>/<pre> blocks with the latest release
// tag fetched from GitHub. Used by the Verify Your Download section; benign
// no-op on pages without the placeholder. If the fetch fails, the placeholder
// stays and the in-page instructions tell the reader to substitute manually.
(function () {
  fetch('https://api.github.com/repos/spring-ai-community/spring-ai-playground/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then(function (response) {
      if (!response.ok) throw new Error('release fetch failed');
      return response.json();
    })
    .then(function (release) {
      var version = (release.tag_name || '').replace(/^v/, '');
      if (!version) return;
      document.querySelectorAll('code, pre').forEach(function (el) {
        if (el.textContent.indexOf('<VERSION>') !== -1) {
          el.textContent = el.textContent.replace(/<VERSION>/g, version);
        }
      });
    })
    .catch(function () {
      // keep <VERSION> placeholders so readers can substitute manually
    });
})();
