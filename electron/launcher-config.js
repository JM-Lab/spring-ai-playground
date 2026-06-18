'use strict';

const path = require('path');

const SERVER_READY_TIMEOUT_MS = 2 * 60 * 1000;
const UI_READY_GRACE_TIMEOUT_MS = 3 * 60 * 1000;

const PRELOAD_PATH = path.join(__dirname, 'preload.js');
const OLLAMA_MANAGER_PRELOAD_PATH = path.join(__dirname, 'ollama-manager-preload.js');
const SPLASH_PATH = path.join(__dirname, 'splash.html');
const CONFIG_EDITOR_PATH = path.join(__dirname, 'config-editor.html');
const OLLAMA_MANAGER_PATH = path.join(__dirname, 'ollama-manager.html');
const SERVER_SPLASH_PATH = path.join(__dirname, 'server-splash.html');

const CONFIG_TEMPLATES = {
  ollama: {
    id: 'template-ollama',
    providerType: 'ollama',
    name: 'Ollama',
    description: 'Default config + Ollama chat and embedding overrides.',
    yaml: `spring:
  ai:
    model:
      chat: ollama
      embedding: ollama
    ollama:
      chat:
        options:
          model: qwen3.5:2b
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - qwen3.5:2b
          - qwen3.5:9b
          - gemma4:e4b
          - gpt-oss:20b
          - deepseek-r1:8b
`,
  },
  openai: {
    id: 'template-openai',
    providerType: 'openai',
    name: 'OpenAI',
    description: 'Default config + OpenAI chat and embedding overrides.',
    yaml: `spring:
  ai:
    model:
      chat: openai
      embedding: openai
    openai:
      api-key: \${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-5.4-mini
          stream-options:
            include-usage: false
      embedding:
        options:
          model: text-embedding-3-small
    playground:
      chat:
        models:
          - gpt-5.4
          - gpt-5.4-mini
          - gpt-5.4-nano
          - gpt-5.2
`,
  },
  openaiCompatibleOllama: {
    id: 'template-openai-compatible-ollama',
    providerType: 'openai-compatible',
    name: 'OpenAI Compatible - Ollama',
    description: 'Default config + Ollama-compatible chat override, Ollama embedding override.',
    yaml: `spring:
  ai:
    model:
      chat: openai
      embedding: ollama
    openai:
      api-key: not-used
      base-url: http://localhost:11434/v1
      chat:
        options:
          model: llama3.2
    ollama:
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - llama3.2
`,
  },
  openaiCompatibleLlamaCpp: {
    id: 'template-openai-compatible-llama-cpp',
    providerType: 'openai-compatible',
    name: 'OpenAI Compatible - llama.cpp',
    description: 'README example for llama.cpp chat + Ollama embedding.',
    yaml: `spring:
  ai:
    model:
      chat: openai
      embedding: ollama
    openai:
      api-key: not-used
      base-url: http://localhost:8080/v1
      chat:
        options:
          model: your-model-name
          extra-body:
            top_k: 40
            repetition_penalty: 1.1
    ollama:
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - your-model-name
`,
  },
  openaiCompatibleTabbyApi: {
    id: 'template-openai-compatible-tabbyapi',
    providerType: 'openai-compatible',
    name: 'OpenAI Compatible - TabbyAPI',
    description: 'README example for TabbyAPI chat + Ollama embedding.',
    yaml: `spring:
  ai:
    model:
      chat: openai
      embedding: ollama
    openai:
      api-key: your-tabby-key
      base-url: http://localhost:5000/v1
      chat:
        options:
          model: your-exllama-model
          extra-body:
            top_p: 0.95
    ollama:
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - your-exllama-model
`,
  },
  openaiCompatibleLmStudio: {
    id: 'template-openai-compatible-lm-studio',
    providerType: 'openai-compatible',
    name: 'OpenAI Compatible - LM Studio',
    description: 'README example for LM Studio chat + Ollama embedding.',
    yaml: `spring:
  ai:
    model:
      chat: openai
      embedding: ollama
    openai:
      api-key: not-used
      base-url: http://localhost:1234/v1
      chat:
        options:
          model: your-loaded-model
          extra-body:
            num_predict: 100
    ollama:
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - your-loaded-model
`,
  },
  openaiCompatibleVllm: {
    id: 'template-openai-compatible-vllm',
    providerType: 'openai-compatible',
    name: 'OpenAI Compatible - vLLM',
    description: 'README example for vLLM chat + Ollama embedding.',
    yaml: `spring:
  ai:
    model:
      chat: openai
      embedding: ollama
    openai:
      api-key: not-used
      base-url: http://localhost:8000/v1
      chat:
        options:
          model: meta-llama/Llama-3-8B-Instruct
          extra-body:
            top_k: 50
    ollama:
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - meta-llama/Llama-3-8B-Instruct
`,
  },
};

