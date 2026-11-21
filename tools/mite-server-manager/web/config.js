// Author: NingMeng123
import { configLabel, sectionLabel } from "./config-labels.js";

const views = [
    { kind: "server", title: ["服务器设置", "Server settings"], path: "server.properties" },
    { kind: "mite", title: ["MITE 本体", "MITE Core"], path: "config/mite-common.toml",
        missing: ["MITE 本体会在服务端首次成功启动时生成此配置文件。",
            "MITE generates this file the first time the server starts successfully."] },
    { kind: "ite", title: ["ITE 扩展", "ITE Add-on"], path: "config/modern_ite-common.toml",
        missing: ["已安装 Modern ITE 的服务端首次成功启动时会生成此配置文件。",
            "Modern ITE generates this file the first time the server starts successfully after installation."] }
];

const serverGroups = [
    [["连接与人数", "Connection and players"], ["motd", "server-port", "max-players", "online-mode", "white-list", "enforce-whitelist",
        "enforce-secure-profile", "server-ip", "enable-status", "hide-online-players", "player-idle-timeout",
        "accepts-transfers", "prevent-proxy-connections"]],
    [["世界与玩法", "World and gameplay"], ["difficulty", "gamemode", "pvp", "force-gamemode", "hardcore", "view-distance", "simulation-distance",
        "allow-nether", "allow-flight", "spawn-animals", "spawn-monsters", "spawn-npcs", "spawn-protection"]],
    [["世界生成与存档", "World generation and saves"], ["level-name", "level-seed", "level-type", "generate-structures", "generator-settings",
        "initial-enabled-packs", "initial-disabled-packs", "max-world-size", "region-file-compression", "sync-chunk-writes"]],
    [["权限与监控", "Permissions and monitoring"], ["op-permission-level", "function-permission-level", "enable-command-block", "broadcast-console-to-ops",
        "broadcast-rcon-to-ops", "enable-query", "query.port", "enable-rcon", "rcon.port", "rcon.password",
        "enable-jmx-monitoring", "log-ips", "bug-report-link"]],
    [["资源包与文本", "Resource packs and text"], ["resource-pack", "resource-pack-id", "resource-pack-prompt", "resource-pack-sha1",
        "require-resource-pack", "text-filtering-config", "text-filtering-version"]],
    [["网络与性能", "Network and performance"], ["max-tick-time", "network-compression-threshold", "rate-limit", "use-native-transport",
        "entity-broadcast-range-percentage", "max-chained-neighbor-updates"]]
];

const serverDefaults = {
    "motd": "A Minecraft Server", "server-port": "25565", "max-players": "20", "online-mode": "true",
    "white-list": "false", "difficulty": "hard", "gamemode": "survival", "pvp": "true",
    "view-distance": "10", "simulation-distance": "10"
};

const serverBooleans = new Set([
    "online-mode", "white-list", "enforce-whitelist", "enforce-secure-profile", "enable-status",
    "hide-online-players", "accepts-transfers", "prevent-proxy-connections", "pvp", "force-gamemode",
    "hardcore", "allow-nether", "allow-flight", "spawn-animals", "spawn-monsters", "spawn-npcs",
    "generate-structures", "sync-chunk-writes", "enable-command-block", "broadcast-console-to-ops",
    "broadcast-rcon-to-ops", "enable-query", "enable-rcon", "enable-jmx-monitoring", "log-ips",
    "require-resource-pack", "use-native-transport"
]);

const serverNumbers = new Set([
    "server-port", "max-players", "view-distance", "simulation-distance", "player-idle-timeout", "spawn-protection",
    "max-world-size", "op-permission-level", "function-permission-level", "query.port", "rcon.port",
    "max-tick-time", "network-compression-threshold", "rate-limit", "entity-broadcast-range-percentage",
    "max-chained-neighbor-updates", "text-filtering-version", "pause-when-empty-seconds"
]);

const serverRanges = {
    "server-port": [1, 65535], "query.port": [1, 65535], "rcon.port": [1, 65535],
    "max-players": [0, 2147483647], "view-distance": [2, 32], "simulation-distance": [2, 32],
    "op-permission-level": [1, 4], "function-permission-level": [1, 4]
};

