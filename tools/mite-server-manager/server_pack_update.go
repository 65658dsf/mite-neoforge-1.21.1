// Author: NingMeng123
package main

import (
	"archive/zip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"

	"github.com/pelletier/go-toml/v2"
)

const (
	serverPackUpdateMarker = ".mite-update-pending.json"
	mitePackManifest       = "server-pack.json"
	miteManifestLimit      = 1 << 20
)

type miteServerPackManifest struct {
	Name      string        `json:"name"`
	Version   string        `json:"version"`
	Minecraft string        `json:"minecraft"`
	NeoForge  string        `json:"neoforge"`
	Mods      []mitePackMod `json:"mods"`
}

type mitePackMod struct {
	IDs  []string `json:"ids"`
	File string   `json:"file"`
	mitePackDigest
}

type mitePackDigest struct {
	Bytes  int64  `json:"bytes"`
	SHA256 string `json:"sha256"`
}

func readMiteServerPackManifest(directory string) (miteServerPackManifest, error) {
	root, err := os.OpenRoot(directory)
	if err != nil {
		return miteServerPackManifest{}, err
	}
	defer root.Close()
	manifest, _, err := loadMiteServerPackManifest(root)
	return manifest, err
}

func loadMiteServerPackManifest(root *os.Root) (miteServerPackManifest, mitePackDigest, error) {
	file, err := openMiteUpdateFile(root, mitePackManifest)
	if err != nil {
		return miteServerPackManifest{}, mitePackDigest{}, err
	}
	defer file.Close()
	body, err := io.ReadAll(io.LimitReader(file, miteManifestLimit+1))
	if err != nil {
		return miteServerPackManifest{}, mitePackDigest{}, err
	}
	var manifest miteServerPackManifest
	if len(body) > miteManifestLimit || json.Unmarshal(body, &manifest) != nil ||
		!strings.EqualFold(strings.ReplaceAll(strings.TrimSpace(manifest.Name), ":", ""), "MITE Modern ITE") ||
		!validServerPackVersion(manifest.Version) || !validServerPackVersion(manifest.Minecraft) ||
		!neoForgeInstaller.MatchString("neoforge-"+manifest.NeoForge+"-installer.jar") ||
		len(manifest.Mods) == 0 || len(manifest.Mods) > maxServerPackEntries {
		return manifest, mitePackDigest{}, fmt.Errorf("invalid MITE server-pack manifest: %s", mitePackManifest)
	}
	paths, ids := make(map[string]bool), make(map[string]bool)
	for i, mod := range manifest.Mods {
		directory, name, found := strings.Cut(mod.File, "/")
		pathKey := strings.ToUpper(mod.File)
		hash, decodeErr := hex.DecodeString(mod.SHA256)
		if !found || !strings.EqualFold(directory, "mods") || !validServerPackComponent(name) ||
			!strings.EqualFold(filepath.Ext(name), ".jar") || paths[pathKey] ||
			mod.Bytes <= 0 || mod.Bytes > maxServerPackFileBytes || decodeErr != nil ||
			len(hash) != sha256.Size || len(mod.IDs) == 0 {
			return manifest, mitePackDigest{}, fmt.Errorf("invalid or duplicate mod entry in server-pack manifest: %q", mod.File)
		}
		paths[pathKey] = true
		manifest.Mods[i].SHA256 = strings.ToLower(mod.SHA256)
		for _, id := range mod.IDs {
			key := strings.ToLower(id)
			if key == "" || strings.TrimSpace(key) != key || ids[key] {
				return manifest, mitePackDigest{}, fmt.Errorf("missing or duplicate mod ID in server-pack manifest: %q", id)
			}
			ids[key] = true
		}
	}
	if !ids["mite"] || !ids["modern_ite"] {
		return manifest, mitePackDigest{}, errors.New("server-pack manifest does not contain MITE Modern and MITE Modern ITE")
	}
	return manifest, digestMiteUpdateBytes(body), nil
}

