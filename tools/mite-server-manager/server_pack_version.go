// Author: NingMeng123
package main

import (
	"cmp"
	"regexp"
	"strings"
)

var serverPackVersionSuffix = regexp.MustCompile(
	`^(dev|snapshot|alpha|beta|pre|preview|rc|bugfix|hotfix|patch|post)(?:[.-]?([0-9]+(?:[.-][0-9]+)*))?$`)

var serverPackSuffixRank = map[string]int{
	"dev": -5, "snapshot": -5, "alpha": -4, "beta": -3, "pre": -2, "preview": -2, "rc": -1,
	"bugfix": 1, "hotfix": 1, "patch": 1, "post": 1,
}

// compareServerPackVersions only authorizes an update when its result is
// positive. Unrecognized same-base suffixes have no safe order.
func compareServerPackVersions(a, b string) int {
	if !validServerPackVersion(a) || !validServerPackVersion(b) {
		return 0
	}
	aBase, aSuffix, _ := strings.Cut(a, "-")
	bBase, bSuffix, _ := strings.Cut(b, "-")
	if order := compareServerPackVersionNumbers(strings.Split(aBase, "."), strings.Split(bBase, ".")); order != 0 {
		return order
	}

	aRank, aRevision, aKnown := parseServerPackSuffix(aSuffix)
	bRank, bRevision, bKnown := parseServerPackSuffix(bSuffix)
	if !aKnown || !bKnown {
		return 0
	}
	if order := cmp.Compare(aRank, bRank); order != 0 {
		return order
	}
	return compareServerPackVersionNumbers(aRevision, bRevision)
}

func parseServerPackSuffix(suffix string) (rank int, parts []string, known bool) {
	if suffix == "" {
		return 0, nil, true
	}
	match := serverPackVersionSuffix.FindStringSubmatch(strings.ToLower(suffix))
	if match == nil {
		return 0, nil, false
	}
	parts = strings.FieldsFunc(match[2], func(r rune) bool { return r == '.' || r == '-' })
	return serverPackSuffixRank[match[1]], parts, true
}

func compareServerPackVersionNumbers(a, b []string) int {
	for i := range max(len(a), len(b)) {
		var left, right string
		if i < len(a) {
			left = strings.TrimLeft(a[i], "0")
		}
		if i < len(b) {
			right = strings.TrimLeft(b[i], "0")
		}
		if order := cmp.Compare(len(left), len(right)); order != 0 {
			return order
		}
		if order := strings.Compare(left, right); order != 0 {
			return order
		}
	}
	return 0
}
