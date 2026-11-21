package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

const eulaURL = "https://www.minecraft.net/eula"

type Preferences struct {
	Directory string            `json:"directory"`
	Java      map[string]string `json:"java"`
	Language  string            `json:"language"`
}

type RuntimeSettings struct {
	Directory      string             `json:"directory"`
	DownloadTarget string             `json:"downloadTarget"`
	PackVersion    string             `json:"packVersion"`
	Java           string             `json:"java"`
	MinMiB         int                `json:"minMiB"`
	MaxMiB         int                `json:"maxMiB"`
	EULA           bool               `json:"eula"`
	Issue          string             `json:"issue"`
	Language       string             `json:"language"`
	JavaScan       []JavaInstallation `json:"javaScan"`
}

type App struct {
	ctx                context.Context
	started            chan struct{}
	mu                 sync.Mutex
	preferences        Preferences
	path               string
	executable         string
	directory          string
	downloadTarget     string
	serverPackReleases map[int]ServerPackRelease
	serverPackState    ServerPackDownloadSnapshot
	checkedPackUpdate  *checkedServerPackUpdate
	cancelServerPack   context.CancelFunc
	serverPackDone     chan struct{}
	session            *Session
	closing            atomic.Bool
	discard            atomic.Bool
	unsaved            atomic.Bool
}

func newApp(arguments []string) *App {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base, _ = os.UserConfigDir()
	}
	var path string
	if base != "" {
		path = filepath.Join(base, "MITE-Server-Manager", "preferences.json")
	}
	var prefs Preferences
	if contents, err := os.ReadFile(path); err == nil {
		_ = json.Unmarshal(contents, &prefs)
	}
	if prefs.Java == nil {
		prefs.Java = make(map[string]string)
	}
	if prefs.Language != "en" {
		prefs.Language = "zh-CN"
	}
	executable, _ := os.Executable()
	working, _ := os.Getwd()
	requested := ""
	if len(arguments) == 2 && arguments[0] == "--server" {
		requested = arguments[1]
	}
	directory, downloadTarget := initialServerLocation(executable, requested, prefs.Directory, working)
	if directory != "" {
		prefs.Directory = directory
	}
	session := NewSession()
	session.english.Store(prefs.Language == "en")
	return &App{
		preferences: prefs, path: path, session: session, started: make(chan struct{}),
		executable: executable, directory: directory, downloadTarget: downloadTarget,
		serverPackState: ServerPackDownloadSnapshot{Phase: "idle"},
	}
}

func isServerDirectory(path string) bool {
	if path == "" {
		return false
	}
	isFile := func(name string) bool {
		info, err := os.Stat(filepath.Join(path, name))
		return err == nil && info.Mode().IsRegular()
	}
	return isFile("server.properties") ||
		(isFile("start.bat") && isFile("install.bat")) ||
		(isFile("run.bat") && isFile("user_jvm_args.txt"))
}

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	close(a.started)
}

func (a *App) windowContext() context.Context {
	<-a.started
	return a.ctx
}

func (a *App) text(chinese, english string) string {
	return a.session.text(chinese, english)
}

func (a *App) SetLanguage(language string) error {
	if language != "zh-CN" && language != "en" {
		return errors.New("unsupported interface language")
	}
	a.mu.Lock()
	previous := a.preferences.Language
	a.preferences.Language = language
	if err := a.savePreferences(); err != nil {
		a.preferences.Language = previous
		a.mu.Unlock()
		return err
	}
	a.session.english.Store(language == "en")
	a.mu.Unlock()
	select {
	case <-a.started:
		runtime.WindowSetTitle(a.ctx, a.text("MITE 服务端管理器", "MITE Server Manager"))
	default:
	}
	return nil
}

