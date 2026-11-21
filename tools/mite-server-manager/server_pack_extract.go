// Author: NingMeng123
package main

import (
	"archive/zip"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"unicode/utf8"

	"golang.org/x/sys/windows"
)

const (
	maxServerPackEntries      = 8192
	maxServerPackPaths        = maxServerPackEntries * 8
	maxServerPackArchiveBytes = 1 << 30
	maxServerPackFileBytes    = 512 << 20
	maxServerPackExtractBytes = 2 << 30
)

type serverPackEntry struct {
	path string
	file *zip.File
}

func extractMiteServerPack(ctx context.Context, archive, target string) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	target, err := filepath.Abs(target)
	if err != nil {
		return err
	}
	parent := filepath.Dir(target)
	if parent == target || !validServerPackComponent(filepath.Base(target)) {
		return errors.New("invalid server-pack destination")
	}
	if _, err := os.Lstat(target); !errors.Is(err, os.ErrNotExist) {
		if err == nil {
			return fmt.Errorf("server-pack destination already exists: %s", target)
		}
		return err
	}

	info, err := os.Stat(archive)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Size() > maxServerPackArchiveBytes {
		return errors.New("server-pack ZIP is not a regular file or exceeds the archive size limit")
	}
	zipFile, err := zip.OpenReader(archive)
	if zipFile != nil {
		defer zipFile.Close()
	}
	if err != nil {
		return fmt.Errorf("could not read server-pack ZIP: %w", err)
	}
	entries, err := inspectServerPack(ctx, zipFile.File)
	if err != nil {
		return err
	}

	staging, err := os.MkdirTemp(parent, ".mite-server-pack-")
	if err != nil {
		return err
	}
	defer func() {
		if staging != "" {
			_ = os.RemoveAll(staging)
		}
	}()
	if err := unpackServerPack(ctx, staging, entries); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := publishServerPack(staging, target); err != nil {
		return fmt.Errorf("could not publish server pack to %s: %w", target, err)
	}
	staging = ""
	return nil
}

type serverPackPath struct {
	index     int
	name      string
	directory bool
	explicit  bool
}

type serverPackPathKey struct {
	parent    int
	component string
}

func inspectServerPack(ctx context.Context, files []*zip.File) ([]serverPackEntry, error) {
	if len(files) == 0 || len(files) > maxServerPackEntries {
		return nil, errors.New("invalid number of entries in the server-pack ZIP")
	}
	entries := make([]serverPackEntry, 0, len(files))
	paths := make(map[serverPackPathKey]serverPackPath)
	var size uint64
	for _, file := range files {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		kind := file.Mode().Type()
		if kind != 0 && kind != os.ModeDir {
			return nil, fmt.Errorf("special ZIP entry is unsupported: %q", file.Name)
		}
		if file.UncompressedSize64 > maxServerPackFileBytes ||
			file.UncompressedSize64 > maxServerPackExtractBytes-size ||
			kind == os.ModeDir && file.UncompressedSize64 != 0 {
			return nil, fmt.Errorf("server-pack extraction size limit exceeded at %q", file.Name)
		}
		size += file.UncompressedSize64
		name := strings.TrimSuffix(file.Name, "/")
		parts := strings.Split(name, "/")
		parent, pathEnd := 0, 0
		for i, component := range parts {
			if !validServerPackComponent(component) {
				return nil, fmt.Errorf("unsafe ZIP path: %q", file.Name)
			}
			pathEnd += len(component)
			path := name[:pathEnd]
			key := serverPackPathKey{parent: parent, component: strings.ToUpper(component)}
			explicit := i == len(parts)-1
			directory := !explicit || kind == os.ModeDir
			current, exists := paths[key]
			if !exists {
				if len(paths) >= maxServerPackPaths {
					return nil, errors.New("server-pack ZIP contains too many paths")
				}
				current = serverPackPath{index: len(paths) + 1, name: path, directory: directory}
			} else if current.name != path || current.directory != directory || explicit && current.explicit {
				return nil, fmt.Errorf("conflicting ZIP paths: %q and %q", current.name, file.Name)
			}
			current.explicit = current.explicit || explicit
			paths[key] = current
			parent = current.index
			pathEnd++
		}
		entries = append(entries, serverPackEntry{path: name, file: file})
	}
	if isMitePackLayout(entries) {
		return entries, nil
	}
	// Some server archives wrap all content in a single folder.
	first, _, _ := strings.Cut(entries[0].path, "/")
	prefix := first + "/"
	inner := make([]serverPackEntry, 0, len(entries))
	for _, entry := range entries {
		if entry.path == first && entry.file.Mode().IsDir() {
			continue
		}
		relative, found := strings.CutPrefix(entry.path, prefix)
		if !found {
			return nil, errors.New("ZIP does not contain a MITE Modern ITE NeoForge server pack")
		}
		inner = append(inner, serverPackEntry{path: relative, file: entry.file})
	}
	if !isMitePackLayout(inner) {
		return nil, errors.New("ZIP does not contain a MITE Modern ITE NeoForge server pack")
	}
	return inner, nil
}

