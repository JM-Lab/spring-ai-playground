'use strict';

const { app, net } = require('electron');
const { execFile } = require('child_process');

const GH_REPO = 'spring-ai-community/spring-ai-playground';
const GH_OWNER = 'spring-ai-community';
const GH_NAME = 'spring-ai-playground';

const PRE_RELEASE_STAGES = { M: 1, RC: 2 };
const STAGE_UNKNOWN = 0;
const STAGE_GA = 3;

function parseTag(tag) {
  const cleaned = String(tag || '').trim().replace(/^v/i, '');
  const match = cleaned.match(/^(\d+)\.(\d+)\.(\d+)(?:-(.+))?$/);
  if (!match) return null;
  const suffix = match[4] || '';
  const preRelease = suffix.match(/^(M|RC)(\d+)$/i);
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    stage: !suffix ? STAGE_GA : preRelease ? PRE_RELEASE_STAGES[preRelease[1].toUpperCase()] : STAGE_UNKNOWN,
    pre: preRelease ? Number(preRelease[2]) : 0,
  };
}

function isNewer(remote, local) {
  for (const field of ['major', 'minor', 'patch', 'stage', 'pre']) {
    if (remote[field] !== local[field]) return remote[field] > local[field];
  }
  return false;
}

function fetchLatestRelease() {
  return new Promise((resolve, reject) => {
    const request = net.request({
      method: 'GET',
      url: `https://api.github.com/repos/${GH_REPO}/releases/latest`,
    });
    request.setHeader('Accept', 'application/vnd.github+json');
    request.setHeader('User-Agent', 'spring-ai-playground-desktop');
    let body = '';
    request.on('response', (response) => {
      if (response.statusCode !== 200) {
        response.on('data', () => {});
        response.on('end', () => reject(new Error(`HTTP ${response.statusCode}`)));
        return;
      }
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        try { resolve(JSON.parse(body)); } catch (error) { reject(error); }
      });
    });
    request.on('error', reject);
    request.end();
  });
}

function pickAssetForPlatform(release) {
  const assets = Array.isArray(release.assets) ? release.assets : [];
  let needles;
  if (process.platform === 'darwin') needles = process.arch === 'arm64' ? ['mac-arm64.dmg'] : ['mac-x64.dmg'];
  else if (process.platform === 'win32') needles = ['win-x64.exe', 'win.exe', '.exe'];
  else needles = ['.deb', '.rpm'];
  for (const needle of needles) {
    const hit = assets.find((asset) => typeof asset.name === 'string' && asset.name.endsWith(needle));
    if (hit) return { name: hit.name, url: hit.browser_download_url, size: hit.size };
  }
  return null;
}

async function checkForUpdate(currentVersion) {
  if (/0\.0\.0-dev/.test(String(currentVersion))) return { status: 'dev', current: currentVersion };
  let release;
  try {
    release = await fetchLatestRelease();
  } catch (error) {
    return { status: 'error', error: error.message || String(error) };
  }
  const remote = parseTag(release.tag_name);
  const local = parseTag(currentVersion);
  if (!remote || !local) return { status: 'unknown', current: currentVersion };
  if (!isNewer(remote, local)) return { status: 'up-to-date', current: currentVersion, latest: release.tag_name };
  const asset = pickAssetForPlatform(release);
  return {
    status: 'update-available',
    version: release.tag_name,
    releaseUrl: release.html_url,
    asset,
    downloadUrl: asset ? asset.url : release.html_url,
  };
}

// macOS stays semi-automatic: Squirrel.Mac refuses to swap unsigned builds.
const AUTO_INSTALL_PLATFORMS = new Set(['win32', 'linux']);
let cachedAutoUpdater;

function supportsAutoInstall() {
  return app.isPackaged && AUTO_INSTALL_PLATFORMS.has(process.platform);
}

function packageOwnsApp(command, args) {
  return new Promise((resolve) => {
    const child = execFile(command, args, { timeout: 4000 }, (error) => resolve(!error));
    child.on('error', () => resolve(false));
  });
}

async function detectLinuxPackageFormat() {
  if (await packageOwnsApp('dpkg', ['-s', GH_NAME])) return 'deb';
  if (await packageOwnsApp('rpm', ['-q', GH_NAME])) return 'rpm';
  return null;
}

async function getAutoUpdater() {
  if (cachedAutoUpdater !== undefined) return cachedAutoUpdater;
  cachedAutoUpdater = null;
  if (!supportsAutoInstall()) return null;
  try {
    const electronUpdater = require('electron-updater');
    let autoUpdater = null;
    if (process.platform === 'win32') {
      autoUpdater = electronUpdater.autoUpdater;
    } else {
      const format = await detectLinuxPackageFormat();
      if (format === 'deb') autoUpdater = new electronUpdater.DebUpdater();
      else if (format === 'rpm') autoUpdater = new electronUpdater.RpmUpdater();
    }
    if (autoUpdater) {
      autoUpdater.autoDownload = false;
      autoUpdater.autoInstallOnAppQuit = false;
      // checkForUpdate above owns the m/rc/ga ordering; accept any differing version here.
      autoUpdater.allowDowngrade = true;
      autoUpdater.setFeedURL({ provider: 'github', owner: GH_OWNER, repo: GH_NAME });
    }
    cachedAutoUpdater = autoUpdater;
  } catch (error) {
    cachedAutoUpdater = null;
  }
  return cachedAutoUpdater;
}

async function downloadUpdatePackage(onProgress) {
  const autoUpdater = await getAutoUpdater();
  if (!autoUpdater) throw new Error('Automatic install is unavailable on this platform.');
  const check = await autoUpdater.checkForUpdates();
  if (!check || !check.updateInfo) throw new Error('No update metadata is published for the latest release.');
  const progressHandler = onProgress ? (progress) => onProgress(progress) : null;
  if (progressHandler) autoUpdater.on('download-progress', progressHandler);
  try {
    await autoUpdater.downloadUpdate();
  } finally {
    if (progressHandler) autoUpdater.removeListener('download-progress', progressHandler);
  }
  return {
    version: check.updateInfo.version,
    installNow: () => autoUpdater.quitAndInstall(true, true),
    installOnQuit: () => { autoUpdater.autoInstallOnAppQuit = true; },
  };
}

module.exports = {
  checkForUpdate,
  parseTag,
  isNewer,
  supportsAutoInstall,
  downloadUpdatePackage,
  GH_REPO,
};
