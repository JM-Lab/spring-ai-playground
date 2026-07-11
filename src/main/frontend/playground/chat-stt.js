const ICON_MIC = 'vaadin:microphone';
const ICON_STOP = 'vaadin:stop';

const TARGET_SAMPLE_RATE = 16000;
const SILENCE_THRESHOLD_RMS = 0.012;
const SILENCE_TAIL_MS = 600;
const POLL_INTERVAL_MS = 100;
const VOICE_TRIGGER_MS = 250;
const WARMUP_MS = 350;
const COUNTDOWN_TICK_MS = 100;
const COUNTDOWN_VISIBLE_BELOW_MS = 1000;
const WORKLET_PROCESSOR_NAME = 'spring-ai-playground-stt-capture';

let activeMode = null;
let activeButton = null;
let activeTextArea = null;

let speechRecognition = null;
let webSpeechSilenceTimer = null;
let lastWebSpeechResultAt = 0;

let audioContext = null;
let mediaStream = null;
let captureSource = null;
let captureWorklet = null;
let captureChunks = [];
let captureSampleRate = 0;
let recordingStartedAt = 0;
let lastVoiceAt = 0;
let voiceMsAccumulated = 0;
let pollTimer = null;
let uploadInFlight = false;
let lastStreamedText = '';

let countdownTimer = null;
let countdownPie = null;

let captureSessionToken = 0;

window.STTModule = {
    isRecording() {
        if (activeMode === 'webspeech') return speechRecognition !== null;
        if (activeMode === 'local') return captureWorklet !== null;
        return false;
    },

    async start(textAreaId, buttonId, timeoutSec = 3) {
        const textArea = document.getElementById(textAreaId);
        const button = document.getElementById(buttonId);
        if (!textArea || !button) {
            console.warn('[stt] textArea or button missing', textAreaId, buttonId);
            return;
        }
        const mode = button.dataset.sttMode || 'local';
        if (mode === 'webspeech') {
            await startWebSpeech(textArea, button, timeoutSec);
        } else {
            try {
                await startLocal(textArea, button, timeoutSec);
            } catch (e) {
                console.warn('[stt] startLocal threw', e);
                try { await teardownLocalCaptureSafely(); } catch (_) {}
                reportError(button, `Voice input failed to start: ${e.message || e}`);
            }
        }
    },

    stop() {
        if (activeMode === 'webspeech') {
            if (speechRecognition) speechRecognition.stop();
        } else if (activeMode === 'local') {
            stopLocalCapture();
        }
    },

    async toggle(textAreaId, buttonId, timeoutSec = 3) {
        if (this.isRecording()) this.stop();
        else await this.start(textAreaId, buttonId, timeoutSec);
    }
};

function trackVoiceInput(mode) {
    if (window.gtag) window.gtag('event', 'voice_input_used', { mode });
}

async function startWebSpeech(textArea, button, timeoutSec) {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
        reportError(button, 'This browser does not support Web Speech API.');
        return;
    }

    activeMode = 'webspeech';
    activeButton = button;
    activeTextArea = textArea;
    lastWebSpeechResultAt = Date.now();

    speechRecognition = new SpeechRecognition();
    speechRecognition.lang = navigator.language || 'en-US';
    speechRecognition.continuous = true;
    speechRecognition.interimResults = true;

    speechRecognition.onresult = (event) => {
        let transcript = '';
        for (let i = 0; i < event.results.length; i++) transcript += event.results[i][0].transcript;
        textArea.value = transcript;
        textArea.dispatchEvent(new Event('change'));
        lastWebSpeechResultAt = Date.now();
        resetWebSpeechSilenceTimer(timeoutSec);
    };
    speechRecognition.onstart = () => {
        trackVoiceInput('webspeech');
        setButtonState(button, 'recording');
        lastWebSpeechResultAt = Date.now();
        resetWebSpeechSilenceTimer(timeoutSec);
        startCountdownDisplay(button, () => lastWebSpeechResultAt, timeoutSec * 1000);
    };
    speechRecognition.onerror = (event) => {
        reportError(button, `Voice recognition error: ${event.error || 'unknown'}`);
    };
    speechRecognition.onend = () => {
        stopCountdownDisplay();
        setButtonState(button, 'idle');
        clearTimeout(webSpeechSilenceTimer);
        webSpeechSilenceTimer = null;
        speechRecognition = null;
        activeMode = null;
        textArea.focus();
        textArea.selectionStart = textArea.selectionEnd = textArea.value.length;
    };

    try {
        speechRecognition.start();
    } catch (e) {
        speechRecognition = null;
        activeMode = null;
        reportError(button, `Voice recognition could not start: ${e.message}`);
    }
}

