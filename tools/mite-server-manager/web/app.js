import { mountConfig } from './config.js';

const byId = id => document.getElementById(id);
const pageButtons = [...document.querySelectorAll('[data-page]')];
const playerButtons = [...document.querySelectorAll('[data-command]')];
const playerName = byId('player-name');
const command = byId('command');
const logView = byId('log-view');
const logLines = byId('log-lines');
const java = byId('java');
const javaScanButton = byId('scan-java');
const javaScanPanel = byId('java-scan');
const javaScanStatus = byId('java-scan-status');
const javaScanResults = byId('java-scan-results');
const minimum = byId('memory-min');
const maximum = byId('memory-max');
const dialogue = byId('confirm-dialog');
const packDialog = byId('server-pack-dialog');
const packRelease = byId('pack-release');
const languageSelect = byId('language');
const username = /^[A-Za-z0-9_-]{1,16}$/;
const history = [];
const translations = ['textContent', 'placeholder', 'aria-label', 'title'].flatMap(property => {
  const attribute = property === 'textContent' ? 'data-en' : 'data-en-' + property;
  return [...document.querySelectorAll('[' + attribute + ']')].map(element => ({
    element, property, english: element.getAttribute(attribute),
    chinese: property === 'textContent' ? element.textContent : element.getAttribute(property),
  }));
});

let api;
let config;
let settings;
let lastLogId = 0;
let historyIndex = 0;
let active = false;
let ready = false;
let currentPhase = 'idle';
let inTransition = false;
let runtimeSaving = false;
let javaScan = { phase: 'idle', candidates: null };
let javaScanRevision = 0;
let previousPlayerNames = '';
let reportedUnsaved = false;
let language = 'zh-CN';
let packStatus = { phase: 'idle' };
let packAction = '';
let packRevision = 0;
let packRuntimeError = '';
let packReleases = null;
let packListing = false;
let packListError = '';
let selectedPack = '';
const packChangelogs = new Map();

function t(chinese, english) {
  return language === 'en' ? english : chinese;
}

function applyLanguage(value) {
  language = value === 'en' ? 'en' : 'zh-CN';
  languageSelect.value = language;
  document.documentElement.lang = language;
  document.title = t('MITE 服务端管理器', 'MITE Server Manager');
  for (const { element, property, chinese, english } of translations) {
    const text = t(chinese, english);
    if (property === 'textContent') element.textContent = text;
    else element.setAttribute(property, text);
  }
  const selectedPage = pageButtons.find(button => button.hasAttribute('aria-current'));
  showPage(selectedPage?.dataset.page ?? 'console');
  config?.setLanguage?.();
  updateDirectoryText();
  renderJavaScan();
  renderPackDialog();
  updateControls();
}

function toast(message, type = 'info') {
  const item = document.createElement('div');
  item.className = 'toast';
  item.dataset.tone = type;
  item.textContent = message;
  byId('toasts').append(item);
  setTimeout(() => item.remove(), type === 'error' ? 7500 : 3700);
}

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function confirm({ title, message, accept = t('确认', 'Confirm') }) {
  if (dialogue.open) return Promise.resolve(false);
  byId('confirm-title').textContent = title;
  byId('confirm-message').textContent = message;
  byId('confirm-accept').textContent = accept;
  dialogue.returnValue = 'cancel';
  dialogue.showModal();
  return new Promise(resolve => dialogue.addEventListener('close', () => resolve(dialogue.returnValue === 'accept'), { once: true }));
}

function showPage(page) {
  for (const button of pageButtons) {
    const selected = button.dataset.page === page;
    button.classList.toggle('current', selected);
    if (selected) {
      button.setAttribute('aria-current', 'page');
      byId('page-title').textContent = button.querySelector('[data-en]').textContent;
    }
    else button.removeAttribute('aria-current');
    byId('page-' + button.dataset.page).hidden = !selected;
  }
}

function runtimeChanged() {
  return Boolean(settings?.directory) &&
    (java.value.trim() !== settings.java || minimum.valueAsNumber !== settings.minMiB || maximum.valueAsNumber !== settings.maxMiB);
}

function hasChanges() {
  return runtimeChanged() || config.hasChanges();
}

function reportUnsaved() {
  if (!api || !config) return;
  const unsaved = hasChanges();
  if (reportedUnsaved !== unsaved) {
    reportedUnsaved = unsaved;
    void api.SetUnsaved(unsaved).catch(() => {});
  }
}

