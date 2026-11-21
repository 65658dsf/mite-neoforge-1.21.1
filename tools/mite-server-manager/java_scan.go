// Author: NingMeng123
package main

import (
	"bytes"
	"context"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/windows/registry"
)

const (
	maxJavaCandidates = 48
	maxJavaFolders    = 384
	javaProbeTimeout  = 3 * time.Second
	javaScanTimeout   = 10 * time.Second
)

var errJavaVersionUnknown = errors.New("could not determine Java version")

type JavaInstallation struct {
	Path         string `json:"path"`
	Version      string `json:"version"`
	Architecture string `json:"architecture"`
	Compatible   bool   `json:"compatible"`
	Major        int    `json:"-"`
	Bits         int    `json:"-"`
}

func scanJava(directory string, additional ...string) []JavaInstallation {
	candidates := newJavaCandidates()
	for _, path := range additional {
		candidates.add(path)
	}
	for _, path := range javaCandidatePaths(directory) {
		candidates.add(path)
	}
	ctx, cancel := context.WithTimeout(context.Background(), javaScanTimeout)
	defer cancel()
	results := make([]JavaInstallation, len(candidates.paths))
	jobs := make(chan int, len(candidates.paths))
	for index := range candidates.paths {
		jobs <- index
	}
	close(jobs)

	var workers sync.WaitGroup
	for range min(4, len(candidates.paths)) {
		workers.Go(func() {
			for index := range jobs {
				if ctx.Err() != nil {
					return
				}
				java, err := probeJava(ctx, candidates.paths[index], directory)
				if err == nil {
					results[index] = java
				}
			}
		})
	}
	workers.Wait()

	installations := make([]JavaInstallation, 0, len(results))
	seen := make(map[string]bool)
	for _, result := range results {
		key := strings.ToLower(result.Path)
		if key != "" && !seen[key] {
			seen[key] = true
			installations = append(installations, result)
		}
	}
	slices.SortStableFunc(installations, func(a, b JavaInstallation) int {
		if a.Compatible == b.Compatible {
			return 0
		}
		if a.Compatible {
			return -1
		}
		return 1
	})
	return installations
}

func javaCandidatePaths(directory string) []string {
	candidates := newJavaCandidates()
	homes := []string{
		os.Getenv("JAVA21_HOME"), os.Getenv("JAVA_HOME_21"), os.Getenv("JAVA_HOME"),
		os.Getenv("JDK_HOME"), os.Getenv("JRE_HOME"),
	}
	for _, home := range homes {
		candidates.add(home)
	}
	if directory != "" {
		candidates.scan(filepath.Join(directory, "runtimes"), 3)
	}
	for _, entry := range filepath.SplitList(os.Getenv("PATH")) {
		candidates.add(entry)
	}

	// A common JAVA_HOME is a link to the active JDK in a directory of installed JDKs.
	for _, home := range homes {
		executable := resolvedJavaExecutable(home)
		if executable == "" || !strings.EqualFold(filepath.Base(filepath.Dir(executable)), "bin") {
			continue
		}
		installation := filepath.Dir(filepath.Dir(executable))
		parent := filepath.Dir(installation)
		if filepath.Dir(parent) != parent {
			candidates.scan(parent, 1)
		}
	}
	for _, home := range javaRegistryHomes() {
		candidates.add(home)
	}

	vendors := []string{
		"Java", "Eclipse Adoptium", "Adoptium", "Amazon Corretto", "Azul Systems", "Zulu",
		"Microsoft", "BellSoft", "Semeru", "IBM", "Red Hat", "OpenJDK", "GraalVM", "SAPMachine",
	}
	local := os.Getenv("LOCALAPPDATA")
	programDirectories := []string{
		os.Getenv("ProgramW6432"), os.Getenv("ProgramFiles"), os.Getenv("ProgramFiles(x86)"),
	}
	if local != "" {
		programDirectories = append(programDirectories, filepath.Join(local, "Programs"))
		candidates.scan(filepath.Join(local, "Java"), 2)
		candidates.scan(filepath.Join(local, "jdk"), 1)
		candidates.scan(filepath.Join(local, "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local", "runtime"), 3)
	}
	if home, err := os.UserHomeDir(); err == nil && home != "" {
		candidates.scan(filepath.Join(home, ".jdks"), 2)
		candidates.scan(filepath.Join(home, ".jenv", "versions"), 1)
	}
	if roaming := os.Getenv("APPDATA"); roaming != "" {
		candidates.scan(filepath.Join(roaming, ".minecraft", "runtime"), 3)
	}
	for _, root := range programDirectories {
		if root == "" {
			continue
		}
		for _, vendor := range vendors {
			depth := 1
			if vendor == "Azul Systems" || vendor == "IBM" {
				depth = 2
			}
			candidates.scan(filepath.Join(root, vendor), depth)
		}
		candidates.scan(filepath.Join(root, "Minecraft Launcher", "runtime"), 3)
	}
	return candidates.paths
}

