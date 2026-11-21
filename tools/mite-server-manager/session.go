// Author: NingMeng123
package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"unicode/utf8"

	"golang.org/x/sys/windows"
)

const maxLogLines = 2500

var (
	errStartCanceled     = errors.New("启动已取消")
	neoForgeVersion      = "[0-9]+(?:\\.[0-9]+){2}(?:-[A-Za-z0-9.-]+)?"
	neoForgeBatch        = regexp.MustCompile("(?i)libraries[\\\\/]net[\\\\/]neoforged[\\\\/]neoforge[\\\\/](" + neoForgeVersion + ")[\\\\/]win_args\\.txt")
	neoForgeInstaller    = regexp.MustCompile("(?i)^neoforge-(" + neoForgeVersion + ")-installer\\.jar$")
	neoForgeDirectory    = regexp.MustCompile("^" + neoForgeVersion + "$")
	ansiColor            = regexp.MustCompile("\x1b\\[[0-9;]*m")
	installerSignedBytes = regexp.MustCompile(`[\x{FF80}-\x{FFFF}]+`)
	serverMessage        = regexp.MustCompile("^(?:\\[[^\\]]+\\]\\s+)?\\[Server thread/INFO\\](?:\\s+\\[[^\\]]+\\])?:\\s*(.*)$")
	playerList           = regexp.MustCompile("^There are ([0-9]+) of a max of [0-9]+ players online:\\s*(.*)$")
	listedPlayer         = regexp.MustCompile("^([A-Za-z0-9_-]{1,16})(?: \\([0-9a-fA-F-]{36}\\))?$")
	playerChange         = regexp.MustCompile("^([A-Za-z0-9_-]{1,16})(?: \\(formerly known as [^)]+\\))? (joined|left) the game$")
	javaOutputArgs       = []string{"-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"}
)

type LogLine struct {
	ID   uint64 `json:"id"`
	Text string `json:"text"`
}

type SessionSnapshot struct {
	Phase      string                     `json:"phase"`
	Status     string                     `json:"status"`
	Active     bool                       `json:"active"`
	Ready      bool                       `json:"ready"`
	Players    []string                   `json:"players"`
	Logs       []LogLine                  `json:"logs"`
	LastLogID  uint64                     `json:"lastLogId"`
	ServerPack ServerPackDownloadSnapshot `json:"serverPack"`
}

type Session struct {
	mu            sync.Mutex
	english       atomic.Bool
	phase         string
	status        string
	directory     string
	active        bool
	reachedReady  bool
	stopRequested bool
	cancelPrepare context.CancelFunc
	stdin         io.WriteCloser
	players       map[string]string
	logs          [maxLogLines]LogLine
	logStart      int
	logCount      int
	lastLogID     uint64
}

func NewSession() *Session {
	return &Session{phase: "idle", status: "未启动"}
}

func (s *Session) text(chinese, english string) string {
	if s.english.Load() {
		return english
	}
	return chinese
}

var englishSessionStatuses = map[string]string{
	"未启动":           "Stopped",
	"已停止":           "Stopped",
	"正在检查服务端与 Java": "Checking server and Java",
	"正在安装 NeoForge": "Installing NeoForge",
	"正在启动服务端":       "Starting server",
	"运行中":           "Running",
	"正在取消启动":        "Canceling startup",
	"等待安装完成后取消启动":   "Finishing installation before canceling startup",
	"正在停止并保存世界":     "Stopping and saving the world",
	"启动已取消":         "Startup canceled",
}

func (s *Session) IsActive() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.active
}

func (s *Session) Snapshot(after uint64) SessionSnapshot {
	s.mu.Lock()
	defer s.mu.Unlock()

	players := make([]string, 0, len(s.players))
	for _, name := range s.players {
		players = append(players, name)
	}
	sort.Strings(players)

	logs := make([]LogLine, 0)
	for i := 0; i < s.logCount; i++ {
		line := s.logs[(s.logStart+i)%maxLogLines]
		if line.ID > after {
			logs = append(logs, line)
		}
	}
	status := s.status
	if english := englishSessionStatuses[status]; english != "" {
		status = s.text(status, english)
	}
	return SessionSnapshot{
		Phase: s.phase, Status: status, Active: s.active, Ready: s.phase == "running",
		Players: players, Logs: logs, LastLogID: s.lastLogID,
	}
}

