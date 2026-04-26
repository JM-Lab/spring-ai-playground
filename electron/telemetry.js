(function () {
  var params = new URLSearchParams(window.location.search);
  if (params.get('telemetry') === '0') return;

  var s = document.createElement('script');
  s.async = true;
  s.src = 'https://www.googletagmanager.com/gtag/js?id=G-52TGT1G9B3';
  document.head.appendChild(s);

  window.dataLayer = window.dataLayer || [];
  function gtag() { window.dataLayer.push(arguments); }
  window.gtag = gtag;
  gtag('js', new Date());
  gtag('set', 'user_properties', { app_surface: 'desktop-launcher' });
  gtag('config', 'G-52TGT1G9B3', {
    app_name: 'spring-ai-playground-desktop-launcher',
    app_surface: 'desktop-launcher'
  });
})();
