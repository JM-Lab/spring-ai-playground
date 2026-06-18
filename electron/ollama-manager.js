'use strict';

const yaml = require('js-yaml');

let ollamaClientModulePromise = null;

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
  } catch {
    return {};
  }
}

function parseYamlDocs(yamlText) {
  try {
    const docs = [];
    yaml.loadAll(yamlText, (doc) => {
      if (isPlainObject(doc)) docs.push(doc);
    });
    return docs;
  } catch {
    return [];
  }
}

function parseProfileList(value) {
  if (Array.isArray(value)) {
    return value.flatMap((item) => parseProfileList(item));
  }
  if (typeof value !== 'string') return [];
  return value.split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function getDocActivationProfiles(doc = {}) {
  return parseProfileList(doc?.spring?.config?.activate?.['on-profile']);
}

function mergeYamlDocs(docs = [], activeProfiles = []) {
  const activeSet = new Set(activeProfiles);
  return docs.reduce((merged, doc) => {
    const activationProfiles = getDocActivationProfiles(doc);
    if (activationProfiles.length && !activationProfiles.some((profile) => activeSet.has(profile))) {
      return merged;
    }
    return deepMerge(merged, doc);
  }, {});
}

function getDeclaredActiveProfiles(docs = []) {
  const mergedBaseDocs = mergeYamlDocs(docs, []);
  return parseProfileList(mergedBaseDocs?.spring?.profiles?.active);
}

function getDeclaredDefaultProfiles(docs = []) {
  const mergedBaseDocs = mergeYamlDocs(docs, []);
  return parseProfileList(mergedBaseDocs?.spring?.profiles?.default);
}

function getResolvedYamlObj({ defaultYamlText = '', overrideYamlText = '' } = {}) {
  const defaultDocs = parseYamlDocs(defaultYamlText);
  const overrideDocs = parseYamlDocs(overrideYamlText);
  const hasOverride = Boolean(String(overrideYamlText || '').trim());

  let activeProfiles = getDeclaredActiveProfiles(overrideDocs);
  if (!activeProfiles.length && !hasOverride) {
    activeProfiles = getDeclaredActiveProfiles(defaultDocs);
  }
  if (!activeProfiles.length && !hasOverride) {
    activeProfiles = getDeclaredDefaultProfiles(defaultDocs);
  }

  return deepMerge(
    mergeYamlDocs(defaultDocs, activeProfiles),
    mergeYamlDocs(overrideDocs, activeProfiles),
  );
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

function getPlaygroundSection(doc = {}) {
  return deepMerge(
    isPlainObject(getLegacySpringDoc(doc)?.playground) ? { ...getLegacySpringDoc(doc).playground } : {},
    isPlainObject(getSpringAiDoc(doc)?.playground) ? getSpringAiDoc(doc).playground : {},
  );
}

function getEffectiveYamlText(defaultYamlText = '', overrideYamlText = '') {
  const base = String(defaultYamlText || '').trim();
  const override = String(overrideYamlText || '').trim();
  if (!override) return base;
  if (!base) return override;
  return `${base}\n---\n${override}\n`;
}

function detectProviderType(yamlText = '') {
  const doc = getYamlObj(yamlText);
  return detectProviderTypeFromDoc(doc);
}

function detectProviderTypeFromDoc(doc = {}) {
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

function isOfficialOpenAiBaseUrl(baseUrl) {
  if (!baseUrl) return false;
  try {
    return new URL(String(baseUrl)).hostname === 'api.openai.com';
  } catch {
    return false;
  }
}

function hasEmbeddingModelConfig(yamlText = '') {
  const doc = getYamlObj(yamlText);
  return hasEmbeddingModelConfigFromDoc(doc);
}

function hasEmbeddingModelConfigFromDoc(doc = {}) {
  const modelSection = getModelSection(doc);
  const ollamaSection = getOllamaSection(doc);
  const openAiSection = getOpenAiSection(doc);
  return !!modelSection?.embedding ||
    !!ollamaSection?.embedding?.options?.model ||
    !!openAiSection?.embedding?.options?.model;
}

function isOllamaRequired(yamlText = '') {
  const doc = getYamlObj(yamlText);
  const providerType = detectProviderTypeFromDoc(doc);
  return isOllamaRequiredFromDoc(doc, providerType);
}

function isOllamaRequiredFromDoc(doc = {}, providerType = detectProviderTypeFromDoc(doc)) {
  if (providerType === 'ollama') return true;
  return getModelSection(doc)?.embedding === 'ollama';
}

function parseOllamaBaseUrl(yamlText = '') {
  const doc = getYamlObj(yamlText);
  return parseOllamaBaseUrlFromDoc(doc);
}

function parseOllamaBaseUrlFromDoc(doc = {}) {
  const explicitOllamaBaseUrl = getOllamaSection(doc)?.['base-url'];
  if (explicitOllamaBaseUrl) return String(explicitOllamaBaseUrl);
  const compatibleUrl = getOpenAiSection(doc)?.['base-url'];
  if (compatibleUrl && String(compatibleUrl).includes('11434')) return String(compatibleUrl).replace(/\/v1\/?$/i, '');
  return 'http://127.0.0.1:11434';
}

function getConfiguredChatModelInfo(yamlText = '') {
  const doc = getYamlObj(yamlText);
  return getConfiguredChatModelInfoFromDoc(doc);
}

function getConfiguredChatModelInfoFromDoc(doc = {}) {
  const provider = getModelSection(doc)?.chat || null;
  let model = null;
  if (provider === 'ollama') model = getOllamaSection(doc)?.chat?.options?.model || null;
  if (provider === 'openai') model = getOpenAiSection(doc)?.chat?.options?.model || null;
  return { provider, model };
}

function getConfiguredEmbeddingModelInfo(yamlText = '') {
  const doc = getYamlObj(yamlText);
  return getConfiguredEmbeddingModelInfoFromDoc(doc);
}

function getConfiguredEmbeddingModelInfoFromDoc(doc = {}) {
  const provider = getModelSection(doc)?.embedding || null;
  let model = null;
  if (provider === 'ollama') model = getOllamaSection(doc)?.embedding?.options?.model || null;
  if (provider === 'openai') model = getOpenAiSection(doc)?.embedding?.options?.model || null;
  return { provider, model };
}

function getRecommendedChatModels(yamlText = '') {
  const doc = getYamlObj(yamlText);
  return getRecommendedChatModelsFromDoc(doc);
}

function getRecommendedChatModelsFromDoc(doc = {}) {
  const models = getPlaygroundSection(doc)?.chat?.models;
  if (!Array.isArray(models)) return [];
  return [...new Set(models
    .map((model) => typeof model === 'string' ? model.trim() : '')
    .filter(Boolean))];
}

function normalizeOllamaModelName(name) {
  if (typeof name !== 'string') return null;
  const trimmed = name.trim();
  if (!trimmed) return null;
  return trimmed.replace(/:latest$/i, '');
}

function getInstalledModelName(modelEntry) {
  if (!modelEntry || typeof modelEntry !== 'object') return null;
  const candidate = modelEntry.name || modelEntry.model || null;
  return typeof candidate === 'string' && candidate.trim() ? candidate.trim() : null;
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

  const embeddingHints = ['embedding', 'embed', 'nomic-embed', 'mxbai', 'bge', 'snowflake-arctic-embed', 'all-minilm', 'granite-embedding'];
  if (embeddingHints.some(hint => signals.includes(hint))) return 'embedding';
  return 'chat';
}

function buildInstalledModelSet(names = []) {
  return new Set(names.flatMap((name) => {
    const normalized = normalizeOllamaModelName(name);
    return normalized && normalized !== name ? [name, normalized] : [name];
  }).filter(Boolean));
}

async function getOllamaClient(baseUrl) {
  if (!ollamaClientModulePromise) {
    ollamaClientModulePromise = import('ollama');
  }
  const ollamaModule = await ollamaClientModulePromise;
  const Ollama = ollamaModule.Ollama || ollamaModule.default?.Ollama;
  if (!Ollama) {
    throw new Error('Failed to load the ollama package.');
  }
  return new Ollama({ host: baseUrl });
}

function parseInstalledModels(parsed = {}) {
  const rawModels = Array.isArray(parsed?.models) ? parsed.models : [];
  const installedModels = rawModels.map(getInstalledModelName).filter(Boolean).sort((a, b) => a.localeCompare(b));
  const installedChatModels = rawModels.filter(item => classifyInstalledModel(item) === 'chat')
    .map(getInstalledModelName).filter(Boolean).sort((a, b) => a.localeCompare(b));
  const installedEmbeddingModels = rawModels.filter(item => classifyInstalledModel(item) === 'embedding')
    .map(getInstalledModelName).filter(Boolean).sort((a, b) => a.localeCompare(b));
  return {
    installedModels,
    installedChatModels,
    installedEmbeddingModels,
  };
}

async function listInstalledModels(baseUrl) {
  const client = await getOllamaClient(baseUrl);
  const parsed = await client.list();
  return parseInstalledModels(parsed);
}

async function getServerVersion(baseUrl) {
  const client = await getOllamaClient(baseUrl);
  const response = await client.version();
  return response?.version || null;
}

async function createPullStream(baseUrl, model) {
  if (!model || !String(model).trim()) throw new Error('Model name is required.');
  const client = await getOllamaClient(baseUrl);
  const stream = await client.pull({ model: String(model).trim(), stream: true });
  return { client, stream };
}

async function getOllamaEnvironmentInfo({ yamlText = '', defaultYamlText = '', ollamaInstalled = false } = {}) {
  const resolvedDoc = getResolvedYamlObj({ defaultYamlText, overrideYamlText: yamlText });
  const baseUrl = parseOllamaBaseUrlFromDoc(resolvedDoc);
  const providerType = detectProviderTypeFromDoc(resolvedDoc);
  const embeddingConfigured = hasEmbeddingModelConfigFromDoc(resolvedDoc);
  const ollamaRequired = isOllamaRequiredFromDoc(resolvedDoc, providerType);
  const chatModel = getConfiguredChatModelInfoFromDoc(resolvedDoc);
  const embeddingModel = getConfiguredEmbeddingModelInfoFromDoc(resolvedDoc);
  const recommendedModels = getRecommendedChatModelsFromDoc(resolvedDoc);

  let running = false;
  let version = null;
  let error = null;
  let installedModels = [];
  let installedChatModels = [];
  let installedEmbeddingModels = [];

  try {
    const installed = await listInstalledModels(baseUrl);
    const serverVersion = await getServerVersion(baseUrl);
    running = true;
    version = serverVersion;
    installedModels = installed.installedModels;
    installedChatModels = installed.installedChatModels;
    installedEmbeddingModels = installed.installedEmbeddingModels;
  } catch (requestError) {
    error = requestError.message || String(requestError);
  }

  const installedModelSet = buildInstalledModelSet(installedModels);
  return {
    ollamaInstalled,
    ollamaRequired,
    providerType,
    embeddingConfigured,
    recommendedModels,
    baseUrl,
    running,
    version,
    error,
    chatModel: {
      ...chatModel,
      installationKnown: running,
      installed: chatModel.provider === 'ollama' && !!chatModel.model && installedModelSet.has(chatModel.model),
    },
    embeddingModel: {
      ...embeddingModel,
      installationKnown: running,
      installed: embeddingModel.provider === 'ollama' && !!embeddingModel.model && installedModelSet.has(embeddingModel.model),
    },
    installedModels,
    installedChatModels,
    installedEmbeddingModels,
  };
}

async function pullModel(baseUrl, model, onProgress) {
  const { stream: response } = await createPullStream(baseUrl, model);
  for await (const part of response) {
    if (typeof onProgress === 'function') onProgress(part);
  }
  return { ok: true };
}

async function deleteModel(baseUrl, model) {
  const client = await getOllamaClient(baseUrl);
  await client.delete({ model });
  return { ok: true };
}

async function copyModel(baseUrl, source, destination) {
  const client = await getOllamaClient(baseUrl);
  await client.copy({ source, destination });
  return { ok: true };
}

module.exports = {
  normalizeOllamaModelName,
  getEffectiveYamlText,
  getOllamaEnvironmentInfo,
  listInstalledModels,
  createPullStream,
  pullModel,
  deleteModel,
  copyModel,
};
