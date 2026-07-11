import { optimize, extractExif } from './chat-image-attach.js';

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

class ChatAttach extends HTMLElement {
  connectedCallback() {
    if (this._input) return;
    this._input = document.createElement('input');
    this._input.type = 'file';
    const accept = this.getAttribute('accept-types');
    if (accept) this._input.accept = accept;
    this._input.multiple = true;
    this._input.hidden = true;
    this.appendChild(this._input);
    this._input.addEventListener('change', () => {
      const files = Array.from(this._input.files || []);
      this._input.value = '';
      files.forEach((file) => this.process(file));
    });
  }

  openPicker() {
    if (this._input) this._input.click();
  }

  bindTo(target) {
    if (!target || target.__chatAttachBound) return;
    target.__chatAttachBound = true;
    target.addEventListener('dragover', (e) => {
      if (Array.from(e.dataTransfer?.types || []).includes('Files')) e.preventDefault();
    });
    target.addEventListener('drop', (e) => {
      const files = Array.from(e.dataTransfer?.files || []);
      if (!files.length) return;
      e.preventDefault();
      files.forEach((file) => this.process(file));
    });
    target.addEventListener('paste', (e) => {
      const files = Array.from(e.clipboardData?.items || [])
        .filter((item) => item.kind === 'file')
        .map((item) => item.getAsFile())
        .filter(Boolean);
      if (!files.length) return;
      e.preventDefault();
      files.forEach((file) => this.process(file));
    });
  }

  async process(file) {
    if (!file) return;
    if (!file.type || !file.type.startsWith('image/')) return;
    if (file.size > MAX_IMAGE_BYTES) {
      this.$server.attachFailed(`"${file.name}" is too large (max ${Math.round(MAX_IMAGE_BYTES / 1024 / 1024)}MB).`);
      return;
    }
    this.$server.imageAttachStarted();
    try {
      const exif = await extractExif(file);
      const { base64, mimeType } = await optimize(file);
      this.$server.receiveImage(file.name, base64, mimeType, exif);
    } catch (err) {
      this.$server.attachFailed((err && err.message) ? err.message : String(err));
    }
  }
}

if (!customElements.get('chat-attach')) {
  customElements.define('chat-attach', ChatAttach);
}
