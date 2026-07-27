package main

import (
	"context"
	"errors"
	"io"
	"net/http"
	"strings"
	"testing"
)

type roundTripFunction func(*http.Request) (*http.Response, error)

func (function roundTripFunction) RoundTrip(request *http.Request) (*http.Response, error) {
	return function(request)
}

func TestDockerMemoryReaderReturnsContainerIdentityAndMemory(t *testing.T) {
	reader := &dockerMemoryReader{
		client: &http.Client{
			Transport: roundTripFunction(func(request *http.Request) (*http.Response, error) {
				switch {
				case strings.HasSuffix(request.URL.Path, "/json"):
					return jsonResponse(http.StatusOK, `{
						"Id": "container-id",
						"State": {
							"Running": true,
							"Status": "running",
							"StartedAt": "2026-07-01T10:00:00Z"
						}
					}`), nil
				case strings.HasSuffix(request.URL.Path, "/stats"):
					return jsonResponse(http.StatusOK, `{
						"memory_stats": {
							"usage": 314572800,
							"stats": {"inactive_file": 20971520}
						}
					}`), nil
				default:
					t.Fatalf("unexpected Docker request: %s", request.URL)
					return nil, nil
				}
			}),
		},
	}

	sample, err := reader.MemoryUsage(context.Background(), "service-a")
	if err != nil {
		t.Fatal(err)
	}
	if sample.ContainerID != "container-id" {
		t.Errorf("expected container identity, got %q", sample.ContainerID)
	}
	if sample.UsageBytes != 280*mebibyte {
		t.Errorf("expected 280 MiB, got %d bytes", sample.UsageBytes)
	}
}

func TestDockerMemoryReaderIdentifiesStoppedContainer(t *testing.T) {
	reader := &dockerMemoryReader{
		client: &http.Client{
			Transport: roundTripFunction(func(*http.Request) (*http.Response, error) {
				return jsonResponse(http.StatusOK, `{
					"Id": "container-id",
					"State": {"Running": false, "Status": "exited"}
				}`), nil
			}),
		},
	}

	_, err := reader.MemoryUsage(context.Background(), "service-a")
	var inactiveError *containerInactiveError
	if !errors.As(err, &inactiveError) {
		t.Fatalf("expected inactive container error, got %v", err)
	}
	if inactiveError.State != "exited" {
		t.Errorf("expected exited state, got %q", inactiveError.State)
	}
}

func jsonResponse(status int, body string) *http.Response {
	return &http.Response{
		StatusCode: status,
		Status:     http.StatusText(status),
		Body:       io.NopCloser(strings.NewReader(body)),
		Header:     make(http.Header),
	}
}

func TestDockerCLICompatibleMemoryUsageUsesCgroupV1Cache(t *testing.T) {
	actual := dockerCLICompatibleMemoryUsage(300*mebibyte, map[string]uint64{
		"total_inactive_file": 40 * mebibyte,
		"inactive_file":       20 * mebibyte,
	})

	if actual != 260*mebibyte {
		t.Errorf("expected 260 MiB, got %d bytes", actual)
	}
}

func TestDockerCLICompatibleMemoryUsageUsesCgroupV2Cache(t *testing.T) {
	actual := dockerCLICompatibleMemoryUsage(300*mebibyte, map[string]uint64{
		"inactive_file": 20 * mebibyte,
	})

	if actual != 280*mebibyte {
		t.Errorf("expected 280 MiB, got %d bytes", actual)
	}
}

func TestDockerCLICompatibleMemoryUsageDoesNotUnderflow(t *testing.T) {
	actual := dockerCLICompatibleMemoryUsage(10, map[string]uint64{
		"inactive_file": 20,
	})

	if actual != 0 {
		t.Errorf("expected zero, got %d", actual)
	}
}
