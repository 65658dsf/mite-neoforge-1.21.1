// Author: NingMeng123
package main

import (
	"errors"
	"fmt"
	"strconv"
	"strings"

	"github.com/pelletier/go-toml/v2"
	"github.com/pelletier/go-toml/v2/unstable"
)

type ConfigField struct {
	ID          string `json:"id"`
	Key         string `json:"key"`
	Section     string `json:"section"`
	Type        string `json:"type"`
	Value       string `json:"value"`
	Description string `json:"description,omitempty"`
}

type tomlFormField struct {
	ConfigField
	start, end int
	kind       unstable.Kind
}

func readTOMLFields(text string) ([]ConfigField, error) {
	entries, err := tomlFormFields(text)
	if err != nil {
		return nil, err
	}
	fields := make([]ConfigField, 0, len(entries))
	for _, entry := range entries {
		fields = append(fields, entry.ConfigField)
	}
	return fields, nil
}

func applyTOMLFields(text string, updates map[string]string) (string, error) {
	if len(updates) == 0 {
		return text, nil
	}
	entries, err := tomlFormFields(text)
	if err != nil {
		return "", err
	}
	var result strings.Builder
	cursor, matched := 0, 0
	for _, entry := range entries {
		value, exists := updates[entry.ID]
		if !exists {
			continue
		}
		matched++
		if entry.Type == "raw" {
			return "", fmt.Errorf("%s 需要在高级编辑中修改", entry.Key)
		}
		if value == entry.Value {
			continue
		}
		replacement, err := formatTOMLField(entry, value)
		if err != nil {
			key := entry.Key
			if entry.Section != "" {
				key = entry.Section + " / " + key
			}
			return "", fmt.Errorf("%s：%w", key, err)
		}
		replacement = strings.ReplaceAll(strings.ReplaceAll(replacement, "\r\n", "\n"), "\n", tomlValueNewline(text, entry))
		result.WriteString(text[cursor:entry.start])
		result.WriteString(replacement)
		cursor = entry.end
	}
	if matched != len(updates) {
		return "", errors.New("表单中的部分配置项已失效，请重新加载")
	}
	if cursor == 0 {
		return text, nil
	}
	result.WriteString(text[cursor:])
	output := result.String()
	if err := validateTOMLForm(output); err != nil {
		return "", err
	}
	return output, nil
}

func tomlFormFields(text string) ([]tomlFormField, error) {
	if err := validateTOMLForm(text); err != nil {
		return nil, err
	}
	source := strings.TrimPrefix(text, "\ufeff")
	bomLength := len(text) - len(source)
	parser := unstable.Parser{KeepComments: true}
	parser.Reset([]byte(source))

	var fields []tomlFormField
	var tablePath, tableLabel []string
	arrayTables := make(map[string]bool)
	tableIndex := 0
	for parser.NextExpression() {
		expression := parser.Expression()
		switch expression.Kind {
		case unstable.Table, unstable.ArrayTable:
			tablePath, tableLabel, _, _ = tomlKey(&parser, expression)
			if expression.Kind == unstable.ArrayTable {
				arrayTables[tomlPathID(nil, tablePath)] = true
			}
			tableIndex++
		case unstable.KeyValue:
			keys, labels, keyStart, keyEnd := tomlKey(&parser, expression)
			start := tomlValueStart(source, keyEnd)
			value := expression.Value()
			end, sensitive, err := tomlValueEnd(&parser, source, value, start)
			if err != nil {
				return nil, err
			}
			fieldKind := tomlValueType(value.Kind)
			id := tomlPathID(tablePath, keys)
			if tomlInArrayTable(tablePath, arrayTables) {
				id = "@" + strconv.Itoa(tableIndex) + id
				sensitive = true
			}
			if sensitive {
				fieldKind = "raw"
			}
			display := source[start:end]
			if fieldKind == "string" {
				display = string(value.Data)
			}
			section := strings.Join(append(append([]string(nil), tableLabel...), labels[:len(labels)-1]...), ".")
			fields = append(fields, tomlFormField{
				ConfigField: ConfigField{
					ID: id, Key: keys[len(keys)-1], Section: section, Type: fieldKind,
					Value: display, Description: tomlDescription(source, keyStart),
				},
				start: start + bomLength, end: end + bomLength, kind: value.Kind,
			})
		}
	}
	return fields, parser.Error()
}

func validateTOMLForm(text string) error {
	var document map[string]any
	err := toml.Unmarshal([]byte(strings.TrimPrefix(text, "\ufeff")), &document)
	var parseError *toml.DecodeError
	if errors.As(err, &parseError) {
		line, column := parseError.Position()
		return fmt.Errorf("TOML 第 %d 行第 %d 列：%w", line, column, err)
	}
	return err
}

func tomlKey(parser *unstable.Parser, node *unstable.Node) (keys, labels []string, start, end int) {
	parts := node.Key()
	for parts.Next() {
		part := parts.Node()
		if len(keys) == 0 {
			start = int(part.Raw.Offset)
		}
		keys = append(keys, string(part.Data))
		labels = append(labels, string(parser.Raw(part.Raw)))
		end = int(part.Raw.Offset + part.Raw.Length)
	}
	return keys, labels, start, end
}

func tomlPathID(table, keys []string) string {
	var id strings.Builder
	for _, path := range [][]string{table, keys} {
		for _, key := range path {
			id.WriteByte('/')
			id.WriteString(strings.ReplaceAll(strings.ReplaceAll(key, "~", "~0"), "/", "~1"))
		}
	}
	return id.String()
}

