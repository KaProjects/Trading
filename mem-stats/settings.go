package main

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

const mebibyte = uint64(1024 * 1024)

type runtimeConfig struct {
	HTTPAddress        string
	DockerSocket       string
	ConfigPath         string
	HistoryPath        string
	SelfContainer      string
	SampleInterval     time.Duration
	CheckpointInterval time.Duration
	RequestTimeout     time.Duration
	BucketSize         uint64
}

func loadRuntimeConfig() (runtimeConfig, error) {
	selfContainer := os.Getenv("SELF_CONTAINER_NAME")
	if selfContainer == "" {
		return runtimeConfig{}, fmt.Errorf("SELF_CONTAINER_NAME is required")
	}
	if err := validateContainerName(selfContainer); err != nil {
		return runtimeConfig{}, fmt.Errorf("SELF_CONTAINER_NAME: %w", err)
	}

	sampleInterval, err := durationEnvironment("SAMPLE_INTERVAL", time.Minute)
	if err != nil {
		return runtimeConfig{}, err
	}

	checkpointInterval, err := durationEnvironment("CHECKPOINT_INTERVAL", time.Hour)
	if err != nil {
		return runtimeConfig{}, err
	}

	requestTimeout, err := durationEnvironment("DOCKER_REQUEST_TIMEOUT", 10*time.Second)
	if err != nil {
		return runtimeConfig{}, err
	}

	bucketSizeMiB, err := positiveIntegerEnvironment("BUCKET_SIZE_MIB", 10)
	if err != nil {
		return runtimeConfig{}, err
	}

	return runtimeConfig{
		HTTPAddress:        environment("HTTP_ADDRESS", ":8080"),
		DockerSocket:       environment("DOCKER_SOCKET", "/var/run/docker.sock"),
		ConfigPath:         environment("CONFIG_PATH", "/data/containers.json"),
		HistoryPath:        environment("HISTORY_PATH", "/data/history.csv"),
		SelfContainer:      selfContainer,
		SampleInterval:     sampleInterval,
		CheckpointInterval: checkpointInterval,
		RequestTimeout:     requestTimeout,
		BucketSize:         uint64(bucketSizeMiB) * mebibyte,
	}, nil
}

func environment(name, defaultValue string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return defaultValue
}

func durationEnvironment(name string, defaultValue time.Duration) (time.Duration, error) {
	value := os.Getenv(name)
	if value == "" {
		return defaultValue, nil
	}

	duration, err := time.ParseDuration(value)
	if err != nil || duration <= 0 {
		return 0, fmt.Errorf("%s must be a positive duration: %q", name, value)
	}
	return duration, nil
}

func positiveIntegerEnvironment(name string, defaultValue int) (int, error) {
	value := os.Getenv(name)
	if value == "" {
		return defaultValue, nil
	}

	number, err := strconv.Atoi(value)
	if err != nil || number <= 0 {
		return 0, fmt.Errorf("%s must be a positive integer: %q", name, value)
	}
	return number, nil
}
