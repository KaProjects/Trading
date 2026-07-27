package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
)

type fixedMemoryReader struct {
	value uint64
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
	application := newApplication(monitor, store)

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

func TestReportShowsStatisticsAndHistogram(t *testing.T) {
	store := newConfigStore(filepath.Join(t.TempDir(), "containers.json"))
	monitor := newTestMonitor(
		fixedMemoryReader{value: 152 * mebibyte},
		&collectingStatisticsStore{},
	)
	addTestContainer(t, monitor, "service-a", nil)
	monitor.Sample(context.Background(), "service-a")
	application := newApplication(monitor, store)

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
