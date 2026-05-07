window.pwaInstall = {
    deferredPrompt: null,
};

window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    window.pwaInstall.deferredPrompt = e;
});
