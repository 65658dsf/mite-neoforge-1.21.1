package main

import (
	"os"
	"path/filepath"
	"slices"
	"testing"
)

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
