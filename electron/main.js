'use strict';
const { app, BrowserWindow, ipcMain, dialog, clipboard, screen, shell, safeStorage, systemPreferences, session, } =
require
('electron');
const path = require('path');
const { spawn, execFile } = require('child_process');
const http = require('http');
const treeKill = require('tree-kill');
const fs = require('fs');
const { pathToFileURL } = require('url');
const yaml = require('js-yaml');
const {
  SERVER_READY_TIMEOUT_MS,
  UI_READY_GRACE_TIMEOUT_MS,
  PRELOAD_PATH,
  OLLAMA_MANAGER_PRELOAD_PATH,
  SPLASH_PATH,
  CONFIG_EDITOR_PATH,
  OLLAMA_MANAGER_PATH,
  SERVER_SPLASH_PATH,
  CONFIG_TEMPLATES,
  DEFAULT_STARTER_TEMPLATE_IDS,
  startTempServer,
  OLLAMA_APPLE_SILICON_YAML,
  toMlxModel,
} = require('./launcher-config');
const ollamaManager = require('./ollama-manager');

let tempServer = null;
let mainWindow, splashWindow, serverSplashWindow, configWindow, serverProcess, ollamaManagerWindow;
let dynamicServerPort = null;
let dynamicServerUrl = null;

const TELEMETRY_DISABLED = process.env.SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED === 'false';
const TELEMETRY_QUERY_STRING = TELEMETRY_DISABLED ? '?telemetry=0' : '';
const isDev = !app.isPackaged;
let isQuitting = false;
let allowAppExit = false;
let shutdownPromise = null;
let fullLogBuffer = "";
let activeConfigPath = null;
let currentConfigId = null;
let restartToConfigAfterStop = false;
let currentLaunchToken = 0;
let lastLaunchCommand = '';
let autoCopyLaunchLogsPending = false;
let secretsStoreCache = null;
let secretsEncryptionAvailable = null;

let serverReadyStartTime = 0;
let launchReadinessState = {
  phase: 'idle',
  timedOut: false,
  timeoutMs: null,
  message: 'Preparing the launch environment.',
};

const providerTypeCache = new Map();
let ollamaManagerContext = { yamlText: '', configId: null, configName: '', environmentInfo: null };
let ollamaDownloadQueue = [];
let activeOllamaDownload = null;
let nextOllamaDownloadId = 1;
let allowOllamaManagerWindowClose = false;
let ollamaManagerCloseInProgress = false;

function openMicrophonePrivacySettings() {
  if (process.platform === 'darwin') {
    shell.openExternal('x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone');
    return;
  }

  if (process.platform === 'win32') {
    shell.openExternal('ms-settings:privacy-microphone');
  }
}

async function ensureMacMicrophoneAccess() {
  if (process.platform !== 'darwin') {
    return { status: 'not-applicable', granted: true };
  }

  const status = systemPreferences.getMediaAccessStatus('microphone');
  if (status === 'granted') {
    return { status, granted: true };
  }

  const granted = await systemPreferences.askForMediaAccess('microphone');
  const nextStatus = systemPreferences.getMediaAccessStatus('microphone');
  return { status: nextStatus, granted };
}

function getTemplateByName(name) {
  return Object.values(CONFIG_TEMPLATES).find(item => item.name === name) || null;
}

function getStarterTemplateForProviderType(providerType = 'ollama') {
  return Object.values(CONFIG_TEMPLATES).find(item =>
    DEFAULT_STARTER_TEMPLATE_IDS.has(item.id) && item.providerType === providerType
  ) || CONFIG_TEMPLATES.ollama;
}

function isAppleSilicon() {
  if (process.platform !== 'darwin') return false;
  if (process.arch === 'arm64') return true;
  try {
    return app.runningUnderRosettaTranslation === true;
  } catch {
    return false;
  }
}

function templateYaml(template) {
  if (!template || typeof template.yaml !== 'string') return template?.yaml ?? '';
  if (template.providerType === 'ollama' && isAppleSilicon()) {
    return OLLAMA_APPLE_SILICON_YAML;
  }
  return template.yaml;
}

function shouldCompactToTemplateYaml(yamlText = '') {
  const text = String(yamlText || '');
  return text.includes('system-prompt:') || text.includes('\n---\n') || text.includes('spring.config.activate');
}

function getPreferredTemplateNameForYaml(yamlText = '') {
  const providerType = detectProviderType(yamlText);
  if (providerType === 'openai') return 'OpenAI';
  if (providerType === 'openai-compatible') return 'OpenAI Compatible';
  return 'Ollama';
}


function getFriendlyConfigName(rawName = '', yamlText = '') {
  const preferredName = getPreferredTemplateNameForYaml(yamlText);
  const normalized = sanitizeConfigName(rawName) || preferredName;
  const duplicateMatch = normalized.match(/^(Ollama|OpenAI|OpenAI Compatible)\s+(\d+)$/i);
  if (duplicateMatch) {
    const modelName = extractPrimaryModelName(yamlText);
    if (modelName) return `${preferredName} - ${modelName}`;
    return `${preferredName} Custom ${duplicateMatch[2]}`;
  }
  if (['default', 'test'].includes(normalized.toLowerCase())) return preferredName;
  return normalized;
}

function getConfigsForProviderType(index, providerType = 'ollama') {
  return index.configs.filter(config => getCachedProviderType(config.id) === providerType);
}

function ensureProviderConfig(index, providerType = 'ollama') {
  const existing = getConfigsForProviderType(index, providerType);
  if (existing.length > 0) return existing[0];
  const template = getStarterTemplateForProviderType(providerType);
  const existingIds = new Set(index.configs.map(config => config.id));
  const existingNames = new Set(index.configs.map(config => config.name.toLowerCase()));
  const configId = createUniqueConfigId(template.name, existingIds);
  const configName = createUniqueConfigName(template.name, existingNames);
  fs.writeFileSync(getConfigFilePath(configId), templateYaml(template), 'utf8');
  const created = { id: configId, name: configName };
  index.configs.push(created);
  return created;
}

function extractPlaceholdersFromText(text = '') {
  return [...String(text).matchAll(/\$\{([^}]+)\}/g)].map(match => match[1]);
}

function collectSecretSuggestions(yamlText = '') {
  const suggestions = new Map();
  const providerType = detectProviderType(yamlText);

  for (const placeholder of extractPlaceholdersFromText(yamlText)) {
    let description = 'Referenced by the selected YAML override.';
    if (placeholder === 'OPENAI_API_KEY' && providerType === 'openai') {
      description = 'Needed when this setting uses the OpenAI backend for chat and embedding.';
    } else if (placeholder === 'OPENAI_API_KEY' && providerType === 'openai-compatible') {
      description = 'Needed only if your OpenAI-compatible server expects an API key.';
    }
    suggestions.set(placeholder, { key: placeholder, source: 'profile', profileRequired: true, toolNames: [], description });
  }

  for (const placeholder of extractPlaceholdersFromText(fs.readFileSync(getDefaultConfigPath(), 'utf8'))) {
    if (suggestions.has(placeholder)) continue;
    suggestions.set(placeholder, {
      key: placeholder,
      source: 'application',
      profileRequired: false,
      toolNames: [],
      description: `Available to the bundled Spring configuration as environment variable ${placeholder}.`,
    });
  }

  for (const toolSpecsPath of getDefaultToolSpecsPaths()) {
    if (!fs.existsSync(toolSpecsPath)) continue;
    try {
      const toolSpecs = JSON.parse(fs.readFileSync(toolSpecsPath, 'utf8'));
      for (const tool of toolSpecs) {
        for (const variable of tool.staticVariables || []) {
          for (const value of Object.values(variable)) {
            if (typeof value !== 'string') continue;
            for (const placeholder of extractPlaceholdersFromText(value)) {
              const current = suggestions.get(placeholder) || { key: placeholder, source: 'tool', profileRequired: false, toolNames: [], description: '' };
              current.source = current.profileRequired ? 'profile+tool' : 'tool';
              current.toolNames = [...new Set([...(current.toolNames || []), tool.name])].sort();
              suggestions.set(placeholder, current);
            }
          }
        }
      }
    } catch (error) {
    }
  }

  return [...suggestions.values()]
    .map(item => {
      const toolText = item.toolNames?.length
        ? item.toolNames.length === 1
          ? `Used by tool "${item.toolNames[0]}".`
          : `Used by tools: ${item.toolNames.join(', ')}.`
        : '';
      const whenText = item.profileRequired
        ? 'Used by the currently selected setting when that backend is enabled.'
        : toolText
          ? 'Optional unless you use the related built-in tool.'
          : 'Optional unless you enable the related feature.';
      return { ...item, whenText, toolText };
    })
    .filter(item => item.profileRequired || item.toolNames?.length)
    .sort((a, b) => {
      if (a.profileRequired !== b.profileRequired) return a.profileRequired ? -1 : 1;
      return a.key.localeCompare(b.key);
    });
}

function parseJavaPropertiesToMap(lines = []) {
  const map = {};
  for (const line of lines) {
    const trimmed = String(line).trim().replace(/^-D/, '');
    const separatorIndex = trimmed.indexOf('=');
    if (separatorIndex <= 0) continue;
    map[trimmed.slice(0, separatorIndex)] = trimmed.slice(separatorIndex + 1);
  }
  return map;
}

function getWindowIconPath() {
  return path.join(__dirname, 'static', 'icons', 'icon.png');
}

function getDefaultConfigPath() {
  const packagedPath = path.join(process.resourcesPath, 'default-application.yaml');
  if (!isDev && fs.existsSync(packagedPath)) return packagedPath;
  const preparedResourcePath = path.join(__dirname, 'resources', 'default-application.yaml');
  if (fs.existsSync(preparedResourcePath)) return preparedResourcePath;
  return path.join(__dirname, '..', 'src', 'main', 'resources', 'application.yaml');
}

function getDefaultToolSpecsPath() {
  const paths = getDefaultToolSpecsPaths();
  return paths.length > 0 ? paths[0] : '';
}

function getDefaultToolSpecsPaths() {
  const candidateDirs = [
    !isDev ? path.join(process.resourcesPath, 'catalog') : null,
    path.join(__dirname, 'resources', 'catalog'),
    path.join(__dirname, '..', 'src', 'main', 'resources', 'tool'),
  ].filter(Boolean);
  for (const dir of candidateDirs) {
    if (!fs.existsSync(dir)) continue;
    const matches = fs.readdirSync(dir)
      .filter(f => /^default-tool-specs.*\.json$/.test(f))
      .map(f => path.join(dir, f));
    if (matches.length > 0) return matches;
  }
  return [];
}

function getUserConfigPath() {
  return path.join(app.getPath('userData'), 'application.yaml');
}

function getCatalogDir() {
  const candidates = [
    !isDev ? path.join(process.resourcesPath, 'catalog') : null,
    path.join(__dirname, 'resources', 'catalog'),
    path.join(__dirname, '..', 'src', 'main', 'resources', 'tool'),
  ].filter(Boolean);
  return candidates.find(dir => fs.existsSync(dir)) || null;
}

function getSpringAiPlaygroundHomeDir() {
  return path.join(app.getPath('userData'), 'spring-ai-playground');
}

function getDefaultToolsPreferencePath() {
  return path.join(getSpringAiPlaygroundHomeDir(), 'tool', 'save', 'default-tools-preference.json');
}

function loadPresetCatalog() {
  const catalogDir = getCatalogDir();
  if (!catalogDir) {
    return { presets: [], defaultToolCount: 0 };
  }
  let presets = [];
  const presetsFile = path.join(catalogDir, 'default-tool-presets.json');
  if (fs.existsSync(presetsFile)) {
    try {
      presets = JSON.parse(fs.readFileSync(presetsFile, 'utf8'));
    } catch (error) {
      console.error('Failed to parse default-tool-presets.json:', error);
    }
  }
  let defaultToolCount = 0;
  for (const file of fs.readdirSync(catalogDir)) {
    if (!/^default-tool-specs.*\.json$/.test(file)) continue;
    try {
      const specs = JSON.parse(fs.readFileSync(path.join(catalogDir, file), 'utf8'));
      defaultToolCount += Array.isArray(specs) ? specs.length : 0;
    } catch (error) {
      console.warn(`Failed to count specs in ${file}:`, error.message);
    }
  }
  return { presets, defaultToolCount };
}

function writeDefaultToolsPreference(preference) {
  const file = getDefaultToolsPreferencePath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(preference));
  fs.renameSync(tmp, file);
}

function getEffectiveYamlText(overrideYamlText = '') {
  const defaultYamlText = fs.existsSync(getDefaultConfigPath())
    ? fs.readFileSync(getDefaultConfigPath(), 'utf8')
    : '';
  const overrideText = String(overrideYamlText || '').trim();
  if (!overrideText) return defaultYamlText;
  if (!defaultYamlText.trim()) return overrideText;
  return `${defaultYamlText}\n---\n${overrideText}\n`;
}

function getDefaultYamlText() {
  return fs.existsSync(getDefaultConfigPath())
    ? fs.readFileSync(getDefaultConfigPath(), 'utf8')
    : '';
}

function getConfigDirectory() {
  return path.join(app.getPath('userData'), 'configs');
}

function getConfigIndexPath() {
  return path.join(getConfigDirectory(), 'index.json');
}

function getSecretsStorePath() {
  return path.join(getConfigDirectory(), 'secrets.store');
}

function secureSecretsStorePermissions(storePath = getSecretsStorePath()) {
  if (process.platform === 'win32') return;
  try {
    if (fs.existsSync(storePath)) fs.chmodSync(storePath, 0o600);
  } catch {
  }
}

function cloneSecretsStore(store) {
  return JSON.parse(JSON.stringify(store || {}));
}

function clearSecretsStoreCache() {
  secretsStoreCache = null;
}

function isSecretsEncryptionAvailable() {
  if (secretsEncryptionAvailable == null) {
    secretsEncryptionAvailable = safeStorage.isEncryptionAvailable();
  }
  return secretsEncryptionAvailable;
}