function updateControls() {
  const selected = Boolean(settings?.directory);
  const packBusy = packIsBusy();
  const runtimeLocked = !selected || active || inTransition || runtimeSaving || packBusy;
  byId('choose-directory').disabled = active || inTransition || runtimeSaving || packBusy;
  byId('open-directory').disabled = (!selected && !settings?.downloadTarget) || packBusy;
  byId('start-server').disabled = runtimeLocked;
  byId('start-server').setAttribute('aria-busy', String(inTransition && !active));
  byId('stop-server').disabled = !active || currentPhase === 'stopping';
  byId('send-command').disabled = !ready;
  byId('refresh-players').disabled = !ready;
  command.disabled = !ready;
  playerName.disabled = !ready;
  updatePlayerButtons();
  java.disabled = minimum.disabled = maximum.disabled = runtimeLocked;
  byId('browse-java').disabled = byId('save-runtime').disabled = runtimeLocked;
  updateJavaScanControls();
  byId('accept-eula').disabled = runtimeLocked || settings.eula;
  byId('runtime-note').textContent = settings?.issue || (!selected ? t('请先选择服务端目录。', 'Choose a server folder first.')
    : active ? t('服务器运行中，停服后可以编辑。', 'Stop the server to edit these settings.')
    : runtimeChanged() ? t('有未保存的启动设置。', 'Launch settings have unsaved changes.')
      : t('Java 路径留空时自动查找。', 'Leave the Java path empty to find Java automatically.'));
  byId('eula-state').textContent = settings?.eula ? t('此服务端已接受协议。', 'The EULA has been accepted for this server.')
    : t('首次启动需要阅读并接受协议。', 'Read and accept the EULA before the first launch.');
  renderPackState();
}

function updateDirectoryText() {
  const directory = settings?.directory ?? '';
  const name = directory.split(/[\\/]/).filter(Boolean).pop();
  const downloadTarget = settings?.downloadTarget || '';
  const targetName = downloadTarget.split(/[\\/]/).filter(Boolean).pop();
  const selectFolder = t('选择服务端目录', 'Choose server folder');
  byId('server-name').textContent = name ? name.replace(/-server$/i, '') : t('MITE 服务端', 'MITE server');
  byId('directory-label').textContent = name || targetName || selectFolder;
  byId('choose-directory').title = directory || downloadTarget || selectFolder;
  const version = byId('pack-version');
  version.hidden = !settings?.packVersion;
  version.textContent = settings?.packVersion || '';
  version.title = t('MITE Modern ITE 服务端包版本', 'MITE Modern ITE server pack version');
  if (!name) byId('phase-label').textContent = t('等待选择服务端', 'Waiting for a server folder');
  byId('log-empty-hint').textContent = !name ? t('选择服务端目录后即可启动。', 'Choose a server folder to get started.')
    : active ? t('新日志会继续显示在这里。', 'New log messages will appear here.')
    : settings.eula ? t('启动服务器后，日志会出现在这里。', 'Start the server to see its logs.')
    : t('首次启动前，请在启动设置中接受 Minecraft EULA。', 'Accept the Minecraft EULA in Launch settings before starting.');
}

function applySettings(value, preserveInputs = false, scanRevision = javaScanRevision) {
  const folderChanged = value.directory !== settings?.directory;
  settings = value;
  if (!preserveInputs || folderChanged) {
    java.value = value.java;
    minimum.value = value.minMiB;
    maximum.value = value.maxMiB;
  }
  updateDirectoryText();
  if (scanRevision === javaScanRevision && (folderChanged || javaScan.candidates === null) && Array.isArray(value.javaScan)) {
    javaScan = { ...javaScan, candidates: value.javaScan };
    renderJavaScan();
  }
  if (folderChanged) {
    logLines.replaceChildren();
    byId('log-empty').hidden = false;
    playerName.value = '';
    history.length = 0;
    historyIndex = 0;
    command.value = '';
  }
  config.setContext(value.directory, active);
  updateControls();
  reportUnsaved();
}

function packIsWorking() {
  return ['downloading', 'verifying', 'extracting'].includes(packStatus.phase);
}

function packIsBusy() {
  return Boolean(packAction) || packIsWorking();
}

function packFormatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '';
  const units = ['B', 'KiB', 'MiB', 'GiB'];
  let index = 0;
  while (bytes >= 1024 && index < units.length - 1) {
    bytes /= 1024;
    index++;
  }
  const amount = new Intl.NumberFormat(language, { maximumFractionDigits: index === 0 ? 0 : 1 }).format(bytes);
  return amount + ' ' + units[index];
}