func (a *App) beforeClose(ctx context.Context) bool {
	a.mu.Lock()
	if a.discard.Load() {
		a.mu.Unlock()
		return false
	}
	active := a.session.IsActive()
	if !a.closing.CompareAndSwap(false, true) {
		a.mu.Unlock()
		return true
	}
	if a.cancelServerPack != nil {
		a.cancelServerPack()
		done := a.serverPackDone
		a.mu.Unlock()
		go func() {
			<-done
			a.discard.Store(true)
			runtime.Quit(ctx)
		}()
		return true
	}
	a.mu.Unlock()
	if !active && !a.unsaved.Load() {
		return false
	}
	go func() {
		message := a.text("还有未保存的设置。确定放弃修改并退出？", "Discard unsaved changes and exit?")
		if active {
			message = a.text("服务器正在运行。是否停服、保存世界后退出管理器？", "Stop the server, save the world, and exit?")
		}
		answer, err := runtime.MessageDialog(ctx, runtime.MessageDialogOptions{
			Type: runtime.QuestionDialog, Title: a.text("退出管理器", "Exit server manager"), Message: message,
			Buttons: []string{"Yes", "No"}, DefaultButton: "No", CancelButton: "No",
		})
		if err != nil || answer != "Yes" {
			a.closing.Store(false)
			return
		}
		if active {
			if err := a.session.Stop(); err != nil {
				_, _ = runtime.MessageDialog(ctx, runtime.MessageDialogOptions{
					Type: runtime.ErrorDialog, Title: a.text("停服失败", "Could not stop the server"), Message: err.Error(),
				})
				a.closing.Store(false)
				return
			}
			for a.session.IsActive() {
				time.Sleep(250 * time.Millisecond)
			}
		}
		a.discard.Store(true)
		runtime.Quit(ctx)
	}()
	return true
}

func (a *App) SetUnsaved(unsaved bool) { a.unsaved.Store(unsaved) }

func (a *App) Snapshot(after uint64) SessionSnapshot {
	a.mu.Lock()
	defer a.mu.Unlock()
	snapshot := a.session.Snapshot(after)
	snapshot.ServerPack = a.serverPackState
	return snapshot
}

func (a *App) GetRuntime() RuntimeSettings {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.runtimeSettings()
}

func (a *App) runtimeSettings() RuntimeSettings {
	directory := a.directory
	result := RuntimeSettings{
		Directory: directory, MinMiB: 2048, MaxMiB: 6144,
		Language: a.preferences.Language, JavaScan: a.cachedJavaScan(), DownloadTarget: a.downloadTarget,
	}
	if directory == "" {
		return result
	}
	result.PackVersion = installedServerPackVersion(directory)
	result.Java = a.preferences.Java[strings.ToLower(directory)]
	minimum, maximum, err := readMemory(directory)
	if err == nil {
		result.MinMiB, result.MaxMiB = minimum, maximum
	} else {
		result.Issue = a.text("读取内存配置失败。 ", "Could not read memory settings. ") + err.Error()
	}
	result.EULA, err = isEULAAccepted(directory)
	if err != nil {
		result.Issue = a.text("读取 Minecraft EULA 状态失败。 ", "Could not read the Minecraft EULA status. ") + err.Error()
	}
	return result
}

func (a *App) ChooseDirectory() (RuntimeSettings, error) {
	current := a.GetRuntime()
	defaultDirectory := current.Directory
	if defaultDirectory == "" {
		defaultDirectory = current.DownloadTarget
	}
	directory, err := runtime.OpenDirectoryDialog(a.windowContext(), runtime.OpenDialogOptions{
		Title:            a.text("选择服务端或空安装目录", "Choose a server folder or an empty installation folder"),
		DefaultDirectory: defaultDirectory, CanCreateDirectories: true,
	})
	if err != nil || directory == "" {
		return current, err
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.closing.Load() {
		return current, errors.New(a.text("管理器正在退出。", "The server manager is closing."))
	}
	if a.session.IsActive() {
		return current, errors.New(a.text("停服后可以切换目录。", "Stop the server before switching folders."))
	}
	if a.cancelServerPack != nil {
		return current, errors.New(a.text("请等服务端下载结束后再切换目录。", "Wait for the server pack download to end before switching folders."))
	}
	selected, downloadTarget := resolveServerLocation(directory, a.executable, a.preferences.Directory)
	if selected == "" && downloadTarget == "" {
		return current, errors.New(a.text("请选择已有服务端目录或一个空安装目录。", "Choose an existing server folder or an empty installation folder."))
	}
	if strings.EqualFold(selected, a.directory) && strings.EqualFold(downloadTarget, a.downloadTarget) {
		return a.runtimeSettings(), nil
	}
	if selected != "" {
		old := a.preferences.Directory
		a.preferences.Directory = selected
		if err := a.savePreferences(); err != nil {
			a.preferences.Directory = old
			return current, err
		}
	}
	a.directory, a.downloadTarget = selected, downloadTarget
	a.serverPackState = ServerPackDownloadSnapshot{Phase: "idle"}
	a.checkedPackUpdate = nil
	a.session.Reset()
	return a.runtimeSettings(), nil
}

func (a *App) OpenDirectory() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory := a.directory
	if directory == "" {
		directory = a.downloadTarget
	}
	if directory == "" {
		return errors.New(a.text("请先选择服务端目录。", "Choose a server folder first."))
	}
	command := exec.Command("explorer.exe", directory)
	if err := command.Start(); err != nil {
		return err
	}
	return command.Process.Release()
}

