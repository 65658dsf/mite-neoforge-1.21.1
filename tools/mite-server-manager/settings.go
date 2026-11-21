// Author: NingMeng123
package main

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"sort"
	"strconv"
	"strings"
	"sync"
	"unicode/utf16"
	"unicode/utf8"
)

const missingRevision = "missing"
const hardServerDifficulty = "hard"

var (
	errConfigConflict = errors.New("文件在读取后已被修改，请重新加载")
	settingsWriteLock sync.Mutex
	jvmArguments      = regexp.MustCompile(`#[^\r\n]*|(?:[^\s#'"]|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*')+`)
	jvmArgumentQuotes = strings.NewReplacer("'", "", "\"", "")
	lineEndings       = regexp.MustCompile(`\r\n|\n|\r`)
)

type ConfigResult struct {
	Kind       string            `json:"kind"`
	Path       string            `json:"path"`
	Text       string            `json:"text"`
	Exists     bool              `json:"exists"`
	Revision   string            `json:"revision"`
	Properties map[string]string `json:"properties"`
	Fields     []ConfigField     `json:"fields"`
	FormIssue  string            `json:"formIssue,omitempty"`
}

func configPath(kind string) (string, error) {
	switch kind {
	case "server":
		return "server.properties", nil
	case "mite":
		return filepath.Join("config", "mite-common.toml"), nil
	case "ite":
		return filepath.Join("config", "modern_ite-common.toml"), nil
	default:
		return "", fmt.Errorf("不支持的配置类型 %q", kind)
	}
}

func readConfig(directory, kind string) (ConfigResult, error) {
	path, err := configPath(kind)
	if err != nil {
		return ConfigResult{}, err
	}
	absolute, err := filepath.Abs(directory)
	if err != nil {
		return ConfigResult{}, err
	}
	root, err := os.OpenRoot(absolute)
	if err != nil {
		return ConfigResult{}, err
	}
	defer root.Close()
	doc, err := readSettingsFile(root, path)
	if err != nil {
		return ConfigResult{}, err
	}
	return configResult(doc, kind, filepath.Join(absolute, path)), nil
}

func saveConfig(directory, kind, content, revision string) (ConfigResult, error) {
	return updateConfig(directory, kind, revision, func(doc settingsFile) (string, error) {
		return alignConfigLineEndings(content, doc.text), nil
	})
}

func saveProperties(directory string, updates map[string]string, revision string) (ConfigResult, error) {
	return updateConfig(directory, "server", revision, func(doc settingsFile) (string, error) {
		return alignConfigLineEndings(mergeProperties(doc.text, updates), doc.text), nil
	})
}

func saveTOML(directory, kind string, updates map[string]string, revision string) (ConfigResult, error) {
	if kind != "mite" && kind != "ite" {
		return ConfigResult{}, errors.New("请选择 MITE 本体或 ITE 扩展配置")
	}
	return updateConfig(directory, kind, revision, func(doc settingsFile) (string, error) {
		return applyTOMLFields(doc.text, updates)
	})
}

func alignConfigLineEndings(text, original string) string {
	normalized := normalizedLines(text)
	if normalized == normalizedLines(original) {
		return original
	}
	return strings.ReplaceAll(normalized, "\n", preferredLineEnding(original))
}

func enforceServerDifficulty(directory string) error {
	const path = "server.properties"
	settingsWriteLock.Lock()
	defer settingsWriteLock.Unlock()
	root, err := os.OpenRoot(directory)
	if err != nil {
		return err
	}
	defer root.Close()
	doc, err := readSettingsFile(root, path)
	if err != nil {
		return err
	}
	_, err = writeSettingsFile(root, path, doc, withHardServerDifficulty(doc.text))
	return err
}

func withHardServerDifficulty(text string) string {
	return alignConfigLineEndings(mergeProperties(text, map[string]string{"difficulty": hardServerDifficulty}), text)
}

func updateConfig(directory, kind, revision string, edit func(settingsFile) (string, error)) (ConfigResult, error) {
	path, err := configPath(kind)
	if err != nil {
		return ConfigResult{}, err
	}
	absolute, err := filepath.Abs(directory)
	if err != nil {
		return ConfigResult{}, err
	}
	settingsWriteLock.Lock()
	defer settingsWriteLock.Unlock()
	root, err := os.OpenRoot(absolute)
	if err != nil {
		return ConfigResult{}, err
	}
	defer root.Close()

	doc, err := readSettingsFile(root, path)
	if err != nil {
		return ConfigResult{}, err
	}
	if doc.revision != revision {
		return ConfigResult{}, errConfigConflict
	}
	if kind != "server" && !doc.exists {
		return ConfigResult{}, errors.New("此配置将在服务端首次成功启动后生成")
	}
	text, err := edit(doc)
	if err != nil {
		return ConfigResult{}, err
	}
	if kind == "server" {
		text = withHardServerDifficulty(text)
	}
	doc, err = writeSettingsFile(root, path, doc, text)
	if err != nil {
		return ConfigResult{}, err
	}
	return configResult(doc, kind, filepath.Join(absolute, path)), nil
}

