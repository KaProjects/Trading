package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

var containerNamePattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$`)

type containerConfig struct {
	Containers []string `json:"containers"`
}

type configStore struct {
	path string
}

func newConfigStore(path string) *configStore {
	return &configStore{path: path}
}

func (store *configStore) Load(defaultContainers ...string) ([]string, error) {
	data, err := os.ReadFile(store.path)
	if errors.Is(err, os.ErrNotExist) {
		if err := store.Save(defaultContainers); err != nil {
			return nil, err
		}
		return normalizeContainerNames(defaultContainers)
	}
	if err != nil {
		return nil, fmt.Errorf("read container config %q: %w", store.path, err)
	}
	if len(strings.TrimSpace(string(data))) == 0 {
		return []string{}, nil
	}

	var config containerConfig
	if err := json.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("decode container config %q: %w", store.path, err)
	}

	containers, err := normalizeContainerNames(config.Containers)
	if err != nil {
		return nil, fmt.Errorf("validate container config %q: %w", store.path, err)
	}
	return containers, nil
}

func (store *configStore) Save(containers []string) error {
	containers, err := normalizeContainerNames(containers)
	if err != nil {
		return err
	}

	directory := filepath.Dir(store.path)
	if err := os.MkdirAll(directory, 0755); err != nil {
		return fmt.Errorf("create config directory %q: %w", directory, err)
	}

	tempFile, err := os.CreateTemp(directory, ".containers-*.json")
	if err != nil {
		return fmt.Errorf("create temporary container config: %w", err)
	}
	tempPath := tempFile.Name()
	defer os.Remove(tempPath)

	if err := tempFile.Chmod(0644); err != nil {
		tempFile.Close()
		return fmt.Errorf("set temporary container config permissions: %w", err)
	}

	encoder := json.NewEncoder(tempFile)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(containerConfig{Containers: containers}); err != nil {
		tempFile.Close()
		return fmt.Errorf("encode container config: %w", err)
	}
	if err := tempFile.Close(); err != nil {
		return fmt.Errorf("close temporary container config: %w", err)
	}
	if err := os.Rename(tempPath, store.path); err != nil {
		return fmt.Errorf("replace container config %q: %w", store.path, err)
	}
	return nil
}

func normalizeContainerNames(containers []string) ([]string, error) {
	unique := make(map[string]struct{}, len(containers))
	for _, container := range containers {
		name := strings.TrimSpace(container)
		if err := validateContainerName(name); err != nil {
			return nil, err
		}
		unique[name] = struct{}{}
	}

	normalized := make([]string, 0, len(unique))
	for name := range unique {
		normalized = append(normalized, name)
	}
	sort.Strings(normalized)
	return normalized, nil
}

func validateContainerName(containerName string) error {
	if !containerNamePattern.MatchString(containerName) {
		return fmt.Errorf("invalid container name %q", containerName)
	}
	return nil
}