func (a *App) ChooseJava() (string, error) {
	current := a.GetRuntime()
	directory := filepath.Dir(current.Java)
	if current.Java == "" && os.Getenv("JAVA_HOME") != "" {
		directory = filepath.Join(os.Getenv("JAVA_HOME"), "bin")
	}
	if stat, err := os.Stat(directory); err != nil || !stat.IsDir() {
		directory = ""
	}
	return runtime.OpenFileDialog(a.windowContext(), runtime.OpenDialogOptions{
		Title: a.text("选择 64 位 Java 21 的 java.exe", "Choose the 64-bit Java 21 executable"), DefaultDirectory: directory,
		Filters: []runtime.FileFilter{{DisplayName: a.text("Java 可执行文件", "Java executable"), Pattern: "java.exe"}},
	})
}

func (a *App) ScanJava() ([]JavaInstallation, error) {
	path := a.javaScanPath()
	if path == "" {
		return nil, errors.New(a.text("无法找到 Windows 用户配置目录。", "Could not find the Windows user settings folder."))
	}
	a.mu.Lock()
	directory := a.directory
	selected := a.preferences.Java[strings.ToLower(directory)]
	a.mu.Unlock()

	results := scanJava(directory, selected)
	if err := saveUserJSON(path, results); err != nil {
		return nil, fmt.Errorf(a.text("无法保存 Java 扫描结果：%w", "Could not save the Java scan results: %w"), err)
	}
	return results, nil
}

func (a *App) javaScanPath() string {
	if a.path == "" {
		return ""
	}
	return filepath.Join(filepath.Dir(a.path), "java-scan.json")
}

func (a *App) cachedJavaScan() []JavaInstallation {
	path := a.javaScanPath()
	if path == "" {
		return nil
	}
	content, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var results []JavaInstallation
	if err := json.Unmarshal(content, &results); err != nil {
		return nil
	}
	return slices.DeleteFunc(results, func(java JavaInstallation) bool {
		return !filepath.IsAbs(java.Path) || !strings.EqualFold(filepath.Base(java.Path), "java.exe") || !regularFile(java.Path)
	})
}

func (a *App) SaveRuntime(java string, minimum, maximum int) (RuntimeSettings, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, unlock, err := a.lockEditableDirectory()
	if err != nil {
		return RuntimeSettings{}, err
	}
	defer unlock()
	java = strings.Trim(strings.TrimSpace(java), "\"")
	if java != "" {
		java, err = filepath.Abs(os.ExpandEnv(java))
		if err != nil {
			return RuntimeSettings{}, err
		}
		info, statErr := os.Stat(java)
		if statErr == nil && info.IsDir() {
			executable := filepath.Join(java, "java.exe")
			if _, statErr = os.Stat(executable); statErr != nil {
				executable = filepath.Join(java, "bin", "java.exe")
			}
			java = executable
			info, statErr = os.Stat(java)
		}
		if statErr != nil || !info.Mode().IsRegular() || !strings.EqualFold(filepath.Base(java), "java.exe") {
			return RuntimeSettings{}, errors.New(a.text("请选择有效的 Java 21 安装目录或 java.exe。", "Choose a valid Java 21 installation folder or java.exe."))
		}
	}
	if minimum < 1 || maximum < minimum {
		return RuntimeSettings{}, errors.New(a.text("最小内存需大于 0，最大内存需大于或等于最小内存。", "Minimum memory must be positive and no greater than maximum memory."))
	}
	key := strings.ToLower(directory)
	oldJava := a.preferences.Java[key]
	a.preferences.Java[key] = java
	if err = a.savePreferences(); err != nil {
		a.preferences.Java[key] = oldJava
		return RuntimeSettings{}, err
	}
	if err = saveMemory(directory, minimum, maximum); err != nil {
		a.preferences.Java[key] = oldJava
		if restoreErr := a.savePreferences(); restoreErr != nil {
			return RuntimeSettings{}, fmt.Errorf(a.text("%w；恢复 Java 路径失败：%v", "%w. Could not restore the Java path: %v"), err, restoreErr)
		}
		return RuntimeSettings{}, err
	}
	return a.runtimeSettings(), nil
}

