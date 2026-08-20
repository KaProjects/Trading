package main

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

type fixedMemoryReader struct {
	value         uint64
	validationErr error
}

func (reader fixedMemoryReader) ValidateRunning(context.Context, string) error {
	return reader.validationErr
}

func (reader fixedMemoryReader) MemoryUsage(
	context.Context,
	string,
) (containerMemorySample, error) {
	return memorySample("container-id", "start-time", reader.value), nil
}

func TestAddAndDeleteEndpointsUpdateMonitorAndConfig(t *testing.T) {
	store := newConfigStore(filepath.Join(t.TempDir(), "containers.json"))
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(fixedMemoryReader{value: 120 * mebibyte}, statistics)
	application := newApplication(monitor, store, statistics)

	addRequest := httptest.NewRequest(http.MethodPost, "/add/service-a", nil)
	addResponse := httptest.NewRecorder()
	application.routes().ServeHTTP(addResponse, addRequest)

	if addResponse.Code != http.StatusNoContent {
		t.Fatalf("expected add status 204, got %d", addResponse.Code)
	}
	if !monitor.Has("service-a") {
		t.Fatal("expected container to be monitored")
	}
	containers, err := store.Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(containers) != 1 || containers[0] != "service-a" {
		t.Fatalf("expected persisted container, got %v", containers)
	}

	deleteRequest := httptest.NewRequest(http.MethodDelete, "/del/service-a", nil)
	deleteResponse := httptest.NewRecorder()
	application.routes().ServeHTTP(deleteResponse, deleteRequest)

	if deleteResponse.Code != http.StatusNoContent {
		t.Fatalf("expected delete status 204, got %d", deleteResponse.Code)
	}
	if monitor.Has("service-a") {
		t.Fatal("expected container to be removed")
	}
	records := statistics.Records()
	if len(records) != 1 || records[0].Reason != "removed" {
		t.Fatalf("expected active record to be finalized on removal, got %+v", records)
	}
}

func TestAddEndpointRejectsUnavailableContainerWithoutMutation(t *testing.T) {
	for _, state := range []string{"not found", "exited"} {
		t.Run(state, func(t *testing.T) {
			configPath := filepath.Join(t.TempDir(), "containers.json")
			store := newConfigStore(configPath)
			if err := store.Save([]string{}); err != nil {
				t.Fatal(err)
			}
			configBefore, err := os.ReadFile(configPath)
			if err != nil {
				t.Fatal(err)
			}

			statistics := &collectingStatisticsStore{}
			monitor := newTestMonitor(
				fixedMemoryReader{validationErr: &containerInactiveError{State: state}},
				statistics,
			)
			application := newApplication(monitor, store, statistics)

			request := httptest.NewRequest(http.MethodPost, "/add/missing-service", nil)
			response := httptest.NewRecorder()
			application.routes().ServeHTTP(response, request)

			if response.Code != http.StatusBadRequest {
				t.Fatalf("expected status 400, got %d", response.Code)
			}
			if monitor.Has("missing-service") {
				t.Fatal("unavailable container must not enter the monitoring pool")
			}
			configAfter, err := os.ReadFile(configPath)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(configBefore, configAfter) {
				t.Fatal("unavailable container must not change persisted configuration")
			}
			if records := statistics.Records(); len(records) != 0 {
				t.Fatalf("unavailable container must not create history, got %+v", records)
			}
		})
	}
}

func TestReportShowsStatisticsAndHistogram(t *testing.T) {
	store := newConfigStore(filepath.Join(t.TempDir(), "containers.json"))
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(
		fixedMemoryReader{value: 152 * mebibyte},
		statistics,
	)
	addTestContainer(t, monitor, "service-a", nil)
	monitor.Sample(context.Background(), "service-a")
	application := newApplication(monitor, store, statistics)

	request := httptest.NewRequest(http.MethodGet, "/", nil)
	response := httptest.NewRecorder()
	application.routes().ServeHTTP(response, request)

	body := response.Body.String()
	for _, expected := range []string{
		"service-a",
		"152.00 MiB",
		"150-&lt;160 MiB",
		"100.00%",
	} {
		if !strings.Contains(body, expected) {
			t.Errorf("expected report to contain %q", expected)
		}
	}
}

func TestHistoryShowsFinalizedRunsAndOmitsActiveCheckpoint(t *testing.T) {
	store := newConfigStore(filepath.Join(t.TempDir(), "containers.json"))
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(fixedMemoryReader{}, statistics)
	active := exampleHistoryRecord()
	if err := statistics.Checkpoint([]historyRecord{active}); err != nil {
		t.Fatal(err)
	}
	finalized := exampleHistoryRecord()
	finalized.ID = finalizedRecordID("service-a", finalized.ObservedUntil)
	finalized.Reason = "container_restarted"
	if err := statistics.Finalize(finalized); err != nil {
		t.Fatal(err)
	}
	otherActive := exampleHistoryRecord()
	otherActive.ContainerName = "still-running"
	if err := statistics.Checkpoint([]historyRecord{otherActive}); err != nil {
		t.Fatal(err)
	}

	application := newApplication(monitor, store, statistics)
	request := httptest.NewRequest(http.MethodGet, "/history", nil)
	response := httptest.NewRecorder()
	application.routes().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected history status 200, got %d", response.Code)
	}
	body := response.Body.String()
	for _, expected := range []string{
		"service-a",
		"container_restarted",
		"100.00 MiB",
		"110.00 MiB",
		"120.00 MiB",
		"50.00%",
		"href=\"/history\"",
	} {
		if !strings.Contains(body, expected) {
			t.Errorf("expected history to contain %q", expected)
		}
	}
	if strings.Contains(body, "still-running") {
		t.Error("active checkpoints must stay on the current page, not history")
	}
}

func TestHistoryShowsEmptyState(t *testing.T) {
	store := newConfigStore(filepath.Join(t.TempDir(), "containers.json"))
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(fixedMemoryReader{}, statistics)
	application := newApplication(monitor, store, statistics)

	request := httptest.NewRequest(http.MethodGet, "/history", nil)
	response := httptest.NewRecorder()
	application.routes().ServeHTTP(response, request)

	if !strings.Contains(response.Body.String(), "No completed monitoring runs") {
		t.Fatal("expected empty history message")
	}
}