func configResult(doc settingsFile, kind, absolutePath string) ConfigResult {
	result := ConfigResult{
		Kind: kind, Path: absolutePath, Text: doc.text, Exists: doc.exists,
		Revision: doc.revision, Properties: make(map[string]string), Fields: []ConfigField{},
	}
	if kind == "server" {
		result.Properties = readProperties(doc.text)
	} else if doc.exists {
		fields, err := readTOMLFields(doc.text)
		if err != nil {
			result.FormIssue = "无法生成配置表单。请切换到原文查看。 " + err.Error()
		} else if fields != nil {
			result.Fields = fields
		}
	}
	return result
}

func isEULAAccepted(directory string) (bool, error) {
	root, err := os.OpenRoot(directory)
	if err != nil {
		return false, err
	}
	defer root.Close()
	doc, err := readSettingsFile(root, "eula.txt")
	if err != nil {
		return false, err
	}
	return strings.EqualFold(readProperties(doc.text)["eula"], "true"), nil
}

func acceptEULA(directory string) error {
	settingsWriteLock.Lock()
	defer settingsWriteLock.Unlock()
	root, err := os.OpenRoot(directory)
	if err != nil {
		return err
	}
	defer root.Close()
	const path = "eula.txt"
	doc, err := readSettingsFile(root, path)
	if err != nil {
		return err
	}
	if strings.EqualFold(readProperties(doc.text)["eula"], "true") {
		return nil
	}
	text := doc.text
	if !doc.exists {
		text = "# Read https://www.minecraft.net/eula before accepting.\n"
	}
	updated := mergeProperties(text, map[string]string{"eula": "true"})
	updated = strings.ReplaceAll(updated, "\n", preferredLineEnding(doc.text))
	_, err = writeSettingsFile(root, path, doc, updated)
	return err
}

func jvmArgsPath(directory string) string {
	packArgs := filepath.Join(directory, "server_jvm_args.txt")
	if info, err := os.Stat(packArgs); err == nil && info.Mode().IsRegular() {
		return packArgs
	}
	return filepath.Join(directory, "user_jvm_args.txt")
}

func readMemory(directory string) (minMiB, maxMiB int, err error) {
	root, err := os.OpenRoot(directory)
	if err != nil {
		return 0, 0, err
	}
	defer root.Close()
	path := filepath.Base(jvmArgsPath(directory))
	doc, err := readSettingsFile(root, path)
	if err != nil {
		return 0, 0, err
	}
	for _, option := range heapOptions(doc.text) {
		if option.argument[3] == 's' {
			minMiB = parseHeapMiB(option.argument[4:])
		} else {
			maxMiB = parseHeapMiB(option.argument[4:])
		}
	}
	if minMiB == 0 {
		minMiB = 2048
		if maxMiB > 0 {
			minMiB = min(minMiB, maxMiB)
		}
	}
	if maxMiB == 0 {
		maxMiB = max(6144, minMiB)
	}
	return minMiB, maxMiB, nil
}

func saveMemory(directory string, minMiB, maxMiB int) error {
	if minMiB <= 0 || maxMiB < minMiB {
		return errors.New("最大内存必须大于或等于最小内存，且最小内存必须大于 0 MiB")
	}
	settingsWriteLock.Lock()
	defer settingsWriteLock.Unlock()
	root, err := os.OpenRoot(directory)
	if err != nil {
		return err
	}
	defer root.Close()
	path := filepath.Base(jvmArgsPath(directory))
	doc, err := readSettingsFile(root, path)
	if err != nil {
		return err
	}
	minimum, maximum := heapArgument("-Xms", minMiB), heapArgument("-Xmx", maxMiB)
	hasMinimum, hasMaximum := false, false
	var updated strings.Builder
	cursor := 0
	for _, option := range heapOptions(doc.text) {
		updated.WriteString(doc.text[cursor:option.start])
		replacement := maximum
		if option.argument[3] == 's' {
			hasMinimum = true
			replacement = minimum
		} else {
			hasMaximum = true
		}
		updated.WriteString(replaceHeapToken(doc.text[option.start:option.end], replacement))
		cursor = option.end
	}
	updated.WriteString(doc.text[cursor:])
	text := updated.String()
	newline := preferredLineEnding(doc.text)
	for _, argument := range []struct {
		present bool
		token   string
	}{{hasMinimum, minimum}, {hasMaximum, maximum}} {
		if argument.present {
			continue
		}
		if text != "" && !strings.HasSuffix(text, "\n") && !strings.HasSuffix(text, "\r") {
			text += newline
		}
		text += argument.token + newline
	}
	_, err = writeSettingsFile(root, path, doc, text)
	return err
}