function readSecretsStore() {
  if (secretsStoreCache) {
    return cloneSecretsStore(secretsStoreCache);
  }
  const legacyPath = path.join(getConfigDirectory(), 'secrets.json.enc');
  const storePath = getSecretsStorePath();
  if (fs.existsSync(legacyPath) && !fs.existsSync(storePath)) {
    try { fs.renameSync(legacyPath, storePath); } catch { }
  }
  if (!fs.existsSync(storePath)) {
    secretsStoreCache = {};
    return {};
  }
  try {
    const raw = fs.readFileSync(storePath);
    let parsed;
    if (isSecretsEncryptionAvailable()) {
      parsed = JSON.parse(safeStorage.decryptString(raw));
    } else {
      parsed = JSON.parse(raw.toString('utf8'));
    }
    secureSecretsStorePermissions(storePath);
    secretsStoreCache = parsed && typeof parsed === 'object' ? parsed : {};
    return cloneSecretsStore(secretsStoreCache);
  } catch (error) {
    secretsStoreCache = {};
    return {};
  }
}

function writeSecretsStore(store) {
  const storePath = getSecretsStorePath();
  const payload = JSON.stringify(store, null, 2);
  if (isSecretsEncryptionAvailable()) {
    fs.writeFileSync(storePath, safeStorage.encryptString(payload));
  } else {
    fs.writeFileSync(storePath, payload, 'utf8');
  }
  secureSecretsStorePermissions(storePath);
  secretsStoreCache = cloneSecretsStore(store);
}

function getSecretsStorageStatus() {
  const encrypted = isSecretsEncryptionAvailable();
  return {
    encrypted,
    label: encrypted
      ? 'Encrypted by your OS secure storage'
      : 'OS-backed encryption unavailable — stored as plain text in this session',
  };
}

function getSecretsForConfig(configId) {
  const store = readSecretsStore();
  return store?.[configId] && typeof store[configId] === 'object' ? store[configId] : {};
}

function saveSecretsForConfig(configId, secretValues = {}) {
  const store = readSecretsStore();
  const nextSecrets = {};
  for (const [key, value] of Object.entries(secretValues || {})) {
    if (value != null && String(value).trim() !== '') {
      nextSecrets[key] = String(value).trim();
    }
  }
  if (JSON.stringify(store[configId] || {}) === JSON.stringify(nextSecrets)) {
    return;
  }
  store[configId] = nextSecrets;
  writeSecretsStore(store);
}

function deleteSecretsForConfig(configId) {
  const store = readSecretsStore();
  delete store[configId];
  writeSecretsStore(store);
}

function buildConfigBundle(configId, payload = null) {
  const index = readConfigIndex();
  const selectedConfig = getConfigRecord(configId, index);
  if (!selectedConfig) throw new Error('Selected config was not found.');
  const savedRuntime = index.runtime || getDefaultRuntimeSettings();
  const savedPreferences = index.preferences || getDefaultPreferences();
  const effectivePayload = payload || {};
  return {
    format: 'spring-ai-playground-config',
    version: 1,
    exportedAt: new Date().toISOString(),
    config: {
      id: selectedConfig.id,
      name: selectedConfig.name,
      yamlText: effectivePayload?.yamlText ?? readConfigYaml(selectedConfig.id),
      runtime: {
        jvmOptionsText: effectivePayload?.jvmOptionsText ?? (Array.isArray(savedRuntime.jvmOptions) ? savedRuntime.jvmOptions.join('\n') : ''),
        appArgsText: effectivePayload?.appArgsText ?? (Array.isArray(savedRuntime.appArgs) ? savedRuntime.appArgs.join('\n') : ''),
      },
      // Export config shape without serializing local secrets to disk.
      environmentVariables: {},
      preferences: {
        skipOllamaCheck: effectivePayload?.preferences?.skipOllamaCheck ?? savedPreferences.skipOllamaCheck,
      },
    },
  };
}

function applyImportedConfigBundle(bundle) {
  if (!bundle || typeof bundle !== 'object' || bundle.format !== 'spring-ai-playground-config') {
    throw new Error('This file is not a Spring AI Playground config export.');
  }
  const imported = bundle.config || {};
  const importedName = getFriendlyConfigName(imported.name || 'Imported Config', imported.yamlText || '');
  const yamlText = String(imported.yamlText || '').trim();
  if (!yamlText) throw new Error('The imported config file does not contain YAML overrides.');
  const index = readConfigIndex();
  let target = index.configs.find(config => config.name.toLowerCase() === importedName.toLowerCase()) || null;
  if (!target) {
    const existingIds = new Set(index.configs.map(config => config.id));
    const configId = createUniqueConfigId(importedName, existingIds);
    target = { id: configId, name: importedName };
    index.configs.push(target);
  } else {
    target.name = importedName;
  }
  saveYamlToConfig(target.id, yamlText);
  saveSecretsForConfig(target.id, imported.environmentVariables || {});
  ensureRuntimeSettings(index);
  index.runtime.jvmOptions = parseArgsText(imported.runtime?.jvmOptionsText);
  index.runtime.appArgs = parseArgsText(imported.runtime?.appArgsText);
  ensurePreferences(index);
  index.preferences.skipOllamaCheck = imported.preferences?.skipOllamaCheck ?? index.preferences.skipOllamaCheck;
  index.activeConfigId = target.id;
  saveConfigIndex(index);
  return buildConfigLoadPayload(target.id);
}

function getDefaultRuntimeSettings() {
  return { jvmOptions: ['-Xmx2g'], appArgs: ['--logging.level.root=INFO'] };
}

function getDefaultPreferences() {
  return { autoCopyLogs: true, skipOllamaCheck: false };
}

function isFirstLaunch(index) {
  const currentVersion = app.getVersion();
  return !index?.meta?.hasCompletedInitialSetup || index?.meta?.initialSetupCompletedVersion !== currentVersion;
}

function getTemplateList() {
  return Object.values(CONFIG_TEMPLATES).map(template => ({
    id: template.id,
    providerType: template.providerType,
    name: template.name,
    description: template.description,
  }));
}


function isPlainObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value);
}

function deepMerge(target, source) {
  if (!isPlainObject(source)) return target;
  for (const [key, value] of Object.entries(source)) {
    if (Array.isArray(value)) {
      target[key] = [...value];
    } else if (isPlainObject(value)) {
      target[key] = deepMerge(isPlainObject(target[key]) ? { ...target[key] } : {}, value);
    } else {
      target[key] = value;
    }
  }
  return target;
}

function getYamlObj(yamlText) {
  try {
    const docs = [];
    yaml.loadAll(yamlText, (doc) => {
      if (isPlainObject(doc)) docs.push(doc);
    });
    if (!docs.length) return {};
    return docs.reduce((merged, doc) => deepMerge(merged, doc), {});
  } catch (e) {
    return {};
  }
}

function normalizeSpringAiYamlText(yamlText = '') {
  const source = String(yamlText ?? '');
  if (!source.trim()) return source;

  const docs = [];
  let hadYamlError = false;
  try {
    yaml.loadAll(source, (doc) => {
      docs.push(doc);
    });
  } catch (error) {
    hadYamlError = true;
  }
  if (hadYamlError || !docs.length) return source;

  let changed = false;
  const normalizedDocs = docs.map((doc) => {
    if (!isPlainObject(doc) || !isPlainObject(doc.spring)) return doc;
    const spring = { ...doc.spring };
    const ai = isPlainObject(spring.ai) ? { ...spring.ai } : {};
    let docChanged = false;

    for (const key of ['model', 'ollama', 'openai', 'playground']) {
      if (!isPlainObject(spring[key])) continue;
      ai[key] = deepMerge(isPlainObject(ai[key]) ? { ...ai[key] } : {}, spring[key]);
      delete spring[key];
      docChanged = true;
    }

    if (!docChanged) return doc;
    changed = true;
    spring.ai = ai;
    return { ...doc, spring };
  });

  if (!changed) return source;
  return normalizedDocs
    .map((doc) => yaml.dump(doc, { lineWidth: -1, noRefs: true }).trimEnd())
    .join('\n---\n');
}

function getSpringAiDoc(doc = {}) {
  return doc?.spring?.ai || {};
}

function getLegacySpringDoc(doc = {}) {
  return doc?.spring || {};
}

function getModelSection(doc = {}) {
  return deepMerge(
    isPlainObject(getLegacySpringDoc(doc)?.model) ? { ...getLegacySpringDoc(doc).model } : {},
    isPlainObject(getSpringAiDoc(doc)?.model) ? getSpringAiDoc(doc).model : {},
  );
}

function getOllamaSection(doc = {}) {
  return deepMerge(
    isPlainObject(getLegacySpringDoc(doc)?.ollama) ? { ...getLegacySpringDoc(doc).ollama } : {},
    isPlainObject(getSpringAiDoc(doc)?.ollama) ? getSpringAiDoc(doc).ollama : {},
  );
}

function getOpenAiSection(doc = {}) {
  return deepMerge(
    isPlainObject(getLegacySpringDoc(doc)?.['openai']) ? { ...getLegacySpringDoc(doc)['openai'] } : {},
    isPlainObject(getSpringAiDoc(doc)?.['openai']) ? getSpringAiDoc(doc)['openai'] : {},
  );
}

function parseDurationToMs(value) {
  if (value == null) return null;
  if (typeof value === 'number' && Number.isFinite(value)) return value;

  const text = String(value).trim();
  if (!text) return null;
  if (/^\d+$/.test(text)) return Number(text);

  const simpleMatch = text.match(/^(\d+(?:\.\d+)?)(ms|s|m|h)$/i);
  if (simpleMatch) {
    const amount = Number(simpleMatch[1]);
    const unit = simpleMatch[2].toLowerCase();
    if (unit === 'ms') return Math.round(amount);
    if (unit === 's') return Math.round(amount * 1000);
    if (unit === 'm') return Math.round(amount * 60_000);
    if (unit === 'h') return Math.round(amount * 3_600_000);
  }

  const isoMatch = text.match(/^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$/i);
  if (isoMatch) {
    const hours = Number(isoMatch[1] || 0);
    const minutes = Number(isoMatch[2] || 0);
    const seconds = Number(isoMatch[3] || 0);
    return Math.round((hours * 3600 + minutes * 60 + seconds) * 1000);
  }

  return null;
}