type miteUpdateOperation struct {
	Path   string          `json:"path"`
	Before *mitePackDigest `json:"before,omitempty"`
	After  *mitePackDigest `json:"after,omitempty"`
}

type mitePendingUpdate struct {
	BackupPath       string                `json:"backupPath"`
	Directory        string                `json:"directory"`
	StagingDirectory string                `json:"stagingDirectory"`
	InstalledVersion string                `json:"installedVersion"`
	TargetVersion    string                `json:"targetVersion"`
	StartedAt        time.Time             `json:"startedAt"`
	Operations       []miteUpdateOperation `json:"operations"`
}

func applyMiteServerPackUpdate(ctx context.Context, current, incoming, backupPath, installedVersion, targetVersion string) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	backupPath, err := filepath.Abs(backupPath)
	if err != nil {
		return err
	}
	backup, err := os.Lstat(backupPath)
	if err != nil || !backup.Mode().IsRegular() || backup.Size() == 0 {
		return fmt.Errorf("server pack update requires a completed backup: %s", backupPath)
	}
	if current == "" || incoming == "" {
		return errors.New("missing server-pack update directory")
	}
	current, err = filepath.Abs(current)
	if err != nil {
		return err
	}
	original, err := os.OpenRoot(current)
	if err != nil {
		return err
	}
	defer original.Close()
	next, err := os.OpenRoot(incoming)
	if err != nil {
		return err
	}
	defer next.Close()
	if err := expectMiteUpdateFile(ctx, original, serverPackUpdateMarker, nil); err != nil {
		return fmt.Errorf("an interrupted server-pack update needs recovery: %w", err)
	}

	oldPack, oldDigest, err := loadMiteServerPackManifest(original)
	if err != nil {
		return fmt.Errorf("could not read the installed server-pack manifest: %w", err)
	}
	newPack, newDigest, err := loadMiteServerPackManifest(next)
	if err != nil {
		return fmt.Errorf("could not read the downloaded server-pack manifest: %w", err)
	}
	if !strings.EqualFold(oldPack.Version, installedVersion) || !strings.EqualFold(newPack.Version, targetVersion) ||
		strings.EqualFold(oldPack.Version, newPack.Version) {
		return errors.New("the server-pack manifests do not match the requested version change")
	}
	if oldPack.Minecraft != newPack.Minecraft {
		return fmt.Errorf("this update would change Minecraft from %s to %s. Install it in a separate folder", oldPack.Minecraft, newPack.Minecraft)
	}

	operations, err := planMiteModUpdate(ctx, original, next, oldPack, newPack)
	if err != nil {
		return err
	}
	if oldPack.NeoForge != newPack.NeoForge {
		loader, err := planMiteLoaderUpdate(ctx, original, next, oldPack.NeoForge, newPack.NeoForge)
		if err != nil {
			return err
		}
		operations = append(operations, loader...)
	}
	config, err := planMiteConfigDefaults(ctx, original, next)
	if err != nil {
		return err
	}
	operations = append(operations, config...)
	operations = append(operations, miteUpdateOperation{Path: mitePackManifest, Before: &oldDigest, After: &newDigest})

	staging, err := os.MkdirTemp(current, ".mite-update-")
	if err != nil {
		return err
	}
	keepStaging := false
	defer func() {
		if !keepStaging {
			_ = os.RemoveAll(staging)
		}
	}()
	transaction := mitePendingUpdate{
		BackupPath: backupPath, Directory: current, StagingDirectory: staging,
		InstalledVersion: oldPack.Version, TargetVersion: newPack.Version, StartedAt: time.Now().UTC(),
		Operations: operations,
	}
	for i, operation := range operations {
		if operation.After != nil {
			if err := stageMiteUpdateFile(ctx, next, operation, transaction.staged(i, "new")); err != nil {
				return err
			}
		}
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := transaction.createMarker(); err != nil {
		return err
	}
	keepStaging = true
	state, applyErr := transaction.apply(ctx, original)
	if applyErr != nil {
		if rollbackErr := transaction.rollback(original, state); rollbackErr != nil {
			return errors.Join(applyErr, fmt.Errorf("rollback is incomplete. Restore from %s: %w", backupPath, rollbackErr))
		}
	}
	if err := original.Remove(serverPackUpdateMarker); err != nil {
		return errors.Join(applyErr, fmt.Errorf("could not clear the update marker. Backup retained at %s: %w", backupPath, err))
	}
	keepStaging = false
	return applyErr
}