func (s *Session) Reset() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.active {
		return
	}
	s.resetLocked()
}

func (s *Session) resetLocked() {
	s.phase, s.status, s.directory = "idle", "未启动", ""
	s.logs = [maxLogLines]LogLine{}
	s.logStart, s.logCount = 0, 0
}

func (s *Session) Start(directory, javaPath string) error {
	directory = strings.TrimSpace(directory)
	if directory == "" {
		return errors.New(s.text("请选择 MITE 服务端目录", "Choose a MITE server folder"))
	}
	directory, err := filepath.Abs(directory)
	if err != nil {
		return fmt.Errorf(s.text("无法识别服务端目录：%w", "Could not resolve the server folder: %w"), err)
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	if s.active {
		return errors.New(s.text("服务端正在启动、运行或停止", "The server is already starting, running, or stopping"))
	}
	if !strings.EqualFold(s.directory, directory) {
		s.resetLocked()
	}
	s.directory, s.active = directory, true
	s.stopRequested, s.reachedReady = false, false
	s.players = make(map[string]string)
	s.phase, s.status = "checking", "正在检查服务端与 Java"
	go func() { s.finish(s.run(directory, javaPath)) }()
	return nil
}

func (s *Session) Send(command string) error {
	if strings.ContainsAny(command, "\r\n\x00") {
		return errors.New(s.text("每次只能执行一行命令", "Send one command at a time"))
	}
	command = strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(command), "/"))
	if command == "" {
		return errors.New(s.text("请输入命令", "Enter a command"))
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	if s.phase != "running" {
		return errors.New(s.text("服务端尚未就绪", "The server is not ready"))
	}
	if err := s.writeCommand(command); err != nil {
		return fmt.Errorf(s.text("无法发送命令：%w", "Could not send the command: %w"), err)
	}
	return nil
}

func (s *Session) Stop() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.active || s.stopRequested {
		return nil
	}

	status := "正在取消启动"
	if s.stdin != nil {
		if err := s.writeCommand("stop"); err != nil {
			return fmt.Errorf(s.text("无法发送停服命令：%w", "Could not send the stop command: %w"), err)
		}
		status = "正在停止并保存世界"
	} else if s.cancelPrepare != nil {
		s.cancelPrepare()
	} else if s.phase == "installing" {
		status = "等待安装完成后取消启动"
	}
	s.stopRequested = true
	s.phase, s.status = "stopping", status
	return nil
}

func (s *Session) writeCommand(command string) error {
	_, err := io.WriteString(s.stdin, command+"\n")
	return err
}

func (s *Session) appendLog(line string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.appendLogLocked(line)
}

func (s *Session) appendLogLocked(line string) {
	s.lastLogID++
	index := (s.logStart + s.logCount) % maxLogLines
	s.logs[index] = LogLine{ID: s.lastLogID, Text: line}
	if s.logCount == maxLogLines {
		s.logStart = (s.logStart + 1) % maxLogLines
	} else {
		s.logCount++
	}
}

func (s *Session) advance(phase, status string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.stopRequested {
		return false
	}
	s.phase, s.status = phase, status
	return true
}

func (s *Session) finish(err error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.active, s.stdin, s.players = false, nil, nil
	switch {
	case err == nil:
		s.phase, s.status = "idle", "已停止"
	case errors.Is(err, errStartCanceled):
		s.phase, s.status = "idle", errStartCanceled.Error()
	default:
		s.phase, s.status = "error", err.Error()
		s.appendLogLocked(err.Error())
	}
}

