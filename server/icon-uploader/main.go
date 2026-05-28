package main

import (
	"errors"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var packageFile = regexp.MustCompile(`^[A-Za-z0-9_.-]+\.png$`)

func main() {
	root := env("ICON_ROOT", "/data/icons")
	token := os.Getenv("ICON_UPLOAD_TOKEN")
	if err := os.MkdirAll(root, 0o755); err != nil {
		log.Fatal(err)
	}

	http.HandleFunc("/icons/", func(w http.ResponseWriter, r *http.Request) {
		name, err := cleanName(r.URL.Path)
		if err != nil {
			http.Error(w, "bad icon path", http.StatusBadRequest)
			return
		}
		path := filepath.Join(root, name)

		switch r.Method {
		case http.MethodGet, http.MethodHead:
			w.Header().Set("Cache-Control", "public, max-age=604800, immutable")
			http.ServeFile(w, r, path)
		case http.MethodPut:
			if token == "" || r.Header.Get("Authorization") != "Bearer "+token {
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return
			}
			if ct := r.Header.Get("Content-Type"); !strings.HasPrefix(ct, "image/png") {
				http.Error(w, "png only", http.StatusUnsupportedMediaType)
				return
			}
			f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
			if err != nil {
				http.Error(w, "write failed", http.StatusInternalServerError)
				return
			}
			defer f.Close()
			if _, err := io.Copy(f, http.MaxBytesReader(w, r.Body, 512*1024)); err != nil {
				http.Error(w, "upload failed", http.StatusBadRequest)
				return
			}
			w.WriteHeader(http.StatusNoContent)
		default:
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	})

	addr := env("LISTEN_ADDR", ":8080")
	log.Printf("icon uploader listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, nil))
}

func cleanName(path string) (string, error) {
	name := strings.TrimPrefix(path, "/icons/")
	if name == "" || strings.Contains(name, "/") || !packageFile.MatchString(name) {
		return "", errors.New("invalid name")
	}
	return name, nil
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