function extractShutdownTimeoutMs(yamlText = '') {
  const match = String(yamlText).match(/^\s*timeout-per-shutdown-phase\s*:\s*([^\s#]+)\s*$/m);
  return match ? parseDurationToMs(match[1]) : null;
}

function getShutdownWaitMs(configPath = null) {
  const defaultTimeoutMs = fs.existsSync(getDefaultConfigPath())
    ? extractShutdownTimeoutMs(fs.readFileSync(getDefaultConfigPath(), 'utf8'))
    : null;
  const overrideTimeoutMs = configPath && fs.existsSync(configPath)
    ? extractShutdownTimeoutMs(fs.readFileSync(configPath, 'utf8'))
    : null;
  const springTimeoutMs = overrideTimeoutMs ?? defaultTimeoutMs ?? 90_000;
  const electronBufferMs = 10_000;
  return springTimeoutMs + electronBufferMs;
}

function isOfficialOpenAiBaseUrl(baseUrl) {
  if (!baseUrl) return false;
  try {
    return new URL(String(baseUrl)).hostname === 'api.openai.com';
  } catch (error) {
    return false;
  }
}

function detectProviderType(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const modelSection = getModelSection(doc);
  const ollamaSection = getOllamaSection(doc);
  const openAiSection = getOpenAiSection(doc);
  const chatModel = modelSection?.chat;
  const openaiBaseUrl = openAiSection?.['base-url'];
  const embeddingModel = modelSection?.embedding;
  const hasOllamaEmbedding = embeddingModel === 'ollama' || !!ollamaSection?.embedding;
  const usesCompatibleBaseUrl = Boolean(openaiBaseUrl) && !isOfficialOpenAiBaseUrl(openaiBaseUrl);
  if (chatModel === 'openai' && (usesCompatibleBaseUrl || hasOllamaEmbedding)) return 'openai-compatible';
  if (chatModel === 'openai' || Object.keys(openAiSection).length) return 'openai';
  if (chatModel === 'ollama' || Object.keys(ollamaSection).length) return 'ollama';
  return 'custom';
}
function hasEmbeddingModelConfig(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const modelSection = getModelSection(doc);
  const ollamaSection = getOllamaSection(doc);
  const openAiSection = getOpenAiSection(doc);
  return !!modelSection?.embedding || !!ollamaSection?.embedding?.options?.model || !!openAiSection?.embedding?.options?.model;
}
function isOllamaRequired(yamlText = '') {
  const providerType = detectProviderType(yamlText);
  if (providerType === 'ollama') return true;
  const doc = getYamlObj(yamlText);
  return getModelSection(doc)?.embedding === 'ollama';
}
function parseOllamaBaseUrl(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const explicitOllamaBaseUrl = getOllamaSection(doc)?.['base-url'];
  if (explicitOllamaBaseUrl) return String(explicitOllamaBaseUrl);
  const compatibleUrl = getOpenAiSection(doc)?.['base-url'];
  if (compatibleUrl && String(compatibleUrl).includes('11434')) return String(compatibleUrl).replace(/\/v1\/?$/i, '');
  return 'http://127.0.0.1:11434';
}
function extractPrimaryModelName(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const chatModel = getModelSection(doc)?.chat;
  if (chatModel === 'ollama') return getOllamaSection(doc)?.chat?.options?.model || null;
  if (chatModel === 'openai') return getOpenAiSection(doc)?.chat?.options?.model || null;
  return null;
}

function getConfiguredChatModelInfo(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const provider = getModelSection(doc)?.chat || null;
  let model = null;
  if (provider === 'ollama') model = getOllamaSection(doc)?.chat?.options?.model || null;
  if (provider === 'openai') model = getOpenAiSection(doc)?.chat?.options?.model || null;
  return { provider, model };
}

function getConfiguredEmbeddingModelInfo(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const provider = getModelSection(doc)?.embedding || null;
  let model = null;
  if (provider === 'ollama') model = getOllamaSection(doc)?.embedding?.options?.model || null;
  if (provider === 'openai') model = getOpenAiSection(doc)?.embedding?.options?.model || null;
  return { provider, model };
}

function getInstalledModelName(modelEntry) {
  if (!modelEntry || typeof modelEntry !== 'object') return null;
  const candidate = modelEntry.name || modelEntry.model || null;
  return typeof candidate === 'string' && candidate.trim() ? candidate.trim() : null;
}

function normalizeOllamaModelName(name) {
  if (typeof name !== 'string') return null;
  const trimmed = name.trim();
  if (!trimmed) return null;
  return trimmed.replace(/:latest$/i, '');
}

function classifyInstalledModel(modelEntry) {
  const details = modelEntry?.details || {};
  const signals = [
    modelEntry?.name,
    modelEntry?.model,
    details?.family,
    ...(Array.isArray(details?.families) ? details.families : []),
  ]
    .filter(value => typeof value === 'string' && value.trim())
    .join(' ')
    .toLowerCase();

  const embeddingHints = [
    'embedding',
    'embed',
    'nomic-embed',
    'mxbai',
    'bge',
    'snowflake-arctic-embed',
    'all-minilm',
    'granite-embedding',
  ];

  if (embeddingHints.some(hint => signals.includes(hint))) return 'embedding';
  return 'chat';
}

function getCachedProviderType(configId) {
  if (!providerTypeCache.has(configId)) {
    try {
      providerTypeCache.set(configId, detectProviderType(readConfigYaml(configId)));
    } catch {
      providerTypeCache.set(configId, 'custom');
    }
  }
  return providerTypeCache.get(configId);
}



function checkOllamaInstalled() {
  const command = process.platform === 'win32' ? 'where' : 'which';
  return new Promise((resolve) => {
    const child = spawn(command, ['ollama'], { stdio: 'ignore' });
    child.on('error', () => resolve(false));
    child.on('close', (code) => resolve(code === 0));
  });
}


function getOllamaAppCandidates() {
  if (process.platform === 'darwin') {
    return ['/Applications/Ollama.app', path.join(app.getPath('home'), 'Applications', 'Ollama.app')];
  }
  if (process.platform === 'win32') {
    const localAppData = process.env.LOCALAPPDATA || '';
    return [
      path.join(localAppData, 'Programs', 'Ollama', 'ollama app.exe'),
      path.join(localAppData, 'Ollama', 'ollama app.exe'),
    ];
  }
  return [];
}

function getOllamaBinaryCandidates() {
  if (process.platform === 'win32') {
    const localAppData = process.env.LOCALAPPDATA || '';
    return [
      path.join(localAppData, 'Programs', 'Ollama', 'ollama.exe'),
      path.join(localAppData, 'Ollama', 'ollama.exe'),
    ];
  }
  return [];
}

async function findOllamaLaunchTarget() {
  if (await commandExists('ollama')) return { kind: 'command', command: 'ollama' };
  for (const appPath of getOllamaAppCandidates()) {
    if (appPath && fs.existsSync(appPath)) return { kind: 'app', path: appPath };
  }
  for (const binaryPath of getOllamaBinaryCandidates()) {
    if (binaryPath && fs.existsSync(binaryPath)) return { kind: 'binary', path: binaryPath };
  }
  return null;
}

function parseArgsText(text = '') {
  return String(text).split(/\r?\n/).map(line => line.trim()).filter(Boolean);
}

function ensureRuntimeSettings(index) {
  const defaults = getDefaultRuntimeSettings();
  if (!index.runtime || typeof index.runtime !== 'object') index.runtime = defaults;
  index.runtime.jvmOptions = Array.isArray(index.runtime.jvmOptions) && index.runtime.jvmOptions.length
    ? index.runtime.jvmOptions
    : [...defaults.jvmOptions];
  index.runtime.appArgs = Array.isArray(index.runtime.appArgs) && index.runtime.appArgs.length
    ? index.runtime.appArgs
    : [...defaults.appArgs];
  if (index.runtime.appArgs.length === 1 && (
    index.runtime.appArgs[0] === '--logging.level.org.springframework.ai=INFO' ||
    index.runtime.appArgs[0] === '--logging.level.jm.kr.spring.ai.playground=INFO'
  )) {
    index.runtime.appArgs = [...defaults.appArgs];
  }
}

function ensurePreferences(index) {
  if (!index.preferences || typeof index.preferences !== 'object') {
    index.preferences = getDefaultPreferences();
  }
  if (typeof index.preferences.autoCopyLogs !== 'boolean') index.preferences.autoCopyLogs = true;
  if (typeof index.preferences.skipOllamaCheck !== 'boolean') index.preferences.skipOllamaCheck = false;
}

function getRuntimeSettingsPayload(index, secretSuggestions = collectSecretSuggestions()) {
  ensureRuntimeSettings(index);
  const secretKeys = new Set(secretSuggestions.map(item => item.key));
  const envVariablesMap = { ...getSecretsForConfig(index.activeConfigId) };
  for (const suggestion of secretSuggestions) {
    if ((envVariablesMap[suggestion.key] == null || envVariablesMap[suggestion.key] === '') && process.env[suggestion.key]) {
      envVariablesMap[suggestion.key] = process.env[suggestion.key];
    }
  }
  const visibleEnvVariables = Object.fromEntries(Object.entries(envVariablesMap).filter(([key]) => !secretKeys.has(key)));
  return {
    jvmOptionsText: index.runtime.jvmOptions.join('\n'),
    appArgsText: index.runtime.appArgs.join('\n'),
    envVariablesMap,
    customEnvVariables: visibleEnvVariables,
  };
}

function buildSpawnArguments(jrePath, jarPath, configPath, runtimeSettings, configId) {
  const jvmOptions = Array.isArray(runtimeSettings?.jvmOptions) ? runtimeSettings.jvmOptions : [];
  const appArgs = Array.isArray(runtimeSettings?.appArgs) ? runtimeSettings.appArgs : [];
  const userHome = app.getPath('userData');
  const configArg = `--spring.config.additional-location=${pathToFileURL(configPath).href}`;
  const envVariables = getSecretsForConfig(configId);

  return {
    args: [
      `-Dspring.ai.playground.user-home=${userHome}`,
      ...jvmOptions,
      '-jar',
      jarPath,
      configArg,
      // Electron owns the MLX decision, so disable the JVM-side auto-activator.
      '--spring.ai.playground.ollama.mlx-auto-select=false',
      ...appArgs,
    ],
    env: { ...process.env, ...envVariables },
  };
}

function httpGetJson(targetUrl) {
  return new Promise((resolve, reject) => {
    const request = http.get(targetUrl, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
          resolve({ statusCode: res.statusCode, body });
        } else {
          reject(new Error(`HTTP ${res.statusCode || 'error'}`));
        }
      });
    });
    request.on('error', reject);
    request.setTimeout(2000, () => request.destroy(new Error('Timeout')));
  });
}

function httpGetText(targetUrl) {
  return new Promise((resolve, reject) => {
    const request = http.get(targetUrl, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => resolve({ statusCode: res.statusCode, body }));
    });
    request.on('error', reject);
    request.setTimeout(2000, () => request.destroy(new Error('Timeout')));
  });
}

async function getOllamaEnvironmentInfo(yamlText = '') {
  const ollamaTarget = await findOllamaLaunchTarget();
  const baseEnvironment = await ollamaManager.getOllamaEnvironmentInfo({
    yamlText,
    defaultYamlText: getDefaultYamlText(),
    ollamaInstalled: Boolean(ollamaTarget),
  });
  const fallback = await getCliInstalledModelFallback(baseEnvironment);
  const environment = {
    ...baseEnvironment,
    ...fallback,
  };
  const installedSet = new Set((environment.installedModels || []).flatMap((name) => {
    const text = String(name || '').trim();
    if (!text) return [];
    const normalized = text.replace(/:latest$/i, '');
    return normalized && normalized !== text ? [text, normalized] : [text];
  }));
  return {
    ...environment,
    chatModel: environment.chatModel ? {
      ...environment.chatModel,
      installationKnown: environment.running,
      installed: environment.chatModel.provider === 'ollama' && !!environment.chatModel.model && installedSet.has(environment.chatModel.model),
    } : environment.chatModel,
    embeddingModel: environment.embeddingModel ? {
      ...environment.embeddingModel,
      installationKnown: environment.running,
      installed: environment.embeddingModel.provider === 'ollama' && !!environment.embeddingModel.model && installedSet.has(environment.embeddingModel.model),
    } : environment.embeddingModel,
    installTarget: ollamaTarget?.kind || null,
    platform: process.platform,
    canAutoInstall: process.platform === 'darwin' || process.platform === 'linux',
    canAutoStart: Boolean(ollamaTarget),
  };
}

function classifyInstalledModelName(name = '') {
  const signals = String(name || '').toLowerCase();
  const embeddingHints = ['embedding', 'embed', 'nomic-embed', 'mxbai', 'bge', 'snowflake-arctic-embed', 'all-minilm', 'granite-embedding'];
  return embeddingHints.some((hint) => signals.includes(hint)) ? 'embedding' : 'chat';
}

function parseOllamaListCliOutput(output = '') {
  const lines = String(output || '').split(/\r?\n/).map((line) => line.trimEnd()).filter(Boolean);
  if (lines.length <= 1) return { installedModels: [], installedChatModels: [], installedEmbeddingModels: [] };
  const models = lines.slice(1)
    .map((line) => line.split(/\s{2,}/)[0]?.trim())
    .filter(Boolean);
  const installedChatModels = models.filter((name) => classifyInstalledModelName(name) === 'chat').sort((a, b) => a.localeCompare(b));
  const installedEmbeddingModels = models.filter((name) => classifyInstalledModelName(name) === 'embedding').sort((a, b) => a.localeCompare(b));
  return {
    installedModels: [...models].sort((a, b) => a.localeCompare(b)),
    installedChatModels,
    installedEmbeddingModels,
  };
}

async function getCliInstalledModelFallback(environment = {}) {
  if (!environment?.running || (environment?.installedModels?.length ?? 0) > 0 || !(await commandExists('ollama'))) {
    return {};
  }
  try {
    const output = await new Promise((resolve, reject) => {
      execFile('ollama', ['list'], { timeout: 4000 }, (error, stdout) => {
        if (error) return reject(error);
        resolve(stdout || '');
      });
    });
    const fallback = parseOllamaListCliOutput(output);
    return fallback.installedModels.length ? fallback : {};
  } catch {
    return {};
  }
}

function getLaunchChatModelList(installedChatModels = [], configuredChatModel = null) {
  const uniqueModels = [...new Set(
    (Array.isArray(installedChatModels) ? installedChatModels : [])
      .map((model) => typeof model === 'string' ? model.trim() : '')
      .filter(Boolean)
  )];
  if (!uniqueModels.length) return [];

  const normalizedConfigured = normalizeOllamaModelName(configuredChatModel);
  const prioritizedModel = normalizedConfigured
    ? uniqueModels.find((model) => normalizeOllamaModelName(model) === normalizedConfigured) || null
    : null;
  const remainingModels = uniqueModels
    .filter((model) => model !== prioritizedModel)
    .sort((left, right) => left.localeCompare(right));

  return prioritizedModel ? [prioritizedModel, ...remainingModels] : [...remainingModels];
}

function buildLaunchOverrideYaml(chatModels = [], chatModelOverride = null) {
  const ai = {
    playground: {
      chat: {
        models: chatModels,
      },
    },
  };
  if (chatModelOverride) {
    ai.ollama = { chat: { options: { model: chatModelOverride } } };
  }
  return yaml.dump({ spring: { ai } }, { lineWidth: -1, noRefs: true }).trimEnd();
}

function resolveAppleSiliconMlxModel(configuredChatModel, installedModels = []) {
  if (!isAppleSilicon() || !configuredChatModel) return null;
  const mlxModel = toMlxModel(configuredChatModel);
  if (mlxModel === configuredChatModel) return null;
  return (Array.isArray(installedModels) ? installedModels : []).includes(mlxModel) ? mlxModel : null;
}

async function resolveLaunchConfigPath(configPath) {
  const yamlText = fs.readFileSync(configPath, 'utf8');
  if (detectProviderType(yamlText) !== 'ollama') return configPath;

  const ollamaInfo = await getOllamaEnvironmentInfo(yamlText);
  if (!ollamaInfo.running) {
    appendLog('Ollama runtime model sync skipped because Ollama is not responding. Using the saved launcher YAML as-is.', true);
    return configPath;
  }

  const configuredChatModel = ollamaInfo.chatModel?.provider === 'ollama' ? ollamaInfo.chatModel.model : null;
  const mlxChatModel = resolveAppleSiliconMlxModel(configuredChatModel, ollamaInfo.installedModels);
  const runtimeChatModels = getLaunchChatModelList(ollamaInfo.installedChatModels, mlxChatModel || configuredChatModel);
  const runtimeYamlText = normalizeSpringAiYamlText(yamlText).trimEnd();
  const overrideYamlText = buildLaunchOverrideYaml(runtimeChatModels, mlxChatModel);
  const launchConfigPath = getUserConfigPath();
  const nextYamlText = runtimeYamlText
    ? `${runtimeYamlText}\n---\n${overrideYamlText}\n`
    : `${overrideYamlText}\n`;

  fs.writeFileSync(launchConfigPath, nextYamlText, 'utf8');
  appendLog(`Resolved ${runtimeChatModels.length} downloaded Ollama chat model(s) for launch.`);
  if (mlxChatModel) {
    appendLog(`Apple Silicon: upgraded chat model ${configuredChatModel} to MLX build ${mlxChatModel} for launch.`);
  }
  return launchConfigPath;
}

function runDetachedCommand(command, args = []) {
  const child = spawn(command, args, { detached: true, stdio: 'ignore' });
  child.unref();
}

function commandExists(command) {
  const lookup = process.platform === 'win32' ? 'where' : 'which';
  return new Promise((resolve) => {
    const child = spawn(lookup, [command], { stdio: 'ignore' });
    child.on('error', () => resolve(false));
    child.on('close', code => resolve(code === 0));
  });
}

