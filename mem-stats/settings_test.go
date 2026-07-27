package main

import "testing"

func TestRuntimeConfigRequiresSelfContainerName(t *testing.T) {
	t.Setenv("SELF_CONTAINER_NAME", "")

	if _, err := loadRuntimeConfig(); err == nil {
		t.Fatal("expected missing SELF_CONTAINER_NAME to be rejected")
	}
}

func TestRuntimeConfigUsesConfiguredSelfContainerName(t *testing.T) {
	t.Setenv("SELF_CONTAINER_NAME", "self-monitor")

	config, err := loadRuntimeConfig()
	if err != nil {
		t.Fatal(err)
	}
	if config.SelfContainer != "self-monitor" {
		t.Errorf("expected configured self container, got %q", config.SelfContainer)
	}
}