func tomlInArrayTable(table []string, arrays map[string]bool) bool {
	for i := 1; i <= len(table); i++ {
		if arrays[tomlPathID(nil, table[:i])] {
			return true
		}
	}
	return false
}

func tomlValueStart(source string, keyEnd int) int {
	value := strings.TrimLeft(source[keyEnd:], " \t")
	value = strings.TrimLeft(value[1:], " \t")
	return len(source) - len(value)
}

func tomlValueEnd(parser *unstable.Parser, source string, value *unstable.Node, start int) (int, bool, error) {
	if value.Kind != unstable.Array && value.Kind != unstable.InlineTable {
		raw := value.Raw
		if raw.Length == 0 {
			raw = parser.Range(value.Data)
		}
		if int(raw.Offset) != start || raw.Length == 0 {
			return 0, false, errors.New("无法识别该 TOML 值的原始位置")
		}
		return int(raw.Offset + raw.Length), false, nil
	}
	// The parser exposes complete ranges for literals. Container offsets are
	// recovered from their children so delimiters inside strings stay opaque.
	cursor := start + 1
	sensitive := value.Kind == unstable.InlineTable
	children := value.Children()
	for children.Next() {
		child := children.Node()
		if child.Kind == unstable.Comment {
			cursor = int(child.Raw.Offset + child.Raw.Length)
			sensitive = true
			continue
		}
		childStart := skipTOMLContainerTrivia(source, cursor)
		if child.Kind == unstable.KeyValue {
			_, _, _, keyEnd := tomlKey(parser, child)
			childStart, child = tomlValueStart(source, keyEnd), child.Value()
		}
		childEnd, childSensitive, err := tomlValueEnd(parser, source, child, childStart)
		if err != nil {
			return 0, false, err
		}
		cursor, sensitive = childEnd, sensitive || childSensitive
	}
	cursor = skipTOMLContainerTrivia(source, cursor)
	close := byte(']')
	if value.Kind == unstable.InlineTable {
		close = '}'
	}
	if cursor >= len(source) || source[cursor] != close {
		return 0, false, errors.New("无法识别该 TOML 容器的结束位置")
	}
	return cursor + 1, sensitive, nil
}

func skipTOMLContainerTrivia(source string, pos int) int {
	for pos < len(source) {
		switch source[pos] {
		case ' ', '\t', '\r', '\n', ',':
			pos++
		case '#':
			newline := strings.IndexByte(source[pos:], '\n')
			if newline < 0 {
				return len(source)
			}
			pos += newline + 1
		default:
			return pos
		}
	}
	return pos
}

func tomlValueType(kind unstable.Kind) string {
	switch kind {
	case unstable.Bool:
		return "boolean"
	case unstable.Integer:
		return "integer"
	case unstable.Float:
		return "float"
	case unstable.String:
		return "string"
	case unstable.Array:
		return "array"
	case unstable.LocalDate, unstable.LocalTime, unstable.LocalDateTime, unstable.DateTime:
		return "date"
	default:
		return "raw"
	}
}

func tomlDescription(source string, keyStart int) string {
	var comments []string
	end := strings.LastIndexByte(source[:keyStart], '\n')
	for end >= 0 {
		start := strings.LastIndexByte(source[:end], '\n') + 1
		line := strings.TrimSpace(source[start:end])
		if !strings.HasPrefix(line, "#") {
			break
		}
		comments = append(comments, strings.TrimSpace(strings.TrimPrefix(line, "#")))
		end = start - 1
	}
	for i, j := 0, len(comments)-1; i < j; i, j = i+1, j-1 {
		comments[i], comments[j] = comments[j], comments[i]
	}
	return strings.Join(comments, "\n")
}

func formatTOMLField(field tomlFormField, value string) (string, error) {
	const prefix = "v = "
	if field.kind == unstable.String {
		encoded, err := toml.Marshal(map[string]string{"v": value})
		if err != nil {
			return "", err
		}
		return strings.TrimSuffix(strings.TrimPrefix(string(encoded), prefix), "\n"), nil
	}
	literal := strings.TrimSpace(value)
	if field.kind == unstable.Float {
		if _, err := strconv.ParseInt(strings.ReplaceAll(literal, "_", ""), 10, 64); err == nil {
			literal += ".0"
		}
	}
	expression := prefix + literal
	if err := validateTOMLForm(expression); err != nil {
		return "", err
	}
	parser := unstable.Parser{}
	parser.Reset([]byte(expression))
	if !parser.NextExpression() {
		return "", errors.New("请输入一个有效的 TOML 值")
	}
	node := parser.Expression().Value()
	if node.Kind != field.kind {
		return "", fmt.Errorf("需要 %s 类型的 TOML 值", field.Type)
	}
	end, _, err := tomlValueEnd(&parser, expression, node, len(prefix))
	if err != nil {
		return "", err
	}
	if end != len(expression) {
		return "", errors.New("只能修改此项的值")
	}
	return literal, nil
}

func tomlValueNewline(text string, entry tomlFormField) string {
	for _, line := range []string{text[entry.start:entry.end], text[entry.end:], text[:entry.start]} {
		index := strings.IndexByte(line, '\n')
		if index >= 0 {
			if index > 0 && line[index-1] == '\r' {
				return "\r\n"
			}
			return "\n"
		}
	}
	return "\n"
}