const serverChoices = {
    gamemode: [["survival", "生存", "Survival"], ["creative", "创造", "Creative"],
        ["adventure", "冒险", "Adventure"], ["spectator", "旁观", "Spectator"]],
    "region-file-compression": [["deflate", "Deflate", "Deflate"], ["lz4", "LZ4", "LZ4"],
        ["none", "不压缩", "Uncompressed"]]
};

const customSetting = ["自定义设置", "Custom setting"];
const customGroup = ["自定义分组", "Custom group"];

function localized(pair, language) {
    return pair[language === "en" ? 1 : 0];
}

function otherLanguage(language) {
    return language === "en" ? "zh-CN" : "en";
}

function element(tag, className, content) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (content !== undefined) node.textContent = content;
    return node;
}

function button(className, title) {
    const node = element("button", className, title);
    node.type = "button";
    return node;
}

function blankState(kind) {
    return { kind, document: null, text: "", updates: new Map(), groups: [], mode: "form", query: "",
        rawQuery: "", loading: false, saving: false, stale: false, error: "" };
}

function formDirty(state) {
    return state.updates.size !== 0;
}

function rawDirty(state) {
    return state.document !== null && state.text !== state.document.text;
}

function dirty(state) {
    return formDirty(state) || rawDirty(state);
}

function errorMessage(error, fallback) {
    return (typeof error === "string" ? error : error?.message) || fallback;
}

function translatedField(kind, key, path, language) {
    for (const candidate of new Set([path, key])) {
        const label = configLabel(kind, candidate, language);
        if (label && (label !== candidate || configLabel(kind, candidate, otherLanguage(language)) !== candidate))
            return label;
    }
    return localized(customSetting, language);
}

function makeServerField(id, value, language) {
    const options = Object.hasOwn(serverChoices, id) ? serverChoices[id] : null;
    let type = "string";
    if (options) type = "select";
    else if (id === "rcon.password") type = "password";
    else if (serverBooleans.has(id)) type = "boolean";
    else if (serverNumbers.has(id)) type = "integer";
    else if (id === "motd" || value.includes("\n")) type = "multiline";
    return {
        id, key: id, label: translatedField("server", id, id, language), value, type, options,
        alias: translatedField("server", id, id, otherLanguage(language)),
        range: Object.hasOwn(serverRanges, id) ? serverRanges[id] : null,
        legacyEnum: id === "gamemode"
    };
}

function groupedServerFields(properties, language) {
    const values = new Map(Object.entries({ ...serverDefaults, ...properties }));
    const groups = [];
    for (const [title, keys] of serverGroups) {
        const fields = [];
        for (const key of keys) {
            if (!values.has(key)) continue;
            fields.push(makeServerField(key, values.get(key), language));
            values.delete(key);
        }
        if (fields.length) groups.push({ title: localized(title, language), alias: localized(title, otherLanguage(language)), fields });
    }
    if (values.size) {
        const title = ["其他设置", "Other settings"];
        groups.push({ title: localized(title, language), alias: localized(title, otherLanguage(language)),
            fields: [...values].map(([key, value]) => makeServerField(key, value, language)) });
    }
    return groups;
}

function groupedTOMLFields(kind, fields, language) {
    const groups = new Map();
    for (const field of fields) {
        const key = field.section || "";
        if (!groups.has(key)) {
            const title = sectionLabel(kind, key, language);
            const alias = sectionLabel(kind, key, otherLanguage(language));
            const custom = title === key && alias === key && !/\p{Script=Han}/u.test(key);
            groups.set(key, { key, alias, title: custom ? localized(customGroup, language) : title,
                custom, fields: [] });
        }
        const path = key ? key + "." + field.key : field.key;
        groups.get(key).fields.push({ ...field, value: String(field.value ?? ""),
            label: translatedField(kind, field.key, path, language),
            alias: translatedField(kind, field.key, path, otherLanguage(language)) });
    }
    return [...groups.values()];
}

function refreshGroups(state, language) {
    const snapshot = state.document;
    if (!snapshot) return;
    state.groups = state.kind === "server" ? groupedServerFields(snapshot.properties, language)
        : groupedTOMLFields(state.kind, snapshot.fields || [], language);
}