async function startOllamaService() {
  const target = await findOllamaLaunchTarget();
  if (!target) { await shell.openExternal('https://ollama.com/download'); return { mode: 'external' }; }
  if (process.platform === 'darwin') {
    if (target.kind === 'app' && await commandExists('open')) { runDetachedCommand('open', ['-a', 'Ollama']); return { mode: 'started' }; }
  }
  if (target.kind === 'command') { runDetachedCommand('ollama', ['serve']); return { mode: 'started' }; }
  if (target.kind === 'binary') { runDetachedCommand(target.path, ['serve']); return { mode: 'started' }; }
  if (target.kind === 'app') { await shell.openPath(target.path); return { mode: 'started' }; }
  await shell.openExternal('https://ollama.com/download');
  return { mode: 'external' };
}

async function prepareOllamaForLaunch(configPath) {
  const yamlText = fs.readFileSync(configPath, 'utf8');
  const providerType = detectProviderType(yamlText);
  if (providerType === 'openai') {
    appendLog('Selected profile uses OpenAI only, so Ollama startup is skipped.');
    return true;
  }
  const ollamaInfo = await getOllamaEnvironmentInfo(yamlText);
  const index = readConfigIndex();
  const skipOllamaCheck = index.preferences?.skipOllamaCheck ?? false;
  if (skipOllamaCheck) {
    appendLog('Skipping Ollama startup check because it is disabled in launcher settings.');
    return true;
  }
  if (!ollamaInfo.ollamaRequired) {
    appendLog('Selected profile does not require Ollama for startup.');
    return true;
  }
  appendLog(`Checking Ollama endpoint: ${ollamaInfo.baseUrl}`);
  if (ollamaInfo.running) {
    appendLog(`Ollama is already running at ${ollamaInfo.baseUrl}.`);
    return true;
  }
  if (!ollamaInfo.ollamaInstalled) {
    appendLog('Ollama is not installed. Open https://ollama.com/download to install it if this profile needs local models.', true);
  } else {
    appendLog(`Ollama is not responding at ${ollamaInfo.baseUrl}. Check that Ollama is running before using this setting.`, true);
  }
  const choice = await dialog.showMessageBox({
    type: 'warning',
    title: 'Ollama Connection Check',
    buttons: ['Continue Launch', 'Back to Setup'],
    defaultId: 1,
    cancelId: 1,
    noLink: true,
    message: 'Ollama is not responding.',
    detail: [
      'This setting uses Ollama for chat or embedding.',
      `Configured Ollama endpoint: ${ollamaInfo.baseUrl}`,
      '',
      'Check that Ollama is installed and running, then verify the connection at that address.',
      'Choose Continue Launch to start the server anyway, or Back to Setup to review the config.',
    ].join('\n'),
  });
  if (choice.response === 0) {
    appendLog(`Continuing launch after Ollama warning for ${ollamaInfo.baseUrl}.`, true);
    return true;
  }
  appendLog('Launch canceled after Ollama warning. Returning to config setup.', true);
  return false;
}

function sanitizeConfigName(name) {
  return String(name || '').trim()
    .replace(/[<>:"/\\|?*\u0000-\u001F]/g, '-')
    .replace(/\s+/g, ' ');
}

function slugifyConfigName(name) {
  return sanitizeConfigName(name)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'config';
}

function createUniqueConfigId(name, existingIds = new Set()) {
  const baseSlug = slugifyConfigName(name);
  const base = `cfg-${baseSlug || 'config'}`;
  let candidate = base;
  let counter = 2;
  while (existingIds.has(candidate)) { candidate = `${base}-${counter}`; counter += 1; }
  return candidate;
}

function createUniqueConfigName(name, existingNames = new Set()) {
  let candidate = name;
  let counter = 2;
  while (existingNames.has(candidate.toLowerCase())) { candidate = `${name} ${counter}`; counter += 1; }
  existingNames.add(candidate.toLowerCase());
  return candidate;
}

function getConfigFilePath(configId) {
  return path.join(getConfigDirectory(), `${configId}.yaml`);
}

function readJsonFile(filePath, fallbackValue) {
  if (!fs.existsSync(filePath)) return fallbackValue;
  try { return JSON.parse(fs.readFileSync(filePath, 'utf8')); } catch { return fallbackValue; }
}

function writeJsonFile(filePath, value) {
  fs.writeFileSync(filePath, JSON.stringify(value, null, 2), 'utf8');
}

function createDefaultConfigRecord(existingIds = new Set()) {
  const defaultYaml = templateYaml(CONFIG_TEMPLATES.ollama);
  const configId = createUniqueConfigId('Default', existingIds);
  const config = { id: configId, name: 'Ollama' };
  fs.writeFileSync(getConfigFilePath(configId), normalizeSpringAiYamlText(defaultYaml), 'utf8');
  return config;
}

function ensureStarterConfigs(index) {
  const existingNames = new Set(index.configs.map(config => config.name.toLowerCase()));
  const existingIds = new Set(index.configs.map(config => config.id));
  for (const template of Object.values(CONFIG_TEMPLATES)) {
    if (!DEFAULT_STARTER_TEMPLATE_IDS.has(template.id)) continue;
    if (existingNames.has(template.name.toLowerCase())) continue;
    const configId = createUniqueConfigId(template.name, existingIds);
    existingIds.add(configId);
    fs.writeFileSync(getConfigFilePath(configId), normalizeSpringAiYamlText(templateYaml(template)), 'utf8');
    index.configs.push({ id: configId, name: template.name });
  }
}

function normalizeConfigYamlToTemplateIfNeeded(config) {
  const configPath = getConfigFilePath(config.id);
  const template = getTemplateByName(config.name);
  if (!fs.existsSync(configPath)) {
    fs.writeFileSync(
      configPath,
      normalizeSpringAiYamlText(templateYaml(template ?? CONFIG_TEMPLATES.ollama)),
      'utf8'
    );
    return;
  }
  const existingYaml = fs.readFileSync(configPath, 'utf8');
  const normalizedYaml = normalizeSpringAiYamlText(existingYaml);
  if (normalizedYaml !== existingYaml) {
    fs.writeFileSync(configPath, normalizedYaml, 'utf8');
    return;
  }
  if (template && shouldCompactToTemplateYaml(existingYaml)) {
    fs.writeFileSync(configPath, normalizeSpringAiYamlText(templateYaml(template)), 'utf8');
  }
}

function ensureConfigStore() {
  const configDir = getConfigDirectory();
  fs.mkdirSync(configDir, { recursive: true });
  const indexPath = getConfigIndexPath();
  const legacyConfigPath = getUserConfigPath();
  let index = readJsonFile(indexPath, null);

  if (!index || !Array.isArray(index.configs) || index.configs.length === 0) {
    let initialYaml = null;
    if (fs.existsSync(legacyConfigPath)) initialYaml = fs.readFileSync(legacyConfigPath, 'utf8');
    const config = { id: 'default', name: 'Ollama' };
    fs.writeFileSync(
      getConfigFilePath(config.id),
      normalizeSpringAiYamlText(initialYaml ?? templateYaml(CONFIG_TEMPLATES.ollama)),
      'utf8'
    );
    index = { activeConfigId: config.id, configs: [config], meta: { hasCompletedInitialSetup: false } };
    writeJsonFile(indexPath, index);
  }

  if (!index.meta || typeof index.meta !== 'object') index.meta = { hasCompletedInitialSetup: false };
  ensureRuntimeSettings(index);
  ensurePreferences(index);

  const existingIds = new Set();
  index.configs = index.configs
    .filter(config => config && config.id && config.name)
    .map(config => {
      const originalConfigId = String(config.id);
      let configId = originalConfigId;
      if (!configId.startsWith('cfg-') || existingIds.has(configId)) {
        configId = createUniqueConfigId(config.name, existingIds);
      }
      if (configId !== originalConfigId) {
        const oldPath = getConfigFilePath(originalConfigId);
        const newPath = getConfigFilePath(configId);
        if (fs.existsSync(oldPath) && !fs.existsSync(newPath)) fs.renameSync(oldPath, newPath);
      }
      existingIds.add(configId);
      const configPath = fs.existsSync(getConfigFilePath(configId))
        ? getConfigFilePath(configId)
        : (fs.existsSync(getConfigFilePath(originalConfigId)) ? getConfigFilePath(originalConfigId) : null);
      const existingYaml = configPath && fs.existsSync(configPath) ? fs.readFileSync(configPath, 'utf8') : '';
      return { id: configId, name: getFriendlyConfigName(config.name, existingYaml) };
    });

  const existingNames = new Set();
  index.configs = index.configs.map(config => ({
    ...config,
    name: createUniqueConfigName(config.name, existingNames),
  }));

  if (index.configs.length === 0) index.configs.push(createDefaultConfigRecord(existingIds));
  ensureStarterConfigs(index);
  for (const config of index.configs) normalizeConfigYamlToTemplateIfNeeded(config);
  for (const config of index.configs) {
    const configPath = getConfigFilePath(config.id);
    if (!fs.existsSync(configPath)) {
      const template = getTemplateByName(config.name);
      fs.writeFileSync(
        configPath,
        normalizeSpringAiYamlText(templateYaml(template ?? CONFIG_TEMPLATES.ollama)),
        'utf8'
      );
    }
  }
  if (!index.configs.some(config => config.id === index.activeConfigId)) {
    index.activeConfigId = index.configs[0].id;
  }
  writeJsonFile(indexPath, index);
  currentConfigId = index.activeConfigId;
  return index;
}

function readConfigIndex() {
  return ensureConfigStore();
}

function saveConfigIndex(index) {
  writeJsonFile(getConfigIndexPath(), index);
  currentConfigId = index.activeConfigId;
}

function getConfigRecord(configId, index = readConfigIndex()) {
  return index.configs.find(config => config.id === configId) || null;
}

function readConfigYaml(configId) {
  return fs.readFileSync(getConfigFilePath(configId), 'utf8');
}

function saveYamlToConfig(configId, yamlText) {
  fs.writeFileSync(getConfigFilePath(configId), normalizeSpringAiYamlText(yamlText), 'utf8');
  providerTypeCache.delete(configId);
}

function buildConfigLoadPayload(selectedConfigId = null) {
  const index = readConfigIndex();
  const resolvedConfigId = selectedConfigId && getConfigRecord(selectedConfigId, index)
    ? selectedConfigId
    : index.activeConfigId;
  const selectedConfig = getConfigRecord(resolvedConfigId, index);
  index.activeConfigId = resolvedConfigId;
  saveConfigIndex(index);
  const selectedConfigYaml = readConfigYaml(selectedConfig.id);
  const secretSuggestions = collectSecretSuggestions(selectedConfigYaml);
  return {
    activeConfigId: resolvedConfigId,
    selectedConfig: {
      id: selectedConfig.id,
      name: selectedConfig.name,
      path: getConfigFilePath(selectedConfig.id),
      yaml: selectedConfigYaml,
      providerType: detectProviderType(selectedConfigYaml),
      embeddingConfigured: hasEmbeddingModelConfig(selectedConfigYaml),
    },
    configs: index.configs.map(config => ({
      id: config.id,
      name: config.name,
      path: getConfigFilePath(config.id),
      providerType: getCachedProviderType(config.id),
    })),
    configDirectory: getConfigDirectory(),
    defaultConfigPath: getDefaultConfigPath(),
    templates: getTemplateList(),
    isFirstLaunch: isFirstLaunch(index),
    runtimeSettings: getRuntimeSettingsPayload(index, secretSuggestions),
    secretSuggestions,
    secretsStorage: getSecretsStorageStatus(),
    preferences: {
      autoCopyLogs: index.preferences.autoCopyLogs,
      skipOllamaCheck: index.preferences.skipOllamaCheck,
    },
    launchCommand: lastLaunchCommand,
  };
}

function getJarPath() {
  if (isDev) {
    const targetDir = path.resolve(__dirname, '..', 'target');
    if (!fs.existsSync(targetDir)) return null;
    const files = fs.readdirSync(targetDir);
    const jarName = files.find(f => f.endsWith('.jar') && !f.startsWith('original-') && !f.endsWith('-sources.jar'));
    return jarName ? path.join(targetDir, jarName) : null;
  }
  return path.join(process.resourcesPath, 'app.jar');
}

function createConfigWindow() {
  const { workAreaSize } = screen.getPrimaryDisplay();
  configWindow = new BrowserWindow({
    width: 1080,
    height: workAreaSize.height,
    minWidth: 980,
    minHeight: 860,
    show: false,
    autoHideMenuBar: true,
    icon: getWindowIconPath(),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: PRELOAD_PATH,
    },
  });
  configWindow.on('closed', () => {
    configWindow = null;
    if (!mainWindow && !serverSplashWindow && !ollamaManagerWindow && !isQuitting) app.quit();
  });
  configWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    appendLog(`Config window failed to load (${errorCode}): ${errorDescription} - ${validatedURL || CONFIG_EDITOR_PATH}`, true);
  });
  configWindow.webContents.on('did-finish-load', () => {
    fitConfigWindowToContent();
  });

  configWindow.loadURL(tempServer.getUrl(CONFIG_EDITOR_PATH) + TELEMETRY_QUERY_STRING);
  configWindow.once('ready-to-show', () => configWindow.show());
}

async function fitConfigWindowToContent() {
  if (!configWindow || configWindow.isDestroyed()) return;
  const { workAreaSize } = screen.getPrimaryDisplay();
  try {
    const contentHeight = await configWindow.webContents.executeJavaScript(
      `(() => {
        const root = document.querySelector('.shell');
        const body = document.body;
        const measure = (element) => {
          if (!element) return 0;
          const styles = window.getComputedStyle(element);
          const marginTop = parseFloat(styles.marginTop || '0');
          const marginBottom = parseFloat(styles.marginBottom || '0');
          return Math.ceil(Math.max(
            element.scrollHeight || 0,
            element.offsetHeight || 0,
            element.getBoundingClientRect().height || 0
          ) + marginTop + marginBottom);
        };
        const rootHeight = measure(root);
        if (rootHeight > 0) return rootHeight;
        return measure(body);
      })()`,
      true
    );
    if (!Number.isFinite(contentHeight)) return;
    const bounds = configWindow.getContentBounds();
    const targetHeight = Math.max(760, Math.min(workAreaSize.height, contentHeight + 24));
    if (Math.abs(bounds.height - targetHeight) < 8) return;
    configWindow.setContentSize(bounds.width, targetHeight);
  } catch {
    // Ignore sizing failures and keep the default window size.
  }
}