type heapOption struct {
	start    int
	end      int
	argument string
}

func heapOptions(text string) []heapOption {
	var options []heapOption
	for _, span := range jvmArguments.FindAllStringIndex(text, -1) {
		start, end := span[0], span[1]
		// The Java launcher discards the current token if a comment starts before whitespace.
		if text[start] == '#' || end < len(text) && text[end] == '#' {
			continue
		}
		argument := jvmArgumentQuotes.Replace(text[start:end])
		if strings.HasPrefix(argument, "-Xms") || strings.HasPrefix(argument, "-Xmx") {
			options = append(options, heapOption{start, end, argument})
		}
	}
	return options
}

func replaceHeapToken(original, replacement string) string {
	if len(original) > 1 && (original[0] == '\'' || original[0] == '"') && original[0] == original[len(original)-1] {
		return original[:1] + replacement + original[len(original)-1:]
	}
	return replacement
}

func parseHeapMiB(value string) int {
	if value == "" {
		return 0
	}
	var divisor uint64 = 1024 * 1024
	var multiplier uint64 = 1
	switch value[len(value)-1] {
	case 'k', 'K':
		divisor = 1024
	case 'm', 'M':
		divisor = 1
	case 'g', 'G':
		divisor, multiplier = 1, 1024
	case 't', 'T':
		divisor, multiplier = 1, 1024*1024
	}
	if divisor != 1024*1024 {
		value = value[:len(value)-1]
	}
	size, err := strconv.ParseUint(value, 10, 64)
	if err != nil || size == 0 {
		return 0
	}
	size = (size-1)/divisor + 1
	if size > uint64(^uint(0)>>1)/multiplier {
		return 0
	}
	return int(size * multiplier)
}

func heapArgument(flag string, value int) string {
	if value%1024 == 0 {
		return flag + strconv.Itoa(value/1024) + "G"
	}
	return flag + strconv.Itoa(value) + "M"
}

type settingsFile struct {
	text     string
	revision string
	format   fileEncoding
	mode     os.FileMode
	exists   bool
}

func readSettingsFile(root *os.Root, path string) (settingsFile, error) {
	info, err := root.Lstat(path)
	if errors.Is(err, os.ErrNotExist) {
		return settingsFile{revision: missingRevision, mode: 0600}, nil
	}
	if err != nil {
		return settingsFile{}, err
	}
	if !info.Mode().IsRegular() {
		return settingsFile{}, fmt.Errorf("配置路径不是普通文件：%s", path)
	}
	data, err := root.ReadFile(path)
	if err != nil {
		return settingsFile{}, err
	}
	encoding, text, err := decodeSettings(data)
	if err != nil {
		return settingsFile{}, fmt.Errorf("%s：%w", path, err)
	}
	return settingsFile{
		text: text, revision: revisionFor(data), format: encoding,
		mode: info.Mode().Perm(), exists: true,
	}, nil
}

func writeSettingsFile(root *os.Root, path string, before settingsFile, text string) (settingsFile, error) {
	data, err := before.format.encode(text)
	if err != nil {
		return settingsFile{}, err
	}
	revision := revisionFor(data)
	if revision == before.revision {
		return before, nil
	}

	var suffix [12]byte
	if _, err := rand.Read(suffix[:]); err != nil {
		return settingsFile{}, err
	}
	pending := filepath.Join(filepath.Dir(path), ".mite-manager-"+hex.EncodeToString(suffix[:])+".tmp")
	file, err := root.OpenFile(pending, os.O_WRONLY|os.O_CREATE|os.O_EXCL, before.mode)
	if err != nil {
		return settingsFile{}, err
	}
	defer root.Remove(pending)
	_, err = file.Write(data)
	if err == nil {
		err = file.Sync()
	}
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return settingsFile{}, err
	}

	current, err := readSettingsFile(root, path)
	if err != nil {
		return settingsFile{}, err
	}
	if current.revision != before.revision {
		return settingsFile{}, errConfigConflict
	}
	if err := root.Rename(pending, path); err != nil {
		return settingsFile{}, err
	}
	before.text, before.revision, before.exists = text, revision, true
	return before, nil
}