function resetWebSpeechSilenceTimer(timeoutSec) {
    clearTimeout(webSpeechSilenceTimer);
    webSpeechSilenceTimer = setTimeout(() => {
        if (speechRecognition) speechRecognition.stop();
    }, timeoutSec * 1000);
}

async function startLocal(textArea, button, timeoutSec) {
    if (!isElectron()) {
        reportError(button, 'Voice input requires Chrome browser or the Spring AI Playground desktop app.');
        return;
    }
    if (window.electronAPI.platform === 'darwin' && window.electronAPI.arch === 'x64') {
        reportError(button, 'Voice input is not supported on Intel Mac yet. '
                + 'Please use an Apple Silicon Mac, or open this app in Chrome browser.');
        return;
    }
    if (!('mediaDevices' in navigator) || !navigator.mediaDevices.getUserMedia) {
        reportError(button, 'Microphone API is not available in this browser.');
        return;
    }
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx || !AudioCtx.prototype || !('audioWorklet' in AudioCtx.prototype)) {
        reportError(button, 'AudioWorklet API is not available in this browser.');
        return;
    }

    let stream;
    try {
        stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (e) {
        reportError(button, `Microphone permission denied or unavailable (${e.name || 'Error'}).`);
        return;
    }

    activeMode = 'local';
    activeButton = button;
    activeTextArea = textArea;
    captureChunks = [];
    voiceMsAccumulated = 0;
    mediaStream = stream;
    recordingStartedAt = Date.now();
    lastVoiceAt = Date.now();
    uploadInFlight = false;
    lastStreamedText = '';
    captureSessionToken++;

    audioContext = new AudioCtx();
    captureSampleRate = audioContext.sampleRate;

    const workletSource = `
        class SttCaptureProcessor extends AudioWorkletProcessor {
            process(inputs) {
                const input = inputs[0];
                if (input && input[0] && input[0].length) {
                    this.port.postMessage(input[0].slice());
                }
                return true;
            }
        }
        registerProcessor('${WORKLET_PROCESSOR_NAME}', SttCaptureProcessor);
    `;
    const moduleUrl = URL.createObjectURL(new Blob([workletSource], { type: 'application/javascript' }));
    try {
        await audioContext.audioWorklet.addModule(moduleUrl);
    } finally {
        URL.revokeObjectURL(moduleUrl);
    }

    captureSource = audioContext.createMediaStreamSource(stream);
    captureWorklet = new AudioWorkletNode(audioContext, WORKLET_PROCESSOR_NAME);
    captureWorklet.port.onmessage = (event) => {
        const samples = event.data;
        if (!samples || !samples.length) return;
        captureChunks.push(samples);
        let sum = 0;
        for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
        const rms = Math.sqrt(sum / samples.length);
        if (rms > SILENCE_THRESHOLD_RMS) {
            lastVoiceAt = Date.now();
            voiceMsAccumulated += (samples.length * 1000) / captureSampleRate;
        }
    };
    captureSource.connect(captureWorklet);
    trackVoiceInput('local-whisper');
    setButtonState(button, 'recording');

    const silenceTimeoutMs = Math.max(1000, timeoutSec * 1000);
    pollTimer = setInterval(() => {
        const now = Date.now();
        const recordingDuration = now - recordingStartedAt;
        if (recordingDuration > Math.max(SILENCE_TAIL_MS, WARMUP_MS)
                && now - lastVoiceAt >= silenceTimeoutMs) {
            stopLocalCapture();
            return;
        }
        if (uploadInFlight) return;
        if (recordingDuration < WARMUP_MS) return;
        if (voiceMsAccumulated < VOICE_TRIGGER_MS) return;
        voiceMsAccumulated = 0;
        sendGrowingWindow();
    }, POLL_INTERVAL_MS);

    startCountdownDisplay(button, () => lastVoiceAt, silenceTimeoutMs);
}

