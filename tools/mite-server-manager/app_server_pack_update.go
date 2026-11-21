package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

type ServerPackUpdateCheck struct {
	InstalledVersion string             `json:"installedVersion"`
	Release          *ServerPackRelease `json:"release,omitempty"`
}

type checkedServerPackUpdate struct {
	directory string
	installed string
	release   ServerPackRelease
}

func (a *App) CheckServerPackUpdate() (ServerPackUpdateCheck, error) {
	a.mu.Lock()
	directory := a.directory
	a.mu.Unlock()
	if directory == "" {
		return ServerPackUpdateCheck{}, nil
	}
	if err := a.interruptedServerPackUpdate(directory); err != nil {
		return ServerPackUpdateCheck{}, err
	}
	installed, err := readMiteServerPackManifest(directory)
	if errors.Is(err, os.ErrNotExist) {
		return ServerPackUpdateCheck{}, nil
	}
	if err != nil {
		return ServerPackUpdateCheck{}, fmt.Errorf(a.text("无法读取已安装服务端的版本。%w", "Could not identify the installed server pack. %w"), err)
	}
	if !validServerPackVersion(installed.Version) || installed.Minecraft != "1.21.1" {
		return ServerPackUpdateCheck{}, nil
	}

	releases, err := a.ListServerPacks()
	if err != nil {
		return ServerPackUpdateCheck{}, err
	}
	var latest *ServerPackRelease
	for _, release := range releases {
		if compareServerPackVersions(release.Version, installed.Version) <= 0 {
			continue
		}
		if latest == nil || compareServerPackVersions(release.Version, latest.Version) > 0 {
			latest = &release
		}
	}

	a.mu.Lock()
	defer a.mu.Unlock()
	if !strings.EqualFold(directory, a.directory) || a.cancelServerPack != nil {
		return ServerPackUpdateCheck{}, errors.New(a.text("服务端目录或状态已变更，请重新检查。", "The server folder or state changed. Check for updates again."))
	}
	a.checkedPackUpdate = nil
	if latest != nil {
		a.checkedPackUpdate = &checkedServerPackUpdate{directory: directory, installed: installed.Version, release: *latest}
	}
	return ServerPackUpdateCheck{InstalledVersion: installed.Version, Release: latest}, nil
}

func (a *App) BeginServerPackUpdate(id int, installedVersion string) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, err := a.editableDirectory()
	if err != nil {
		return err
	}
	if a.unsaved.Load() {
		return errors.New(a.text("请先保存未保存的设置，再更新服务端。", "Save your settings before updating the server pack."))
	}
	checked := a.checkedPackUpdate
	if checked == nil || checked.release.ID != id || checked.installed != installedVersion ||
		!strings.EqualFold(directory, checked.directory) ||
		compareServerPackVersions(checked.release.Version, installedVersion) <= 0 {
		return a.stalePackUpdate()
	}

	manifest, err := readMiteServerPackManifest(directory)
	if err != nil || manifest.Version != checked.installed {
		return a.stalePackUpdate()
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	a.cancelServerPack, a.serverPackDone = cancel, done
	a.serverPackState = ServerPackDownloadSnapshot{Operation: "update", Phase: "backing-up", Total: checked.release.FileLength}
	go a.runServerPackUpdate(ctx, cancel, done, *checked)
	return nil
}

func (a *App) stalePackUpdate() error {
	return errors.New(a.text("服务端版本已经变更，请重新检查更新。", "The server pack version changed. Check for updates again."))
}

func (a *App) runServerPackUpdate(ctx context.Context, cancel context.CancelFunc, done chan struct{}, checked checkedServerPackUpdate) {
	defer close(done)
	defer cancel()
	backup, err := a.updateServerPack(ctx, checked)
	a.mu.Lock()
	defer a.mu.Unlock()
	a.cancelServerPack, a.serverPackDone = nil, nil

	state := ServerPackDownloadSnapshot{Operation: "update", Phase: "complete", BackupPath: backup}
	if err != nil {
		if errors.Is(err, context.Canceled) && a.interruptedServerPackUpdate(checked.directory) == nil {
			state.Phase = "canceled"
		} else {
			state.Phase = "error"
			state.Error = a.text("服务端更新未完成。", "The server pack update did not finish. ") + err.Error()
		}
	} else {
		a.checkedPackUpdate = nil
	}
	a.serverPackState = state
}

func (a *App) updateServerPack(ctx context.Context, checked checkedServerPackUpdate) (string, error) {
	directory := checked.directory
	unlock, err := acquireServerLock(directory)
	if err != nil {
		return "", err
	}
	defer unlock()

	unlockWorld, err := holdWorldLock(directory)
	if err != nil {
		return "", err
	}
	if unlockWorld != nil {
		defer unlockWorld()
	}

	if err := a.interruptedServerPackUpdate(directory); err != nil {
		return "", err
	}
	manifest, err := readMiteServerPackManifest(directory)
	if err != nil || manifest.Version != checked.installed {
		return "", a.stalePackUpdate()
	}
	backup, err := backupMiteServer(ctx, directory, checked.installed)
	if err != nil {
		return "", fmt.Errorf(a.text("备份未完成。%w", "Backup did not finish. %w"), err)
	}

	err = a.withDownloadedPack(ctx, filepath.Dir(directory), checked.release, "update", backup, func(archive string) error {
		incoming := filepath.Join(filepath.Dir(archive), "incoming")
		defer os.RemoveAll(incoming)
		if err := extractMiteServerPack(ctx, archive, incoming); err != nil {
			return err
		}
		if unlockWorld == nil {
			lock, err := holdWorldLock(directory)
			if err != nil {
				return err
			}
			if lock != nil {
				defer lock()
			}
		}
		a.mu.Lock()
		a.serverPackState = ServerPackDownloadSnapshot{Operation: "update", Phase: "updating", BackupPath: backup}
		a.mu.Unlock()
		return applyMiteServerPackUpdate(ctx, directory, incoming, backup, checked.installed, checked.release.Version)
	})
	return backup, err
}

func (a *App) interruptedServerPackUpdate(directory string) error {
	path := filepath.Join(directory, serverPackUpdateMarker)
	info, err := os.Lstat(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}

	var record struct {
		BackupPath string `json:"backupPath"`
	}
	if err == nil && info.Mode().IsRegular() {
		file, openErr := os.Open(path)
		if openErr == nil {
			_ = json.NewDecoder(io.LimitReader(file, 64<<10)).Decode(&record)
			file.Close()
		}
	}
	message := a.text("检测到未完成的服务端更新。请先将备份解压到新目录并切换，再启动服务端。", "An incomplete server pack update was found. Extract the backup into a new folder and select it before starting the server.")
	if filepath.IsAbs(record.BackupPath) {
		message += " " + record.BackupPath
	}
	return errors.New(message)
}
