// Author: NingMeng123
package main

import (
	"archive/zip"
	"bufio"
	"context"
	"crypto/rand"
	"crypto/sha1" // The expected digests are provided by the NeoForge installer.
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const installerGeoURL = "https://www.cloudflare.com/cdn-cgi/trace"

type installerLibrary struct {
	Path string `json:"path"`
	URL  string `json:"url"`
	SHA1 string `json:"sha1"`
	Size int64  `json:"size"`
}

type installerMetadata struct {
	JSON      string `json:"json"`
	Libraries []struct {
		Name      string `json:"name"`
		Downloads struct {
			Artifact installerLibrary `json:"artifact"`
		} `json:"downloads"`
	} `json:"libraries"`
}

type installerMirror struct {
	name     string
	bases    func(installerLibrary) []string
	mu       sync.Mutex
	failures int
	cooldown time.Time
}

func installerMavenMirrors() []*installerMirror {
	return []*installerMirror{
		{name: "BMCLAPI", bases: func(installerLibrary) []string {
			return []string{"https://bmclapi2.bangbang93.com/maven/"}
		}},
		{name: "MCMirror", bases: func(installerLibrary) []string {
			return []string{
				"https://mirror.minguncle.cn/maven.neoforged.net/releases/",
				"https://mirror.minguncle.cn/maven/",
			}
		}},
		{name: "SJTUG BMCLAPI", bases: func(installerLibrary) []string {
			return []string{"https://mirror.sjtu.edu.cn/bmclapi/"}
		}},
	}
}

func prepareInstallerLibraries(ctx context.Context, directory, installer string, logf func(string), english bool) error {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.DialContext = (&net.Dialer{Timeout: 4 * time.Second, KeepAlive: 30 * time.Second}).DialContext
	transport.TLSHandshakeTimeout = 4 * time.Second
	transport.ResponseHeaderTimeout = 6 * time.Second
	defer transport.CloseIdleConnections()
	client := &http.Client{Transport: transport}

	geo, cancelGeo := context.WithTimeout(ctx, 3*time.Second)
	country := installerCountry(geo, client, installerGeoURL)
	cancelGeo()
	if err := ctx.Err(); err != nil {
		return err
	}
	if country == "" {
		mirrorLog(logf, english, "无法识别公网 IP 所在地区，将使用官方源。", "Could not determine the public IP region. Using the official sources.")
		return nil
	}
	if country != "CN" {
		mirrorLog(logf, english, "NeoForge Maven 依赖将由官方源下载。", "NeoForge will use the official Maven sources.")
		return nil
	}
	if installer == "." || installer != filepath.Base(installer) || !filepath.IsLocal(installer) {
		return errors.New("invalid installer filename")
	}
	root, err := os.OpenRoot(directory)
	if err != nil {
		return err
	}
	defer root.Close()

	libraries, err := readInstallerLibraries(root, installer)
	if err != nil {
		return err
	}
	if len(libraries) == 0 {
		return nil
	}
	mirrorLog(logf, english,
		"中国大陆网络，依次使用 BMCLAPI、MCMirror、SJTUG BMCLAPI 预下载 Maven 依赖，缺失项由官方源补齐。",
		"Mainland China network. Trying BMCLAPI, MCMirror and SJTUG BMCLAPI in order, with the official sources as fallback.")
	prefetch, cancelPrefetch := context.WithTimeout(ctx, 2*time.Minute)
	defer cancelPrefetch()
	prefetchInstallerMaven(prefetch, client, root, libraries, installerMavenMirrors(), logf, english)
	return ctx.Err()
}

func installerCountry(ctx context.Context, client *http.Client, endpoint string) string {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return ""
	}
	resp, err := client.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return ""
	}
	scanner := bufio.NewScanner(io.LimitReader(resp.Body, 4096))
	for scanner.Scan() {
		if value, ok := strings.CutPrefix(scanner.Text(), "loc="); ok {
			if len(value) == 2 && value[0] >= 'A' && value[0] <= 'Z' && value[1] >= 'A' && value[1] <= 'Z' {
				return value
			}
			return ""
		}
	}
	return ""
}

