// Author: NingMeng123
package main

import (
	"archive/zip"
	"compress/flate"
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

const serverPackBackupFolder = "MITE-Server-Backups"

func backupMiteServer(ctx context.Context, directory, version string) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if directory == "" || !validServerPackVersion(version) {
		return "", errors.New("invalid server directory or version for backup")
	}
	directory, err := filepath.Abs(directory)
	if err != nil {
		return "", err
	}
	info, err := os.Lstat(directory)
	if err != nil {
		return "", fmt.Errorf("cannot inspect the server directory: %w", err)
	}
	if !info.IsDir() || backupReparsePoint(info) {
		return "", errors.New("the server directory must be a regular directory, not a symbolic link or junction")
	}
	physicalPath, err := filepath.EvalSymlinks(directory)
	if err != nil {
		return "", fmt.Errorf("cannot resolve the server directory: %w", err)
	}
	parentPath := filepath.Dir(physicalPath)
	if parentPath == physicalPath || strings.EqualFold(filepath.Base(physicalPath), serverPackBackupFolder) {
		return "", errors.New("cannot place a separate backup next to this server directory")
	}
	parent, err := os.OpenRoot(parentPath)
	if err != nil {
		return "", fmt.Errorf("cannot open the server directory's parent: %w", err)
	}
	defer parent.Close()
	source, err := parent.OpenRoot(filepath.Base(physicalPath))
	if err != nil {
		return "", fmt.Errorf("cannot open the server for backup: %w", err)
	}
	defer source.Close()
	opened, err := source.Stat(".")
	if err != nil || !os.SameFile(info, opened) {
		return "", errors.New("the server directory changed while opening it for backup")
	}

	if err := parent.Mkdir(serverPackBackupFolder, 0700); err != nil && !errors.Is(err, fs.ErrExist) {
		return "", fmt.Errorf("cannot create the backup folder: %w", err)
	}
	backupInfo, err := parent.Lstat(serverPackBackupFolder)
	if err != nil {
		return "", fmt.Errorf("cannot inspect the backup folder: %w", err)
	}
	if !backupInfo.IsDir() || backupReparsePoint(backupInfo) {
		return "", errors.New("the backup folder must be a regular directory, not a symbolic link or junction")
	}
	target, err := parent.OpenRoot(serverPackBackupFolder)
	if err != nil {
		return "", fmt.Errorf("cannot open the backup folder: %w", err)
	}
	defer target.Close()
	opened, err = target.Stat(".")
	if err != nil || !os.SameFile(backupInfo, opened) {
		return "", errors.New("the backup folder changed while opening it")
	}

	suffix := rand.Text()
	name := "MITE-Server-" + version + "-" + time.Now().Format("20060102-150405") + "-" + suffix + ".zip"
	pending := ".mite-backup-" + suffix + ".part"
	file, err := target.OpenFile(pending, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return "", fmt.Errorf("cannot create the backup archive: %w", err)
	}
	defer target.Remove(pending)
	defer file.Close()
	if err := writeServerBackup(ctx, source, file); err != nil {
		return "", err
	}
	if err := file.Sync(); err != nil {
		return "", fmt.Errorf("cannot synchronize the backup archive: %w", err)
	}
	if err := file.Close(); err != nil {
		return "", fmt.Errorf("cannot close the backup archive: %w", err)
	}
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if err := target.Rename(pending, name); err != nil {
		return "", fmt.Errorf("cannot publish the completed backup archive: %w", err)
	}
	return filepath.Join(parentPath, serverPackBackupFolder, name), nil
}

func writeServerBackup(ctx context.Context, source *os.Root, output io.Writer) error {
	archive := zip.NewWriter(output)
	archive.RegisterCompressor(zip.Deflate, func(output io.Writer) (io.WriteCloser, error) {
		return flate.NewWriter(output, flate.BestSpeed)
	})
	buffer := make([]byte, 128<<10)
	walkErr := fs.WalkDir(source.FS(), ".", func(name string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return fmt.Errorf("cannot read %s for backup: %w", name, walkErr)
		}
		if err := ctx.Err(); err != nil {
			return err
		}
		if name == "." || strings.EqualFold(name, ".mite-manager.lock") {
			return nil
		}
		if err := writeServerBackupEntry(ctx, source, archive, name, entry, buffer); err != nil {
			return fmt.Errorf("cannot back up %s: %w", name, err)
		}
		return nil
	})
	closeErr := archive.Close()
	if walkErr != nil {
		return walkErr
	}
	if closeErr != nil {
		return fmt.Errorf("cannot finalize the backup archive: %w", closeErr)
	}
	return ctx.Err()
}

func writeServerBackupEntry(ctx context.Context, source *os.Root, archive *zip.Writer, name string, entry fs.DirEntry, buffer []byte) error {
	info, err := entry.Info()
	if err != nil {
		return err
	}
	if backupReparsePoint(info) || (!info.IsDir() && !info.Mode().IsRegular()) {
		return errors.New("symbolic links, junctions and other special files cannot be included in the backup")
	}
	if info.Mode().IsRegular() && strings.EqualFold(filepath.Base(name), "session.lock") {
		return nil
	}
	header, err := zip.FileInfoHeader(info)
	if err != nil {
		return err
	}
	header.Name = name
	if info.IsDir() {
		header.Name += "/"
		_, err := archive.CreateHeader(header)
		return err
	}
	header.Method = zip.Deflate
	switch strings.ToLower(filepath.Ext(name)) {
	case ".jar", ".zip", ".gz", ".xz", ".7z", ".png", ".jpg", ".jpeg", ".webp":
		header.Method = zip.Store
	}

	input, err := source.Open(filepath.FromSlash(name))
	if err != nil {
		return err
	}
	defer input.Close()
	before, err := input.Stat()
	if err != nil {
		return err
	}
	if !os.SameFile(info, before) || !before.Mode().IsRegular() || !sameBackupFileState(info, before) {
		return errors.New("file changed before it could be backed up")
	}
	output, err := archive.CreateHeader(header)
	if err != nil {
		return err
	}
	reader := &serverBackupReader{ctx: ctx, input: input}
	copied, err := io.CopyBuffer(output, io.LimitReader(reader, before.Size()+1), buffer)
	if err != nil {
		return err
	}
	after, err := input.Stat()
	if err != nil {
		return err
	}
	if copied != before.Size() || !sameBackupFileState(before, after) {
		return errors.New("file changed during backup")
	}
	return nil
}

func backupReparsePoint(info fs.FileInfo) bool {
	if info.Mode()&(os.ModeSymlink|os.ModeIrregular) != 0 {
		return true
	}
	attributes, ok := info.Sys().(*syscall.Win32FileAttributeData)
	return ok && attributes.FileAttributes&syscall.FILE_ATTRIBUTE_REPARSE_POINT != 0
}

func sameBackupFileState(a, b fs.FileInfo) bool {
	return a.Size() == b.Size() && a.ModTime().Equal(b.ModTime())
}

type serverBackupReader struct {
	ctx   context.Context
	input io.Reader
}

func (reader *serverBackupReader) Read(buffer []byte) (int, error) {
	if err := reader.ctx.Err(); err != nil {
		return 0, err
	}
	return reader.input.Read(buffer)
}
