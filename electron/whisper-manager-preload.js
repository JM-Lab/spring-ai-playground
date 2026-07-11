'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const ALLOWED_INVOKE = new Set([
  'whisper-manager:get-context',
  'whisper-manager:fit-window',
  'whisper-manager:request-close',
  'stt:status',
  'stt:download-model',
  'stt:cancel-download',
  'stt:open-folder',
  'stt:set-preferred-model',
  'stt:set-enabled',
]);

const ALLOWED_ON = new Set([
  'stt:download-progress',
  'stt:download-complete',
  'stt:download-error',
]);

contextBridge.exposeInMainWorld('whisperManagerAPI', {
  invoke: (channel, ...args) => {
    if (!ALLOWED_INVOKE.has(channel)) throw new Error(`Blocked IPC channel: ${channel}`);
    return ipcRenderer.invoke(channel, ...args);
  },
  on: (channel, listener) => {
    if (!ALLOWED_ON.has(channel)) throw new Error(`Blocked IPC listen channel: ${channel}`);
    const wrapped = (_event, ...args) => listener(...args);
    ipcRenderer.on(channel, wrapped);
    return () => ipcRenderer.removeListener(channel, wrapped);
  },
});