func readInstallerLibraries(root *os.Root, installer string) ([]installerLibrary, error) {
	file, err := root.Open(installer)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return nil, err
	}
	archive, err := zip.NewReader(file, info.Size())
	if err != nil {
		return nil, err
	}
	profile, err := readInstallerMetadata(archive, "install_profile.json")
	if err != nil {
		return nil, err
	}
	versionName := strings.TrimPrefix(profile.JSON, "/")
	if versionName == "" {
		versionName = "version.json"
	}
	if !fs.ValidPath(versionName) || strings.Contains(versionName, "\\") {
		return nil, errors.New("invalid version metadata path in installer")
	}
	version, err := readInstallerMetadata(archive, versionName)
	if err != nil {
		return nil, err
	}

	seen := make(map[string]installerLibrary)
	libraries := make([]installerLibrary, 0, len(profile.Libraries))
	for _, metadata := range []installerMetadata{version, profile} {
		for _, entry := range metadata.Libraries {
			library := entry.Downloads.Artifact
			if !validInstallerLibrary(entry.Name, library) {
				continue
			}
			key := strings.ToLower(library.Path)
			if previous, exists := seen[key]; exists {
				if !strings.EqualFold(previous.SHA1, library.SHA1) || previous.Size != library.Size {
					return nil, fmt.Errorf("conflicting checksums for %s", library.Path)
				}
				continue
			}
			seen[key] = library
			libraries = append(libraries, library)
		}
	}
	return libraries, nil
}

func readInstallerMetadata(archive *zip.Reader, name string) (installerMetadata, error) {
	const maxMetadataSize = 2 << 20
	var metadata installerMetadata
	file, err := archive.Open(name)
	if err != nil {
		return metadata, err
	}
	defer file.Close()
	text, err := io.ReadAll(io.LimitReader(file, maxMetadataSize+1))
	if err != nil {
		return metadata, err
	}
	if len(text) > maxMetadataSize {
		return metadata, errors.New("installer metadata is too large")
	}
	if err := json.Unmarshal(text, &metadata); err != nil {
		return metadata, fmt.Errorf("%s: %w", name, err)
	}
	return metadata, nil
}

func validInstallerLibrary(coordinate string, library installerLibrary) bool {
	if library.Size <= 0 || library.Size > 512<<20 || len(library.SHA1) != 2*sha1.Size {
		return false
	}
	if _, err := hex.DecodeString(library.SHA1); err != nil ||
		!fs.ValidPath(library.Path) || !filepath.IsLocal(filepath.FromSlash(library.Path)) ||
		strings.ContainsAny(library.Path, "\\:%?#\x00") {
		return false
	}
	original, err := url.Parse(library.URL)
	return err == nil && original.Scheme == "https" && original.User == nil && original.Hostname() != "" &&
		library.Path == installerCoordinatePath(coordinate)
}

func installerCoordinatePath(coordinate string) string {
	name, extension, explicitExtension := strings.Cut(coordinate, "@")
	if !explicitExtension {
		extension = "jar"
	}
	segments := strings.Split(name, ":")
	if (len(segments) != 3 && len(segments) != 4) || extension == "" {
		return ""
	}
	group, artifact, version := segments[0], segments[1], segments[2]
	if group == "" || artifact == "" || version == "" {
		return ""
	}
	filename := artifact + "-" + version
	if len(segments) == 4 {
		filename += "-" + segments[3]
	}
	return strings.ReplaceAll(group, ".", "/") + "/" + artifact + "/" + version + "/" + filename + "." + extension
}

type installerFetchResult struct {
	library installerLibrary
	source  string
	cached  bool
}

