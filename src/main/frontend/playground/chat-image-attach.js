import exifr from 'exifr';

const MAX_EDGE = 2048;
const JPEG_QUALITY = 0.92;
const MAX_BYTES = 10 * 1024 * 1024;
const EXIF_FIELDS = ['Make', 'Model', 'LensModel', 'DateTimeOriginal', 'FNumber', 'ExposureTime',
  'ISO', 'FocalLength', 'Orientation', 'latitude', 'longitude', 'Software'];

export function bytesToBase64(bytes) {
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

// Extract EXIF from the ORIGINAL file before it is resized (canvas re-encoding strips EXIF).
export async function extractExif(file) {
  try {
    const data = await exifr.parse(file, { gps: true });
    if (!data) return null;
    const picked = {};
    for (const key of EXIF_FIELDS) {
      if (data[key] === undefined || data[key] === null) continue;
      picked[key] = data[key] instanceof Date ? data[key].toISOString() : data[key];
    }
    return Object.keys(picked).length ? JSON.stringify(picked) : null;
  } catch (e) {
    return null;
  }
}

export async function optimize(file) {
  // GIF is kept as-is to preserve animation; vision models see a still frame anyway.
  if (file.type === 'image/gif') {
    const buffer = await file.arrayBuffer();
    return { base64: bytesToBase64(new Uint8Array(buffer)), mimeType: 'image/gif' };
  }
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const w = Math.max(1, Math.round(bitmap.width * scale));
  const h = Math.max(1, Math.round(bitmap.height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  canvas.getContext('2d').drawImage(bitmap, 0, 0, w, h);
  if (bitmap.close) bitmap.close();
  const keepAlpha = file.type === 'image/png' || file.type === 'image/webp';
  const outType = keepAlpha ? 'image/png' : 'image/jpeg';
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, outType, JPEG_QUALITY));
  const buffer = await blob.arrayBuffer();
  return { base64: bytesToBase64(new Uint8Array(buffer)), mimeType: outType };
}

class ChatImageAttach extends HTMLElement {
  connectedCallback() {
    if (this._input) return;
    this._input = document.createElement('input');
    this._input.type = 'file';
    this._input.accept = 'image/*';
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
    if (!target || target.__imageAttachBound) return;
    target.__imageAttachBound = true;
    target.addEventListener('dragover', (e) => {
      if (Array.from(e.dataTransfer?.types || []).includes('Files')) e.preventDefault();
    });
    target.addEventListener('drop', (e) => {
      const images = Array.from(e.dataTransfer?.files || []).filter((f) => f.type.startsWith('image/'));
      if (!images.length) return;
      e.preventDefault();
      images.forEach((file) => this.process(file));
    });
    target.addEventListener('paste', (e) => {
      const images = Array.from(e.clipboardData?.items || [])
        .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
        .map((item) => item.getAsFile())
        .filter(Boolean);
      if (!images.length) return;
      e.preventDefault();
      images.forEach((file) => this.process(file));
    });
  }

  async process(file) {
    if (!file) return;
    if (!file.type || !file.type.startsWith('image/')) {
      this.$server.attachFailed(`"${file.name}" is not an image.`);
      return;
    }
    if (file.size > MAX_BYTES) {
      this.$server.attachFailed(`"${file.name}" is too large (max ${Math.round(MAX_BYTES / 1024 / 1024)}MB).`);
      return;
    }
    try {
      const exif = await extractExif(file);
      const { base64, mimeType } = await optimize(file);
      this.$server.receiveImage(file.name, base64, mimeType, exif);
    } catch (err) {
      this.$server.attachFailed((err && err.message) ? err.message : String(err));
    }
  }
}

if (!customElements.get('chat-image-attach')) {
  customElements.define('chat-image-attach', ChatImageAttach);
}
