package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type memoryReader interface {
	ValidateRunning(context.Context, string) error
	MemoryUsage(context.Context, string) (containerMemorySample, error)
}

type dockerMemoryReader struct {
	client *http.Client
}

type containerMemorySample struct {
	ContainerID        string
	ContainerStartedAt string
	UsageBytes         uint64
}

type containerInactiveError struct {
	State string
}

func (err *containerInactiveError) Error() string {
	return "container is not running: " + err.State
}

type dockerInspectResponse struct {
	ID    string `json:"Id"`
	State struct {
		Running   bool   `json:"Running"`
		Status    string `json:"Status"`
		StartedAt string `json:"StartedAt"`
	} `json:"State"`
}

type dockerStatsResponse struct {
	MemoryStats struct {
		Usage uint64            `json:"usage"`
		Stats map[string]uint64 `json:"stats"`
	} `json:"memory_stats"`
}

func newDockerMemoryReader(socketPath string, timeout time.Duration) *dockerMemoryReader {
	transport := &http.Transport{
		DisableCompression: true,
		DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
			return (&net.Dialer{}).DialContext(ctx, "unix", socketPath)
		},
	}

	return &dockerMemoryReader{
		client: &http.Client{
			Transport: transport,
			Timeout:   timeout,
		},
	}
}

func (reader *dockerMemoryReader) MemoryUsage(ctx context.Context, containerName string) (containerMemorySample, error) {
	container, err := reader.runningContainer(ctx, containerName)
	if err != nil {
		return containerMemorySample{}, err
	}

	endpoint := dockerContainerEndpoint(containerName) + "/stats?stream=false&one-shot=true"
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return containerMemorySample{}, fmt.Errorf("create Docker stats request: %w", err)
	}

	response, err := reader.client.Do(request)
	if err != nil {
		return containerMemorySample{}, fmt.Errorf("read Docker stats for %q: %w", containerName, err)
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return containerMemorySample{}, dockerResponseError(response, containerName, "stats")
	}

	var stats dockerStatsResponse
	if err := json.NewDecoder(response.Body).Decode(&stats); err != nil {
		return containerMemorySample{}, fmt.Errorf("decode Docker stats for %q: %w", containerName, err)
	}

	return containerMemorySample{
		ContainerID:        container.ID,
		ContainerStartedAt: container.State.StartedAt,
		UsageBytes:         dockerCLICompatibleMemoryUsage(stats.MemoryStats.Usage, stats.MemoryStats.Stats),
	}, nil
}

func (reader *dockerMemoryReader) ValidateRunning(ctx context.Context, containerName string) error {
	_, err := reader.runningContainer(ctx, containerName)
	return err
}

func (reader *dockerMemoryReader) runningContainer(
	ctx context.Context,
	containerName string,
) (dockerInspectResponse, error) {
	container, err := reader.inspectContainer(ctx, containerName)
	if err != nil {
		return dockerInspectResponse{}, err
	}
	if !container.State.Running {
		return dockerInspectResponse{}, &containerInactiveError{State: container.State.Status}
	}
	return container, nil
}

func (reader *dockerMemoryReader) inspectContainer(
	ctx context.Context,
	containerName string,
) (dockerInspectResponse, error) {
	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodGet,
		dockerContainerEndpoint(containerName)+"/json",
		nil,
	)
	if err != nil {
		return dockerInspectResponse{}, fmt.Errorf("create Docker inspect request: %w", err)
	}

	response, err := reader.client.Do(request)
	if err != nil {
		return dockerInspectResponse{}, fmt.Errorf("inspect Docker container %q: %w", containerName, err)
	}
	defer response.Body.Close()

	if response.StatusCode == http.StatusNotFound {
		return dockerInspectResponse{}, &containerInactiveError{State: "not found"}
	}
	if response.StatusCode != http.StatusOK {
		return dockerInspectResponse{}, dockerResponseError(response, containerName, "inspect")
	}

	var container dockerInspectResponse
	if err := json.NewDecoder(response.Body).Decode(&container); err != nil {
		return dockerInspectResponse{}, fmt.Errorf("decode Docker inspection for %q: %w", containerName, err)
	}
	return container, nil
}

func dockerContainerEndpoint(containerName string) string {
	return "http://docker/containers/" + url.PathEscape(containerName)
}

func dockerResponseError(response *http.Response, containerName, operation string) error {
	body, _ := io.ReadAll(io.LimitReader(response.Body, 1024))
	message := strings.TrimSpace(string(body))
	if message == "" {
		message = response.Status
	}
	return fmt.Errorf("Docker %s for %q returned %s", operation, containerName, message)
}

// Docker's Linux CLI subtracts inactive file cache from cgroup memory usage.
func dockerCLICompatibleMemoryUsage(usage uint64, stats map[string]uint64) uint64 {
	cache, found := stats["total_inactive_file"]
	if !found {
		cache = stats["inactive_file"]
	}
	if cache >= usage {
		return 0
	}
	return usage - cache
}
