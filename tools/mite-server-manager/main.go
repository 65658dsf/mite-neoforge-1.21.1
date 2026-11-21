package main

import (
	"embed"
	"fmt"
	"io/fs"
	"os"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	"github.com/wailsapp/wails/v2/pkg/options/windows"
)

//go:embed web/*.html web/*.css web/*.js
var files embed.FS

func main() {
	assets, err := fs.Sub(files, "web")
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}
	app := newApp(os.Args[1:])
	err = wails.Run(&options.App{
		Title: app.text("MITE 服务端管理器", "MITE Server Manager"), Width: 1216, Height: 810, MinWidth: 790, MinHeight: 565,
		BackgroundColour: options.NewRGB(16, 22, 20),
		AssetServer:      &assetserver.Options{Assets: assets},
		OnStartup:        app.startup,
		OnBeforeClose:    app.beforeClose,
		Bind:             []interface{}{app},
		Windows:          &windows.Options{Theme: windows.Dark},
	})
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
	}
}