func (s *Session) run(directory, javaPath string) error {
	info, err := os.Stat(directory)
	if err != nil || !info.IsDir() {
		return errors.New(s.text("请选择有效的 MITE 服务端目录", "Choose a valid MITE server folder"))
	}
	unlock, err := acquireServerLock(directory)
	if err != nil {
		return err
	}
	defer unlock()

	if err := probeWorldLock(directory); err != nil {
		return err
	}
	version, err := s.findNeoForge(directory)
	if err != nil {
		return err
	}
	accepted, err := isEULAAccepted(directory)
	if err != nil {
		return fmt.Errorf(s.text("无法读取 Minecraft EULA：%w", "Could not read the Minecraft EULA: %w"), err)
	}
	if !accepted {
		return errors.New(s.text("启动前请阅读并在设置中同意 Minecraft EULA", "Read and accept the Minecraft EULA in Launch settings before starting"))
	}
	java, err := s.findJava(directory, javaPath)
	if err != nil {
		return err
	}
	s.appendLog(s.text("使用 Java 21（64 位）：", "Using 64-bit Java 21: ") + java)

	runtimeArgs := filepath.Join("libraries", "net", "neoforged", "neoforge", version, "win_args.txt")
	if !neoForgeRuntimeReady(directory, runtimeArgs, version) {
		if !s.advance("installing", "正在安装 NeoForge") {
			return errStartCanceled
		}
		if err := s.installNeoForge(java, directory, version); err != nil {
			return err
		}
		if !neoForgeRuntimeReady(directory, runtimeArgs, version) {
			return fmt.Errorf(s.text("NeoForge 安装后运行文件仍不完整：%s", "NeoForge runtime files are still incomplete after installation: %s"), filepath.Dir(runtimeArgs))
		}
	}
	if !s.advance("starting", "正在启动服务端") {
		return errStartCanceled
	}
	if err := enforceServerDifficulty(directory); err != nil {
		return fmt.Errorf(s.text("无法将游戏难度设为困难。%w", "Could not set the server difficulty to Hard. %w"), err)
	}
	return s.runServer(java, directory, runtimeArgs)
}

func neoForgeRuntimeReady(directory, runtimeArgs, version string) bool {
	path := filepath.Join(directory, runtimeArgs)
	if !regularFile(path) {
		return false
	}
	// NeoForge 21.1 writes its launch arguments before it patches the server.
	return !strings.HasPrefix(version, "21.1.") ||
		regularFile(filepath.Join(filepath.Dir(path), "neoforge-"+version+"-server.jar"))
}

func (s *Session) installNeoForge(java, directory, version string) error {
	installer := "neoforge-" + version + "-installer.jar"
	if !regularFile(filepath.Join(directory, installer)) {
		return fmt.Errorf(s.text("缺少 NeoForge 安装器 %s", "Missing NeoForge installer %s"), installer)
	}

	s.appendLog(fmt.Sprintf(s.text("正在安装 NeoForge %s，首次安装需要联网。", "Installing NeoForge %s. Internet access is required."), version))
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	s.mu.Lock()
	if s.stopRequested {
		s.mu.Unlock()
		return errStartCanceled
	}
	s.cancelPrepare = cancel
	s.mu.Unlock()
	prepareErr := prepareInstallerLibraries(ctx, directory, installer, s.appendLog, s.english.Load())
	s.mu.Lock()
	s.cancelPrepare = nil
	stopped := s.stopRequested
	s.mu.Unlock()
	if stopped {
		return errStartCanceled
	}
	if prepareErr != nil {
		s.appendLog(s.text("镜像预下载未完成，将继续使用官方安装器。 ", "Mirror preparation did not complete. Continuing with the official installer. ") + prepareErr.Error())
	}

	args := append(append([]string(nil), javaOutputArgs...), "-Dfile.encoding=UTF-8", "-jar", installer, "--installServer")
	cmd, flush := s.javaCommand(java, directory, args, false)
	s.mu.Lock()
	if s.stopRequested {
		s.mu.Unlock()
		return errStartCanceled
	}
	err := cmd.Start()
	s.mu.Unlock()
	if err != nil {
		return fmt.Errorf(s.text("无法运行 NeoForge 安装器：%w", "Could not run the NeoForge installer: %w"), err)
	}
	err = cmd.Wait()
	flush()
	if err != nil {
		return fmt.Errorf(s.text("NeoForge 安装失败，请查看控制台日志：%w", "NeoForge installation failed. See the console log: %w"), err)
	}
	return nil
}

