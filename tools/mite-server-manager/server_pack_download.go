// Author: NingMeng123
package main

import (
	"archive/zip"
	"context"
	"crypto/sha1" // CurseForge exposes SHA-1 for files that publish a checksum.
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"sort"
	"strings"
	"sync"

	"golang.org/x/net/html"
)

const (
	serverPackPageURL    = "https://www.curseforge.com/minecraft/modpacks/mite-modern-ite/files/all"
	curseForgeSiteHost   = "www.curseforge.com"
	curseForgeFileHost   = "mediafilez.forgecdn.net"
	miteCurseForgeID     = 1807086
	curseForgeFilesPath  = "https://www.curseforge.com/api/v1/mods/1807086/files"
	verifiedServerPackID = 9728437
	verifiedPackLength   = 76549283
	verifiedPackSHA256   = "8012eb0560f783e47299785a9dbf8b1ea39ed3e63f0144b50c43ad74637c1540"
)

var (
	cursePackVersion = "[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?"
	curseServerName  = regexp.MustCompile("(?i)^MITE-Modern-ITE-(" + cursePackVersion + ")-server\\.zip$")
	curseClientName  = regexp.MustCompile("(?i)^MITE-Modern-ITE-(" + cursePackVersion + ")-CurseForge(?:-[A-Za-z0-9.-]+)?\\.zip$")
)

type ServerPackRelease struct {
	ID         int    `json:"id"`
	ClientID   int    `json:"clientId"`
	Version    string `json:"version"`
	FileName   string `json:"fileName"`
	FileLength int64  `json:"fileLength"`
	SHA1       string `json:"-"`
	Published  string `json:"published"`
}

type curseForgeSiteFile struct {
	ID                     int      `json:"id"`
	ProjectID              int      `json:"projectId"`
	ParentProjectFileID    int      `json:"parentProjectFileId"`
	FileName               string   `json:"fileName"`
	FileLength             int64    `json:"fileLength"`
	DateCreated            string   `json:"dateCreated"`
	GameVersions           []string `json:"gameVersions"`
	Status                 int      `json:"status"`
	ReleaseType            int      `json:"releaseType"`
	AdditionalFilesCount   *int     `json:"additionalFilesCount"`
	IsCompatibleWithClient bool     `json:"isCompatibleWithClient"`
	IsEarlyAccessContent   bool     `json:"isEarlyAccessContent"`
	Hashes                 []struct {
		Algo  int    `json:"algo"`
		Value string `json:"value"`
	} `json:"hashes"`
}

type curseForgeSiteFiles struct {
	Data       []curseForgeSiteFile `json:"data"`
	Pagination struct {
		TotalCount int `json:"totalCount"`
	} `json:"pagination"`
}