func revisionFor(data []byte) string {
	digest := sha256.Sum256(data)
	return hex.EncodeToString(digest[:])
}

func normalizedLines(text string) string {
	return strings.ReplaceAll(strings.ReplaceAll(text, "\r\n", "\n"), "\r", "\n")
}

func preferredLineEnding(text string) string {
	counts := make(map[string]int, 3)
	for _, ending := range lineEndings.FindAllString(text, -1) {
		counts[ending]++
	}
	if counts["\n"] > counts["\r\n"] && counts["\n"] >= counts["\r"] {
		return "\n"
	}
	if counts["\r"] > counts["\r\n"] {
		return "\r"
	}
	return "\r\n"
}

type fileEncoding struct {
	bom   []byte
	width int
	order interface {
		binary.ByteOrder
		binary.AppendByteOrder
	}
	latin bool
}

func decodeSettings(data []byte) (fileEncoding, string, error) {
	formats := []fileEncoding{
		{bom: []byte{0xff, 0xfe, 0, 0}, width: 4, order: binary.LittleEndian},
		{bom: []byte{0, 0, 0xfe, 0xff}, width: 4, order: binary.BigEndian},
		{bom: []byte{0xff, 0xfe}, width: 2, order: binary.LittleEndian},
		{bom: []byte{0xfe, 0xff}, width: 2, order: binary.BigEndian},
		{bom: []byte{0xef, 0xbb, 0xbf}},
	}
	for _, format := range formats {
		if !bytes.HasPrefix(data, format.bom) {
			continue
		}
		payload := data[len(format.bom):]
		text, err := format.decode(payload)
		return format, text, err
	}
	if utf8.Valid(data) {
		return fileEncoding{}, string(data), nil
	}
	var text strings.Builder
	for _, value := range data {
		text.WriteRune(rune(value))
	}
	return fileEncoding{latin: true}, text.String(), nil
}

func (encoding fileEncoding) decode(data []byte) (string, error) {
	invalid := errors.New("配置文件编码无效")
	switch encoding.width {
	case 0:
		if !utf8.Valid(data) {
			return "", invalid
		}
		return string(data), nil
	case 2:
		if len(data)%2 != 0 {
			return "", invalid
		}
		units := make([]uint16, 0, len(data)/2)
		for len(data) > 0 {
			units = append(units, encoding.order.Uint16(data))
			data = data[2:]
		}
		runes := utf16.Decode(units)
		if !slices.Equal(units, utf16.Encode(runes)) {
			return "", invalid
		}
		return string(runes), nil
	default:
		if len(data)%4 != 0 {
			return "", invalid
		}
		var text strings.Builder
		for len(data) > 0 {
			value := rune(encoding.order.Uint32(data))
			if !utf8.ValidRune(value) {
				return "", invalid
			}
			text.WriteRune(value)
			data = data[4:]
		}
		return text.String(), nil
	}
}

func (encoding fileEncoding) encode(text string) ([]byte, error) {
	if !utf8.ValidString(text) {
		return nil, errors.New("配置包含无效的 UTF-8 字符")
	}
	data := append([]byte(nil), encoding.bom...)
	if encoding.latin {
		for _, value := range text {
			if value > 255 {
				return nil, errors.New("文件原有编码不支持这次编辑中的部分字符")
			}
			data = append(data, byte(value))
		}
		return data, nil
	}
	switch encoding.width {
	case 2:
		for _, value := range utf16.Encode([]rune(text)) {
			data = encoding.order.AppendUint16(data, value)
		}
	case 4:
		for _, value := range text {
			data = encoding.order.AppendUint32(data, uint32(value))
		}
	default:
		data = append(data, text...)
	}
	return data, nil
}

type propertyRecord struct {
	start int
	end   int
	key   string
	value string
}

func scanProperties(text string) []propertyRecord {
	records := make([]propertyRecord, 0)
	const whitespace = " \t\f"
	for offset := 0; offset < len(text); {
		start := offset
		readLine := func() string {
			line, remainder, found := strings.Cut(text[offset:], "\n")
			offset = len(text) - len(remainder)
			if !found {
				offset = len(text)
			}
			return strings.TrimLeft(line, whitespace)
		}
		line := readLine()
		if line == "" || line[0] == '#' || line[0] == '!' {
			continue
		}
		var logical strings.Builder
		for {
			withoutSlashes := strings.TrimRight(line, "\\")
			if (len(line)-len(withoutSlashes))%2 == 0 {
				logical.WriteString(line)
				break
			}
			logical.WriteString(line[:len(line)-1])
			if offset >= len(text) {
				break
			}
			line = readLine()
		}
		key, value, ok := splitProperty(logical.String())
		if ok {
			records = append(records, propertyRecord{start, offset, key, value})
		}
	}
	return records
}