func validServerPackComponent(name string) bool {
	return name != "" && name != "." && name != ".." && utf8.ValidString(name) &&
		filepath.IsLocal(name) && !strings.HasSuffix(name, ".") && !strings.HasSuffix(name, " ") &&
		!strings.ContainsAny(name, `<>:"/\|?*`) && !serverPackWindowsDevice(name) &&
		strings.IndexFunc(name, func(r rune) bool { return r < 32 }) < 0
}

func serverPackWindowsDevice(name string) bool {
	stem, _, _ := strings.Cut(name, ".")
	stem = strings.ToUpper(strings.TrimRight(stem, " "))
	switch stem {
	case "AUX", "CON", "NUL", "PRN", "CONIN$", "CONOUT$":
		return true
	}
	if strings.HasPrefix(stem, "COM") || strings.HasPrefix(stem, "LPT") {
		suffix, size := utf8.DecodeRuneInString(stem[3:])
		return size == len(stem)-3 && strings.ContainsRune("0123456789¹²³", suffix)
	}
	return false
}

func isMitePackLayout(entries []serverPackEntry) bool {
	files := make(map[string]bool, len(entries))
	var core, ite, installer bool
	var installedVersions []string
	for _, entry := range entries {
		if entry.file.Mode().IsDir() {
			continue
		}
		name := strings.ToLower(entry.path)
		files[name] = true
		if neoForgeInstaller.MatchString(name) {
			installer = true
		}
		const forge = "libraries/net/neoforged/neoforge/"
		if rest, found := strings.CutPrefix(name, forge); found {
			if version, args, found := strings.Cut(rest, "/"); found && args == "win_args.txt" &&
				neoForgeInstaller.MatchString("neoforge-"+version+"-installer.jar") {
				installedVersions = append(installedVersions, version)
			}
		}
		if mod, found := strings.CutPrefix(name, "mods/"); found && !strings.ContainsRune(mod, '/') && strings.HasSuffix(mod, ".jar") {
			if strings.HasPrefix(mod, "mite-modern-ite-") {
				ite = true
			} else if strings.HasPrefix(mod, "mite-modern-") {
				core = true
			}
		}
	}
	if !files["server.properties"] || !core || !ite {
		return false
	}
	if installer && (files["run.bat"] || files["start.bat"] && files["install.bat"]) {
		return true
	}
	if files["run.bat"] || files["start.bat"] {
		for _, version := range installedVersions {
			patched := "libraries/net/neoforged/neoforge/" + version + "/neoforge-" + version + "-server.jar"
			if !strings.HasPrefix(version, "21.1.") || files[patched] {
				return true
			}
		}
	}
	return false
}

func unpackServerPack(ctx context.Context, directory string, entries []serverPackEntry) error {
	root, err := os.OpenRoot(directory)
	if err != nil {
		return err
	}
	defer root.Close()
	for _, entry := range entries {
		if err := ctx.Err(); err != nil {
			return err
		}
		name := filepath.FromSlash(entry.path)
		if entry.file.Mode().IsDir() {
			if err := root.MkdirAll(name, 0755); err != nil {
				return err
			}
			continue
		}
		if err := root.MkdirAll(filepath.Dir(name), 0755); err != nil {
			return err
		}
		if err := unpackServerPackFile(ctx, root, name, entry.file); err != nil {
			return fmt.Errorf("could not extract %q: %w", entry.path, err)
		}
	}
	return nil
}

func unpackServerPackFile(ctx context.Context, root *os.Root, name string, entry *zip.File) error {
	source, err := entry.Open()
	if err != nil {
		return err
	}
	defer source.Close()
	target, err := root.OpenFile(name, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0644)
	if err != nil {
		return err
	}
	expected := int64(entry.UncompressedSize64)
	written, err := io.Copy(target, io.LimitReader(serverPackReader{ctx, source}, expected+1))
	if closeErr := target.Close(); err == nil {
		err = closeErr
	}
	if err == nil && written != expected {
		err = errors.New("ZIP entry size did not match its metadata")
	}
	return err
}

type serverPackReader struct {
	ctx    context.Context
	source io.Reader
}

func (r serverPackReader) Read(p []byte) (int, error) {
	if err := r.ctx.Err(); err != nil {
		return 0, err
	}
	return r.source.Read(p)
}

func publishServerPack(staging, target string) error {
	movePath := func(path string) (*uint16, error) {
		if !strings.HasPrefix(path, `\\?\`) {
			if unc, found := strings.CutPrefix(path, `\\`); found {
				path = `\\?\UNC\` + unc
			} else {
				path = `\\?\` + path
			}
		}
		return windows.UTF16PtrFromString(path)
	}
	from, err := movePath(staging)
	if err != nil {
		return err
	}
	to, err := movePath(target)
	if err != nil {
		return err
	}
	// MoveFileEx without MOVEFILE_REPLACE_EXISTING keeps the destination intact if it appeared during extraction.
	return windows.MoveFileEx(from, to, 0)
}
