'use strict';

const fs = require('fs');
const https = require('https');
const path = require('path');
const os = require('os');
const { URL } = require('url');

const DEFAULT_MODEL_FILENAME = 'ggml-large-v3-turbo-q5_0.bin';
const DEFAULT_MODEL_URL = `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${DEFAULT_MODEL_FILENAME}`;
const PREFERRED_MODEL_FILENAME = 'preferred-model.txt';
const ENABLED_FLAG_FILENAME = 'stt-enabled.txt';

let activeDownload = null;
let lastError = null;
let lastLog = '';

function whisperHomeDir() {
  return path.join(os.homedir(), '.spring-ai-playground', 'whisper');
}

function fileExists(p) {
  try { return Boolean(p) && fs.statSync(p).isFile(); } catch { return false; }
}

function modelPathFor(filename) {
  return path.join(whisperHomeDir(), filename);
}

function getPreferredModelFilename() {
  const file = path.join(whisperHomeDir(), PREFERRED_MODEL_FILENAME);
  try {
    const raw = fs.readFileSync(file, 'utf8').trim();
    return raw || null;
  } catch { return null; }
}

function setPreferredModelFilename(filename) {
  if (!filename || typeof filename !== 'string') return;
  fs.mkdirSync(whisperHomeDir(), { recursive: true });
  fs.writeFileSync(path.join(whisperHomeDir(), PREFERRED_MODEL_FILENAME), `${filename}\n`, 'utf8');
}

function isSttEnabled() {
  const file = path.join(whisperHomeDir(), ENABLED_FLAG_FILENAME);
  try {
    const raw = fs.readFileSync(file, 'utf8').trim().toLowerCase();
    return raw === 'true' || raw === '1' || raw === 'yes' || raw === 'on';
  } catch { return false; }
}

function setSttEnabled(enabled) {
  fs.mkdirSync(whisperHomeDir(), { recursive: true });
  fs.writeFileSync(path.join(whisperHomeDir(), ENABLED_FLAG_FILENAME),
      `${enabled ? 'true' : 'false'}\n`, 'utf8');
}

function listInstalledModels() {
  try {
    return fs.readdirSync(whisperHomeDir())
      .filter((name) => /^ggml-.*\.bin$/.test(name))
      .map((name) => {
        const full = path.join(whisperHomeDir(), name);
        let size = 0;
        try { size = fs.statSync(full).size; } catch { }
        return { filename: name, path: full, sizeBytes: size };
      })
      .sort((a, b) => a.filename.localeCompare(b.filename));
  } catch {
    return [];
  }
}

function getInstallationStatus({ modelFilename } = {}) {
  const requested = modelFilename || getPreferredModelFilename() || DEFAULT_MODEL_FILENAME;
  const requestedPath = modelPathFor(requested);
  const requestedSize = fileExists(requestedPath) ? safeSize(requestedPath) : null;
  const installed = listInstalledModels();
  return {
    enabled: isSttEnabled(),
    whisperHome: whisperHomeDir(),
    preferredModelFilename: getPreferredModelFilename(),
    activeModel: {
      filename: requested,
      path: requestedPath,
      downloaded: fileExists(requestedPath),
      sizeBytes: requestedSize,
    },
    installedModels: installed,
    download: activeDownload
      ? {
          modelFilename: activeDownload.modelFilename,
          downloadedBytes: activeDownload.downloadedBytes,
          totalBytes: activeDownload.totalBytes,
          startedAt: activeDownload.startedAt,
        }
      : null,
    lastError,
  };
}

function isDownloading() {
  return activeDownload !== null;
}

function cancelModelDownload() {
  if (!activeDownload) return false;
  activeDownload.canceled = true;
  if (activeDownload.req) {
    try { activeDownload.req.destroy(new Error('canceled by user')); } catch { }
  }
  if (activeDownload.fileStream) {
    try { activeDownload.fileStream.close(); } catch { }
  }
  return true;
}