function stopLocalCapture() {
    const button = activeButton;
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    stopCountdownDisplay();
    teardownLocalCaptureSafely();

    captureChunks = [];
    voiceMsAccumulated = 0;
    activeMode = null;
    activeButton = null;
    activeTextArea = null;
    if (button) setButtonState(button, 'idle');
}

async function teardownLocalCaptureSafely() {
    try {
        if (captureWorklet) {
            captureWorklet.port.onmessage = null;
            try { captureWorklet.disconnect(); } catch (_) {}
            captureWorklet = null;
        }
        if (captureSource) {
            try { captureSource.disconnect(); } catch (_) {}
            captureSource = null;
        }
        if (mediaStream) {
            mediaStream.getTracks().forEach((t) => { try { t.stop(); } catch (_) {} });
            mediaStream = null;
        }
        const ctx = audioContext;
        audioContext = null;
        if (ctx) { try { await ctx.close(); } catch (_) {} }
    } catch (e) {
        console.warn('[stt] teardownLocalCaptureSafely error', e);
    }
}

async function sendGrowingWindow() {
    if (uploadInFlight) return;
    if (captureChunks.length === 0) return;
    uploadInFlight = true;
    const textArea = activeTextArea;
    const sessionAtStart = captureSessionToken;
    const chunksSnapshot = captureChunks.slice();
    const sampleRateSnapshot = captureSampleRate;
    try {
        const merged = mergeFloat32Chunks(chunksSnapshot);
        const samples16k = await resampleToMono16k(merged, sampleRateSnapshot);
        const result = await window.electronAPI.invoke('stt:transcribe', {
            samples: samples16k,
            language: 'auto',
        });
        if (!result || !result.ok) {
            throw new Error(result && (result.message || result.code) ? `${result.code || 'STT_ERROR'}: ${result.message || ''}` : 'unknown error');
        }
        const text = (result.text || '').trim();
        if (text && textArea && captureSessionToken === sessionAtStart) {
            replaceStreamingText(textArea, text);
        }
    } catch (e) {
        console.warn('[stt] partial upload failed (will retry on next interval)', e);
    } finally {
        uploadInFlight = false;
    }
}

function isElectron() {
    return typeof window !== 'undefined'
            && window.electronAPI
            && typeof window.electronAPI.invoke === 'function';
}

function replaceStreamingText(textArea, transcribedText) {
    const currentValue = (typeof textArea.value === 'string') ? textArea.value : '';
    let baseValue;
    if (lastStreamedText && currentValue.endsWith(lastStreamedText)) {
        baseValue = currentValue.slice(0, currentValue.length - lastStreamedText.length);
    } else if (lastStreamedText === '') {
        baseValue = currentValue;
    } else {
        baseValue = currentValue + (currentValue.endsWith(' ') || currentValue.length === 0 ? '' : ' ');
        lastStreamedText = '';
    }
    const separator = baseValue.length === 0 || baseValue.endsWith(' ') ? '' : ' ';
    const merged = baseValue + separator + transcribedText;
    setTextAreaValue(textArea, merged);
    lastStreamedText = (separator + transcribedText);
}