function packSelectedRelease() {
  return packReleases?.find(release => String(release.id) === selectedPack);
}

function packPhaseText() {
  if (packRuntimeError) return t('服务端包已下载，暂时无法载入目录', 'Server pack downloaded. Could not load its folder.');
  if (packAction === 'cancel') return t('正在取消下载…', 'Canceling download…');
  if (packAction === 'restore' || packStatus.phase === 'complete') return t('正在载入服务端目录…', 'Loading the server folder…');
  if (packAction === 'begin') return t('正在准备下载…', 'Preparing the download…');
  switch (packStatus.phase) {
    case 'downloading': return t('正在下载 MITE Modern ITE…', 'Downloading MITE Modern ITE…');
    case 'verifying': return t('正在校验服务端包…', 'Verifying the server pack…');
    case 'extracting': return t('正在解压服务端包…', 'Unpacking the server pack…');
    case 'error': return t('服务端包下载失败', 'Server pack download failed');
    case 'canceled': return t('服务端包下载已取消', 'Server pack download canceled');
    default: return t('未下载 MITE Modern ITE 服务端', 'MITE Modern ITE server pack is not installed');
  }
}

function renderPackState() {
  const selected = Boolean(settings?.directory);
  const available = Boolean(api?.ListServerPacks && api?.BeginServerPackDownload);
  const visible = Boolean(settings && !selected && available);
  const button = byId('open-server-pack');
  const cancel = byId('cancel-server-pack');
  button.hidden = !visible || packIsBusy();
  button.disabled = active || inTransition || runtimeSaving;
  button.textContent = packRuntimeError ? t('载入下载目录', 'Load downloaded server')
    : (packStatus.phase === 'error' || packStatus.phase === 'canceled') ? t('重试下载', 'Retry download')
      : t('下载服务端', 'Download server pack');
  cancel.hidden = !visible || !(packIsWorking() || packAction === 'begin' || packAction === 'cancel');
  cancel.disabled = Boolean(packAction);
  byId('start-server').hidden = visible;
  byId('stop-server').hidden = visible;
  byId('begin-server-pack').disabled = !visible || packListing || packIsBusy() || inTransition || !settings?.downloadTarget || !packSelectedRelease();
  byId('pack-setup').hidden = !visible;
  logView.classList.toggle('pack-setup-visible', visible);
  if (!visible) return;

  const headline = packPhaseText();
  const status = byId('pack-setup-status');
  if (status.textContent !== headline) status.textContent = headline;
  const overview = byId('phase-label');
  const overviewText = packIsBusy() ? headline : t('尚无服务端目录', 'No server folder selected');
  if (overview.textContent !== overviewText) overview.textContent = overviewText;
  byId('phase-dot').dataset.tone = packIsBusy() ? 'busy' : packStatus.phase === 'error' || packRuntimeError ? 'error' : 'idle';
  const progress = byId('pack-progress');
  progress.hidden = !packIsBusy();
  if (packStatus.phase === 'downloading' && packStatus.total > 0) {
    progress.max = packStatus.total;
    progress.value = Math.min(packStatus.total, packStatus.received || 0);
  } else {
    progress.removeAttribute('value');
  }
  let detail = '';
  if (packStatus.phase === 'downloading') {
    const received = packFormatSize(packStatus.received || 0);
    detail = packStatus.total > 0
      ? received + ' / ' + packFormatSize(packStatus.total) + ' · ' + Math.floor(progress.value / progress.max * 100) + '%'
      : (received ? received + t(' 已下载', ' downloaded') : '');
  } else if (packStatus.phase === 'idle' && !packIsBusy()) {
    detail = t('选择版本下载安装，或通过右上角打开已有的服务端目录。',
      'Choose a version to download, or open an existing server folder from the top bar.');
  }
  byId('pack-progress-detail').textContent = detail;
  const problem = byId('pack-setup-error');
  const message = packRuntimeError || packStatus.error || '';
  if (problem.textContent !== message) problem.textContent = message;
  problem.hidden = !message;
}