function downloadModel({ url, dest, onProgress = () => {}, onLog = () => {} } = {}) {
  if (activeDownload) return Promise.reject(new Error('A download is already in progress.'));
  fs.mkdirSync(whisperHomeDir(), { recursive: true });
  const downloadUrl = url || DEFAULT_MODEL_URL;
  const filename = (dest && path.basename(dest)) || filenameFromUrl(downloadUrl) || DEFAULT_MODEL_FILENAME;
  const finalPath = dest || path.join(whisperHomeDir(), filename);
  const partialPath = `${finalPath}.partial`;
  const startBytes = fileExists(partialPath) ? fs.statSync(partialPath).size : 0;

  return new Promise((resolve, reject) => {
    activeDownload = {
      url: downloadUrl,
      finalPath,
      partialPath,
      modelFilename: filename,
      downloadedBytes: startBytes,
      totalBytes: null,
      startedAt: Date.now(),
      canceled: false,
      req: null,
      fileStream: null,
    };
    const cleanup = () => { activeDownload = null; };

    const followRedirect = (currentUrl, redirectsLeft) => {
      if (redirectsLeft < 0) {
        cleanup();
        reject(new Error('Too many redirects'));
        return;
      }
      const parsed = new URL(currentUrl);
      const headers = startBytes > 0 ? { Range: `bytes=${startBytes}-` } : {};
      onLog(`[whisper] download GET ${parsed.host}${parsed.pathname} (resume from ${startBytes})`);
      const req = https.get({
        protocol: parsed.protocol,
        hostname: parsed.hostname,
        port: parsed.port || 443,
        path: parsed.pathname + parsed.search,
        headers,
      }, (res) => {
        if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
          const next = new URL(res.headers.location, currentUrl).toString();
          res.resume();
          followRedirect(next, redirectsLeft - 1);
          return;
        }
        if (res.statusCode !== 200 && res.statusCode !== 206) {
          res.resume();
          cleanup();
          reject(new Error(`Download HTTP ${res.statusCode}`));
          return;
        }
        const contentLength = Number(res.headers['content-length']) || null;
        const totalFromRange = res.headers['content-range']
          ? Number(String(res.headers['content-range']).split('/').pop())
          : null;
        if (activeDownload) {
          activeDownload.totalBytes = totalFromRange || (contentLength
            ? contentLength + (res.statusCode === 206 ? startBytes : 0)
            : null);
        }
        const fileStream = fs.createWriteStream(partialPath, { flags: 'a' });
        if (activeDownload) activeDownload.fileStream = fileStream;
        let lastEmit = 0;

        res.on('data', (chunk) => {
          if (!activeDownload) return;
          activeDownload.downloadedBytes += chunk.length;
          const now = Date.now();
          if (now - lastEmit > 250) {
            lastEmit = now;
            onProgress({
              downloadedBytes: activeDownload.downloadedBytes,
              totalBytes: activeDownload.totalBytes,
            });
          }
        });
        res.on('error', (err) => {
          fileStream.close();
          cleanup();
          reject(err);
        });
        fileStream.on('finish', () => {
          if (activeDownload && activeDownload.canceled) {
            cleanup();
            reject(new Error('canceled'));
            return;
          }
          try {
            fs.renameSync(partialPath, finalPath);
            const size = fs.statSync(finalPath).size;
            setPreferredModelFilename(path.basename(finalPath));
            onLog(`[whisper] download complete: ${finalPath} (${size} bytes); preferred-model.txt updated`);
            cleanup();
            resolve({ ok: true, path: finalPath, size });
          } catch (e) {
            cleanup();
            reject(e);
          }
        });
        fileStream.on('error', (err) => {
          cleanup();
          reject(err);
        });
        res.pipe(fileStream);
      });
      req.on('error', (err) => {
        cleanup();
        reject(err);
      });
      if (activeDownload) activeDownload.req = req;
    };

    followRedirect(downloadUrl, 5);
  });
}

function filenameFromUrl(url) {
  try {
    const u = new URL(url);
    const name = path.basename(u.pathname);
    return name && /\.bin$/.test(name) ? name : null;
  } catch { return null; }
}

function safeSize(p) {
  try { return fs.statSync(p).size; } catch { return null; }
}

function getRecentLog(maxBytes = 16 * 1024) {
  return lastLog.slice(-maxBytes);
}

module.exports = {
  getInstallationStatus,
  downloadModel,
  cancelModelDownload,
  isDownloading,
  getRecentLog,
  whisperHomeDir,
  getPreferredModelFilename,
  setPreferredModelFilename,
  isSttEnabled,
  setSttEnabled,
  DEFAULT_MODEL_FILENAME,
  DEFAULT_MODEL_URL,
};
