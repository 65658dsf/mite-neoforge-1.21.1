package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestBlankFolderTakesPriorityOverPreviousServer(t *testing.T) {
	portable := t.TempDir()
	executable := filepath.Join(portable, "MITE-Server-Manager.exe")
	if err := os.WriteFile(executable, nil, 0600); err != nil {
		t.Fatal(err)
	}
	previous := t.TempDir()
	if err := os.WriteFile(filepath.Join(previous, "server.properties"), nil, 0600); err != nil {
		t.Fatal(err)
	}

	if directory, target := initialServerLocation(executable, "", previous, ""); directory != "" || target != portable {
		t.Fatalf("expected the portable folder to offer a new installation, got %q, %q", directory, target)
	}
	if directory, target := initialServerLocation(executable, previous, "", ""); directory != previous || target != "" {
		t.Fatalf("expected an explicit existing server to be selected, got %q, %q", directory, target)
	}
}

func TestInstalledServerPackIsRecognizedUnderPortableFolder(t *testing.T) {
	portable := t.TempDir()
	directory := filepath.Join(portable, "MITE-Modern-ITE-0.3.8-bugfix1-server")
	if err := os.Mkdir(directory, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "server.properties"), nil, 0600); err != nil {
		t.Fatal(err)
	}

	if selected, target := resolveServerLocation(portable, "", ""); selected != directory || target != "" {
		t.Fatalf("expected the installed server folder to be selected, got %q, %q", selected, target)
	}
	if version := installedServerPackVersion(directory); version != "0.3.8-bugfix1" {
		t.Fatalf("unexpected installed version %q", version)
	}
}
