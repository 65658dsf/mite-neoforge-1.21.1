package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

const (
	packFolderPrefix       = "MITE-Modern-ITE-"
	packFolderSuffix       = "-server"
	packDownloadTempPrefix = ".mite-server-download-"
)

var serverPackVersionPattern = regexp.MustCompile(`^[0-9]+(?:\.[0-9]+){1,3}(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?$`)

type ServerPackDownloadSnapshot struct {
	Phase    string `json:"phase"`
	Received int64  `json:"received"`
	Total    int64  `json:"total"`
	Error    string `json:"error"`
}

func initialServerLocation(executable, requested, previous, working string) (directory, target string) {
	executableDirectory := ""
	if executable != "" {
		executableDirectory = filepath.Dir(executable)
	}
	for _, candidate := range []string{requested, executableDirectory, previous, working} {
		directory, target := resolveServerLocation(candidate, executable, previous)
		if directory != "" || target != "" {
			return directory, target
		}
	}
	return "", ""
}

func resolveServerLocation(candidate, executable, previous string) (directory, target string) {
	if candidate == "" {
		return "", ""
	}
	absolute, err := filepath.Abs(candidate)
	if err != nil {
		return "", ""
	}
	if isServerDirectory(absolute) {
		return absolute, ""
	}

	entries, err := os.ReadDir(absolute)
	if err != nil {
		return "", ""
	}
	installed := ""
	count := 0
	for _, entry := range entries {
		if !entry.IsDir() || packFolderVersion(entry.Name()) == "" {
			continue
		}
		path := filepath.Join(absolute, entry.Name())
		if !isServerDirectory(path) {
			continue
		}
		if strings.EqualFold(path, previous) {
			return path, ""
		}
		installed = path
		count++
	}
	if count == 1 {
		return installed, ""
	}
	if emptyServerPackFolder(absolute, executable, entries, false) {
		return "", absolute
	}
	return "", ""
}

func emptyServerPackFolder(directory, executable string, entries []os.DirEntry, locked bool) bool {
	for _, entry := range entries {
		if !entry.IsDir() && strings.EqualFold(filepath.Join(directory, entry.Name()), executable) {
			continue
		}
		if locked && entry.Name() == ".mite-manager.lock" {
			continue
		}
		if entry.IsDir() && (strings.HasPrefix(entry.Name(), packDownloadTempPrefix) ||
			strings.HasPrefix(entry.Name(), ".mite-server-pack-")) {
			continue
		}
		return false
	}
	return true
}

func packFolderVersion(name string) string {
	lower := strings.ToLower(name)
	if !strings.HasPrefix(lower, strings.ToLower(packFolderPrefix)) || !strings.HasSuffix(lower, packFolderSuffix) ||
		len(name) <= len(packFolderPrefix)+len(packFolderSuffix) {
		return ""
	}
	version := name[len(packFolderPrefix) : len(name)-len(packFolderSuffix)]
	if !validServerPackVersion(version) {
		return ""
	}
	return version
}

func validServerPackVersion(version string) bool {
	return len(version) <= 45 && serverPackVersionPattern.MatchString(version)
}

func installedServerPackVersion(directory string) string {
	file, err := os.Open(filepath.Join(directory, "server-pack.json"))
	if err == nil {
		defer file.Close()
		var metadata struct {
			Name    string `json:"name"`
			Version string `json:"version"`
		}
		if json.NewDecoder(io.LimitReader(file, 128<<10)).Decode(&metadata) == nil &&
			strings.EqualFold(strings.ReplaceAll(metadata.Name, ":", ""), "MITE Modern ITE") &&
			validServerPackVersion(metadata.Version) {
			return metadata.Version
		}
	}

	return packFolderVersion(filepath.Base(directory))
}

func (a *App) ListServerPacks() ([]ServerPackRelease, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()
	releases, err := listMiteServerPacks(ctx, &http.Client{Timeout: 12 * time.Second})
	if err != nil {
		return nil, fmt.Errorf(a.text("无法读取 CurseForge 服务端版本。%w", "Could not load the CurseForge server pack releases. %w"), err)
	}
	byID := make(map[int]ServerPackRelease, len(releases))
	filtered := make([]ServerPackRelease, 0, len(releases))
	for _, release := range releases {
		if release.ID <= 0 || release.ClientID <= 0 || !validServerPackVersion(release.Version) {
			continue
		}
		byID[release.ID] = release
		filtered = append(filtered, release)
	}
	a.mu.Lock()
	a.serverPackReleases = byID
	a.mu.Unlock()
	return filtered, nil
}

