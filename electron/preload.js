'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const ALLOWED_INVOKE = new Set([
  'config:load', 'config:save', 'config:save-as', 'config:select',
  'config:select-provider', 'config:export', 'config:import', 'config:reset',
  'config:delete', 'config:launch',
  'config:get-tools-preset', 'config:set-tools-preference', 'config:get-tools-curation-data',
  'config:environment-info', 'config:open-ollama-download', 'config:fit-window',
  'ollama-manager:open',
  'app:launch-state', 'app:restart-to-config', 'app:quit-launcher', 'app:set-auto-copy-logs',
  'app:retry-launch-readiness', 'app:start-without-secrets',
  'stt:status', 'stt:download-model', 'stt:cancel-download', 'stt:open-folder',
  'stt:set-preferred-model', 'stt:set-enabled', 'stt:transcribe',
  'calendar:open-ics',
]);

const ALLOWED_ON = new Set([
  'server-log',
  'server-error',
  'launch-state',
  'stt:download-progress',
  'stt:download-complete',
  'stt:download-error',
]);

contextBridge.exposeInMainWorld('electronAPI', {
  invoke: (channel, ...args) => {
    if (!ALLOWED_INVOKE.has(channel)) {
      throw new Error(`Blocked IPC channel: ${channel}`);
    }

    return ipcRenderer.invoke(channel, ...args);
  },
  on: (channel, listener) => {
    if (!ALLOWED_ON.has(channel)) {
      throw new Error(`Blocked IPC listen channel: ${channel}`);
    }

    const wrapped = (_event, ...args) => listener(...args);
    ipcRenderer.on(channel, wrapped);
    return () => ipcRenderer.removeListener(channel, wrapped);
  },
  platform: process.platform,
  arch: process.arch,
});