function adopt(state, snapshot, language) {
    state.document = { ...snapshot, text: (snapshot.text || "").replace(/\r\n?/g, "\n") };
    state.text = state.document.text;
    refreshGroups(state, language);
    state.updates.clear();
    state.error = "";
}

export function mountConfig(container, api) {
    let directory = "";
    let active = false;
    let generation = 0;
    let current = views[0];
    let documents = new Map();
    let reportedDirty = false;
    let language = api.language?.() === "en" ? "en" : "zh-CN";
    const t = (zh, en) => localized([zh, en], language);

    const root = element("section", "config-workspace");
    const tabs = element("div", "config-tabs");
    tabs.setAttribute("role", "tablist");
    const toolbar = element("div", "config-toolbar");
    const modes = element("div", "config-modes");
    modes.setAttribute("role", "group");
    const formButton = button("config-mode");
    const rawButton = button("config-mode");
    modes.append(formButton, rawButton);
    const searchGroup = element("div", "config-search-group");
    const search = element("input", "config-input config-search");
    search.type = "search";
    const find = button("config-button");
    const result = element("span", "config-search-result");
    result.setAttribute("role", "status");
    searchGroup.append(search, find, result);
    toolbar.append(searchGroup, modes);
    const body = element("div", "config-content");
    body.id = "config-content";
    body.setAttribute("role", "tabpanel");
    const footer = element("footer", "config-footer");
    const info = element("div", "config-info");
    const fileName = element("code", "config-filename");
    const status = element("span", "config-status");
    status.setAttribute("role", "status");
    info.append(fileName, status);
    const actions = element("div", "config-actions");
    const reloadButton = button("config-button");
    const saveButton = button("config-button config-primary");
    actions.append(reloadButton, saveButton);
    footer.append(info, actions);
    root.append(tabs, toolbar, body, footer);
    container.replaceChildren(root);

    const tabButtons = views.map((view, index) => {
        const tab = button("config-tab");
        const marker = element("span", "config-unsaved");
        marker.setAttribute("aria-hidden", "true");
        tab.append(element("span", "config-tab-label"), marker);
        tab.id = "config-tab-" + view.kind;
        tab.setAttribute("role", "tab");
        tab.setAttribute("aria-controls", body.id);
        tab.addEventListener("click", () => choose(view));
        tab.addEventListener("keydown", event => {
            const adjacent = {
                ArrowRight: (index + 1) % views.length, ArrowLeft: (index + views.length - 1) % views.length,
                Home: 0, End: views.length - 1
            };
            if (!Object.hasOwn(adjacent, event.key)) return;
            event.preventDefault();
            const target = adjacent[event.key];
            choose(views[target]);
            tabButtons[target].focus();
        });
        tabs.append(tab);
        return tab;
    });

    function selected() {
        if (!documents.has(current.kind)) documents.set(current.kind, blankState(current.kind));
        return documents.get(current.kind);
    }

    function hasChanges() {
        return [...documents.values()].some(dirty);
    }

    function choose(view) {
        if (current === view) return;
        current = view;
        render();
        ensureLoaded();
    }

    function chooseMode(mode) {
        const state = selected();
        if (state.mode === mode) return;
        state.mode = mode;
        render();
    }

    function lockedByOtherMode(state) {
        return state.mode === "form" ? rawDirty(state) : formDirty(state);
    }

    function contentAvailable(state) {
        return state.document && (state.document.exists || state.kind === "server");
    }

    function canEdit(state) {
        return directory && contentAvailable(state) && !active && !state.loading && !state.saving
            && !lockedByOtherMode(state) && (state.mode === "raw" || !state.document.formIssue);
    }

    function localizedError(issue) {
        if (issue.conflict) return t("文件已被外部修改。可以复制当前编辑，再重新读取。",
            "The file changed outside the app. Copy your edits before reloading.");
        const fallback = issue.stage === "read"
            ? t("无法读取配置。", "Could not read the configuration.")
            : t("无法保存配置。", "Could not save the configuration.");
        const detail = api.errorMessage?.(issue.error) || errorMessage(issue.error, "");
        return detail && (language !== "en" || !/\p{Script=Han}/u.test(detail)) ? detail : fallback;
    }

    function updateMeta() {
        const state = selected();
        for (const [index, view] of views.entries()) {
            const item = documents.get(view.kind);
            const isDirty = Boolean(item && dirty(item));
            const tab = tabButtons[index];
            tab.setAttribute("aria-selected", String(current === view));
            tab.tabIndex = current === view ? 0 : -1;
            const title = localized(view.title, language);
            tab.title = isDirty ? title + t("（有未保存的修改）", " (unsaved changes)") : title;
            tab.querySelector(".config-unsaved").hidden = !isDirty;
        }
        body.setAttribute("aria-labelledby", "config-tab-" + current.kind);
        body.setAttribute("aria-busy", String(state.loading));
        formButton.setAttribute("aria-pressed", String(state.mode === "form"));
        rawButton.setAttribute("aria-pressed", String(state.mode === "raw"));
        formButton.title = formButton.textContent;
        rawButton.title = rawButton.textContent;
        formButton.disabled = rawButton.disabled = !contentAvailable(state);
        fileName.textContent = state.document?.path || current.path;
        fileName.title = fileName.textContent;

        status.classList.toggle("config-error", Boolean(state.error));
        status.title = state.error ? errorMessage(state.error.error, "") : "";
        if (state.loading) status.textContent = t("正在读取配置…", "Loading configuration…");
        else if (state.saving) status.textContent = t("正在保存…", "Saving…");
        else if (state.error) status.textContent = localizedError(state.error);
        else if (!directory) status.textContent = "";
        else if (lockedByOtherMode(state))
            status.textContent = state.mode === "form"
                ? t("文件原文有未保存的修改，返回原文可保存。", "Switch to Raw file to save your edits.")
                : t("配置表单有未保存的修改，返回表单可保存。", "Switch to Form to save your edits.");
        else if (active) status.textContent = t("服务端运行中，停服后可修改。", "Stop the server to edit settings.");
        else if (dirty(state)) status.textContent = t("有未保存的修改", "Unsaved changes");
        else if (current.kind === "server" && state.document && !state.document.exists)
            status.textContent = t("保存后会创建 server.properties。", "Saving creates server.properties.");
        else if (state.document?.exists) status.textContent = t("更改将在下次启动时生效。", "Changes take effect on the next start.");
        else status.textContent = "";

        reloadButton.disabled = !directory || state.loading || state.saving;
        saveButton.disabled = !canEdit(state) || !dirty(state);
        for (const control of body.querySelectorAll("[data-config-edit]")) {
            if (control.classList.contains("config-text")) control.readOnly = !canEdit(state);
            else control.disabled = !canEdit(state);
        }
        const isDirty = hasChanges();
        if (isDirty !== reportedDirty) {
            reportedDirty = isDirty;
            api.onDirtyChange?.();
        }
    }

    function makeSelect(field, value) {
        const control = element("select", "config-input");
        const equivalent = field.legacyEnum && /^\d+$/.test(value) ? field.options[Number(value)]?.[0] : value.toLowerCase();
        const known = field.options.some(([key]) => key === equivalent);
        if (!known) {
            const existing = element("option", "", value || t("（空）", "(empty)"));
            existing.value = value;
            control.append(existing);
        }
        for (const [key, zh, en] of field.options) {
            const option = element("option", "", t(zh, en));
            option.value = key;
            control.append(option);
        }
        control.value = known ? equivalent : value;
        return control;
    }

    function makeField(state, group, field) {
        const row = element("label", "config-field");
        const title = element("span", "config-field-label", field.label);
        const key = element("code", "config-field-key", field.key);
        if (field.label !== localized(customSetting, language)) key.setAttribute("aria-hidden", "true");
        if (field.description) title.title = field.description;
        row.append(title, key);
        row.dataset.search = [field.label, field.alias, field.key, field.id, group.key, group.title, group.alias]
            .join(" ").toLocaleLowerCase();
        row.dataset.field = field.id;
        if (state.kind === "server" && field.id === "difficulty") {
            const control = element("input", "config-input config-locked-value");
            control.type = "text";
            control.value = t("困难", "Hard");
            control.readOnly = true;
            key.textContent += t(" · 已锁定", " · Locked");
            row.append(control);
            return row;
        }
        const value = state.updates.get(field.id) ?? field.value;
        const isBoolean = field.type === "boolean" || field.type === "bool";
        let control;
        if (field.options) control = makeSelect(field, value);
        else if (field.type !== "password" && (field.type === "array" || field.type === "multiline" || value.includes("\n"))) {
            control = element("textarea", "config-input config-value-block");
            control.rows = 2;
            control.value = value;
            if (field.type === "array") {
                control.placeholder = t("按 TOML 数组格式填写", "Enter a TOML array");
                control.spellcheck = false;
            }
        } else {
            control = element("input", isBoolean ? "config-toggle" : "config-input");
            if (isBoolean) {
                control.type = "checkbox";
                control.checked = value.toLowerCase() === "true";
            } else {
                control.type = field.type === "password" ? "password" : "text";
                if (field.type === "integer" || field.type === "int") control.inputMode = "numeric";
                if (field.type === "float" || field.type === "number") control.inputMode = "decimal";
                control.autocomplete = "off";
                control.value = value;
            }
        }
        if (field.type === "raw") {
            control.readOnly = true;
            control.title = t("此项可在文件原文中修改。", "Edit this value in the raw file.");
            key.textContent += t(" · 仅原文可编辑", " · Raw file only");
            row.append(control);
            return row;
        }
        control.setAttribute("data-config-edit", "");
        control.addEventListener(field.options || isBoolean ? "change" : "input", () => {
            const edited = isBoolean ? String(control.checked) : control.value;
            if (edited === field.value) state.updates.delete(field.id);
            else state.updates.set(field.id, edited);
            state.error = "";
            updateMeta();
        });
        row.append(control);
        return row;
    }

    function configForm(state) {
        const form = element("div", "config-form");
        for (const group of state.groups) {
            const card = element("section", "config-fields");
            const heading = element("h3", "config-group-title", group.title);
            if (group.custom) heading.append(element("code", "config-group-key", group.key));
            card.append(heading);
            const fields = element("div", "config-field-grid");
            for (const field of group.fields) fields.append(makeField(state, group, field));
            card.append(fields);
            form.append(card);
        }
        form.append(element("div", "config-empty config-no-results", t("没有找到匹配的配置项。", "No matching settings.")));
        return form;
    }

    function filterForm(state) {
        const empty = body.querySelector(".config-no-results");
        if (!empty) return;
        const terms = state.query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
        let matches = 0;
        for (const group of body.querySelectorAll(".config-fields")) {
            let visible = false;
            for (const field of group.querySelectorAll(".config-field")) {
                const matched = terms.every(term => field.dataset.search.includes(term));
                field.hidden = !matched;
                if (matched) {
                    visible = true;
                    matches++;
                }
            }
            group.hidden = !visible;
        }
        empty.hidden = matches > 0;
        result.textContent = terms.length ? t("匹配 " + matches + " 项",
            matches + (matches === 1 ? " match" : " matches")) : "";
    }

    function rawEditor(state) {
        const wrapper = element("div", "config-editor");
        const label = element("label", "config-field-label", t("配置文件原文", "Raw configuration file"));
        label.htmlFor = "config-raw";
        const heading = element("div", "config-editor-heading");
        heading.append(label);
        const text = element("textarea", "config-text");
        text.id = label.htmlFor;
        if (state.kind === "server") {
            const note = element("span", "config-editor-note", t("游戏难度已锁定，保存时固定为 difficulty=hard。",
                "Difficulty is locked. The file is saved with difficulty=hard."));
            note.id = "config-difficulty-note";
            text.setAttribute("aria-describedby", note.id);
            heading.append(note);
        }
        text.spellcheck = false;
        text.wrap = "off";
        text.setAttribute("data-config-edit", "");
        text.value = state.text;
        text.addEventListener("input", () => {
            state.text = text.value;
            state.error = "";
            result.textContent = "";
            updateMeta();
        });
        wrapper.append(heading, text);
        return wrapper;
    }

    function findNext() {
        const text = body.querySelector(".config-text");
        if (!text || !search.value) return;
        const literal = search.value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        const pattern = new RegExp(literal, "giu");
        pattern.lastIndex = text.selectionEnd;
        let match = pattern.exec(text.value);
        if (!match) match = pattern.exec(text.value);
        result.textContent = match ? "" : t("无匹配项", "No matches");
        if (!match) return;
        const line = text.value.slice(0, match.index).split("\n").length;
        text.focus();
        text.setSelectionRange(match.index, match.index + match[0].length);
        text.scrollTop = Math.max(0, (line - 2) * parseFloat(getComputedStyle(text).lineHeight));
    }

    function render() {
        const state = selected();
        const raw = state.mode === "raw";
        tabs.setAttribute("aria-label", t("配置文件", "Configuration files"));
        modes.setAttribute("aria-label", t("编辑方式", "Edit mode"));
        for (const [index, view] of views.entries())
            tabButtons[index].querySelector(".config-tab-label").textContent = localized(view.title, language);
        formButton.textContent = t("配置表单", "Form");
        rawButton.textContent = t("文件原文", "Raw file");
        find.textContent = t("查找下一处", "Find next");
        reloadButton.textContent = t("重新读取", "Reload");
        saveButton.textContent = t("保存配置", "Save");
        let content;
        if (!directory) content = element("div", "config-empty", t("选择服务端目录后可查看配置。", "Choose a server folder to view its settings."));
        else if (!state.document)
            content = element("div", "config-empty", state.error
                ? t("配置读取失败，可重新读取。", "Could not load the configuration. Try reloading.")
                : t("正在读取配置…", "Loading configuration…"));
        else if (!contentAvailable(state)) content = element("div", "config-empty", localized(current.missing, language));
        else if (raw) content = rawEditor(state);
        else if (state.document.formIssue) {
            const detail = state.document.formIssue;
            const message = t("此文件暂时无法显示为表单，请切换到「文件原文」。",
                "This file cannot be shown as a form. Switch to Raw file to view it.");
            content = element("div", "config-empty", language === "en" ? message : message + detail);
            content.title = detail;
        } else if (!state.groups.length) content = element("div", "config-empty",
            t("此文件没有可编辑的配置项。", "This file has no editable settings."));
        else content = configForm(state);
        body.replaceChildren(content);
        body.scrollTop = 0;
        const prompt = raw ? t("查找原文内容", "Find in file") : t("搜索中文名称、配置键或分组", "Search names, keys, or groups");
        search.placeholder = prompt;
        search.setAttribute("aria-label", prompt);
        search.value = raw ? state.rawQuery : state.query;
        search.disabled = raw ? !body.querySelector(".config-text") : !body.querySelector(".config-form");
        find.hidden = !raw;
        find.disabled = search.disabled;
        result.textContent = "";
        if (!raw) filterForm(state);
        updateMeta();
    }

    async function read(state) {
        if (state.loading || state.saving) return;
        const requestGeneration = generation;
        state.loading = true;
        state.stale = false;
        state.error = "";
        if (state === selected()) updateMeta();
        try {
            const snapshot = await api.readConfig(state.kind);
            if (requestGeneration === generation) adopt(state, snapshot, language);
        } catch (error) {
            if (requestGeneration === generation) state.error = { stage: "read", error };
        } finally {
            state.loading = false;
            if (requestGeneration === generation) {
                if (state === selected()) {
                    render();
                    if (state.stale) void read(state);
                } else updateMeta();
            }
        }
    }

    function ensureLoaded() {
        const state = selected();
        if (directory && (!state.document || state.stale)) void read(state);
    }

    async function reload() {
        const state = selected();
        const requestGeneration = generation;
        if (dirty(state) && !await api.confirm({
            title: t("重新读取配置", "Reload configuration"),
            message: t("读取磁盘上的文件将丢弃当前文件的未保存修改。", "Reloading discards unsaved changes to this file."),
            accept: t("重新读取", "Reload")
        })) return;
        if (requestGeneration === generation) await read(state);
    }

    function validateServerForm(state) {
        for (const group of state.groups) {
            for (const field of group.fields) {
                if (field.type !== "integer" || !state.updates.has(field.id)) continue;
                const text = state.updates.get(field.id);
                const number = Number(text);
                if (/^[+-]?\d+$/.test(text) && (!field.range || (number >= field.range[0] && number <= field.range[1]))) continue;
                const message = field.range
                    ? t(field.label + "应填写 " + field.range[0] + " 到 " + field.range[1] + " 之间的整数。",
                        "Enter a whole number from " + field.range[0] + " to " + field.range[1] + " for " + field.label + ".")
                    : t(field.label + "应填写整数。", "Enter a whole number for " + field.label + ".");
                api.toast(message, "error");
                const row = [...body.querySelectorAll(".config-field")].find(item => item.dataset.field === field.id);
                if (row?.hidden) {
                    state.query = search.value = "";
                    filterForm(state);
                }
                row?.querySelector("[data-config-edit]").focus();
                return false;
            }
        }
        return true;
    }

    async function save() {
        const state = selected();
        if (!canEdit(state) || !dirty(state)) return;
        const fromForm = state.mode === "form";
        if (fromForm && state.kind === "server" && !validateServerForm(state)) return;
        const requestGeneration = generation;
        const revision = state.document.revision;
        state.saving = true;
        state.error = "";
        updateMeta();
        try {
            let snapshot;
            if (!fromForm) snapshot = await api.saveConfig(state.kind, state.text, revision);
            else if (state.kind === "server") snapshot = await api.saveProperties(Object.fromEntries(state.updates), revision);
            else snapshot = await api.saveTOML(state.kind, Object.fromEntries(state.updates), revision);
            if (requestGeneration !== generation) return;
            adopt(state, snapshot, language);
            api.toast(t("配置已保存。", "Configuration saved."), "success");
        } catch (error) {
            if (requestGeneration !== generation) return;
            const message = errorMessage(error, "");
            const conflict = error?.status === 409 || message.startsWith("文件在读取后已被修改");
            state.error = { stage: "save", error, conflict };
            api.toast(localizedError(state.error), "error");
        } finally {
            state.saving = false;
            if (requestGeneration === generation) {
                if (state === selected()) {
                    const position = body.scrollTop;
                    render();
                    body.scrollTop = position;
                } else updateMeta();
            }
        }
    }

    search.addEventListener("input", () => {
        const state = selected();
        if (state.mode === "form") {
            state.query = search.value;
            filterForm(state);
        } else {
            state.rawQuery = search.value;
            result.textContent = "";
        }
    });
    search.addEventListener("keydown", event => {
        if (selected().mode === "raw" && event.key === "Enter") {
            event.preventDefault();
            findNext();
        }
    });
    find.addEventListener("click", findNext);
    formButton.addEventListener("click", () => chooseMode("form"));
    rawButton.addEventListener("click", () => chooseMode("raw"));
    reloadButton.addEventListener("click", reload);
    saveButton.addEventListener("click", save);
    root.addEventListener("keydown", event => {
        const key = event.key.toLowerCase();
        if ((event.ctrlKey || event.metaKey) && key === "s") {
            event.preventDefault();
            void save();
        }
        if (!search.disabled && (event.ctrlKey || event.metaKey) && key === "f") {
            event.preventDefault();
            search.focus();
            search.select();
        }
        if (!find.hidden && event.key === "F3") {
            event.preventDefault();
            findNext();
        }
    });
    render();

    return {
        setContext(nextDirectory, nextActive) {
            const next = nextDirectory || "";
            const stopped = active && !nextActive;
            active = Boolean(nextActive);
            if (next !== directory) {
                generation++;
                directory = next;
                documents = new Map();
                current = views[0];
                render();
                ensureLoaded();
            } else {
                if (stopped) {
                    for (const state of documents.values()) {
                        if (!dirty(state)) state.stale = true;
                    }
                    ensureLoaded();
                }
                updateMeta();
            }
        },
        setLanguage() {
            const next = api.language?.() === "en" ? "en" : "zh-CN";
            if (language === next) return;
            language = next;
            const position = body.scrollTop;
            const editor = body.querySelector(".config-text");
            const selection = editor && [editor.selectionStart, editor.selectionEnd, editor.selectionDirection];
            const scroll = editor && [editor.scrollTop, editor.scrollLeft];
            for (const state of documents.values()) refreshGroups(state, language);
            render();
            body.scrollTop = position;
            const translatedEditor = body.querySelector(".config-text");
            if (selection && translatedEditor) {
                translatedEditor.setSelectionRange(...selection);
                [translatedEditor.scrollTop, translatedEditor.scrollLeft] = scroll;
            }
        },
        hasChanges
    };
}