func planMiteModUpdate(ctx context.Context, current, incoming *os.Root, oldPack, newPack miteServerPackManifest) ([]miteUpdateOperation, error) {
	if info, err := current.Lstat("mods"); err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return nil, errors.New("the installed server must have a regular mods directory")
	}
	oldByPath, newByPath := make(map[string]mitePackMod), make(map[string]mitePackMod)
	for _, mod := range oldPack.Mods {
		oldByPath[strings.ToUpper(mod.File)] = mod
	}
	for _, mod := range newPack.Mods {
		if err := expectMiteUpdateFile(ctx, incoming, mod.File, &mod.mitePackDigest); err != nil {
			return nil, fmt.Errorf("invalid incoming mod: %w", err)
		}
		newByPath[strings.ToUpper(mod.File)] = mod
	}
	operations := make([]miteUpdateOperation, 0)
	affectedIDs, removing := make(map[string]bool), make(map[string]bool)
	for _, old := range oldPack.Mods {
		if _, stays := newByPath[strings.ToUpper(old.File)]; !stays {
			operations = append(operations, miteUpdateOperation{Path: old.File, Before: &old.mitePackDigest})
			removing[strings.ToUpper(old.File)] = true
		}
	}
	for _, mod := range newPack.Mods {
		path := strings.ToUpper(mod.File)
		old, exists := oldByPath[path]
		if exists && old.mitePackDigest == mod.mitePackDigest {
			continue
		}
		operation := miteUpdateOperation{Path: mod.File, After: &mod.mitePackDigest}
		if exists {
			operation.Path = old.File
			operation.Before = &old.mitePackDigest
			// Matching filenames with different case still refer to one Windows file.
			if operation.Path != mod.File {
				return nil, fmt.Errorf("a changed pack mod uses inconsistent path capitalization: %q and %q", old.File, mod.File)
			}
			removing[path] = true
		}
		operations = append(operations, operation)
		for _, id := range mod.IDs {
			affectedIDs[strings.ToLower(id)] = true
		}
	}
	for _, operation := range operations {
		if err := expectMiteUpdateFile(ctx, current, operation.Path, operation.Before); err != nil {
			return nil, fmt.Errorf("would overwrite or replace a locally changed mod: %w", err)
		}
	}
	if len(affectedIDs) > 0 {
		if err := checkMiteUserModConflicts(ctx, current, oldByPath, removing, affectedIDs); err != nil {
			return nil, err
		}
	}
	return operations, nil
}

func checkMiteUserModConflicts(ctx context.Context, root *os.Root, oldByPath map[string]mitePackMod, removing, affectedIDs map[string]bool) error {
	entries, err := fs.ReadDir(root.FS(), "mods")
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if err := ctx.Err(); err != nil {
			return err
		}
		if entry.IsDir() || !strings.EqualFold(filepath.Ext(entry.Name()), ".jar") {
			continue
		}
		path := "mods/" + entry.Name()
		key := strings.ToUpper(path)
		if removing[key] {
			continue
		}
		if old, managed := oldByPath[key]; managed && expectMiteUpdateFile(ctx, root, path, &old.mitePackDigest) == nil {
			continue
		}
		for _, id := range readMiteJarModIDs(root, path) {
			if affectedIDs[strings.ToLower(id)] {
				return fmt.Errorf("user mod %q already declares %q, which conflicts with the incoming update", path, id)
			}
		}
	}
	return ctx.Err()
}

