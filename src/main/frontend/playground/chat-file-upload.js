import * as XLSX from 'xlsx';

const SPREADSHEET = /\.(xlsx|xls|xlsm|xlsb|ods)$/i;
const TEXT_LIKE = /\.(csv|tsv|txt|json|md|markdown|log|xml|ya?ml|html?|svg)$/i;
const MAX_BYTES = 10 * 1024 * 1024;

function bytesToBase64(bytes) {
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

class ChatFileUpload extends HTMLElement {
  connectedCallback() {
    if (this._built) return;
    this._built = true;
    const accept = this.getAttribute('accept-types') || '';
    this.innerHTML = `
      <div class="cfu-drop">
        <input type="file" ${accept ? `accept="${accept}"` : ''} />
        <div class="cfu-hint">Drop a file here, or click to choose</div>
      </div>
      <div class="cfu-status" aria-live="polite"></div>`;
    this._input = this.querySelector('input');
    this._drop = this.querySelector('.cfu-drop');
    this._statusEl = this.querySelector('.cfu-status');
    this._input.addEventListener('change', (e) => this._handle(e.target.files && e.target.files[0]));
    ['dragover', 'dragenter'].forEach((t) => this._drop.addEventListener(t, (e) => {
      e.preventDefault();
      this._drop.classList.add('cfu-over');
    }));
    ['dragleave', 'dragend', 'drop'].forEach((t) => this._drop.addEventListener(t, () => this._drop.classList.remove('cfu-over')));
    this._drop.addEventListener('drop', (e) => {
      e.preventDefault();
      this._handle(e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]);
    });
  }

  _status(text) {
    if (this._statusEl) this._statusEl.textContent = text || '';
  }

  _busy(busy) {
    if (this._input) this._input.disabled = busy;
    this._drop.classList.toggle('cfu-busy', !!busy);
  }

  async _handle(file) {
    if (!file) return;
    if (file.size > MAX_BYTES) {
      this._status(`"${file.name}" is too large (max ${Math.round(MAX_BYTES / 1024 / 1024)}MB).`);
      return;
    }
    this._busy(true);
    this._status(`Reading "${file.name}"...`);
    try {
      if (SPREADSHEET.test(file.name)) {
        const buffer = await file.arrayBuffer();
        const workbook = XLSX.read(buffer, { type: 'array' });
        const sheet = workbook.Sheets[workbook.SheetNames[0]];
        const csv = XLSX.utils.sheet_to_csv(sheet);
        const csvName = file.name.replace(SPREADSHEET, '.csv');
        this._status(`Converted "${file.name}" to CSV. Uploading...`);
        this.$server.receiveText(csvName, csv, 'text/csv');
      } else if (TEXT_LIKE.test(file.name) || (file.type && file.type.startsWith('text/'))) {
        const text = await file.text();
        this._status(`Uploading "${file.name}"...`);
        this.$server.receiveText(file.name, text, file.type || 'text/plain');
      } else {
        const buffer = await file.arrayBuffer();
        const base64 = bytesToBase64(new Uint8Array(buffer));
        this._status(`Uploading "${file.name}"...`);
        this.$server.receiveBinary(file.name, base64, file.type || 'application/octet-stream');
      }
    } catch (err) {
      this._busy(false);
      const message = (err && err.message) ? err.message : String(err);
      this._status(`Failed to read "${file.name}": ${message}`);
      this.$server.uploadFailed(message);
    }
  }
}

if (!customElements.get('chat-file-upload')) {
  customElements.define('chat-file-upload', ChatFileUpload);
}