async function fitOllamaManagerWindowToContent() {
  if (!ollamaManagerWindow || ollamaManagerWindow.isDestroyed()) return;
  const { workAreaSize } = screen.getPrimaryDisplay();
  try {
    const contentHeight = await ollamaManagerWindow.webContents.executeJavaScript(
      `(() => {
        const root = document.querySelector('.shell');
        const body = document.body;
        const measure = (element) => {
          if (!element) return 0;
          const styles = window.getComputedStyle(element);
          const marginTop = parseFloat(styles.marginTop || '0');
          const marginBottom = parseFloat(styles.marginBottom || '0');
          return Math.ceil(Math.max(
            element.scrollHeight || 0,
            element.offsetHeight || 0,
            element.getBoundingClientRect().height || 0
          ) + marginTop + marginBottom);
        };
        const rootHeight = measure(root);
        if (rootHeight > 0) return rootHeight;
        return measure(body);
      })()`,
      true
    );
    if (!Number.isFinite(contentHeight)) return;
    const bounds = ollamaManagerWindow.getContentBounds();
    const targetHeight = Math.max(460, Math.min(workAreaSize.height - 120, contentHeight + 8));
    if (Math.abs(bounds.height - targetHeight) < 8) return;
    ollamaManagerWindow.setContentSize(bounds.width, targetHeight);
  } catch {
    // Ignore sizing failures and keep the default window size.
  }
}

function createOllamaManagerWindow() {
  if (ollamaManagerWindow && !ollamaManagerWindow.isDestroyed()) {
    ollamaManagerWindow.focus();
    return ollamaManagerWindow;
  }

  allowOllamaManagerWindowClose = false;
  const { workAreaSize } = screen.getPrimaryDisplay();

  ollamaManagerWindow = new BrowserWindow({
    width: 1160,
    height: Math.max(520, workAreaSize.height - 220),
    minWidth: 960,
    minHeight: 460,
    show: false,
    autoHideMenuBar: true,
    parent: configWindow || undefined,
    modal: false,
    icon: getWindowIconPath(),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: OLLAMA_MANAGER_PRELOAD_PATH,
    },
  });
  const requestOllamaManagerClose = async (source = 'window') => {
    if (!ollamaManagerWindow || ollamaManagerWindow.isDestroyed()) return { closed: true };
    if (ollamaManagerCloseInProgress) return { closed: false, busy: true };
    if (allowOllamaManagerWindowClose || !hasPendingOllamaDownloads()) {
      allowOllamaManagerWindowClose = true;
      ollamaManagerWindow.close();
      return { closed: true };
    }
    const choice = dialog.showMessageBoxSync(ollamaManagerWindow, {
      type: 'warning',
      title: source === 'button' ? 'Leave model manager?' : 'Cancel downloads and close?',
      buttons: ['Keep downloading', source === 'button' ? 'Leave and remove downloads' : 'Cancel and close'],
      defaultId: 0,
      cancelId: 0,
      noLink: true,
      message: hasPendingOllamaDownloads()
        ? 'There are downloads in progress or queued.'
        : 'Close the Ollama model manager?',
      detail: 'If you continue, the current download will stop, any queued downloads will be removed, and partially downloaded files for the active model will be cleared.',
    });
    if (choice !== 1) {
      ollamaManagerWindow.focus();
      return { closed: false, canceled: true };
    }
    ollamaManagerCloseInProgress = true;
    try {
      await cancelAllOllamaDownloads();
      allowOllamaManagerWindowClose = true;
      if (ollamaManagerWindow && !ollamaManagerWindow.isDestroyed()) {
        ollamaManagerWindow.close();
      }
      return { closed: true };
    } finally {
      ollamaManagerCloseInProgress = false;
    }
  };
  ollamaManagerWindow.on('close', (event) => {
    if (allowOllamaManagerWindowClose || !hasPendingOllamaDownloads()) return;
    if (ollamaManagerCloseInProgress) {
      event.preventDefault();
      return;
    }
    event.preventDefault();
    requestOllamaManagerClose('native-close').catch((error) => {
      appendLog(`Failed to close Ollama manager cleanly: ${error.message || String(error)}`, true);
    });
  });
  ollamaManagerWindow.on('blur', () => {
    if (!configWindow || configWindow.isDestroyed()) return;
    setTimeout(() => {
      if (!ollamaManagerWindow || ollamaManagerWindow.isDestroyed()) return;
      if (ollamaManagerCloseInProgress) return;
      if (ollamaManagerWindow.isFocused()) return;
      if (!configWindow.isFocused()) return;
      requestOllamaManagerClose('blur-close').catch((error) => {
        appendLog(`Failed to close Ollama manager after blur: ${error.message || String(error)}`, true);
      });
    }, 0);
  });
  ollamaManagerWindow.on('closed', () => {
    allowOllamaManagerWindowClose = false;
    ollamaManagerCloseInProgress = false;
    ollamaManagerWindow = null;
  });
  ollamaManagerWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    appendLog(`Ollama manager failed to load (${errorCode}): ${errorDescription} - ${validatedURL || OLLAMA_MANAGER_PATH}`, true);
  });
  ollamaManagerWindow.webContents.on('render-process-gone', (event, details) => {
    appendLog(`Ollama manager render process gone (${details?.reason || 'unknown'}); reloading.`, true);
    if (ollamaManagerWindow && !ollamaManagerWindow.isDestroyed()) ollamaManagerWindow.reload();
  });
  ollamaManagerWindow.webContents.on('did-finish-load', () => {
    fitOllamaManagerWindowToContent();
  });
  ollamaManagerWindow.loadURL(tempServer.getUrl(OLLAMA_MANAGER_PATH) + TELEMETRY_QUERY_STRING);
  ollamaManagerWindow.once('ready-to-show', async () => {
    await fitOllamaManagerWindowToContent();
    ollamaManagerWindow.show();
  });
  return ollamaManagerWindow;
}

function getSerializableDownloadQueue() {
  const current = activeOllamaDownload ? [activeOllamaDownload] : [];
  return [...current, ...ollamaDownloadQueue].map((task) => ({
    id: task.id,
    model: task.model,
    status: task.status,
    progressText: task.progressText,
    completed: task.completed ?? null,
    total: task.total ?? null,
    percent: task.percent ?? null,
    error: task.error ?? null,
  }));
}

function createOllamaDownloadTask(model) {
  const task = {
    id: nextOllamaDownloadId++,
    model,
    status: 'queued',
    progressText: 'Queued',
    completed: null,
    total: null,
    percent: null,
    error: null,
    client: null,
    stream: null,
    cleanupRequested: false,
    completionPromise: Promise.resolve(),
    resolveCompletion: null,
  };
  task.completionPromise = new Promise((resolve) => {
    task.resolveCompletion = resolve;
  });
  return task;
}

function sendOllamaDownloadQueueUpdate(target = null) {
  const payload = { downloads: getSerializableDownloadQueue() };
  if (target?.isDestroyed?.() === false) {
    target.send('ollama-manager:download-updated', payload);
    return;
  }
  if (ollamaManagerWindow && !ollamaManagerWindow.isDestroyed()) {
    ollamaManagerWindow.webContents.send('ollama-manager:download-updated', payload);
  }
}

let lastDownloadProgressSendAt = 0;

function sendOllamaDownloadProgressUpdate() {
  const now = Date.now();
  if (now - lastDownloadProgressSendAt < 400) return;
  lastDownloadProgressSendAt = now;
  sendOllamaDownloadQueueUpdate();
}

function updateDownloadTaskProgress(task, progress = {}) {
  task.progressText = progress?.status || progress?.error || progress?.digest || task.progressText || 'Downloading...';
  task.completed = typeof progress?.completed === 'number' ? progress.completed : task.completed ?? null;
  task.total = typeof progress?.total === 'number' ? progress.total : task.total ?? null;
  task.percent = task.total > 0 && typeof task.completed === 'number'
    ? Math.max(0, Math.min(100, Math.round((task.completed / task.total) * 100)))
    : task.percent ?? null;
}

function hasPendingOllamaDownloads() {
  return Boolean(activeOllamaDownload) || ollamaDownloadQueue.length > 0;
}

function cancelQueuedOllamaDownloads() {
  for (const task of ollamaDownloadQueue) {
    task.status = 'canceled';
    task.progressText = 'Canceled';
    task.resolveCompletion?.();
    task.resolveCompletion = null;
  }
  ollamaDownloadQueue = [];
}

function abortOllamaDownloadTask(task) {
  if (!task) return;
  try { task.client?.abort?.(); } catch { /* client may already be closed */ }
  try { task.stream?.abort?.(); } catch { /* stream may already be done */ }
}

async function cleanupCanceledOllamaDownload(task) {
  if (!task?.cleanupRequested) return;
  try {
    const environment = await getOllamaEnvironmentInfo(ollamaManagerContext.yamlText);
    await ollamaManager.deleteModel(environment.baseUrl, task.model);
  } catch (error) {
    const message = String(error?.message || error || '');
    if (message.toLowerCase().includes('not found')) return;
    appendLog(`Ignored cleanup failure for canceled Ollama download "${task?.model}": ${message}`);
  }
}

function finalizeOllamaDownloadTask(task) {
  task?.resolveCompletion?.();
  if (task) task.resolveCompletion = null;
}

async function cancelAllOllamaDownloads() {
  const activeTask = activeOllamaDownload;
  cancelQueuedOllamaDownloads();
  if (activeTask) {
    activeTask.cleanupRequested = true;
    activeTask.status = 'canceling';
    activeTask.progressText = 'Canceling download and removing partial files...';
    abortOllamaDownloadTask(activeTask);
  }
  sendOllamaDownloadQueueUpdate();
  await activeTask?.completionPromise;
}

function cancelOllamaDownload(taskId) {
  if (activeOllamaDownload && activeOllamaDownload.id === taskId) {
    const task = activeOllamaDownload;
    task.cleanupRequested = true;
    task.status = 'canceling';
    task.progressText = 'Canceling download and removing partial files...';
    abortOllamaDownloadTask(task);
    return { ok: true, target: 'active' };
  }
  const index = ollamaDownloadQueue.findIndex((task) => task.id === taskId);
  if (index === -1) return { ok: false };
  const [task] = ollamaDownloadQueue.splice(index, 1);
  task.status = 'canceled';
  task.progressText = 'Canceled';
  finalizeOllamaDownloadTask(task);
  return { ok: true, target: 'queued' };
}

async function processNextOllamaDownload() {
  if (activeOllamaDownload || !ollamaDownloadQueue.length) return;
  activeOllamaDownload = ollamaDownloadQueue.shift();
  const task = activeOllamaDownload;
  task.status = 'starting';
  task.progressText = 'Preparing download...';
  sendOllamaDownloadQueueUpdate();

  try {
    const environment = await getOllamaEnvironmentInfo(ollamaManagerContext.yamlText);
    if (!environment.ollamaInstalled) {
      throw new Error('Install Ollama first before downloading models.');
    }
    const { client, stream } = await ollamaManager.createPullStream(environment.baseUrl, task.model);
    task.client = client;
    task.stream = stream;
    if (['canceled', 'canceling'].includes(task.status)) {
      abortOllamaDownloadTask(task);
      throw new Error('Download canceled.');
    }
    for await (const progress of stream) {
      if (['canceled', 'canceling'].includes(task.status)) break;
      if (task.status === 'starting') {
        task.status = 'running';
      }
      updateDownloadTaskProgress(task, progress);
      sendOllamaDownloadProgressUpdate();
      if (ollamaManagerWindow && !ollamaManagerWindow.isDestroyed()) {
        ollamaManagerWindow.webContents.send('ollama-manager:pull-progress', {
          model: task.model,
          status: task.progressText,
        });
      }
    }
    if (!['canceled', 'canceling'].includes(task.status)) {
      task.status = 'completed';
      task.progressText = 'Download complete';
      task.percent = 100;
    } else {
      task.progressText = task.cleanupRequested
        ? 'Removing partial files...'
        : 'Canceled';
    }
    sendOllamaDownloadQueueUpdate();
  } catch (error) {
    task.status = ['canceled', 'canceling'].includes(task.status) ? 'canceled' : 'failed';
    task.error = error.message || String(error);
    task.progressText = task.status === 'canceled'
      ? (task.cleanupRequested ? 'Removing partial files...' : 'Canceled')
      : task.error;
    sendOllamaDownloadQueueUpdate();
  } finally {
    if (task.status === 'canceled') {
      await cleanupCanceledOllamaDownload(task);
    }
    task.progressText = task.status === 'canceled'
      ? 'Canceled and cleared'
      : task.progressText;
    activeOllamaDownload = null;
    sendOllamaDownloadQueueUpdate();
    finalizeOllamaDownloadTask(task);
    setTimeout(() => {
      const activeTaskId = activeOllamaDownload?.id ?? null;
      ollamaDownloadQueue = ollamaDownloadQueue.filter((item) =>
        item.id === activeTaskId || !['completed', 'canceled'].includes(item.status)
      );
      sendOllamaDownloadQueueUpdate();
    }, task.status === 'canceled' ? 0 : 1500);
    processNextOllamaDownload();
  }
}