type javaCandidates struct {
	paths   []string
	seen    map[string]bool
	folders map[string]int
}

func newJavaCandidates() *javaCandidates {
	return &javaCandidates{
		seen: make(map[string]bool), folders: make(map[string]int),
	}
}

func (c *javaCandidates) add(path string) bool {
	if len(c.paths) >= maxJavaCandidates {
		return false
	}
	executable := resolvedJavaExecutable(path)
	if executable == "" {
		return false
	}
	key := strings.ToLower(executable)
	if !c.seen[key] {
		c.seen[key] = true
		c.paths = append(c.paths, executable)
	}
	return true
}

func (c *javaCandidates) scan(path string, depth int) {
	if len(c.paths) >= maxJavaCandidates || len(c.folders) >= maxJavaFolders {
		return
	}
	key := strings.ToLower(filepath.Clean(path))
	if previous, seen := c.folders[key]; seen && previous >= depth {
		return
	}
	c.folders[key] = depth
	if c.add(path) || depth == 0 {
		return
	}
	folder, err := os.Open(path)
	if err != nil {
		return
	}
	entries, _ := folder.ReadDir(64)
	_ = folder.Close()
	slices.SortFunc(entries, func(a, b os.DirEntry) int { return strings.Compare(a.Name(), b.Name()) })
	for _, entry := range entries {
		if entry.IsDir() || entry.Type()&os.ModeSymlink != 0 {
			c.scan(filepath.Join(path, entry.Name()), depth-1)
		}
	}
}

func resolvedJavaExecutable(path string) string {
	executable := javaExecutable(path)
	if executable == "" && strings.Contains(path, "%") {
		if expanded, err := registry.ExpandString(path); err == nil {
			executable = javaExecutable(expanded)
		}
	}
	if executable != "" {
		if resolved, err := filepath.EvalSymlinks(executable); err == nil && strings.EqualFold(filepath.Base(resolved), "java.exe") {
			return resolved
		}
	}
	return executable
}

func javaRegistryHomes() []string {
	subkeys := []string{
		`SOFTWARE\JavaSoft\JDK`, `SOFTWARE\JavaSoft\JRE`,
		`SOFTWARE\JavaSoft\Java Development Kit`, `SOFTWARE\JavaSoft\Java Runtime Environment`,
		`SOFTWARE\Eclipse Adoptium`, `SOFTWARE\AdoptOpenJDK`, `SOFTWARE\Azul Systems`,
		`SOFTWARE\Amazon Corretto`, `SOFTWARE\BellSoft`, `SOFTWARE\Microsoft\JDK`,
	}
	var homes []string
	budget := 180
	for _, hive := range []registry.Key{registry.LOCAL_MACHINE, registry.CURRENT_USER} {
		for _, view := range []uint32{registry.WOW64_64KEY, registry.WOW64_32KEY} {
			access := view | registry.QUERY_VALUE | registry.ENUMERATE_SUB_KEYS
			for _, subkey := range subkeys {
				readJavaRegistry(hive, subkey, access, 4, &budget, &homes)
			}
		}
	}
	return homes
}