function renderPackDialog() {
  byId('pack-target-path').textContent = settings?.downloadTarget || '';
  const catalog = byId('pack-catalog-status');
  const release = packSelectedRelease();
  const prompt = document.createElement('option');
  prompt.value = '';
  prompt.textContent = packListing ? t('正在读取…', 'Loading…') : t('选择服务端包版本', 'Choose a server pack version');
  const options = (packReleases || []).map(item => {
    const option = document.createElement('option');
    option.value = String(item.id);
    option.textContent = item.version || item.fileName;
    return option;
  });
  packRelease.replaceChildren(prompt, ...options);
  packRelease.value = selectedPack;
  packRelease.disabled = packListing || !options.length || packIsBusy();
  catalog.textContent = packListing ? t('正在读取 CurseForge 版本列表…', 'Loading versions from CurseForge…')
    : packListError ? t('无法读取服务端包列表，请重试。', 'Could not load the server pack list. Try again.')
      : (packReleases && !options.length) ? t('未找到可下载的服务端包。', 'No server packs are available to download.') : '';
  catalog.title = packListError;
  catalog.dataset.error = String(Boolean(packListError));
  byId('retry-pack-catalog').hidden = packListing || (!packListError && !(packReleases && !options.length));
  const published = release?.published && new Date(release.published);
  const date = published && !Number.isNaN(published.getTime()) ? new Intl.DateTimeFormat(language, { dateStyle: 'medium' }).format(published) : '';
  byId('pack-release-details').textContent = release
    ? [release.fileName, packFormatSize(release.fileLength), date].filter(Boolean).join(' · ') : '';
  byId('pack-changelog').hidden = !release;
  const log = release ? packChangelogs.get(String(release.id)) : null;
  const entry = byId('pack-changelog-text');
  entry.textContent = log?.loading ? t('正在读取同版客户端更新日志…', 'Loading the matching client changelog…')
    : log?.error ? t('无法读取同版客户端更新日志。', 'Could not load the matching client changelog.')
      : log?.text || t('暂无同版客户端更新日志。', 'No changelog is available for the matching client version.');
  entry.title = log?.error || '';
  renderPackState();
}

async function loadPackChangelog(release) {
  if (!release?.clientId) return;
  const key = String(release.id);
  const existing = packChangelogs.get(key);
  if (existing && !existing.error) return;
  packChangelogs.set(key, { loading: true });
  renderPackDialog();
  try {
    const text = await api.GetServerPackChangelog(release.id);
    packChangelogs.set(key, { text: typeof text === 'string' ? text.trim() : '' });
  } catch (error) {
    packChangelogs.set(key, { error: errorMessage(error) });
  }
  if (selectedPack === key) renderPackDialog();
}

async function loadPackReleases() {
  if (packListing) return;
  packListing = true;
  packListError = '';
  renderPackDialog();
  try {
    const releases = await api.ListServerPacks();
    packReleases = Array.isArray(releases) ? releases : [];
    if (!packSelectedRelease()) selectedPack = packReleases.length ? String(packReleases[0].id) : '';
  } catch (error) {
    packListError = errorMessage(error);
  } finally {
    packListing = false;
    renderPackDialog();
  }
  void loadPackChangelog(packSelectedRelease());
}

async function loadDownloadedServer() {
  if (packAction || settings?.directory) return;
  packAction = 'restore';
  packRuntimeError = '';
  updateControls();
  const scanRevision = javaScanRevision;
  try {
    const value = await api.GetRuntime();
    if (!value.directory) throw new Error(t('下载完成，但未找到可用的服务端目录。', 'The download completed, but no usable server folder was found.'));
    applySettings(value, false, scanRevision);
    toast(t('服务端已下载并解压。', 'Server pack downloaded and unpacked.'));
    if (packStatus.error) toast(packStatus.error, 'error');
  } catch (error) {
    packRuntimeError = errorMessage(error);
  } finally {
    packAction = '';
    updateControls();
  }
}

function applyPackSnapshot(value) {
  if (!value?.phase || packAction === 'begin' || (value.phase === 'idle' && packStatus.transient)) return;
  packStatus = value;
  if (value.phase === 'complete' && !packRuntimeError) void loadDownloadedServer();
  else renderPackState();
}

async function openPackDialog() {
  if (!settings || settings.directory || packIsBusy() || inTransition) return;
  if (packRuntimeError) {
    await loadDownloadedServer();
    return;
  }
  if (!settings.downloadTarget) await chooseDirectory();
  if (!settings.downloadTarget || settings.directory || packIsBusy() || inTransition || packDialog.open) return;
  renderPackDialog();
  packDialog.showModal();
  if (packReleases === null || packListError) void loadPackReleases();
}

