window.pwaInstall = {
    deferredPrompt: null,
};

function showPwaInstallPopup() {
    if (document.getElementById('pwa-install-popup')) {
        return;
    }

    const popup = document.createElement('div');
    popup.id = 'pwa-install-popup';

    const textContainer = document.createElement('div');

    const title = document.createElement('h3');
    title.innerText = 'Install Spring AI Playground';

    const text = document.createElement('p');
    text.innerText = 'Add this web app to your home screen for a better experience.';

    const buttonContainer = document.createElement('div');

    const installButton = document.createElement('button');
    installButton.id = 'pwa-install-button';
    installButton.innerText = 'Install';

    const dismissButton = document.createElement('button');
    dismissButton.id = 'pwa-dismiss-button';
    dismissButton.innerText = 'Later';

    Object.assign(popup.style, {
        position: 'fixed',
        top: '20px',
        left: '50%',
        transform: 'translateX(-50%)',
        zIndex: '10000',
        backgroundColor: 'hsl(214, 90%, 52%)',
        color: 'white',
        padding: '16px 24px',
        borderRadius: '12px',
        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.2)',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        maxWidth: '90%',
        width: '550px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '24px',
    });

    Object.assign(title.style, {
        margin: '0 0 4px 0',
        fontSize: '1em',
        lineHeight: '1.2',
    });

    Object.assign(text.style, {
        margin: '0',
        opacity: '0.9',
        fontSize: '0.9em',
    });

    Object.assign(buttonContainer.style, {
        display: 'flex',
        gap: '12px',
        flexShrink: '0',
    });

    Object.assign(installButton.style, {
        backgroundColor: 'white',
        color: 'hsl(214, 90%, 52%)',
        border: 'none',
        padding: '10px 16px',
        borderRadius: '8px',
        cursor: 'pointer',
        fontWeight: '600',
        fontSize: '0.9em',
    });

    Object.assign(dismissButton.style, {
        backgroundColor: 'transparent',
        color: 'white',
        border: '1px solid rgba(255, 255, 255, 0.5)',
        padding: '10px 16px',
        borderRadius: '8px',
        cursor: 'pointer',
        fontSize: '0.9em',
    });

    installButton.addEventListener('click', () => {
        if (window.pwaInstall.deferredPrompt) {
            window.pwaInstall.deferredPrompt.prompt();
        }
        popup.remove();
    });

    dismissButton.addEventListener('click', () => {
        popup.remove();
    });

    textContainer.appendChild(title);
    textContainer.appendChild(text);
    buttonContainer.appendChild(installButton);
    buttonContainer.appendChild(dismissButton);
    popup.appendChild(textContainer);
    popup.appendChild(buttonContainer);
    document.body.appendChild(popup);
}

window.addEventListener('beforeinstallprompt', (e) => {
    console.log('`beforeinstallprompt` event fired. Preparing JS popup.');
    e.preventDefault();
    window.pwaInstall.deferredPrompt = e;
    showPwaInstallPopup();
});