func readMiteJarModIDs(root *os.Root, path string) []string {
	file, err := openMiteUpdateFile(root, path)
	if err != nil {
		return nil
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil || info.Size() > maxServerPackFileBytes {
		return nil
	}
	jar, err := zip.NewReader(file, info.Size())
	if err != nil {
		return nil
	}
	var result []string
	for _, entry := range jar.File {
		switch entry.Name {
		case "META-INF/neoforge.mods.toml", "META-INF/mods.toml":
			if entry.UncompressedSize64 > miteManifestLimit {
				continue
			}
			body, err := entry.Open()
			if err != nil {
				continue
			}
			data, err := io.ReadAll(io.LimitReader(body, miteManifestLimit+1))
			_ = body.Close()
			var metadata struct {
				Mods []struct {
					ID string `toml:"modId"`
				} `toml:"mods"`
			}
			if err == nil && len(data) <= miteManifestLimit && toml.Unmarshal(data, &metadata) == nil {
				for _, mod := range metadata.Mods {
					result = append(result, mod.ID)
				}
			}
		}
	}
	return result
}

func planMiteLoaderUpdate(ctx context.Context, current, incoming *os.Root, oldVersion, newVersion string) ([]miteUpdateOperation, error) {
	series := func(version string) string {
		parts := strings.SplitN(version, ".", 3)
		return strings.Join(parts[:min(2, len(parts))], ".")
	}
	if series(oldVersion) != series(newVersion) {
		return nil, fmt.Errorf("the NeoForge series changes from %s to %s. Install it in a separate folder", oldVersion, newVersion)
	}
	installer := "neoforge-" + newVersion + "-installer.jar"
	digest, err := digestMiteUpdateFile(ctx, incoming, installer)
	if err != nil {
		return nil, err
	}
	if err := expectMiteUpdateFile(ctx, current, installer, nil); err != nil {
		return nil, err
	}
	operations := []miteUpdateOperation{{Path: installer, After: &digest}}
	for _, path := range []string{"start.bat", "install.bat", "start.sh", "install.sh"} {
		newScript, err := readMiteUpdateScript(incoming, path)
		if errors.Is(err, os.ErrNotExist) && strings.HasSuffix(path, ".sh") {
			continue
		}
		if err != nil || !strings.Contains(string(newScript), newVersion) {
			return nil, fmt.Errorf("incoming NeoForge launcher %q is missing or does not select %s", path, newVersion)
		}
		if path == "start.bat" {
			match := neoForgeBatch.FindStringSubmatch(string(newScript))
			if match == nil || match[1] != newVersion {
				return nil, fmt.Errorf("incoming Windows startup script does not select NeoForge %s", newVersion)
			}
		}
		sum := digestMiteUpdateBytes(newScript)
		operation := miteUpdateOperation{Path: path, After: &sum}
		oldScript, err := readMiteUpdateScript(current, path)
		if err != nil && !errors.Is(err, os.ErrNotExist) {
			return nil, err
		}
		if err == nil {
			oldTemplate := strings.ReplaceAll(string(newScript), newVersion, oldVersion)
			if normalizedLines(string(oldScript)) != normalizedLines(oldTemplate) {
				return nil, fmt.Errorf("cannot safely replace locally customized NeoForge launcher %q", path)
			}
			digest := digestMiteUpdateBytes(oldScript)
			operation.Before = &digest
		}
		operations = append(operations, operation)
	}
	return operations, ctx.Err()
}

func readMiteUpdateScript(root *os.Root, name string) ([]byte, error) {
	file, err := openMiteUpdateFile(root, name)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, miteManifestLimit+1))
	if err == nil && len(content) > miteManifestLimit {
		err = fmt.Errorf("launcher script %q is too large", name)
	}
	return content, err
}