async function beginPackDownload() {
  const release = packSelectedRelease();
  if (!release || !settings?.downloadTarget || settings.directory || packListing || packIsBusy() || inTransition) return;
  packRevision++;
  packAction = 'begin';
  packRuntimeError = '';
  packDialog.close();
  updateControls();
  try {
    await api.BeginServerPackDownload(release.id);
    packStatus = { phase: 'downloading', received: 0, total: release.fileLength, transient: true };
  } catch (error) {
    packStatus = { phase: 'error', error: errorMessage(error), transient: true };
  } finally {
    packRevision++;
    packAction = '';
    updateControls();
  }
}

async function cancelPackDownload() {
  if (packAction || !packIsWorking()) return;
  packAction = 'cancel';
  updateControls();
  try {
    await api.CancelServerPackDownload();
  } catch (error) {
    toast(errorMessage(error), 'error');
  } finally {
    packAction = '';
    updateControls();
  }
}

function normalizedJavaPath(path) {
  return path.trim().replace(/^"(.*)"$/, '$1').replaceAll('/', '\\').toLowerCase();
}

function updateJavaScanControls() {
  const busy = javaScan.phase === 'scanning';
  const buttonLabel = javaScan.candidates === null ? t('扫描 Java', 'Scan for Java') : t('重新扫描 Java', 'Rescan Java');
  if (javaScanButton.textContent !== buttonLabel) javaScanButton.textContent = buttonLabel;
  javaScanButton.disabled = !api?.ScanJava || busy;
  javaScanButton.setAttribute('aria-busy', String(busy));
  javaScanPanel.hidden = javaScan.phase === 'idle' && javaScan.candidates === null;
  javaScanButton.setAttribute('aria-expanded', String(!javaScanPanel.hidden));
  javaScanStatus.dataset.error = String(javaScan.phase === 'error');
  javaScanStatus.title = javaScan.detail ?? '';
  const message = javaScanMessage();
  if (javaScanStatus.textContent !== message) javaScanStatus.textContent = message;
  const selected = normalizedJavaPath(java.value);
  for (const button of javaScanResults.querySelectorAll('button')) {
    button.disabled = java.disabled;
    button.setAttribute('aria-pressed', String(normalizedJavaPath(button.dataset.path) === selected));
  }
}

function setJavaPath(path) {
  if (java.disabled) return;
  java.value = path;
  java.dispatchEvent(new Event('input', { bubbles: true }));
}

function javaArchitecture(value) {
  const architecture = String(value ?? '');
  if (/^(64|64-bit)$/i.test(architecture)) return t('64 位', '64-bit');
  if (/^(32|32-bit)$/i.test(architecture)) return t('32 位', '32-bit');
  return architecture || t('架构未知', 'Unknown architecture');
}

function javaCandidateRow(candidate) {
  const row = document.createElement('li');
  row.className = 'java-candidate';
  const details = document.createElement('div');
  const meta = document.createElement('div');
  meta.className = 'java-candidate-meta';
  const version = document.createElement('span');
  version.className = 'java-candidate-version';
  version.textContent = candidate.version ? 'Java ' + candidate.version : t('版本未知', 'Unknown version');
  const architecture = document.createElement('span');
  architecture.textContent = javaArchitecture(candidate.architecture);
  const badge = document.createElement('span');
  badge.className = 'java-candidate-badge';
  badge.dataset.compatible = String(candidate.compatible);
  badge.textContent = candidate.compatible ? t('兼容', 'Compatible') : t('不兼容', 'Incompatible');
  meta.append(version, architecture, badge);
  const path = document.createElement('code');
  path.className = 'java-candidate-path';
  path.textContent = path.title = candidate.path;
  details.append(meta, path);
  row.append(details);
  if (candidate.compatible) {
    const use = document.createElement('button');
    use.type = 'button';
    use.className = 'btn compact';
    use.dataset.path = candidate.path;
    use.textContent = t('选用', 'Use');
    use.setAttribute('aria-label', t('选用 ', 'Use ') + version.textContent + ' · ' + candidate.path);
    use.addEventListener('click', () => setJavaPath(candidate.path));
    row.append(use);
  }
  return row;
}