func (s *Session) runServer(java, directory, runtimeArgs string) error {
	var args []string
	if path := jvmArgsPath(directory); regularFile(path) {
		args = append(args, "@"+filepath.Base(path))
	}
	args = append(args, javaOutputArgs...)
	args = append(args, "-Dterminal.jline=false", "-Dterminal.ansi=false", "@"+filepath.ToSlash(runtimeArgs), "nogui")
	cmd, flush := s.javaCommand(java, directory, args, true)
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return fmt.Errorf(s.text("无法连接服务器控制台：%w", "Could not connect to the server console: %w"), err)
	}
	defer stdin.Close()

	s.mu.Lock()
	if s.stopRequested {
		s.mu.Unlock()
		return errStartCanceled
	}
	err = cmd.Start()
	if err == nil {
		s.stdin = stdin
	}
	s.mu.Unlock()
	if err != nil {
		return fmt.Errorf(s.text("无法启动服务端：%w", "Could not start the server: %w"), err)
	}
	err = cmd.Wait()
	flush()

	s.mu.Lock()
	s.stdin = nil
	readyOrStopped := s.reachedReady || s.stopRequested
	s.mu.Unlock()
	if err != nil {
		return fmt.Errorf(s.text("服务端异常退出，请检查控制台日志：%w", "The server exited unexpectedly. See the console log: %w"), err)
	}
	if !readyOrStopped {
		return errors.New(s.text("服务端在启动完成前退出，请检查控制台日志", "The server exited before it was ready. See the console log."))
	}
	return nil
}

func (s *Session) javaCommand(java, directory string, args []string, server bool) (*exec.Cmd, func()) {
	cmd := exec.Command(java, args...)
	setJavaProcess(cmd, directory)
	outputLine, errorLine := s.appendLog, s.appendLog
	if server {
		outputLine = s.observe
	} else {
		installerLine := func(line string) { s.appendLog(restoreInstallerUTF8(line)) }
		outputLine, errorLine = installerLine, installerLine
	}
	stdout, stderr := &consoleWriter{line: outputLine}, &consoleWriter{line: errorLine}
	cmd.Stdout, cmd.Stderr = stdout, stderr
	return cmd, func() { stdout.flush(); stderr.flush() }
}

func restoreInstallerUTF8(line string) string {
	// LegacyInstaller sign-extends UTF-8 bytes while forwarding processor output.
	// Reassemble complete UTF-8 runs and leave other Unicode text alone.
	return installerSignedBytes.ReplaceAllStringFunc(line, func(run string) string {
		original := make([]byte, 0, len(run)/3)
		for _, character := range run {
			original = append(original, byte(character))
		}
		if utf8.Valid(original) {
			return string(original)
		}
		return run
	})
}

type consoleWriter struct {
	buffer []byte
	line   func(string)
}

func (w *consoleWriter) Write(data []byte) (int, error) {
	count := len(data)
	for len(data) > 0 {
		index := bytes.IndexByte(data, '\n')
		if index < 0 {
			w.buffer = appendConsoleLine(w.buffer, data)
			break
		}
		w.buffer = appendConsoleLine(w.buffer, data[:index])
		w.flush()
		data = data[index+1:]
	}
	return count, nil
}

func appendConsoleLine(buffer, fragment []byte) []byte {
	const maxLine = 16 * 1024
	if remaining := maxLine - len(buffer); len(fragment) > remaining {
		fragment = fragment[:remaining]
	}
	return append(buffer, fragment...)
}

func (w *consoleWriter) flush() {
	if len(w.buffer) != 0 {
		line := strings.TrimSuffix(strings.ToValidUTF8(string(w.buffer), "�"), "\r")
		w.line(ansiColor.ReplaceAllString(line, ""))
		w.buffer = w.buffer[:0]
	}
}