func readJavaRegistry(parent registry.Key, name string, access uint32, depth int, budget *int, homes *[]string) {
	if *budget <= 0 {
		return
	}
	key, err := registry.OpenKey(parent, name, access)
	if err != nil {
		return
	}
	defer key.Close()
	*budget--
	for _, value := range []string{"JavaHome", "Path", "InstallationPath", "InstallPath"} {
		if home, _, err := key.GetStringValue(value); err == nil && home != "" {
			*homes = append(*homes, home)
		}
	}
	if depth == 0 {
		return
	}
	children, _ := key.ReadSubKeyNames(32)
	for _, child := range children {
		readJavaRegistry(key, child, access, depth-1, budget, homes)
	}
}

func inspectJava(java, directory string) (JavaInstallation, error) {
	info, err := probeJava(context.Background(), java, directory)
	info.Path = java
	return info, err
}

func probeJava(parent context.Context, java, directory string) (JavaInstallation, error) {
	ctx, cancel := context.WithTimeout(parent, javaProbeTimeout)
	defer cancel()
	args := append(append([]string(nil), javaOutputArgs...), "-XshowSettings:properties", "-version")
	cmd := exec.CommandContext(ctx, java, args...)
	cmd.WaitDelay = 300 * time.Millisecond
	if info, err := os.Stat(directory); err != nil || !info.IsDir() {
		directory = ""
	}
	setJavaProcess(cmd, directory)
	output := new(javaProbeOutput)
	cmd.Stdout, cmd.Stderr = output, output
	err := cmd.Run()
	if ctx.Err() != nil {
		return JavaInstallation{Path: java}, ctx.Err()
	}

	info := parseJavaProperties(java, output.buffer.String())
	if err == nil && info.Major == 0 {
		err = errJavaVersionUnknown
	}
	if err == nil {
		if home := javaProperty(output.buffer.String(), "java.home"); home != "" {
			if executable := resolvedJavaExecutable(home); executable != "" {
				info.Path = executable
			}
		}
	}
	info.Compatible = err == nil && info.Major == 21 && info.Bits == 64
	return info, err
}

type javaProbeOutput struct {
	buffer bytes.Buffer
}

func (o *javaProbeOutput) Write(data []byte) (int, error) {
	const outputLimit = 64 << 10
	remaining := outputLimit - o.buffer.Len()
	if remaining > 0 {
		_, _ = o.buffer.Write(data[:min(len(data), remaining)])
	}
	return len(data), nil
}

func parseJavaProperties(executable, output string) JavaInstallation {
	info := JavaInstallation{Path: executable, Version: javaProperty(output, "java.version")}
	spec := javaProperty(output, "java.specification.version")
	if info.Version == "" {
		info.Version = spec
	}
	info.Major = javaMajor(spec)
	if info.Major == 0 {
		info.Major = javaMajor(info.Version)
	}
	info.Architecture, info.Bits = javaArchitecture(output)
	return info
}

func javaProperty(output, name string) string {
	for line := range strings.SplitSeq(output, "\n") {
		key, value, found := strings.Cut(line, "=")
		if found && strings.TrimSpace(key) == name {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func javaMajor(version string) int {
	numbers := strings.TrimPrefix(version, "1.")
	if index := strings.IndexFunc(numbers, func(r rune) bool { return r < '0' || r > '9' }); index >= 0 {
		numbers = numbers[:index]
	}
	major, _ := strconv.Atoi(numbers)
	return major
}

func javaArchitecture(output string) (string, int) {
	name := javaProperty(output, "os.arch")
	bits, _ := strconv.Atoi(javaProperty(output, "sun.arch.data.model"))
	var inferred int
	switch strings.ToLower(name) {
	case "amd64", "x86_64", "x64":
		name, inferred = "x64", 64
	case "i386", "i486", "i586", "i686", "x86":
		name, inferred = "x86", 32
	case "aarch64", "arm64":
		name, inferred = "ARM64", 64
	case "arm", "arm32":
		name, inferred = "ARM", 32
	}
	if bits != 32 && bits != 64 {
		bits = inferred
	}
	if name == "" && bits != 0 {
		name = strconv.Itoa(bits) + "-bit"
	}
	return name, bits
}
