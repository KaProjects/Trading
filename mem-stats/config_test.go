package main

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestConfigStoreCreatesAndPersistsNormalizedConfiguration(t *testing.T) {
	path := filepath.Join(t.TempDir(), "data", "containers.json")
	store := newConfigStore(path)

	containers, err := store.Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(containers) != 0 {
		t.Fatalf("expected empty initial config, got %v", containers)
	}
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("expected config file to be created: %v", err)
	}

	if err := store.Save([]string{"service-b", "service-a", "service-a"}); err != nil {
		t.Fatal(err)
	}
	containers, err = store.Load()
	if err != nil {
		t.Fatal(err)
	}

	expected := []string{"service-a", "service-b"}
	if !reflect.DeepEqual(containers, expected) {
		t.Errorf("expected %v, got %v", expected, containers)
	}
}

func TestConfigStoreUsesDefaultOnlyWhenFileDoesNotExist(t *testing.T) {
	path := filepath.Join(t.TempDir(), "containers.json")
	store := newConfigStore(path)

	containers, err := store.Load("self-monitor")
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(containers, []string{"self-monitor"}) {
		t.Fatalf("expected self-monitor default, got %v", containers)
	}

	if err := store.Save([]string{}); err != nil {
		t.Fatal(err)
	}
	containers, err = store.Load("self-monitor")
	if err != nil {
		t.Fatal(err)
	}
	if len(containers) != 0 {
		t.Fatalf("expected explicitly empty config to remain empty, got %v", containers)
	}
}

func TestConfigStoreRejectsInvalidContainerNames(t *testing.T) {
	store := newConfigStore(filepath.Join(t.TempDir(), "containers.json"))

	if err := store.Save([]string{"../../docker.sock"}); err == nil {
		t.Fatal("expected invalid container name to be rejected")
	}
}