function javaScanMessage() {
  const candidates = javaScan.candidates;
  if (javaScan.phase === 'scanning') return candidates?.length
    ? t('正在重新扫描 Java。下方显示上次扫描结果。', 'Rescanning for Java. Previous results are shown below.')
    : t('正在扫描 Java…', 'Scanning for Java…');
  if (javaScan.phase === 'error') return candidates?.length
    ? t('重新扫描失败，已保留上次扫描结果。', 'Could not rescan for Java. Previous results were kept.')
    : t('扫描 Java 失败。可重试、手动输入或浏览 Java 路径。', 'Could not scan for Java. Try again, enter a path, or use Browse.');
  if (candidates === null) return '';
  if (!candidates.length) return t(
    '未找到 Java 安装。可手动输入或浏览 Java 路径。',
    'No Java installations found. Enter a path or use Browse.',
  );
  if (!candidates.some(candidate => candidate.compatible)) return t(
    '未找到兼容的安装。请选择 64 位 Java 21。',
    'No compatible installation found. Choose a 64-bit Java 21 installation.',
  );
  const scanned = javaScan.phase === 'complete' ? t('扫描完成。', 'Scan complete. ') : t('已加载上次扫描结果。', 'Saved scan results. ');
  if (active) return scanned + t('停服后可选用兼容的 Java。', 'Stop the server to use a compatible Java installation.');
  if (!settings?.directory) return scanned + t('选择服务端目录后可选用 Java。', 'Choose a server folder to use a Java installation.');
  return scanned + t('请选择兼容的 Java，随后保存启动设置。',
    'Choose a compatible Java installation and save your launch settings.');
}

function renderJavaScan() {
  const position = javaScanResults.scrollTop;
  const candidates = javaScan.candidates ?? [];
  javaScanResults.replaceChildren(...candidates.map(javaCandidateRow));
  javaScanResults.scrollTop = position;
  updateJavaScanControls();
}

async function scanJava() {
  if (javaScanButton.disabled) return;
  javaScan = { ...javaScan, phase: 'scanning', detail: '' };
  updateJavaScanControls();
  try {
    const candidates = await api.ScanJava();
    javaScanRevision++;
    javaScan = { phase: 'complete', candidates: Array.isArray(candidates) ? candidates : [] };
    renderJavaScan();
  } catch (error) {
    javaScan = { ...javaScan, phase: 'error', detail: errorMessage(error) };
    updateJavaScanControls();
  }
}

function applySnapshot(snapshot) {
  if (!settings) return;
  const previousActive = active;
  active = snapshot.active;
  ready = snapshot.ready;
  currentPhase = snapshot.phase;
  byId('phase-label').textContent = settings.directory ? snapshot.status : t('等待选择服务端', 'Waiting for a server folder');
  byId('phase-dot').dataset.tone = snapshot.ready ? 'running'
    : snapshot.phase === 'error' ? 'error' : snapshot.active ? 'busy' : 'idle';
  if (previousActive !== active) config.setContext(settings.directory, active);
  if (settings.directory) renderLogs((snapshot.logs ?? []).filter(line => line.id > lastLogId));
  lastLogId = Math.max(lastLogId, snapshot.lastLogId);
  renderPlayers(snapshot.players ?? []);
  updateControls();
  reportUnsaved();
}

function renderLogs(lines) {
  if (!lines.length) return;
  const batch = document.createDocumentFragment();
  for (const line of lines) {
    const entry = document.createElement('div');
    entry.className = 'log-line';
    const timePrefix = line.text.match(/^\[\d{2}:\d{2}:\d{2}\]/);
    if (timePrefix) {
      const time = document.createElement('span');
      time.className = 'log-time';
      time.textContent = timePrefix[0];
      entry.append(time, line.text.slice(timePrefix[0].length).trimStart());
    } else entry.textContent = line.text;
    entry.dataset.tone = /\b(?:ERROR|FATAL|failed)\b|失败|异常/i.test(line.text) ? 'error'
      : /\bWARN\b/.test(line.text) ? 'warn'
      : line.text.startsWith('> ') ? 'command' : 'plain';
    batch.append(entry);
  }
  logLines.append(batch);
  while (logLines.childElementCount > 2000) logLines.firstElementChild.remove();
  byId('log-empty').hidden = true;
  if (byId('follow-log').checked) logView.scrollTop = logView.scrollHeight;
}

function renderPlayers(names) {
  const key = names.join('\n');
  if (key === previousPlayerNames) return;
  previousPlayerNames = key;
  byId('player-count').textContent = names.length;
  byId('player-empty').hidden = names.length !== 0;
  const list = byId('player-list');
  const select = name => {
    playerName.value = name;
    updatePlayerButtons();
    playerName.focus();
  };
  list.replaceChildren(...names.map(name => {
    const row = document.createElement('button');
    const avatar = document.createElement('span');
    row.type = 'button';
    row.className = 'player-row';
    row.dataset.name = name;
    avatar.className = 'player-avatar';
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = name.slice(0, 1).toUpperCase();
    row.append(avatar, name);
    row.addEventListener('click', () => select(name));
    return row;
  }));
  updatePlayerButtons();
}