func prefetchInstallerMaven(ctx context.Context, client *http.Client, root *os.Root, libraries []installerLibrary, mirrors []*installerMirror, logf func(string), english bool) {
	const workers = 6
	jobs := make(chan installerLibrary, len(libraries))
	results := make(chan installerFetchResult, workers)
	for _, library := range libraries {
		jobs <- library
	}
	close(jobs)

	var group sync.WaitGroup
	for range min(workers, len(libraries)) {
		group.Go(func() {
			for library := range jobs {
				if ctx.Err() != nil {
					return
				}
				results <- fetchInstallerLibrary(ctx, client, root, library, mirrors)
			}
		})
	}
	go func() {
		group.Wait()
		close(results)
	}()

	cached, downloaded := 0, 0
	for result := range results {
		if result.cached {
			cached++
		} else if result.source != "" {
			downloaded++
			if logf != nil {
				logf(fmt.Sprintf("[%s] %s", result.source, result.library.Path))
			}
		}
	}
	if !errors.Is(ctx.Err(), context.Canceled) {
		mirrorLog(logf, english, "Maven 依赖：%d 项已缓存，%d 项通过镜像下载，%d 项交由官方源。",
			"Maven libraries: %d cached, %d downloaded from mirrors, %d left to the official sources.",
			cached, downloaded, len(libraries)-cached-downloaded)
	}
}

func fetchInstallerLibrary(ctx context.Context, client *http.Client, root *os.Root, library installerLibrary, mirrors []*installerMirror) installerFetchResult {
	result := installerFetchResult{library: library}
	destination := filepath.Join("libraries", filepath.FromSlash(library.Path))
	if installerLibraryCached(root, destination, library) {
		result.cached = true
		return result
	}
	for _, mirror := range mirrors {
		if ctx.Err() != nil || !mirror.available() {
			continue
		}
		for _, base := range mirror.bases(library) {
			remote, err := url.JoinPath(base, strings.Split(library.Path, "/")...)
			if err != nil {
				continue
			}
			requestContext, cancel := context.WithTimeout(ctx, 14*time.Second)
			err = downloadInstallerLibrary(requestContext, client, root, destination, remote, library)
			cancel()
			var connectionError *url.Error
			transportFailure := errors.As(err, &connectionError) || errors.Is(err, context.DeadlineExceeded)
			mirror.record(transportFailure)
			if err == nil {
				result.source = mirror.name
				return result
			}
			if ctx.Err() != nil || transportFailure {
				break
			}
		}
	}
	return result
}

func (m *installerMirror) available() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return !time.Now().Before(m.cooldown)
}

func (m *installerMirror) record(transportFailure bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if !transportFailure {
		m.failures = 0
		return
	}
	m.failures++
	if m.failures >= 3 {
		m.cooldown = time.Now().Add(25 * time.Second)
		m.failures = 0
	}
}

func installerLibraryCached(root *os.Root, destination string, library installerLibrary) bool {
	file, err := root.Open(destination)
	if err != nil {
		return false
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil || !info.Mode().IsRegular() || info.Size() != library.Size {
		return false
	}
	digest := sha1.New()
	count, err := io.Copy(digest, io.LimitReader(file, library.Size+1))
	return err == nil && count == library.Size && strings.EqualFold(library.SHA1, hex.EncodeToString(digest.Sum(nil)))
}

func downloadInstallerLibrary(ctx context.Context, client *http.Client, root *os.Root, destination, remote string, library installerLibrary) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, remote, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "MITE-Server-Manager (NingMeng123)")
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	if resp.ContentLength >= 0 && resp.ContentLength != library.Size {
		return errors.New("unexpected content length")
	}

	dir := filepath.Dir(destination)
	if err := root.MkdirAll(dir, 0755); err != nil {
		return err
	}
	pending := filepath.Join(dir, ".mite-library-"+rand.Text()+".tmp")
	file, err := root.OpenFile(pending, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0644)
	if err != nil {
		return err
	}
	defer root.Remove(pending)
	digest := sha1.New()
	count, err := io.Copy(io.MultiWriter(file, digest), io.LimitReader(resp.Body, library.Size+1))
	if err == nil && (count != library.Size || !strings.EqualFold(library.SHA1, hex.EncodeToString(digest.Sum(nil)))) {
		err = errors.New("unexpected file size or SHA-1")
	}
	if err == nil {
		err = file.Sync()
	}
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	return root.Rename(pending, destination)
}

func mirrorLog(logf func(string), english bool, chinese, translated string, args ...any) {
	if logf == nil {
		return
	}
	if english {
		chinese = translated
	}
	logf(fmt.Sprintf(chinese, args...))
}