function setTextAreaValue(textArea, value) {
    try { textArea.value = value; } catch (_) {}
    const innerTextArea = (textArea.shadowRoot && textArea.shadowRoot.querySelector('textarea'))
            || textArea.querySelector('textarea');
    if (innerTextArea) {
        innerTextArea.value = value;
        innerTextArea.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
        innerTextArea.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
    }
    textArea.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
    textArea.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
    if (typeof textArea.focus === 'function') textArea.focus();
    if (innerTextArea && typeof innerTextArea.setSelectionRange === 'function') {
        const end = innerTextArea.value.length;
        try { innerTextArea.setSelectionRange(end, end); } catch (_) {}
    }
}

function setButtonState(button, state) {
    if (!button) return;
    button.dataset.sttState = state;
    const icon = micIconOf(button);
    if (icon) icon.setAttribute('icon', state === 'recording' ? ICON_STOP : ICON_MIC);
}

function reportError(button, message) {
    setButtonState(button, 'error');
    if (button) {
        button.dispatchEvent(new CustomEvent('stt-error', { detail: { message }, bubbles: false }));
    } else {
        console.warn('[stt]', message);
    }
}

function startCountdownDisplay(button, getLastActivityAt, timeoutMs) {
    if (!button) return;
    stopCountdownDisplay();
    const icon = micIconOf(button);
    if (!icon) return;

    button.style.position = button.style.position || 'relative';
    countdownPie = document.createElement('span');
    countdownPie.className = 'stt-silence-pie';
    countdownPie.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);'
            + 'width:var(--lumo-icon-size-m,1.5em);height:var(--lumo-icon-size-m,1.5em);'
            + 'pointer-events:none;display:none;';
    button.appendChild(countdownPie);

    countdownTimer = setInterval(() => {
        if (!countdownPie || !button.isConnected) { stopCountdownDisplay(); return; }
        const remaining = timeoutMs - (Date.now() - getLastActivityAt());
        if (remaining <= 0 || remaining >= timeoutMs - COUNTDOWN_VISIBLE_BELOW_MS) {
            countdownPie.style.display = 'none';
            icon.style.visibility = '';
            return;
        }
        const angle = ((1 - remaining / timeoutMs) * 360).toFixed(1);
        countdownPie.style.background = `conic-gradient(from -90deg,`
                + ` var(--lumo-primary-color, #1676f3) 0deg ${angle}deg,`
                + ` var(--lumo-contrast-50pct, #888) ${angle}deg 360deg)`;
        countdownPie.style.display = '';
        icon.style.visibility = 'hidden';
    }, COUNTDOWN_TICK_MS);
}

function stopCountdownDisplay() {
    if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
    if (countdownPie) {
        try { countdownPie.remove(); } catch (_) {}
        countdownPie = null;
    }
    if (activeButton) {
        const icon = micIconOf(activeButton);
        if (icon) icon.style.visibility = '';
    }
}

function micIconOf(button) {
    return button ? button.querySelector('vaadin-icon, iron-icon') || button.firstElementChild : null;
}

function mergeFloat32Chunks(chunks) {
    let totalLen = 0;
    for (const c of chunks) totalLen += c.length;
    const merged = new Float32Array(totalLen);
    let offset = 0;
    for (const c of chunks) { merged.set(c, offset); offset += c.length; }
    return merged;
}

async function resampleToMono16k(samples, sourceRate) {
    if (sourceRate === TARGET_SAMPLE_RATE) return samples;
    const targetLen = Math.max(1, Math.floor(samples.length * TARGET_SAMPLE_RATE / sourceRate));
    const offline = new OfflineAudioContext(1, targetLen, TARGET_SAMPLE_RATE);
    const inBuffer = offline.createBuffer(1, samples.length, sourceRate);
    inBuffer.copyToChannel(samples, 0);
    const src = offline.createBufferSource();
    src.buffer = inBuffer;
    src.connect(offline.destination);
    src.start(0);
    const rendered = await offline.startRendering();
    return rendered.getChannelData(0).slice();
}