func (s *Session) observe(line string) {
	entry := serverMessage.FindStringSubmatch(line)
	s.mu.Lock()
	s.appendLogLocked(line)
	if entry == nil {
		s.mu.Unlock()
		return
	}
	message := entry[1]
	refresh := false
	switch {
	case strings.HasPrefix(message, "Done (") && strings.Contains(message, "For help, type"):
		s.reachedReady = true
		if !s.stopRequested {
			s.phase, s.status = "running", "运行中"
			refresh = true
		}
	case message == "Stopping server":
		s.stopRequested = true
		s.phase, s.status = "stopping", "正在停止并保存世界"
	default:
		if list := playerList.FindStringSubmatch(message); list != nil {
			if names := parsePlayers(list[1], list[2]); names != nil {
				s.players = names
			}
		} else if change := playerChange.FindStringSubmatch(message); change != nil {
			name, action := change[1], change[2]
			if action == "joined" {
				s.players[strings.ToLower(name)] = name
			} else {
				delete(s.players, strings.ToLower(name))
			}
			refresh = true
		}
	}
	s.mu.Unlock()
	if refresh {
		_ = s.Send("list uuids")
	}
}

func parsePlayers(countText, namesText string) map[string]string {
	count, err := strconv.Atoi(countText)
	if err != nil {
		return nil
	}
	names := make(map[string]string)
	for _, item := range strings.Split(namesText, ",") {
		if match := listedPlayer.FindStringSubmatch(strings.TrimSpace(item)); match != nil {
			names[strings.ToLower(match[1])] = match[1]
		}
	}
	if len(names) != count {
		return nil
	}
	return names
}

func (s *Session) findNeoForge(directory string) (string, error) {
	for _, script := range []string{"start.bat", "run.bat"} {
		content, err := os.ReadFile(filepath.Join(directory, script))
		if err == nil {
			if match := neoForgeBatch.FindStringSubmatch(string(content)); match != nil {
				return match[1], nil
			}
		}
	}
	entries, err := os.ReadDir(directory)
	if err != nil {
		return "", fmt.Errorf(s.text("无法读取服务端目录：%w", "Could not read the server folder: %w"), err)
	}
	var installers []string
	for _, entry := range entries {
		if match := neoForgeInstaller.FindStringSubmatch(entry.Name()); match != nil && !entry.IsDir() {
			installers = append(installers, match[1])
		}
	}
	if len(installers) == 1 {
		return installers[0], nil
	}
	libraries := filepath.Join(directory, "libraries", "net", "neoforged", "neoforge")
	entries, _ = os.ReadDir(libraries)
	var versions []string
	for _, entry := range entries {
		if entry.IsDir() && neoForgeDirectory.MatchString(entry.Name()) && regularFile(filepath.Join(libraries, entry.Name(), "win_args.txt")) {
			versions = append(versions, entry.Name())
		}
	}
	if len(versions) == 1 {
		return versions[0], nil
	}
	return "", errors.New(s.text("无法确定 NeoForge 版本，请选择包含启动脚本、单个安装器或已安装 NeoForge 的目录", "Could not identify the NeoForge version. Choose a folder with its launch script, a single installer, or an existing NeoForge installation."))
}

func (s *Session) findJava(directory, configured string) (string, error) {
	if strings.TrimSpace(configured) != "" {
		executable := javaExecutable(configured)
		if executable == "" {
			return "", fmt.Errorf(s.text("所选位置找不到 java.exe：%s", "java.exe was not found at the selected location: %s"), configured)
		}
		if detail, ok := s.checkJava(executable, directory); !ok {
			return "", fmt.Errorf(s.text("需要 64 位 Java 21，所选 Java：%s（%s）", "64-bit Java 21 is required. Found %s (%s)"), detail, executable)
		}
		return executable, nil
	}

	for _, executable := range javaCandidatePaths(directory) {
		detail, ok := s.checkJava(executable, directory)
		if ok {
			return executable, nil
		}
		s.appendLog(fmt.Sprintf(s.text("已跳过无法使用的 Java：%s（%s）", "Skipped incompatible Java: %s (%s)"), detail, executable))
	}
	return "", errors.New(s.text("未找到 64 位 Java 21，请选择 Java 21 的 java.exe 或 JDK 目录", "Could not find 64-bit Java 21. Choose java.exe or a Java 21 installation folder"))
}

func javaExecutable(path string) string {
	path = strings.Trim(strings.TrimSpace(path), "\"")
	if path == "" {
		return ""
	}
	if info, err := os.Stat(path); err == nil && info.IsDir() {
		direct := filepath.Join(path, "java.exe")
		if regularFile(direct) {
			path = direct
		} else {
			path = filepath.Join(path, "bin", "java.exe")
		}
	}
	if !strings.EqualFold(filepath.Base(path), "java.exe") || !regularFile(path) {
		return ""
	}
	absolute, _ := filepath.Abs(path)
	return absolute
}