func (a *App) GetServerPackChangelog(id int) (string, error) {
	a.mu.Lock()
	release, exists := a.serverPackReleases[id]
	a.mu.Unlock()
	if !exists {
		return "", a.unknownServerPack()
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	changelog, err := getMiteClientChangelog(ctx, &http.Client{Timeout: 12 * time.Second}, release.ClientID)
	if err != nil {
		return "", fmt.Errorf(a.text("无法读取对应客户端的更新日志。%w", "Could not load the matching client changelog. %w"), err)
	}
	return changelog, nil
}

func (a *App) BeginServerPackDownload(id int) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.closing.Load() {
		return errors.New(a.text("管理器正在退出。", "The server manager is closing."))
	}
	if a.cancelServerPack != nil {
		return errors.New(a.text("服务端正在下载或解压。", "A server pack is already downloading or extracting."))
	}
	if a.session.IsActive() || a.directory != "" || a.downloadTarget == "" {
		return errors.New(a.text("请选择空安装目录后再下载服务端。", "Choose an empty installation folder before downloading the server pack."))
	}
	release, exists := a.serverPackReleases[id]
	if !exists {
		return a.unknownServerPack()
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	a.cancelServerPack = cancel
	a.serverPackDone = done
	a.serverPackState = ServerPackDownloadSnapshot{Phase: "downloading", Total: release.FileLength}
	go a.runServerPackDownload(ctx, cancel, done, a.downloadTarget, release)
	return nil
}

func (a *App) CancelServerPackDownload() {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.cancelServerPack != nil {
		a.cancelServerPack()
	}
}

func (a *App) OpenServerPackPage() {
	runtime.BrowserOpenURL(a.windowContext(), serverPackPageURL)
}

func (a *App) unknownServerPack() error {
	return errors.New(a.text("找不到该服务端版本，请重新加载版本列表。", "The selected server pack was not found. Reload the release list."))
}

func (a *App) runServerPackDownload(ctx context.Context, cancel context.CancelFunc, done chan struct{}, root string, release ServerPackRelease) {
	defer close(done)
	defer cancel()
	destination := filepath.Join(root, packFolderPrefix+release.Version+packFolderSuffix)
	err := a.installDownloadedServerPack(ctx, root, destination, release)
	a.mu.Lock()
	defer a.mu.Unlock()
	a.cancelServerPack = nil
	a.serverPackDone = nil
	if err != nil {
		if errors.Is(ctx.Err(), context.Canceled) {
			a.serverPackState = ServerPackDownloadSnapshot{Phase: "canceled"}
			return
		}
		a.serverPackState = ServerPackDownloadSnapshot{
			Phase: "error", Error: a.text("无法安装服务端。", "Could not install the server pack. ") + err.Error(),
		}
		return
	}

	a.directory, a.downloadTarget = destination, ""
	a.preferences.Directory = destination
	a.session.Reset()
	if err := a.savePreferences(); err != nil {
		a.serverPackState.Error = a.text("服务端已经解压，无法保存最近使用的目录。", "The pack was extracted, but the recent folder could not be saved.")
	}
	a.serverPackState.Phase = "complete"
}

func (a *App) installDownloadedServerPack(ctx context.Context, root, destination string, release ServerPackRelease) error {
	unlock, err := acquireServerLock(root)
	if err != nil {
		return err
	}
	defer unlock()
	entries, err := os.ReadDir(root)
	if err != nil {
		return err
	}
	if !emptyServerPackFolder(root, a.executable, entries, true) {
		return errors.New(a.text("安装目录已有其他内容，请选择一个空目录。", "The installation folder contains other files. Choose an empty folder."))
	}
	if err := ctx.Err(); err != nil {
		return err
	}

	stage, err := os.MkdirTemp(root, packDownloadTempPrefix)
	if err != nil {
		return err
	}
	defer os.Remove(stage)
	archive := filepath.Join(stage, "server-pack.zip")
	defer os.Remove(archive)
	report := func(phase string, received, total int64) {
		a.mu.Lock()
		a.serverPackState = ServerPackDownloadSnapshot{Phase: phase, Received: received, Total: total}
		a.mu.Unlock()
	}

	if err := downloadMiteServerPack(ctx, &http.Client{Timeout: 20 * time.Minute}, release, archive, report); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	report("extracting", release.FileLength, release.FileLength)
	return extractMiteServerPack(ctx, archive, destination)
}