function createServerSplashWindow() {
  const { workAreaSize } = screen.getPrimaryDisplay();
  const width = Math.min(1040, Math.max(760, workAreaSize.width - 48), workAreaSize.width);
  const height = Math.min(820, Math.max(620, workAreaSize.height - 48), workAreaSize.height);

  serverSplashWindow = new BrowserWindow({
    width,
    height,
    frame: false,
    alwaysOnTop: true,
    transparent: false,
    resizable: true,
    minimizable: true,
    closable: true,
    movable: true,
    minWidth: 760,
    minHeight: 620,
    icon: getWindowIconPath(),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: PRELOAD_PATH,
    },
  });
  serverSplashWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    appendLog(`Splash window failed to load (${errorCode}): ${errorDescription} - ${validatedURL || SERVER_SPLASH_PATH}`, true);
  });

  serverSplashWindow.loadURL(tempServer.getUrl(SERVER_SPLASH_PATH) + TELEMETRY_QUERY_STRING);
}

function createMainWindow() {

  if (tempServer) {
    tempServer.stop();
    tempServer = null;
  }

  const { workAreaSize } = screen.getPrimaryDisplay();
  const width = Math.max(1280, Math.floor(workAreaSize.width * 0.82));
  const height = Math.max(820, Math.floor(workAreaSize.height * 0.9));
  mainWindow = new BrowserWindow({
    width, height,
    minWidth: 1280, minHeight: 820,
    show: false,
    fullscreen: false,
    autoHideMenuBar: true,
    backgroundColor: '#141920',
    icon: getWindowIconPath(),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false,
      allowRunningInsecureContent: true,
      webSecurity: false,
    },
  });
  mainWindow.webContents.on('console-message', (event, level, message, line, sourceId) => {
    appendLog(`UI console [${level}] ${message} (${sourceId}:${line})`, level >= 2);
  });
  mainWindow.webContents.on('render-process-gone', (event, details) => {
    appendLog(`UI render process gone: ${details.reason}`, true);
  });
  mainWindow.webContents.on('did-finish-load', async () => {
    appendLog('UI finished loading.');
    try {
      const pageState = await mainWindow.webContents.executeJavaScript(`
        (() => {
          const html = document.documentElement?.outerHTML || '';
          const bodyText = document.body?.innerText?.trim() || '';
          return {
            title: document.title || '',
            bodyTextLength: bodyText.length,
            hasAppName: html.includes('Spring AI Playground'),
            hasVaadin: /vaadin|hilla/i.test(html),
            url: location.href
          };
        })()
      `);
      appendLog(`UI state: ${JSON.stringify(pageState)}`);
      const looksReady = pageState.hasAppName || pageState.hasVaadin || pageState.bodyTextLength > 0;
      if (!looksReady) {
        appendLog('UI loaded an unexpected blank page. Keeping splash visible.', true);
        return;
      }
    } catch (error) {
      appendLog(`Failed to inspect UI state: ${error.message || error}`, true);
      return;
    }
    if (serverSplashWindow && !serverSplashWindow.isDestroyed()) serverSplashWindow.close();
    copyLaunchLogsToClipboardIfEnabled();
    if (!mainWindow.isDestroyed()) { mainWindow.show(); mainWindow.focus(); }
  });
  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    appendLog(`UI failed to load (${errorCode}): ${errorDescription} - ${validatedURL}`, true);
  });
  mainWindow.on('closed', () => { mainWindow = null; });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
      shell.openExternal(url);
      return { action: 'deny' };
    });
}

function createNamedConfig(name, yamlText) {
  const index = readConfigIndex();
  const normalizedName = sanitizeConfigName(name);
  if (!normalizedName) throw new Error('A config name is required.');
  if (index.configs.some(config => config.name.toLowerCase() === normalizedName.toLowerCase())) {
    throw new Error(`A config named "${normalizedName}" already exists.`);
  }
  const existingIds = new Set(index.configs.map(config => config.id));
  const configId = createUniqueConfigId(normalizedName, existingIds);
  const config = { id: configId, name: normalizedName };
  saveYamlToConfig(configId, yamlText);
  index.configs.push(config);
  index.activeConfigId = configId;
  saveConfigIndex(index);
  return buildConfigLoadPayload(configId);
}

function appendLog(message, isError = false) {
  fullLogBuffer += message + '\n';
  if (serverSplashWindow && !serverSplashWindow.isDestroyed()) {
    serverSplashWindow.webContents.send(isError ? 'server-error' : 'server-log', message);
  }
  if (isError) console.error(message);
  else console.log(message);
}

function copyLaunchLogsToClipboardIfEnabled() {
  if (!autoCopyLaunchLogsPending) return;
  autoCopyLaunchLogsPending = false;
  try {
    const index = readConfigIndex();
    if (index.preferences?.autoCopyLogs) clipboard.writeText(fullLogBuffer);
  } catch {
  }
}

function detectDynamicServerUrl(output) {
  if (dynamicServerPort) return;
  const text = String(output || '');
  const portMatch = text.match(/(?:Tomcat|Netty) started on port(?:\(s\))?:?\s+(\d+)/i);
  if (!portMatch) return;
  dynamicServerPort = parseInt(portMatch[1], 10);
  dynamicServerUrl = `http://localhost:${dynamicServerPort}`;
  appendLog(`Detected Spring Boot running on dynamic port: ${dynamicServerPort}`);
  sendServerSplashState();
}

function sendServerSplashState() {
  if (!serverSplashWindow || serverSplashWindow.isDestroyed()) return;
  const index = readConfigIndex();
  const selectedConfigId = currentConfigId || index.activeConfigId;
  const selectedConfig = getConfigRecord(selectedConfigId, index);
  const configPath = selectedConfig ? getConfigFilePath(selectedConfig.id) : null;
  serverSplashWindow.webContents.send('launch-state', {
    selectedConfig: selectedConfig ? { id: selectedConfig.id, name: selectedConfig.name, path: configPath } : null,
    serverRunning: Boolean(serverProcess),
    serverUrl: dynamicServerUrl,
    preflight: null,
    preferences: {
      autoCopyLogs: index.preferences?.autoCopyLogs ?? true,
      skipOllamaCheck: index.preferences?.skipOllamaCheck ?? false,
    },
    launchCommand: lastLaunchCommand,
    readiness: launchReadinessState,
  });
}

function killProcess(pid, signal = 'SIGTERM') {
  return new Promise((resolve) => {
    const normalizedPid = parseInt(pid, 10);
    if (Number.isNaN(normalizedPid)) {
      resolve();
      return;
    }

    if (process.platform === 'win32') {
      const args = ['/pid', String(normalizedPid), '/T'];
      if (signal === 'SIGKILL') args.push('/F');
      execFile('taskkill', args, (error) => {
        if (error && error.code !== 128 && !/not found|no running instance/i.test(error.message || '')) {
          appendLog(`Kill error (${signal}): ${error.message}`, true);
        }
        resolve();
      });
      return;
    }

    treeKill(normalizedPid, signal, (error) => {
      if (error && error.code !== 'ESRCH') {
        appendLog(`Kill error (${signal}): ${error.message}`, true);
      }
      resolve();
    });
  });
}

async function tryActuatorShutdown() {
  if (!dynamicServerUrl) return false;

  try {
    appendLog('Trying actuator shutdown...');
    await new Promise((resolve, reject) => {
      const req = http.request(`${dynamicServerUrl}/actuator/shutdown`, { method: 'POST' }, (res) => {
        res.resume();
        resolve();
      });
      req.on('error', reject);
      req.setTimeout(4000, () => reject(new Error('Actuator timeout')));
      req.end();
    });
    appendLog('Actuator shutdown request sent.');
    return true;
  } catch (error) {
    appendLog(`Actuator failed: ${error.message}`, true);
    return false;
  }
}

async function stopSpringServer() {
  if (!serverProcess) return;
  const targetProcess = serverProcess;
  const pid = targetProcess.pid;
  const start = Date.now();
  const MAX_WAIT_MS = getShutdownWaitMs(activeConfigPath || getConfigFilePath(currentConfigId || readConfigIndex().activeConfigId));
  const ACTUATOR_WAIT_MS = 5000;
  const maxWaitSeconds = Math.round(MAX_WAIT_MS / 1000);

  appendLog(`Stopping Spring server gracefully... (waiting up to ${maxWaitSeconds} seconds)`);

  let resolved = false;
  let closeListener = null;

  const cleanup = () => {
    if (serverProcess === targetProcess) {
      serverProcess = null;
    }
    dynamicServerPort = null;
    dynamicServerUrl = null;
  };

  const finish = (resolve) => {
    if (resolved) return;
    resolved = true;
    if (closeListener) targetProcess.off('close', closeListener);
    cleanup();
    resolve();
  };

  return new Promise(async (resolve) => {
    closeListener = () => {
      appendLog(`Spring server closed cleanly after ${Math.round((Date.now() - start) / 1000)}s.`);
      finish(resolve);
    };
    targetProcess.once('close', closeListener);

    const actuatorOk = await tryActuatorShutdown();
    const actuatorDeadline = Date.now() + ACTUATOR_WAIT_MS;

    const waitForClose = (deadline, callback) => {
      const checkClosed = () => {
        if (resolved) {
          return;
        }
        if (!serverProcess) {
          appendLog('Spring server stopped completely.');
          finish(resolve);
          return;
        }
        if (Date.now() >= deadline) {
          callback();
          return;
        }
        setTimeout(checkClosed, 300);
      };
      checkClosed();
    };

    if (actuatorOk) {
      appendLog('Waiting for graceful shutdown via actuator...');
      waitForClose(actuatorDeadline, async () => {
        if (resolved) return;
        appendLog('Actuator shutdown is taking longer than expected. Sending SIGTERM...');
        await killProcess(pid, 'SIGTERM');
      });
    } else {
      appendLog('Sending SIGTERM...');
      await killProcess(pid, 'SIGTERM');
    }

    const forceDeadline = start + MAX_WAIT_MS;
    const waitForForcedKill = () => {
      if (resolved) {
        return;
      }
      if (!serverProcess) {
        appendLog('Spring server stopped completely.');
        finish(resolve);
        return;
      }
      if (Date.now() >= forceDeadline) {
        appendLog(`Graceful shutdown timeout after ${Math.round((Date.now() - start) / 1000)}s -> forcing SIGKILL`, true);
        killProcess(pid, 'SIGKILL').finally(() => {
          setTimeout(() => {
            if (!resolved) {
              appendLog('Force cleanup fallback', true);
              finish(resolve);
            }
          }, 2000);
        });
        return;
      }
      setTimeout(waitForForcedKill, 300);
    };

    setTimeout(waitForForcedKill, actuatorOk ? ACTUATOR_WAIT_MS : 0);
  });
}

async function shutdownApplication({ exitCode = 0, logMessage = null } = {}) {
  if (shutdownPromise) return shutdownPromise;
  shutdownPromise = (async () => {
    isQuitting = true;
    const shutdownWaitMs = getShutdownWaitMs(activeConfigPath || getConfigFilePath(currentConfigId || readConfigIndex().activeConfigId));
    const shutdownWaitSeconds = Math.round(shutdownWaitMs / 1000);

    if (tempServer) {
      tempServer.stop();
      tempServer = null;
    }

    if (serverProcess) {
      appendLog(logMessage || `App is quitting. Waiting up to ${shutdownWaitSeconds} seconds for Spring server to shut down...`);
      await stopSpringServer();
    }

    appendLog('All processes stopped. Exiting app now.');
    allowAppExit = true;
    if (exitCode === 0) app.quit();
    else app.exit(exitCode);
  })();
  return shutdownPromise;
}

function handleFatalError(errorMessage) {
  try {
    clipboard.writeText(fullLogBuffer);
  } catch {
  }

  isQuitting = true;

  if (serverProcess) {
    stopSpringServer().finally(() => {
      dialog.showMessageBoxSync({
        type: 'error',
        title: 'Server Error',
        message: 'Failed to start Spring AI Server.',
        detail: errorMessage + '\n\nLogs have been copied to your clipboard.',
        buttons: ['OK']
      });
      app.exit(1);
    });
    return;
  }

  dialog.showMessageBoxSync({
    type: 'error',
    title: 'Server Error',
    message: 'Failed to start Spring AI Server.',
    detail: errorMessage + '\n\nLogs have been copied to your clipboard.',
    buttons: ['OK']
  });
  app.exit(1);
}