func (a *App) savePreferences() error {
	if a.path == "" {
		return errors.New(a.text("无法找到 Windows 用户配置目录。", "Could not find the Windows user settings folder."))
	}
	return saveUserJSON(a.path, a.preferences)
}

func saveUserJSON(path string, value any) error {
	directory := filepath.Dir(path)
	if err := os.MkdirAll(directory, 0700); err != nil {
		return err
	}
	file, err := os.CreateTemp(directory, filepath.Base(path)+"-*.tmp")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	writeErr := json.NewEncoder(file).Encode(value)
	if closeErr := file.Close(); writeErr == nil {
		writeErr = closeErr
	}
	if writeErr != nil {
		return writeErr
	}
	return os.Rename(file.Name(), path)
}

func (a *App) OpenEULA() { runtime.BrowserOpenURL(a.windowContext(), eulaURL) }

func (a *App) AcceptEULA() (RuntimeSettings, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, unlock, err := a.lockEditableDirectory()
	if err != nil {
		return RuntimeSettings{}, err
	}
	defer unlock()
	if err := acceptEULA(directory); err != nil {
		return RuntimeSettings{}, err
	}
	return a.runtimeSettings(), nil
}

func (a *App) Start() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, err := a.editableDirectory()
	if err != nil {
		return err
	}
	accepted, err := isEULAAccepted(directory)
	if err != nil {
		return err
	}
	if !accepted {
		return errors.New(a.text("请先阅读并接受 Minecraft EULA。", "Read and accept the Minecraft EULA first."))
	}
	return a.session.Start(directory, a.preferences.Java[strings.ToLower(directory)])
}

func (a *App) Stop() error { return a.session.Stop() }

func (a *App) Send(command string) error { return a.session.Send(command) }

func (a *App) ReadConfig(kind string) (ConfigResult, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.directory == "" {
		return ConfigResult{}, errors.New(a.text("请先选择服务端目录。", "Choose a server folder first."))
	}
	return readConfig(a.directory, kind)
}

func (a *App) SaveConfig(kind, text, revision string) (ConfigResult, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, unlock, err := a.lockEditableDirectory()
	if err != nil {
		return ConfigResult{}, err
	}
	defer unlock()
	return saveConfig(directory, kind, text, revision)
}

func (a *App) SaveProperties(updates map[string]string, revision string) (ConfigResult, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, unlock, err := a.lockEditableDirectory()
	if err != nil {
		return ConfigResult{}, err
	}
	defer unlock()
	return saveProperties(directory, updates, revision)
}

func (a *App) SaveTOML(kind string, updates map[string]string, revision string) (ConfigResult, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	directory, unlock, err := a.lockEditableDirectory()
	if err != nil {
		return ConfigResult{}, err
	}
	defer unlock()
	return saveTOML(directory, kind, updates, revision)
}

func (a *App) editableDirectory() (string, error) {
	if a.closing.Load() {
		return "", errors.New(a.text("管理器正在退出。", "The server manager is closing."))
	}
	if a.directory == "" {
		return "", errors.New(a.text("请先选择服务端目录。", "Choose a server folder first."))
	}
	if a.cancelServerPack != nil {
		return "", errors.New(a.text("请等服务端操作结束后再继续。", "Wait for the server pack operation to finish."))
	}
	if a.session.IsActive() {
		return "", errors.New(a.text("请在停服后调整设置。", "Stop the server before editing settings."))
	}
	if err := a.interruptedServerPackUpdate(a.directory); err != nil {
		return "", err
	}
	return a.directory, nil
}

func (a *App) lockEditableDirectory() (string, func(), error) {
	directory, err := a.editableDirectory()
	if err != nil {
		return "", nil, err
	}
	unlock, err := acquireServerLock(directory)
	if err != nil {
		return "", nil, err
	}
	if err := probeWorldLock(directory); err != nil {
		unlock()
		return "", nil, err
	}
	return directory, unlock, nil
}
