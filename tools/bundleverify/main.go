// Command bundleverify checks a runtime bundle against its digests file.
//
// Run this before uploading a release asset. A checksum that is wrong in the
// manifest is the one bug that reaches every user at once, because the installer
// refuses to extract and there is nothing they can do about it.
//
//	go run . -dir ./out -manifest ../../app/src/main/res/raw/bootstrap_manifest.json
package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

type component struct {
	ID       string `json:"id"`
	File     string `json:"file"`
	Bytes    int64  `json:"bytes"`
	SHA256   string `json:"sha256"`
	Required bool   `json:"required"`
}

type manifest struct {
	SchemaVersion int         `json:"schemaVersion"`
	Components    []component `json:"components"`
}

func hashFile(path string) (string, int64, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", 0, err
	}
	defer f.Close()

	h := sha256.New()
	n, err := io.Copy(h, f)
	if err != nil {
		return "", 0, err
	}
	return hex.EncodeToString(h.Sum(nil)), n, nil
}

func main() {
	dir := flag.String("dir", ".", "directory containing the built bundle archives")
	manifestPath := flag.String("manifest", "", "path to bootstrap_manifest.json")
	flag.Parse()

	raw, err := os.ReadFile(*manifestPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "cannot read manifest: %v\n", err)
		os.Exit(2)
	}

	var m manifest
	if err := json.Unmarshal(raw, &m); err != nil {
		fmt.Fprintf(os.Stderr, "cannot parse manifest: %v\n", err)
		os.Exit(2)
	}

	failures := 0
	seen := map[string]bool{}

	for _, c := range m.Components {
		seen[c.File] = true
		path := filepath.Join(*dir, c.File)

		digest, size, err := hashFile(path)
		if err != nil {
			fmt.Printf("MISSING  %-42s %v\n", c.File, err)
			failures++
			continue
		}

		problems := []string{}
		if digest != strings.ToLower(c.SHA256) {
			problems = append(problems, fmt.Sprintf("sha256 %s != %s", digest, c.SHA256))
		}
		if size != c.Bytes {
			problems = append(problems, fmt.Sprintf("size %d != %d", size, c.Bytes))
		}

		if len(problems) > 0 {
			fmt.Printf("FAIL     %-42s %s\n", c.File, strings.Join(problems, "; "))
			failures++
		} else {
			fmt.Printf("ok       %-42s %d bytes\n", c.File, size)
		}
	}

	// Anything on disk that the manifest does not mention is either a leftover or a
	// file somebody forgot to list, and both are worth knowing about.
	entries, _ := os.ReadDir(*dir)
	extras := []string{}
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".tar.gz") {
			continue
		}
		if !seen[entry.Name()] {
			extras = append(extras, entry.Name())
		}
	}
	sort.Strings(extras)
	for _, name := range extras {
		fmt.Printf("EXTRA    %-42s not referenced by the manifest\n", name)
	}

	fmt.Printf("\n%d component(s), %d failure(s)\n", len(m.Components), failures)
	if failures > 0 {
		os.Exit(1)
	}
}