function startSpringServer() {
  serverReadyStartTime = Date.now();
  const launchToken = ++currentLaunchToken;
  autoCopyLaunchLogsPending = true;
  launchReadinessState = {
    phase: 'starting',
    timedOut: false,
    timeoutMs: null,
    message: 'Preparing the launch environment.',
  };

  const jarPath = getJarPath();
  if (!jarPath) {
    handleFatalError("Executable JAR not found. Run 'mvn package' first.");
    return;
  }

  const jrePath = isDev
    ? 'java'
    : path.join(process.resourcesPath, 'jre-bundle', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');

  if (!isDev && !fs.existsSync(jrePath)) {
    handleFatalError(
      `Bundled JRE not found at ${jrePath}. The installer may be corrupted or the JRE bundling step (electron/scripts/prepare-resources.mjs) did not run. Reinstall the application.`
    );
    return;
  }

  const index = readConfigIndex();
  const selectedConfigId = currentConfigId || index.activeConfigId;
  const configPath = activeConfigPath || getConfigFilePath(selectedConfigId);

  const spawnConfig = buildSpawnArguments(jrePath, jarPath, configPath, index.runtime, selectedConfigId);

  spawnConfig.args.push('--management.endpoint.shutdown.enabled=true');
  spawnConfig.args.push('--management.endpoints.web.exposure.include=health,shutdown');

  lastLaunchCommand = `${jrePath} ${spawnConfig.args.join(' ')}`;

  appendLog(`Starting JAR: ${jarPath}`);
  appendLog(`Using JRE: ${jrePath}`);
  appendLog(`Using config: ${configPath}`);
  appendLog(`Launch command: ${lastLaunchCommand}`);

  const envKeys = Object.keys(spawnConfig.env).filter(key => !(key in process.env) || process.env[key] !== spawnConfig.env[key]).sort();
  if (envKeys.length) {
    appendLog(`Launch environment keys: ${envKeys.join(', ')}`);
  }

  sendServerSplashState();

  dynamicServerPort = null;
  dynamicServerUrl = null;

  serverProcess = spawn(jrePath, spawnConfig.args, { detached: false, env: spawnConfig.env });

  serverProcess.on('error', (error) => {
    serverProcess = null;
    autoCopyLaunchLogsPending = false;
    if (isQuitting || restartToConfigAfterStop) return;
    launchReadinessState = {
      phase: 'failed',
      timedOut: false,
      timeoutMs: null,
      message: 'Server process failed to start.',
    };
    sendServerSplashState();
    handleFatalError(`Failed to start server process:\n${error.message || String(error)}`);
  });

  serverProcess.stdout.on('data', (data) => {
    const output = data.toString().trim();
    appendLog(output);
    detectDynamicServerUrl(output);
  });

  serverProcess.stderr.on('data', (data) => {
    const output = data.toString().trim();
    appendLog(output, true);
    detectDynamicServerUrl(output);
  });

  serverProcess.on('close', (code) => {
    const shouldReturnToConfig = restartToConfigAfterStop;
    serverProcess = null;
    autoCopyLaunchLogsPending = false;
    dynamicServerPort = null;
    dynamicServerUrl = null;
    launchReadinessState = {
      phase: code === 0 || code === null ? 'stopped' : 'failed',
      timedOut: false,
      timeoutMs: null,
      message: code === 0 || code === null ? 'Server stopped.' : 'Server exited unexpectedly.',
    };
    sendServerSplashState();

    if (code !== 0 && code !== null && !isQuitting && !shouldReturnToConfig) {
      handleFatalError(`Server process exited unexpectedly with code ${code}`);
    }

    if (shouldReturnToConfig && !isQuitting) {
      restartToConfigAfterStop = false;
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.close();
      if (serverSplashWindow && !serverSplashWindow.isDestroyed()) serverSplashWindow.close();
      createConfigWindow();
    }
  });

  checkServerReady(launchToken);
}

function checkServerReady(launchToken) {
  if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;

  const elapsed = Date.now() - serverReadyStartTime;
  const timeoutMs = dynamicServerUrl ? UI_READY_GRACE_TIMEOUT_MS : SERVER_READY_TIMEOUT_MS;
  if (elapsed > timeoutMs) {
    if (!launchReadinessState.timedOut) {
      launchReadinessState = {
        phase: dynamicServerUrl ? 'waiting-for-ui' : 'waiting-for-server',
        timedOut: true,
        timeoutMs,
        message: 'Startup is taking longer than expected. The app may still be downloading models or warming up.',
      };
      appendLog(`Server startup timed out after ${timeoutMs / 1000}s, but the launcher will stay open and keep streaming logs.`, true);
      appendLog('You can keep waiting, retry the readiness check, switch config, or quit from the launcher.');
      sendServerSplashState();
    }
    setTimeout(() => checkServerReady(launchToken), 2000);
    return;
  }

  if (!dynamicServerUrl) {
    launchReadinessState = {
      phase: 'waiting-for-server',
      timedOut: false,
      timeoutMs: null,
      message: 'Waiting for the local app URL...',
    };
    sendServerSplashState();
    setTimeout(() => checkServerReady(launchToken), 500);
    return;
  }

  httpGetJson(new URL('/actuator/health', dynamicServerUrl))
    .then(({ statusCode, body }) => {
      if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;
      let healthStatus = null;
      try {
        healthStatus = JSON.parse(body || '{}')?.status || null;
      } catch (error) {
        appendLog(`Actuator health response could not be parsed yet: ${error.message}`, true);
      }

      if (statusCode === 200 && healthStatus === 'UP') {
        launchReadinessState = {
          phase: 'ready',
          timedOut: false,
          timeoutMs: null,
          message: 'Actuator reports the server is ready. Opening the UI...',
        };
        sendServerSplashState();
        appendLog('Actuator reports server is UP. Launching UI...');
        setTimeout(() => {
          if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;
          createMainWindow();
          mainWindow.loadURL(dynamicServerUrl);
        }, 500);
        return;
      }

      launchReadinessState = {
        phase: 'waiting-for-health',
        timedOut: false,
        timeoutMs: null,
        message: 'Server is running. Waiting for health checks to turn green...',
      };
      sendServerSplashState();
      appendLog(`Waiting for actuator health... status=${statusCode}, health=${healthStatus || 'unknown'}`);
      setTimeout(() => checkServerReady(launchToken), 1000);
    })
    .catch((healthError) => {
      if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;
      appendLog(`Actuator health check not ready yet: ${healthError.message || healthError}`);
      httpGetText(dynamicServerUrl)
        .then(({ statusCode, body }) => {
          if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;
          const isHtmlResponse = /<!doctype html>/i.test(body);
          const looksLikeApp = /Spring AI Playground|vaadin|hilla/i.test(body);
          const hasUsefulBody = Boolean((body || '').trim());
          const canOpenUi = (statusCode >= 200 && statusCode < 400) && (isHtmlResponse || looksLikeApp || hasUsefulBody);
          if (canOpenUi) {
            launchReadinessState = {
              phase: 'ready',
              timedOut: false,
              timeoutMs: null,
              message: 'App UI responded. Opening the window...',
            };
            sendServerSplashState();
            appendLog(`Server responded with status=${statusCode}. Launching UI...`);
            setTimeout(() => {
              if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;
              createMainWindow();
              mainWindow.loadURL(dynamicServerUrl);
            }, 500);
            return;
          }
          launchReadinessState = {
            phase: 'waiting-for-ui',
            timedOut: false,
            timeoutMs: null,
            message: 'Server is reachable. Waiting for the UI to finish rendering...',
          };
          sendServerSplashState();
          appendLog(`Waiting for app UI... status=${statusCode}, html=${isHtmlResponse}, app=${looksLikeApp}`);
          setTimeout(() => checkServerReady(launchToken), 1000);
        })
        .catch((rootError) => {
          if (launchToken !== currentLaunchToken || restartToConfigAfterStop) return;
          launchReadinessState = {
            phase: 'waiting-for-ui',
            timedOut: false,
            timeoutMs: null,
            message: 'Server port is open. Waiting for the app UI...',
          };
          sendServerSplashState();
          appendLog(`Root URL not reachable yet: ${rootError.message || rootError}`);
          setTimeout(() => checkServerReady(launchToken), 1000);
        });
    });
}

function launchApplicationWithConfig(configPath) {
  activeConfigPath = configPath;
  restartToConfigAfterStop = false;
  createServerSplashWindow();
  sendServerSplashState();
  if (configWindow && !configWindow.isDestroyed()) configWindow.close();
  prepareOllamaForLaunch(configPath).then(async (shouldProceed) => {
    if (shouldProceed === false) {
      if (serverSplashWindow && !serverSplashWindow.isDestroyed()) serverSplashWindow.close();
      createConfigWindow();
      return;
    }
    activeConfigPath = await resolveLaunchConfigPath(configPath);
    startSpringServer();
  }).catch(error => {
    handleFatalError(error.message || String(error));
  });
}

ipcMain.handle('config:load', async () => buildConfigLoadPayload());

ipcMain.handle('config:get-tools-preset', async () => {
  try {
    const prefPath = getDefaultToolsPreferencePath();
    const catalog = loadPresetCatalog();
    let active = { state: 'pending' };
    if (fs.existsSync(prefPath)) {
      const pref = JSON.parse(fs.readFileSync(prefPath, 'utf8'));
      const presetId = pref?.preset;
      if (presetId) {
        const preset = catalog.presets.find(p => p.id === presetId);
        if (preset) {
          const toolCount = preset.tools && preset.tools.length > 0 ? preset.tools.length : catalog.defaultToolCount;
          active = { state: 'curated', presetId, displayName: preset.displayName, toolCount };
        } else {
          active = { state: 'unknown', presetId };
        }
      }
    }
    return { active, presets: catalog.presets, total: catalog.defaultToolCount };
  } catch (error) {
    console.error('Failed to read tools preset:', error);
    return { active: { state: 'error', message: error.message }, presets: [], total: 0 };
  }
});

ipcMain.handle('config:set-tools-preference', async (event, preference) => {
  if (!preference || typeof preference !== 'object') throw new Error('preference payload required');
  const catalog = loadPresetCatalog();
  if (preference.preset && !catalog.presets.find(p => p.id === preference.preset)) {
    throw new Error(`Unknown preset: ${preference.preset}`);
  }
  writeDefaultToolsPreference({
    schemaVersion: 3,
    preset: preference.preset || null,
    include: {
      names: Array.isArray(preference.include?.names) ? preference.include.names : [],
      tags: Array.isArray(preference.include?.tags) ? preference.include.tags : [],
      categories: Array.isArray(preference.include?.categories) ? preference.include.categories : [],
    },
    exclude: {
      names: Array.isArray(preference.exclude?.names) ? preference.exclude.names : [],
      tags: Array.isArray(preference.exclude?.tags) ? preference.exclude.tags : [],
      categories: [],
    },
  });
  return { ok: true };
});

ipcMain.handle('config:get-tools-curation-data', async () => {
  try {
    const specPaths = getDefaultToolSpecsPaths();
    const tools = [];
    const tagSet = new Set();
    for (const filepath of specPaths) {
      const arr = JSON.parse(fs.readFileSync(filepath, 'utf8'));
      if (!Array.isArray(arr)) continue;
      for (const spec of arr) {
        if (!spec?.name) continue;
        const tags = Array.isArray(spec.tags) ? spec.tags.slice() : [];
        const description = typeof spec.description === 'string'
          ? spec.description.replace(/\s+/g, ' ').trim().slice(0, 220)
          : '';
        tools.push({ name: spec.name, category: spec.category || null, tags, description });
        for (const tag of tags) tagSet.add(tag);
      }
    }
    let categories = [];
    const catalogDir = getCatalogDir();
    if (catalogDir) {
      const catFile = path.join(catalogDir, 'default-tool-categories.json');
      if (fs.existsSync(catFile)) {
        const catData = JSON.parse(fs.readFileSync(catFile, 'utf8'));
        if (Array.isArray(catData)) {
          categories = catData.map(c => ({
            id: c.id || c.name,
            displayName: c.displayName || c.id || c.name,
            description: c.description || '',
          }));
        }
      }
    }
    return {
      tools: tools.sort((a, b) => a.name.localeCompare(b.name)),
      categories,
      tags: [...tagSet].sort(),
    };
  } catch (error) {
    console.error('Failed to load curation data:', error);
    return { tools: [], categories: [], tags: [] };
  }
});

ipcMain.handle('config:save', async (event, payload) => {
  const index = readConfigIndex();
  const selectedConfigId = currentConfigId || index.activeConfigId;
  saveYamlToConfig(selectedConfigId, payload?.yamlText ?? '');
  ensureRuntimeSettings(index);
  index.runtime.jvmOptions = parseArgsText(payload?.jvmOptionsText);
  index.runtime.appArgs = parseArgsText(payload?.appArgsText);
  saveSecretsForConfig(selectedConfigId, payload?.secretValues || {});
  ensurePreferences(index);
  index.preferences.skipOllamaCheck = payload?.preferences?.skipOllamaCheck ?? index.preferences.skipOllamaCheck;
  saveConfigIndex(index);
  return buildConfigLoadPayload(selectedConfigId);
});

ipcMain.handle('config:select', async (event, configId) => buildConfigLoadPayload(configId));

ipcMain.handle('config:select-provider', async (event, providerType) => {
  const index = readConfigIndex();
  const config = ensureProviderConfig(index, providerType);
  index.activeConfigId = config.id;
  saveConfigIndex(index);
  return buildConfigLoadPayload(config.id);
});

ipcMain.handle('config:save-as', async (event, name, payload) => {
  const created = createNamedConfig(name, payload?.yamlText ?? '');
  const index = readConfigIndex();
  ensureRuntimeSettings(index);
  index.runtime.jvmOptions = parseArgsText(payload?.jvmOptionsText);
  index.runtime.appArgs = parseArgsText(payload?.appArgsText);
  saveSecretsForConfig(created.activeConfigId, payload?.secretValues || {});
  ensurePreferences(index);
  index.preferences.skipOllamaCheck = payload?.preferences?.skipOllamaCheck ?? index.preferences.skipOllamaCheck;
  saveConfigIndex(index);
  return buildConfigLoadPayload(created.activeConfigId);
});

ipcMain.handle('config:export', async (event, payload) => {
  const index = readConfigIndex();
  const selectedConfigId = currentConfigId || index.activeConfigId;
  const selectedConfig = getConfigRecord(selectedConfigId, index);
  const fileName = `${sanitizeConfigName(selectedConfig?.name || 'spring-ai-playground-config')}.json`;
  const result = await dialog.showSaveDialog({
    title: 'Export Spring AI Playground Config',
    defaultPath: path.join(app.getPath('documents'), fileName),
    filters: [{ name: 'JSON', extensions: ['json'] }],
  });
  if (result.canceled || !result.filePath) return { canceled: true };
  const bundle = buildConfigBundle(selectedConfigId, payload);
  fs.writeFileSync(result.filePath, `${JSON.stringify(bundle, null, 2)}\n`, 'utf8');
  return { canceled: false, path: result.filePath, name: bundle.config.name };
});

ipcMain.handle('config:import', async () => {
  const result = await dialog.showOpenDialog({
    title: 'Import Spring AI Playground Config',
    properties: ['openFile'],
    filters: [{ name: 'JSON', extensions: ['json'] }],
  });
  if (result.canceled || !result.filePaths?.length) return { canceled: true };
  const bundle = JSON.parse(fs.readFileSync(result.filePaths[0], 'utf8'));
  return applyImportedConfigBundle(bundle);
});

ipcMain.handle('config:reset', async () => {
  const configDir = getConfigDirectory();

  if (serverProcess) {
    await stopSpringServer();
  }

  try {
    if (fs.existsSync(configDir)) {
      fs.rmSync(configDir, { recursive: true, force: true });
    }
    clearSecretsStoreCache();
    const preferenceFile = getDefaultToolsPreferencePath();
    if (fs.existsSync(preferenceFile)) {
      fs.unlinkSync(preferenceFile);
    }
  } catch (error) {
    console.error("Failed to delete config directory:", error);
  }

  isQuitting = true;
  app.relaunch({ args: process.argv.slice(1).concat(['--relaunch']) });
  app.exit(0);
});

ipcMain.handle('app:factoryReset', async () => {
  if (serverProcess) {
    await stopSpringServer();
  }
  try {
    const configDir = getConfigDirectory();
    if (fs.existsSync(configDir)) fs.rmSync(configDir, { recursive: true, force: true });
    clearSecretsStoreCache();
    const springHome = getSpringAiPlaygroundHomeDir();
    if (fs.existsSync(springHome)) fs.rmSync(springHome, { recursive: true, force: true });
  } catch (error) {
    console.error('Failed to factory-reset user data:', error);
  }
  isQuitting = true;
  app.relaunch({ args: process.argv.slice(1).concat(['--relaunch', '--factory-reset']) });
  app.exit(0);
});

ipcMain.handle('config:delete', async () => {
  const index = readConfigIndex();
  if (index.configs.length <= 1) throw new Error('At least one setting must remain.');
  const selectedConfigId = currentConfigId || index.activeConfigId;
  const selectedConfig = getConfigRecord(selectedConfigId, index);
  if (!selectedConfig) throw new Error('Selected config was not found.');
  const selectedProvider = getCachedProviderType(selectedConfig.id);
  const configPath = getConfigFilePath(selectedConfig.id);
  index.configs = index.configs.filter(config => config.id !== selectedConfig.id);
  providerTypeCache.delete(selectedConfig.id);
  if (fs.existsSync(configPath)) fs.unlinkSync(configPath);
  deleteSecretsForConfig(selectedConfig.id);
  const remainingSameProvider = index.configs.find(config => getCachedProviderType(config.id) === selectedProvider);
  index.activeConfigId = remainingSameProvider?.id || index.configs[0]?.id;
  saveConfigIndex(index);
  return buildConfigLoadPayload(index.activeConfigId);
});

ipcMain.handle('config:launch', async (event, payload) => {
  const index = readConfigIndex();
  const selectedConfigId = currentConfigId || index.activeConfigId;
  const configPath = getConfigFilePath(selectedConfigId);
  saveYamlToConfig(selectedConfigId, payload?.yamlText ?? '');
  ensureRuntimeSettings(index);
  index.runtime.jvmOptions = parseArgsText(payload?.jvmOptionsText);
  index.runtime.appArgs = parseArgsText(payload?.appArgsText);
  saveSecretsForConfig(selectedConfigId, payload?.secretValues || {});
  ensurePreferences(index);
  index.preferences.skipOllamaCheck = payload?.preferences?.skipOllamaCheck ?? index.preferences.skipOllamaCheck;
  index.meta.hasCompletedInitialSetup = true;
  index.meta.initialSetupCompletedVersion = app.getVersion();
  saveConfigIndex(index);
  launchApplicationWithConfig(configPath);
  return buildConfigLoadPayload(selectedConfigId);
});

ipcMain.handle('config:environment-info', async (event, yamlText) => getOllamaEnvironmentInfo(yamlText));

ipcMain.handle('ollama-manager:open', async (event, payload) => {
  ollamaManagerContext = {
    yamlText: payload?.yamlText ?? '',
    configId: payload?.configId ?? null,
    configName: payload?.configName ?? 'Current setting',
    environmentInfo: payload?.environmentInfo ?? null,
  };
  createOllamaManagerWindow();
  return { ok: true };
});

ipcMain.handle('ollama-manager:get-context', async () => {
  const liveEnvironment = await getOllamaEnvironmentInfo(ollamaManagerContext.yamlText);
  const seeded = ollamaManagerContext.environmentInfo || {};
  const useLiveInstalledState = Boolean(liveEnvironment.running);
  const installedModels = useLiveInstalledState
    ? (Array.isArray(liveEnvironment.installedModels) ? liveEnvironment.installedModels : [])
    : (Array.isArray(seeded.installedModels) ? seeded.installedModels : []);
  const installedChatModels = useLiveInstalledState
    ? (Array.isArray(liveEnvironment.installedChatModels) ? liveEnvironment.installedChatModels : [])
    : (Array.isArray(seeded.installedChatModels) ? seeded.installedChatModels : []);
  const installedEmbeddingModels = useLiveInstalledState
    ? (Array.isArray(liveEnvironment.installedEmbeddingModels) ? liveEnvironment.installedEmbeddingModels : [])
    : (Array.isArray(seeded.installedEmbeddingModels) ? seeded.installedEmbeddingModels : []);
  return {
    ...ollamaManagerContext,
    environment: {
      ...seeded,
      ...liveEnvironment,
      installedModels: [...new Set(installedModels.filter(Boolean))],
      installedChatModels: [...new Set(installedChatModels.filter(Boolean))],
      installedEmbeddingModels: [...new Set(installedEmbeddingModels.filter(Boolean))],
    },
  };
});

ipcMain.handle('ollama-manager:get-download-queue', async () => ({
  downloads: getSerializableDownloadQueue(),
}));

ipcMain.handle('config:fit-window', async () => {
  await fitConfigWindowToContent();
  return { ok: true };
});

ipcMain.handle('ollama-manager:fit-window', async () => {
  await fitOllamaManagerWindowToContent();
  return { ok: true };
});

ipcMain.handle('ollama-manager:request-close', async () => {
  if (!ollamaManagerWindow || ollamaManagerWindow.isDestroyed()) return { closed: true };
  if (!hasPendingOllamaDownloads()) {
    allowOllamaManagerWindowClose = true;
    ollamaManagerWindow.close();
    return { closed: true };
  }
  const choice = dialog.showMessageBoxSync(ollamaManagerWindow, {
    type: 'warning',
    title: 'Leave model manager?',
    buttons: ['Stay here', 'Leave and remove downloads'],
    defaultId: 0,
    cancelId: 0,
    noLink: true,
    message: 'There are downloads in progress or queued.',
    detail: 'If you leave now, the active download will stop, queued downloads will be removed, and partially downloaded files for the active model will be cleared.',
  });
  if (choice !== 1) return { closed: false, canceled: true };
  if (ollamaManagerCloseInProgress) return { closed: false, busy: true };
  ollamaManagerCloseInProgress = true;
  try {
    await cancelAllOllamaDownloads();
    allowOllamaManagerWindowClose = true;
    if (ollamaManagerWindow && !ollamaManagerWindow.isDestroyed()) {
      ollamaManagerWindow.close();
    }
    return { closed: true };
  } finally {
    ollamaManagerCloseInProgress = false;
  }
});

ipcMain.handle('ollama-manager:enqueue-pull', async (event, payload) => {
  const model = String(payload?.model || '').trim();
  if (!model) throw new Error('Model name is required.');
  const existingTask = [activeOllamaDownload, ...ollamaDownloadQueue]
    .filter(Boolean)
    .find((task) => task.model === model && ['queued', 'starting', 'running'].includes(task.status));
  if (existingTask) {
    sendOllamaDownloadQueueUpdate(event.sender);
    return { ok: true, taskId: existingTask.id };
  }
  const task = createOllamaDownloadTask(model);
  ollamaDownloadQueue.push(task);
  sendOllamaDownloadQueueUpdate(event.sender);
  processNextOllamaDownload();
  return { ok: true, taskId: task.id };
});

ipcMain.handle('ollama-manager:cancel-download', async (event, payload) => {
  const result = cancelOllamaDownload(payload?.taskId);
  sendOllamaDownloadQueueUpdate(event.sender);
  return result;
});

ipcMain.handle('ollama-manager:list-installed', async () => {
  const environment = await getOllamaEnvironmentInfo(ollamaManagerContext.yamlText);
  const seeded = ollamaManagerContext.environmentInfo || {};
  const useLiveInstalledState = Boolean(environment.running);
  const installedModels = useLiveInstalledState
    ? (Array.isArray(environment.installedModels) ? environment.installedModels : [])
    : (Array.isArray(seeded.installedModels) ? seeded.installedModels : []);
  const installedChatModels = useLiveInstalledState
    ? (Array.isArray(environment.installedChatModels) ? environment.installedChatModels : [])
    : (Array.isArray(seeded.installedChatModels) ? seeded.installedChatModels : []);
  const installedEmbeddingModels = useLiveInstalledState
    ? (Array.isArray(environment.installedEmbeddingModels) ? environment.installedEmbeddingModels : [])
    : (Array.isArray(seeded.installedEmbeddingModels) ? seeded.installedEmbeddingModels : []);
  return {
    installedModels: [...new Set(installedModels.filter(Boolean))],
    installedChatModels: [...new Set(installedChatModels.filter(Boolean))],
    installedEmbeddingModels: [...new Set(installedEmbeddingModels.filter(Boolean))],
  };
});

ipcMain.handle('ollama-manager:delete', async (event, payload) => {
  const environment = await getOllamaEnvironmentInfo(ollamaManagerContext.yamlText);
  await ollamaManager.deleteModel(environment.baseUrl, payload?.model);
  return { ok: true };
});

ipcMain.handle('ollama-manager:copy', async (event, payload) => {
  const environment = await getOllamaEnvironmentInfo(ollamaManagerContext.yamlText);
  await ollamaManager.copyModel(environment.baseUrl, payload?.source, payload?.destination);
  return { ok: true };
});

ipcMain.handle('ollama-manager:open-external', async (event, url) => {
  await shell.openExternal(String(url || 'https://ollama.com/search'));
  return { ok: true };
});

ipcMain.handle('config:open-ollama-download', async () => {
  await shell.openExternal('https://ollama.com/download');
  return { ok: true };
});

ipcMain.handle('app:launch-state', async () => {
  const index = readConfigIndex();
  const selectedConfigId = currentConfigId || index.activeConfigId;
  const selectedConfig = getConfigRecord(selectedConfigId, index);
  return {
    selectedConfig: selectedConfig ? { id: selectedConfig.id, name: selectedConfig.name, path: getConfigFilePath(selectedConfig.id) } : null,
    serverRunning: Boolean(serverProcess),
    serverUrl: dynamicServerUrl,
    preferences: { autoCopyLogs: index.preferences?.autoCopyLogs ?? true, skipOllamaCheck: index.preferences?.skipOllamaCheck ?? false },
    launchCommand: lastLaunchCommand,
    readiness: launchReadinessState,
  };
});

ipcMain.handle('app:set-auto-copy-logs', async (event, enabled) => {
  const index = readConfigIndex();
  ensurePreferences(index);
  index.preferences.autoCopyLogs = Boolean(enabled);
  saveConfigIndex(index);
  return { autoCopyLogs: index.preferences.autoCopyLogs };
});

ipcMain.handle('app:retry-launch-readiness', async () => {
  if (!serverProcess) {
    throw new Error('There is no running launch to retry.');
  }

  serverReadyStartTime = Date.now();
  launchReadinessState = {
    phase: dynamicServerUrl ? 'waiting-for-ui' : 'waiting-for-server',
    timedOut: false,
    timeoutMs: null,
    message: 'Retrying readiness checks while keeping the current launch running...',
  };
  appendLog('Retrying readiness checks from the launcher.');
  sendServerSplashState();
  checkServerReady(currentLaunchToken);
  return { ok: true };
});

ipcMain.handle('app:quit-launcher', async () => {
  await shutdownApplication({ exitCode: 0 });
  return { ok: true };
});

ipcMain.handle('app:restart-to-config', async () => {
  restartToConfigAfterStop = true;
  if (mainWindow && !mainWindow.isDestroyed()) { mainWindow.close(); mainWindow = null; }
  if (serverProcess) {
    appendLog('Stopping current server to return to config selection...');
    await stopSpringServer();
    if (restartToConfigAfterStop) {
      restartToConfigAfterStop = false;
      if (serverSplashWindow && !serverSplashWindow.isDestroyed()) serverSplashWindow.close();
      createConfigWindow();
    }
  } else {
    restartToConfigAfterStop = false;
    if (serverSplashWindow && !serverSplashWindow.isDestroyed()) serverSplashWindow.close();
    createConfigWindow();
  }
  return { ok: true };
});

app.whenReady().then(async () => {

  splashWindow = new BrowserWindow({
    width: 480,
    height: 280,
    frame: false,          // 타이틀바 없음
    transparent: true,     // 배경 투명
    alwaysOnTop: true,     // 항상 위
    center: true,          // 화면 중앙
    resizable: false,
    skipTaskbar: true,     // 작업표시줄 미표시
    webPreferences: {
      preload: PRELOAD_PATH,
      contextIsolation: true,
    },
  });
  splashWindow.loadFile(SPLASH_PATH, TELEMETRY_DISABLED ? { query: { telemetry: '0' } } : undefined);
  splashWindow.show();

  tempServer = await startTempServer();

  session.defaultSession.setPermissionCheckHandler((webContents, permission) => {
    if (permission === 'media' || permission === 'audioCapture' || permission === 'speechRecognition') return true;
    return false;
  });

  session.defaultSession.setPermissionRequestHandler((webContents, permission, callback) => {
    if (permission === 'media' || permission === 'audioCapture' || permission === 'speechRecognition') {
      if (process.platform === 'darwin') {
        ensureMacMicrophoneAccess()
          .then(({ granted }) => callback(granted))
          .catch(() => callback(false));
      } else {
        callback(true);
      }
      return;
    }
    callback(false);
  });

  ensureConfigStore();
  const index = readConfigIndex();

  // Hide splash but keep it alive so window-all-closed doesn't fire during
  // the async window-handoff below (createConfigWindow / launchApplicationWithConfig).
  // Close it once the replacement window exists.
  if (splashWindow && !splashWindow.isDestroyed()) splashWindow.hide();

  if (isFirstLaunch(index)) {
    createConfigWindow();
  } else {
    const configPath = getConfigFilePath(index.activeConfigId);
    launchApplicationWithConfig(configPath);
  }

  if (splashWindow && !splashWindow.isDestroyed()) splashWindow.close();
});

app.on('before-quit', async (event) => {
  if (allowAppExit) return;
  event.preventDefault();
  await shutdownApplication();
});

app.on('window-all-closed', () => app.quit());