func listMiteServerPacks(ctx context.Context, client *http.Client) ([]ServerPackRelease, error) {
	const pageSize, maxPages, workers = 20, 3, 4
	client = curseForgeClient(client, curseForgeSiteHost)
	var parents []curseForgeSiteFile
	var firstErr error
	for page := range maxPages {
		index := page * pageSize
		endpoint := fmt.Sprintf("%s?index=%d&pageSize=%d", curseForgeFilesPath, index, pageSize)
		var files curseForgeSiteFiles
		if err := requestCurseForgeJSON(ctx, client, endpoint, &files); err != nil {
			if page == 0 {
				return nil, err
			}
			firstErr = err
			break
		}
		for _, file := range files.Data {
			if file.published() && file.ParentProjectFileID == 0 && curseClientName.MatchString(file.FileName) &&
				(file.AdditionalFilesCount == nil || *file.AdditionalFilesCount > 0) {
				parents = append(parents, file)
			}
		}
		if len(files.Data) < pageSize || (files.Pagination.TotalCount > 0 && index+pageSize >= files.Pagination.TotalCount) {
			break
		}
	}

	type result struct {
		releases []ServerPackRelease
		err      error
	}
	jobs := make(chan curseForgeSiteFile, len(parents))
	for _, parent := range parents {
		jobs <- parent
	}
	close(jobs)
	results := make(chan result, len(parents))
	var group sync.WaitGroup
	for range min(workers, len(parents)) {
		group.Go(func() {
			for parent := range jobs {
				if ctx.Err() != nil {
					return
				}
				releases, err := curseForgeAdditionalPacks(ctx, client, parent)
				results <- result{releases: releases, err: err}
			}
		})
	}
	group.Wait()
	close(results)

	releases := make([]ServerPackRelease, 0, len(parents))
	seen := make(map[int]bool)
	for result := range results {
		if result.err != nil && firstErr == nil {
			firstErr = result.err
		}
		for _, release := range result.releases {
			if !seen[release.ID] {
				seen[release.ID] = true
				releases = append(releases, release)
			}
		}
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if len(releases) == 0 && firstErr != nil {
		return nil, firstErr
	}
	sort.Slice(releases, func(i, j int) bool { return releases[i].ClientID > releases[j].ClientID })
	return releases, nil
}

func (file curseForgeSiteFile) published() bool {
	return file.ID > 0 && file.ProjectID == miteCurseForgeID && file.Status == 4 &&
		file.ReleaseType == 1 && !file.IsEarlyAccessContent && slices.Contains(file.GameVersions, "1.21.1") &&
		slices.Contains(file.GameVersions, "NeoForge")
}

func curseForgeAdditionalPacks(ctx context.Context, client *http.Client, parent curseForgeSiteFile) ([]ServerPackRelease, error) {
	var files curseForgeSiteFiles
	if err := requestCurseForgeJSON(ctx, client, fmt.Sprintf("%s/%d/additional-files", curseForgeFilesPath, parent.ID), &files); err != nil {
		return nil, err
	}
	clientVersion := curseClientName.FindStringSubmatch(parent.FileName)[1]
	releases := make([]ServerPackRelease, 0, len(files.Data))
	for _, file := range files.Data {
		if !file.published() || file.ParentProjectFileID != parent.ID || file.IsCompatibleWithClient {
			continue
		}
		match := curseServerName.FindStringSubmatch(file.FileName)
		if match == nil || !strings.EqualFold(match[1], clientVersion) {
			continue
		}
		release := ServerPackRelease{
			ID: file.ID, ClientID: parent.ID, Version: match[1], FileName: file.FileName,
			FileLength: file.FileLength, Published: file.DateCreated,
		}
		for _, hash := range file.Hashes {
			if hash.Algo == 1 {
				release.SHA1 = strings.ToLower(hash.Value)
				break
			}
		}
		if validServerPackRelease(release) {
			releases = append(releases, release)
		}
	}
	return releases, nil
}

func getMiteClientChangelog(ctx context.Context, client *http.Client, clientID int) (string, error) {
	if clientID <= 0 {
		return "", errors.New("invalid CurseForge file ID")
	}
	var response struct {
		Body string `json:"changelogBody"`
	}
	client = curseForgeClient(client, curseForgeSiteHost)
	endpoint := fmt.Sprintf("%s/%d/change-log", curseForgeFilesPath, clientID)
	if err := requestCurseForgeJSON(ctx, client, endpoint, &response); err != nil {
		return "", err
	}
	return curseForgeChangelogText(response.Body), nil
}

func curseForgeChangelogText(source string) string {
	tokenizer := html.NewTokenizer(strings.NewReader(source))
	var text strings.Builder
	hidden := 0
	for {
		kind := tokenizer.Next()
		if kind == html.ErrorToken {
			break
		}
		token := tokenizer.Token()
		switch kind {
		case html.TextToken:
			if hidden == 0 {
				text.WriteString(token.Data)
			}
		case html.StartTagToken, html.EndTagToken, html.SelfClosingTagToken:
			switch token.Data {
			case "script", "style", "noscript", "template":
				if kind == html.StartTagToken {
					hidden++
				} else if kind == html.EndTagToken && hidden > 0 {
					hidden--
				}
			default:
				if hidden != 0 {
					continue
				}
				switch token.Data {
				case "br", "div", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "ul", "ol", "tr":
					text.WriteByte('\n')
				case "p", "blockquote", "pre":
					if kind == html.EndTagToken {
						text.WriteByte('\n')
					}
				case "li":
					if kind == html.StartTagToken {
						text.WriteString("\n• ")
					}
				}
			}
		}
	}
	lines := strings.Split(text.String(), "\n")
	plain := lines[:0]
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line != "" {
			plain = append(plain, line)
		}
	}
	return strings.Join(plain, "\n")
}