func splitProperty(line string) (string, string, bool) {
	end := 0
	for end < len(line) && !strings.ContainsRune("=: \t\f", rune(line[end])) {
		if line[end] == '\\' && end+1 < len(line) {
			end++
		}
		end++
	}
	value := strings.TrimLeft(line[end:], " \t\f")
	if strings.HasPrefix(value, "=") || strings.HasPrefix(value, ":") {
		value = strings.TrimLeft(value[1:], " \t\f")
	}
	key, validKey := decodeProperty(line[:end])
	value, validValue := decodeProperty(value)
	return key, value, validKey && validValue
}

func readProperties(text string) map[string]string {
	properties := make(map[string]string)
	for _, record := range scanProperties(normalizedLines(text)) {
		properties[record.key] = record.value
	}
	return properties
}

func mergeProperties(text string, updates map[string]string) string {
	text = normalizedLines(text)
	records := scanProperties(text)
	last := make(map[string]int)
	for i, record := range records {
		last[record.key] = i
	}
	var merged strings.Builder
	offset := 0
	for i, record := range records {
		value, updated := updates[record.key]
		if !updated || last[record.key] != i || value == record.value {
			continue
		}
		merged.WriteString(text[offset:record.start])
		merged.WriteString(encodeProperty(record.key, true) + "=" + encodeProperty(value, false))
		if text[record.end-1] == '\n' {
			merged.WriteByte('\n')
		}
		offset = record.end
	}
	merged.WriteString(text[offset:])

	var newKeys []string
	for key := range updates {
		if _, known := last[key]; !known {
			newKeys = append(newKeys, key)
		}
	}
	sort.Strings(newKeys)
	if len(newKeys) > 0 && merged.Len() > 0 {
		tail := strings.TrimSuffix(merged.String(), "\n")
		if !strings.HasSuffix(merged.String(), "\n") {
			merged.WriteByte('\n')
		}
		if (len(tail)-len(strings.TrimRight(tail, "\\")))%2 == 1 {
			merged.WriteByte('\n')
		}
	}
	for _, key := range newKeys {
		merged.WriteString(encodeProperty(key, true) + "=" + encodeProperty(updates[key], false) + "\n")
	}
	return merged.String()
}

func decodeProperty(text string) (string, bool) {
	var decoded strings.Builder
	for len(text) > 0 {
		before, after, escaped := strings.Cut(text, "\\")
		decoded.WriteString(before)
		if !escaped || after == "" {
			break
		}
		text = after[1:]
		switch after[0] {
		case 't':
			decoded.WriteByte('\t')
		case 'r':
			decoded.WriteByte('\r')
		case 'n':
			decoded.WriteByte('\n')
		case 'f':
			decoded.WriteByte('\f')
		case 'u':
			value, valid := unicodePropertyUnit(text)
			if !valid {
				return "", false
			}
			text = text[4:]
			if value >= 0xd800 && value <= 0xdbff && strings.HasPrefix(text, "\\u") {
				if low, ok := unicodePropertyUnit(text[2:]); ok && low >= 0xdc00 && low <= 0xdfff {
					value = utf16.DecodeRune(value, low)
					text = text[6:]
				}
			}
			decoded.WriteRune(value)
		default:
			escapedRune, width := utf8.DecodeRuneInString(after)
			decoded.WriteRune(escapedRune)
			text = after[width:]
		}
	}
	return decoded.String(), true
}

func unicodePropertyUnit(text string) (rune, bool) {
	if len(text) < 4 {
		return 0, false
	}
	unit, err := strconv.ParseUint(text[:4], 16, 16)
	return rune(unit), err == nil
}

func encodeProperty(text string, key bool) string {
	var out strings.Builder
	for index, value := range text {
		switch value {
		case '\\':
			out.WriteString("\\\\")
		case '\t':
			out.WriteString("\\t")
		case '\n':
			out.WriteString("\\n")
		case '\r':
			out.WriteString("\\r")
		case '\f':
			out.WriteString("\\f")
		case ' ', ':', '=', '#', '!':
			if key || value == ' ' && index == 0 {
				out.WriteByte('\\')
			}
			out.WriteRune(value)
		default:
			if value < 32 || value > 126 {
				for _, unit := range utf16.Encode([]rune{value}) {
					fmt.Fprintf(&out, "\\u%04X", unit)
				}
			} else {
				out.WriteRune(value)
			}
		}
	}
	return out.String()
}