func (s *Session) checkJava(java, directory string) (string, bool) {
	installation, err := inspectJava(java, directory)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			return s.text("版本检查超时", "Java version check timed out"), false
		}
		return s.text("无法运行或识别版本：", "Could not run Java or detect its version: ") + err.Error(), false
	}
	display := "Java " + installation.Version
	if installation.Architecture != "" {
		display += s.text("（"+installation.Architecture+"）", " ("+installation.Architecture+")")
	}
	return display, installation.Compatible
}

func setJavaProcess(cmd *exec.Cmd, directory string) {
	const createNoWindow = 0x08000000
	cmd.Dir = directory
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: createNoWindow}
}

func acquireServerLock(directory string) (func(), error) {
	path, err := syscall.UTF16PtrFromString(filepath.Join(directory, ".mite-manager.lock"))
	if err != nil {
		return nil, fmt.Errorf("服务端目录无效：%w", err)
	}
	const deleteOnClose = 0x04000000
	handle, err := syscall.CreateFile(path, syscall.GENERIC_READ|syscall.GENERIC_WRITE,
		0, nil, syscall.OPEN_ALWAYS, syscall.FILE_ATTRIBUTE_NORMAL|deleteOnClose, 0)
	if err != nil {
		return nil, fmt.Errorf("无法锁定服务端目录，目录可能已被其他管理器占用或无法写入：%w", err)
	}
	return func() { _ = syscall.CloseHandle(handle) }, nil
}

func probeWorldLock(directory string) error {
	unlock, err := holdWorldLock(directory)
	if err != nil || unlock == nil {
		return err
	}
	return unlock()
}

func holdWorldLock(directory string) (func() error, error) {
	config, err := readConfig(directory, "server")
	if err != nil {
		return nil, fmt.Errorf("无法读取世界目录设置：%w", err)
	}
	level := config.Properties["level-name"]
	if level == "" {
		level = "world"
	}
	if !filepath.IsLocal(level) {
		return nil, errors.New("世界目录 level-name 必须位于服务端目录内")
	}
	root, err := os.OpenRoot(directory)
	if err != nil {
		return nil, fmt.Errorf("无法访问服务端目录：%w", err)
	}
	defer root.Close()
	path := filepath.Join(level, "session.lock")
	file, err := root.Open(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if errors.Is(err, windows.ERROR_SHARING_VIOLATION) {
		return nil, fmt.Errorf("世界 %q 正被另一 Minecraft 进程使用，请先停服", level)
	}
	if err != nil {
		return nil, fmt.Errorf("无法打开世界锁 %s：%w", path, err)
	}
	info, err := file.Stat()
	if err != nil {
		file.Close()
		return nil, fmt.Errorf("无法检查世界锁 %s：%w", path, err)
	}
	if !info.Mode().IsRegular() {
		file.Close()
		return nil, fmt.Errorf("世界锁 %s 不是普通文件", path)
	}

	// Java locks the entire file, so testing its first byte detects a running world.
	handle := windows.Handle(file.Fd())
	var position windows.Overlapped
	err = windows.LockFileEx(handle, windows.LOCKFILE_EXCLUSIVE_LOCK|windows.LOCKFILE_FAIL_IMMEDIATELY, 0, 1, 0, &position)
	if err != nil {
		file.Close()
		if errors.Is(err, windows.ERROR_LOCK_VIOLATION) {
			return nil, fmt.Errorf("世界 %q 正被另一 Minecraft 进程使用，请先停服", level)
		}
		return nil, fmt.Errorf("无法检查世界锁 %s：%w", path, err)
	}
	return func() error {
		defer file.Close()
		if err := windows.UnlockFileEx(handle, 0, 1, 0, &position); err != nil {
			return fmt.Errorf("无法释放世界锁 %s：%w", path, err)
		}
		return nil
	}, nil
}

func regularFile(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.Mode().IsRegular()
}