function updatePlayerButtons() {
  const name = playerName.value.trim();
  for (const row of byId('player-list').children) {
    row.setAttribute('aria-pressed', String(row.dataset.name.toLowerCase() === name.toLowerCase()));
  }
  for (const button of playerButtons) button.disabled = !ready || !username.test(name);
}

async function poll() {
  const directory = settings.directory;
  const downloadRevision = packRevision;
  try {
    const snapshot = await api.Snapshot(lastLogId);
    if (downloadRevision === packRevision) applyPackSnapshot(snapshot.serverPack);
    if (!inTransition && settings.directory === directory) applySnapshot(snapshot);
  } catch (error) {
    byId('phase-label').textContent = errorMessage(error);
  } finally {
    setTimeout(poll, document.hidden ? 900 : 450);
  }
}

async function chooseDirectory() {
  if (packIsBusy()) return;
  if (hasChanges() && !await confirm({
    title: t('切换服务端目录', 'Switch server folder'),
    message: t('切换目录会放弃当前服务端的未保存设置。继续选择目录？', 'Switching folders will discard unsaved settings for this server. Continue?'),
    accept: t('继续', 'Continue'),
  })) return;
  inTransition = true;
  updateControls();
  const scanRevision = javaScanRevision;
  try {
    const value = await api.ChooseDirectory();
    if (value.directory !== settings.directory || value.downloadTarget !== settings.downloadTarget) {
      packRevision++;
      packStatus = { phase: 'idle' };
      packRuntimeError = '';
      applySettings(value, false, scanRevision);
    }
  } catch (error) {
    toast(errorMessage(error), 'error');
  } finally {
    inTransition = false;
    updateControls();
  }
}

async function startServer() {
  if (packIsBusy() || !settings?.directory) return;
  if (hasChanges()) {
    showPage(runtimeChanged() ? 'runtime' : 'config');
    toast(t('请先保存正在编辑的设置。', 'Save your changes before starting the server.'), 'error');
    return;
  }
  inTransition = true;
  updateControls();
  try {
    if (!settings.eula) applySettings(await api.GetRuntime());
    if (!settings.eula) {
      showPage('runtime');
      toast(t('请先查看并接受 Minecraft EULA。', 'Read and accept the Minecraft EULA first.'), 'error');
      return;
    }
    await api.Start();
    applySnapshot(await api.Snapshot(lastLogId));
  } catch (error) {
    toast(errorMessage(error), 'error');
  } finally {
    inTransition = false;
    updateControls();
  }
}

async function sendCommand(text) {
  try {
    await api.Send(text);
    return true;
  } catch (error) {
    toast(errorMessage(error), 'error');
    return false;
  }
}

