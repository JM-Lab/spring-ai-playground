'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const ALLOWED_INVOKE = new Set([
  'ollama-manager:get-context',
  'ollama-manager:get-download-queue',
  'ollama-manager:fit-window',
  'ollama-manager:request-close',
  'ollama-manager:enqueue-pull',
  'ollama-manager:cancel-download',
  'ollama-manager:list-installed',
  'ollama-manager:delete',
  'ollama-manager:copy',
  'ollama-manager:open-external',
  'config:open-ollama-download',
]);

const ALLOWED_ON = new Set([
  'ollama-manager:pull-progress',
  'ollama-manager:download-updated',
]);

contextBridge.exposeInMainWorld('ollamaManagerAPI', {
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