const MLX_MODEL_MAP = {
  // plain -mlx builds only; -mlx-bf16 variants are not auto-selected
  'qwen3.5:0.8b': 'qwen3.5:0.8b-mlx',
  'qwen3.5:2b': 'qwen3.5:2b-mlx',
  'qwen3.5:4b': 'qwen3.5:4b-mlx',
  'qwen3.5:9b': 'qwen3.5:9b-mlx',
  'qwen3.5:27b': 'qwen3.5:27b-mlx',
  'qwen3.5:35b': 'qwen3.5:35b-mlx',
  'qwen3.6:27b': 'qwen3.6:27b-mlx',
  'qwen3.6:35b': 'qwen3.6:35b-mlx',
  'gemma4:e2b': 'gemma4:e2b-mlx',
  'gemma4:e4b': 'gemma4:e4b-mlx',
  'gemma4:12b': 'gemma4:12b-mlx',
  'gemma4:26b': 'gemma4:26b-mlx',
  'gemma4:31b': 'gemma4:31b-mlx',
};

function toMlxModel(name) {
  return (typeof name === 'string' && MLX_MODEL_MAP[name]) || name;
}

const OLLAMA_APPLE_SILICON_YAML = `spring:
  ai:
    model:
      chat: ollama
      embedding: ollama
    ollama:
      chat:
        options:
          model: qwen3.5:4b-mlx
      embedding:
        options:
          model: qwen3-embedding:0.6b
    playground:
      chat:
        models:
          - qwen3.5:2b-mlx
          - qwen3.5:4b-mlx
          - qwen3.5:9b-mlx
          - qwen3.6:27b-mlx
          - qwen3.6:35b-mlx
          - gemma4:e2b-mlx
          - gemma4:e4b-mlx
          - gemma4:12b-mlx
          - gemma4:31b-mlx
`;

const http = require('http');
const fs = require('fs');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'text/javascript',
  '.css':  'text/css',
  '.png':  'image/png',
  '.ico':  'image/x-icon',
  '.svg':  'image/svg+xml',
};

function startTempServer() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const urlPath = req.url.split('?')[0];
      const filePath = path.join(__dirname, urlPath === '/' ? 'index.html' : urlPath);
      const ext = path.extname(filePath);
      const contentType = MIME_TYPES[ext] || 'text/plain';

      fs.readFile(filePath, (err, data) => {
        if (err) { res.writeHead(404); res.end('Not found'); return; }
        res.writeHead(200, { 'Content-Type': contentType });
        res.end(data);
      });
    });

    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      resolve({
        port,
        stop: () => server.close(),
        getUrl: (htmlFile) =>
          `http://127.0.0.1:${port}/${path.basename(htmlFile)}`,
      });
    });
  });
}

const DEFAULT_STARTER_TEMPLATE_IDS = new Set([
  'template-ollama',
  'template-openai',
  'template-openai-compatible-ollama',
  'template-openai-compatible-llama-cpp',
  'template-openai-compatible-tabbyapi',
  'template-openai-compatible-lm-studio',
  'template-openai-compatible-vllm',
]);

module.exports = {
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
  toMlxModel,
  OLLAMA_APPLE_SILICON_YAML,
};
