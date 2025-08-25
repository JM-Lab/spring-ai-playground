let recognition;
let isRecording = false;
let silenceTimer;

window.STTModule = {
    start: function (textAreaId, buttonId, timeoutSec = 3) {
        if (!('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)) {
            alert("This browser does not support STT.");
            return;
        }

        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        recognition = new SpeechRecognition();
        recognition.lang = navigator.language || "en-US";
        recognition.continuous = true;
        recognition.interimResults = true;

        const textArea = document.getElementById(textAreaId);
        const button = document.getElementById(buttonId);

        recognition.onresult = (event) => {
            let transcript = "";
            for (let i = 0; i < event.results.length; i++) {
                transcript += event.results[i][0].transcript;
            }
            textArea.value = transcript;
            textArea.dispatchEvent(new Event("change"));
            resetSilenceTimer();
        };

        recognition.onstart = () => {
            isRecording = true;
            button.firstElementChild.setAttribute("icon", "vaadin:stop");
            resetSilenceTimer();
        };

        recognition.onend = () => {
            isRecording = false;
            button.firstElementChild.setAttribute("icon", "vaadin:microphone");
            clearTimeout(silenceTimer);
            textArea.focus();
            textArea.selectionStart = textArea.selectionEnd = textArea.value.length;
        };

        recognition.start();

        function resetSilenceTimer() {
            clearTimeout(silenceTimer);
            silenceTimer = setTimeout(() => {
                recognition.stop();
            }, timeoutSec * 1000);
        }
    },

    stop: function () {
        if (recognition && isRecording) {
            recognition.stop();
        }
    },

    toggle: function (textAreaId, buttonId, timeoutSec = 3) {
        if (isRecording) {
            this.stop();
        } else {
            this.start(textAreaId, buttonId, timeoutSec);
        }
    }
};