func validServerPackRelease(release ServerPackRelease) bool {
	match := curseServerName.FindStringSubmatch(release.FileName)
	if release.ID <= 0 || release.ClientID <= 0 || release.ID == release.ClientID ||
		release.FileLength <= 0 || release.FileLength > maxServerPackArchiveBytes ||
		match == nil || !strings.EqualFold(match[1], release.Version) ||
		(release.ID == verifiedServerPackID && release.FileLength != verifiedPackLength) {
		return false
	}
	if release.SHA1 == "" {
		return true
	}
	digest, err := hex.DecodeString(release.SHA1)
	return err == nil && len(digest) == sha1.Size
}

func downloadMiteServerPack(ctx context.Context, client *http.Client, release ServerPackRelease, destination string, report func(phase string, received, total int64)) error {
	if !validServerPackRelease(release) {
		return errors.New("invalid CurseForge server pack metadata")
	}
	client = curseForgeClient(client, curseForgeFileHost)
	endpoint := fmt.Sprintf("https://%s/files/%d/%d/%s", curseForgeFileHost, release.ID/1000, release.ID%1000, release.FileName)
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/zip")
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("CurseForge CDN returned HTTP %d", response.StatusCode)
	}
	if response.ContentLength >= 0 && response.ContentLength != release.FileLength {
		return fmt.Errorf("CurseForge CDN reported %d bytes, expected %d", response.ContentLength, release.FileLength)
	}

	file, err := os.CreateTemp(filepath.Dir(destination), ".mite-server-pack-*.part")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	defer file.Close()
	hash1, hash256 := sha1.New(), sha256.New()
	progress := &packProgressWriter{writer: io.MultiWriter(file, hash1, hash256), total: release.FileLength, report: report}
	progress.notify("downloading")
	length, err := io.CopyBuffer(progress, io.LimitReader(response.Body, release.FileLength+1), make([]byte, 128<<10))
	if err != nil {
		return err
	}
	if length != release.FileLength {
		return fmt.Errorf("downloaded %d bytes, expected %d", length, release.FileLength)
	}
	progress.notify("verifying")
	if release.SHA1 != "" && !strings.EqualFold(hex.EncodeToString(hash1.Sum(nil)), release.SHA1) {
		return errors.New("server pack SHA-1 verification failed")
	}
	if release.ID == verifiedServerPackID && hex.EncodeToString(hash256.Sum(nil)) != verifiedPackSHA256 {
		return errors.New("server pack SHA-256 verification failed")
	}
	archive, err := zip.NewReader(file, length)
	if err != nil || len(archive.File) == 0 {
		return errors.New("CurseForge download is not a valid ZIP archive")
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := file.Close(); err != nil {
		return err
	}
	return os.Rename(file.Name(), destination)
}

type packProgressWriter struct {
	writer          io.Writer
	received, total int64
	report          func(string, int64, int64)
}

func (writer *packProgressWriter) Write(data []byte) (int, error) {
	count, err := writer.writer.Write(data)
	writer.received += int64(count)
	writer.notify("downloading")
	return count, err
}

func (writer *packProgressWriter) notify(phase string) {
	if writer.report != nil {
		writer.report(phase, min(writer.received, writer.total), writer.total)
	}
}

func curseForgeClient(client *http.Client, host string) *http.Client {
	if client == nil {
		client = http.DefaultClient
	}
	restricted := *client
	restricted.CheckRedirect = func(request *http.Request, previous []*http.Request) error {
		if request.URL.Scheme != "https" || !strings.EqualFold(request.URL.Host, host) ||
			request.URL.User != nil || len(previous) >= 4 {
			return errors.New("CurseForge redirected to an unexpected destination")
		}
		if client.CheckRedirect != nil {
			return client.CheckRedirect(request, previous)
		}
		return nil
	}
	return &restricted
}

func requestCurseForgeJSON(ctx context.Context, client *http.Client, endpoint string, output any) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("CurseForge returned HTTP %d", response.StatusCode)
	}
	const maxResponseSize = 2 << 20
	body, err := io.ReadAll(io.LimitReader(response.Body, maxResponseSize+1))
	if err != nil {
		return err
	}
	if len(body) > maxResponseSize {
		return errors.New("CurseForge returned too much metadata")
	}
	if err := json.Unmarshal(body, output); err != nil {
		return fmt.Errorf("invalid CurseForge metadata: %w", err)
	}
	return nil
}