func planMiteConfigDefaults(ctx context.Context, current, incoming *os.Root) ([]miteUpdateOperation, error) {
	if _, err := incoming.Lstat("config"); errors.Is(err, os.ErrNotExist) {
		return nil, nil
	} else if err != nil {
		return nil, err
	}
	var operations []miteUpdateOperation
	err := fs.WalkDir(incoming.FS(), "config", func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if err := ctx.Err(); err != nil {
			return err
		}
		for _, component := range strings.Split(path, "/") {
			if !validServerPackComponent(component) {
				return fmt.Errorf("unsafe incoming config path: %q", path)
			}
		}
		info, err := current.Lstat(filepath.FromSlash(path))
		if err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
		if entry.IsDir() {
			if err == nil && (!info.IsDir() || info.Mode()&os.ModeSymlink != 0) {
				return fs.SkipDir
			}
			return nil
		}
		if err == nil {
			return nil
		}
		sum, err := digestMiteUpdateFile(ctx, incoming, path)
		if err != nil {
			return err
		}
		operations = append(operations, miteUpdateOperation{Path: path, After: &sum})
		return nil
	})
	return operations, err
}

func openMiteUpdateFile(root *os.Root, path string) (*os.File, error) {
	name := filepath.FromSlash(path)
	info, err := root.Lstat(name)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, fmt.Errorf("server-pack update path is not a regular file: %q", path)
	}
	if info.Size() > maxServerPackFileBytes {
		return nil, fmt.Errorf("server-pack update file is too large: %q", path)
	}
	return root.Open(name)
}

func digestMiteUpdateFile(ctx context.Context, root *os.Root, path string) (mitePackDigest, error) {
	file, err := openMiteUpdateFile(root, path)
	if err != nil {
		return mitePackDigest{}, err
	}
	defer file.Close()
	hash := sha256.New()
	count, err := io.Copy(hash, io.LimitReader(serverPackReader{ctx, file}, maxServerPackFileBytes+1))
	if err == nil && count > maxServerPackFileBytes {
		err = fmt.Errorf("server-pack update file is too large: %q", path)
	}
	return mitePackDigest{Bytes: count, SHA256: hex.EncodeToString(hash.Sum(nil))}, err
}

func digestMiteUpdateBytes(body []byte) mitePackDigest {
	digest := sha256.Sum256(body)
	return mitePackDigest{Bytes: int64(len(body)), SHA256: hex.EncodeToString(digest[:])}
}

func expectMiteUpdateFile(ctx context.Context, root *os.Root, path string, expected *mitePackDigest) error {
	if expected == nil {
		if _, err := root.Lstat(filepath.FromSlash(path)); errors.Is(err, os.ErrNotExist) {
			return nil
		} else if err != nil {
			return err
		}
		return fmt.Errorf("destination already exists: %q", path)
	}
	digest, err := digestMiteUpdateFile(ctx, root, path)
	if err != nil {
		return fmt.Errorf("%q: %w", path, err)
	}
	if digest != *expected {
		return fmt.Errorf("file does not match the recorded size or SHA-256: %q", path)
	}
	return nil
}

func stageMiteUpdateFile(ctx context.Context, root *os.Root, operation miteUpdateOperation, destination string) error {
	source, err := openMiteUpdateFile(root, operation.Path)
	if err != nil {
		return err
	}
	defer source.Close()
	file, err := os.OpenFile(destination, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0644)
	if err != nil {
		return err
	}
	hash := sha256.New()
	count, err := io.Copy(io.MultiWriter(file, hash), io.LimitReader(serverPackReader{ctx, source}, operation.After.Bytes+1))
	if err == nil {
		err = file.Sync()
	}
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	if count != operation.After.Bytes || hex.EncodeToString(hash.Sum(nil)) != operation.After.SHA256 {
		return fmt.Errorf("incoming file changed while staging: %q", operation.Path)
	}
	return nil
}