function actions() {
  document.addEventListener('input', () => queueMicrotask(reportUnsaved));
  languageSelect.addEventListener('change', async () => {
    const next = languageSelect.value;
    languageSelect.disabled = true;
    try {
      await api.SetLanguage(next);
      applyLanguage(next);
    } catch (error) {
      languageSelect.value = language;
      toast(errorMessage(error), 'error');
    } finally {
      languageSelect.disabled = false;
    }
  });
  for (const button of pageButtons) button.addEventListener('click', () => showPage(button.dataset.page));
  byId('choose-directory').addEventListener('click', chooseDirectory);
  byId('open-directory').addEventListener('click', () => api.OpenDirectory().catch(error => toast(errorMessage(error), 'error')));
  byId('open-server-pack').addEventListener('click', openPackDialog);
  byId('begin-server-pack').addEventListener('click', beginPackDownload);
  byId('cancel-server-pack').addEventListener('click', cancelPackDownload);
  byId('close-pack-dialog').addEventListener('click', () => packDialog.close());
  byId('retry-pack-catalog').addEventListener('click', loadPackReleases);
  byId('open-pack-source').addEventListener('click', () => api.OpenServerPackPage().catch(error => toast(errorMessage(error), 'error')));
  packRelease.addEventListener('change', () => {
    selectedPack = packRelease.value;
    renderPackDialog();
    void loadPackChangelog(packSelectedRelease());
  });
  byId('start-server').addEventListener('click', startServer);
  byId('stop-server').addEventListener('click', async () => {
    try {
      await api.Stop();
      applySnapshot(await api.Snapshot(lastLogId));
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  });

  byId('clear-log').addEventListener('click', () => {
    logLines.replaceChildren();
    byId('log-empty').hidden = false;
    updateDirectoryText();
  });
  byId('follow-log').addEventListener('change', () => {
    if (byId('follow-log').checked) logView.scrollTop = logView.scrollHeight;
  });
  byId('command-form').addEventListener('submit', async event => {
    event.preventDefault();
    const text = command.value.trim();
    if (!text || !await sendCommand(text)) return;
    if (history.at(-1) !== text) history.push(text);
    if (history.length > 100) history.shift();
    historyIndex = history.length;
    if (command.value.trim() === text) command.value = '';
    command.focus();
  });
  command.addEventListener('keydown', event => {
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
    if (!history.length) return;
    event.preventDefault();
    historyIndex = Math.max(0, Math.min(history.length, historyIndex + (event.key === 'ArrowUp' ? -1 : 1)));
    command.value = history[historyIndex] ?? '';
    command.setSelectionRange(command.value.length, command.value.length);
  });
  document.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && ready && !byId('page-console').hidden) {
      event.preventDefault();
      command.focus();
    }
  });
  byId('refresh-players').addEventListener('click', () => sendCommand('list uuids'));
  playerName.addEventListener('input', updatePlayerButtons);
  for (const button of playerButtons) {
    button.addEventListener('click', async () => {
      const name = playerName.value.trim();
      const action = button.dataset.command;
      if (!username.test(name)) return;
      if (action === 'ban' && !await confirm({
        title: t('封禁玩家', 'Ban player'),
        message: t('封禁玩家 ' + name + '？', 'Ban ' + name + '?'),
        accept: t('确认封禁', 'Ban player'),
      })) return;
      if (await sendCommand(action + ' ' + name)) {
        toast(t('已发送' + button.textContent + '命令 · ' + name, 'Command sent · /' + action + ' ' + name));
      }
    });
  }

  for (const input of [java, minimum, maximum]) input.addEventListener('input', () => {
    updateControls();
    reportUnsaved();
  });
  byId('browse-java').addEventListener('click', async () => {
    const directory = settings?.directory;
    try {
      const path = await api.ChooseJava();
      if (path && settings?.directory === directory) setJavaPath(path);
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  });
  javaScanButton.addEventListener('click', scanJava);
  byId('runtime-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (runtimeSaving) return;
    runtimeSaving = true;
    updateControls();
    try {
      applySettings(await api.SaveRuntime(java.value, minimum.valueAsNumber, maximum.valueAsNumber));
      toast(t('启动设置已保存。', 'Launch settings saved.'));
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      runtimeSaving = false;
      updateControls();
    }
  });
  byId('read-eula').addEventListener('click', () => api.OpenEULA());
  byId('accept-eula').addEventListener('click', async () => {
    if (!await confirm({
      title: t('接受 Minecraft EULA', 'Accept the Minecraft EULA'),
      message: t('确认你已阅读并接受 Minecraft EULA？这会为当前服务端保存 eula=true。', 'Confirm that you have read and accept the Minecraft EULA. This saves eula=true for this server.'),
      accept: t('接受协议', 'Accept EULA'),
    })) return;
    try {
      applySettings(await api.AcceptEULA(), true);
      toast(t('已保存协议状态。', 'EULA acceptance saved.'));
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  });
}

async function initialize() {
  api = window.go?.main?.App;
  if (!api) {
    byId('phase-label').textContent = t('桌面接口未连接', 'Desktop interface unavailable');
    return;
  }
  config = mountConfig(byId('page-config'), {
    readConfig: kind => api.ReadConfig(kind),
    saveConfig: (kind, text, revision) => api.SaveConfig(kind, text, revision),
    saveProperties: (updates, revision) => api.SaveProperties(updates, revision),
    saveTOML: (kind, updates, revision) => api.SaveTOML(kind, updates, revision),
    onDirtyChange: reportUnsaved,
    language: () => language,
    t,
    toast,
    confirm,
  });
  actions();
  const scanRevision = javaScanRevision;
  try {
    const initial = await api.GetRuntime();
    applyLanguage(initial.language);
    applySettings(initial, false, scanRevision);
    poll();
  } catch (error) {
    toast(errorMessage(error), 'error');
  }
}

if (window.go?.main?.App) initialize();
else window.addEventListener('load', initialize, { once: true });
