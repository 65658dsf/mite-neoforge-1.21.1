package main

import "testing"

const installerPath = `D:\我的世界\MITE - 副本`
const installerPathWithSignedBytes = "D:\\\uFFE6\uFF88\uFF91\uFFE7\uFF9A\uFF84\uFFE4\uFFB8\uFF96\uFFE7\uFF95\uFF8C\\MITE - \uFFE5\uFF89\uFFAF\uFFE6\uFF9C\uFFAC"

func TestRestoreInstallerUTF8(t *testing.T) {
	for _, test := range []struct {
		name, input, want string
	}{
		{"processor path", "  Input: " + installerPathWithSignedBytes, "  Input: " + installerPath},
		{"correct UTF-8", "  Input: " + installerPath, "  Input: " + installerPath},
		{"ordinary halfwidth text", "ｶﾀｶﾅ ＡＢＣ", "ｶﾀｶﾅ ＡＢＣ"},
		{"incomplete sequence", "\uFFE6\uFF88", "\uFFE6\uFF88"},
		{"four-byte sequence", "\uFFF0\uFF9F\uFF8C\uFF8F", "🌏"},
	} {
		t.Run(test.name, func(t *testing.T) {
			if actual := restoreInstallerUTF8(test.input); actual != test.want {
				t.Fatalf("restoreInstallerUTF8(%q) = %q, expected %q", test.input, actual, test.want)
			}
		})
	}
}

func TestInstallerOutputRepairDoesNotAffectServerLogs(t *testing.T) {
	for _, test := range []struct {
		name   string
		server bool
		want   string
	}{
		{"installer", false, installerPath},
		{"server", true, installerPathWithSignedBytes},
	} {
		t.Run(test.name, func(t *testing.T) {
			session := NewSession()
			cmd, flush := session.javaCommand("unused-java.exe", t.TempDir(), nil, test.server)
			for _, chunk := range [][]byte{[]byte(installerPathWithSignedBytes[:4]), []byte(installerPathWithSignedBytes[4:] + "\n")} {
				if _, err := cmd.Stdout.Write(chunk); err != nil {
					t.Fatal(err)
				}
			}
			if _, err := cmd.Stderr.Write([]byte(installerPathWithSignedBytes)); err != nil {
				t.Fatal(err)
			}
			flush()
			logs := session.Snapshot(0).Logs
			if len(logs) != 2 || logs[0].Text != test.want || logs[1].Text != test.want {
				t.Fatalf("expected both streams to show %q, received %+v", test.want, logs)
			}
		})
	}
}