func (t mitePendingUpdate) staged(index int, suffix string) string {
	return filepath.Join(t.StagingDirectory, fmt.Sprintf("%04d.%s", index, suffix))
}

func (t mitePendingUpdate) createMarker() error {
	path := filepath.Join(t.StagingDirectory, "pending.json")
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		return err
	}
	err = json.NewEncoder(file).Encode(t)
	if err == nil {
		err = file.Sync()
	}
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	return publishServerPack(path, filepath.Join(t.Directory, serverPackUpdateMarker))
}

type miteAppliedOperation struct {
	oldMoved bool
	newMoved bool
}

type miteUpdateState struct {
	changes     []miteAppliedOperation
	directories []string
}

func (t mitePendingUpdate) apply(ctx context.Context, root *os.Root) (miteUpdateState, error) {
	state := miteUpdateState{changes: make([]miteAppliedOperation, len(t.Operations))}
	for i, operation := range t.Operations {
		if err := ctx.Err(); err != nil {
			return state, err
		}
		if err := ensureMiteUpdateParent(root, operation.Path, &state.directories); err != nil {
			return state, err
		}
		if err := expectMiteUpdateFile(ctx, root, operation.Path, operation.Before); err != nil {
			return state, err
		}
		target := filepath.Join(t.Directory, filepath.FromSlash(operation.Path))
		if operation.Before != nil {
			if err := publishServerPack(target, t.staged(i, "old")); err != nil {
				return state, fmt.Errorf("could not move old %q: %w", operation.Path, err)
			}
			state.changes[i].oldMoved = true
		}
		if operation.After != nil {
			if err := publishServerPack(t.staged(i, "new"), target); err != nil {
				return state, fmt.Errorf("could not publish new %q: %w", operation.Path, err)
			}
			state.changes[i].newMoved = true
		}
	}
	return state, nil
}

func ensureMiteUpdateParent(root *os.Root, name string, created *[]string) error {
	parts := strings.Split(name, "/")
	for i := 1; i < len(parts); i++ {
		path := filepath.Join(parts[:i]...)
		info, err := root.Lstat(path)
		if errors.Is(err, os.ErrNotExist) {
			if err := root.Mkdir(path, 0755); err != nil {
				return err
			}
			*created = append(*created, path)
		} else if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("update directory is unavailable or is a link: %q", path)
		}
	}
	return nil
}

func (t mitePendingUpdate) rollback(root *os.Root, state miteUpdateState) error {
	var rollbackErrors []error
	for i := len(state.changes) - 1; i >= 0; i-- {
		change := state.changes[i]
		if !change.oldMoved && !change.newMoved {
			continue
		}
		operation := t.Operations[i]
		if err := ensureMiteUpdateParent(root, operation.Path, &state.directories); err != nil {
			rollbackErrors = append(rollbackErrors, err)
			continue
		}
		target := filepath.Join(t.Directory, filepath.FromSlash(operation.Path))
		if change.newMoved {
			if err := expectMiteUpdateFile(context.Background(), root, operation.Path, operation.After); err != nil {
				rollbackErrors = append(rollbackErrors, err)
				continue
			}
			if err := publishServerPack(target, t.staged(i, "new")); err != nil {
				rollbackErrors = append(rollbackErrors, fmt.Errorf("could not move back %q: %w", operation.Path, err))
				continue
			}
		}
		if change.oldMoved {
			if err := publishServerPack(t.staged(i, "old"), target); err != nil {
				rollbackErrors = append(rollbackErrors, fmt.Errorf("could not restore %q: %w", operation.Path, err))
			}
		}
	}
	for _, directory := range slices.Backward(state.directories) {
		if err := root.Remove(directory); err != nil && !errors.Is(err, os.ErrNotExist) {
			rollbackErrors = append(rollbackErrors, err)
		}
	}
	return errors.Join(rollbackErrors...)
}
