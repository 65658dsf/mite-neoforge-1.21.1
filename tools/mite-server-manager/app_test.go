package main

import (
	"context"
	"os"
	"path/filepath"
	"slices"
	"testing"
)

func TestManagerLocksDifficultyOnOpenAndSave(t *testing.T) {
	t.Setenv("LOCALAPPDATA", t.TempDir())
	directory := t.TempDir()
	path := filepath.Join(directory, "server.properties")
	original := "# Dedicated server\r\nmotd=My server\r\ndifficulty=1\r\n"
	if err := os.WriteFile(path, []byte(original), 0600); err != nil {
		t.Fatal(err)
	}
	app := newApp([]string{"--server", directory})
	app.startup(context.Background())

	onDisk, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if want := "# Dedicated server\r\nmotd=My server\r\ndifficulty=hard\r\n"; string(onDisk) != want {
		t.Fatalf("opening the manager wrote %q, want %q", onDisk, want)
	}
	config, err := app.ReadConfig("server")
	if err != nil {
		t.Fatal(err)
	}
	form, err := app.SaveProperties(map[string]string{"difficulty": "peaceful", "max-players": "8"}, config.Revision)
	if err != nil {
		t.Fatal(err)
	}
	if form.Properties["difficulty"] != "hard" || form.Properties["max-players"] != "8" {
		t.Fatalf("unexpected form save: %v", form.Properties)
	}

	raw, err := app.SaveConfig("server", "difficulty:normal\nmotd=Raw edit\ndiffi\\u0063ulty:easy\n", form.Revision)
	if err != nil {
		t.Fatal(err)
	}
	if raw.Properties["difficulty"] != "hard" || raw.Properties["motd"] != "Raw edit" {
		t.Fatalf("unexpected raw save: %v", raw.Properties)
	}
	raw, err = app.SaveConfig("server", "motd=Removed difficulty\n", raw.Revision)
	if err != nil {
		t.Fatal(err)
	}
	if raw.Properties["difficulty"] != "hard" {
		t.Fatalf("removing the difficulty did not restore Hard: %v", raw.Properties)
	}
}

func TestManagerLeavesEmptyInstallFolderEmptyOnOpen(t *testing.T) {
	t.Setenv("LOCALAPPDATA", t.TempDir())
	directory := t.TempDir()
	app := newApp([]string{"--server", directory})
	app.startup(context.Background())
	entries, err := os.ReadDir(directory)
	if err != nil {
		t.Fatal(err)
	}
	if target := app.GetRuntime().DownloadTarget; target != directory || len(entries) != 0 {
		t.Fatalf("expected an empty download target at %q, got %q with %v", directory, target, entries)
	}
}

func TestCachedJavaScanSurvivesPreferenceChangesAndReopening(t *testing.T) {
	t.Setenv("LOCALAPPDATA", t.TempDir())
	app, otherWindow := newApp(nil), newApp(nil)
	if app.GetRuntime().JavaScan != nil {
		t.Fatal("a fresh profile should have no scan results")
	}

	path := filepath.Join(t.TempDir(), "java.exe")
	if err := os.WriteFile(path, nil, 0600); err != nil {
		t.Fatal(err)
	}
	want := []JavaInstallation{{Path: path, Version: "21.0.10", Architecture: "x64", Compatible: true}}
	if err := saveUserJSON(app.javaScanPath(), want); err != nil {
		t.Fatal(err)
	}
	if err := otherWindow.SetLanguage("en"); err != nil {
		t.Fatal(err)
	}

	reopened := newApp(nil)
	if got := reopened.GetRuntime(); !slices.Equal(got.JavaScan, want) || got.Language != "en" {
		t.Fatalf("scan results or separate language preferences were lost: %+v", got)
	}
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	if got := reopened.GetRuntime().JavaScan; got == nil || len(got) != 0 {
		t.Fatalf("a scan with no remaining Java installations should be empty, got %v", got)
	}
}
