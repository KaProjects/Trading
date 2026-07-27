package main

import (
	"encoding/csv"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestCSVHistoryWriterAppendsHeaderAndStatistics(t *testing.T) {
	path := filepath.Join(t.TempDir(), "data", "history.csv")
	writer := newCSVHistoryWriter(path)
	observedFrom := time.Date(2026, time.July, 1, 10, 0, 0, 0, time.UTC)
	observedUntil := observedFrom.Add(time.Hour)

	record := historyRecord{
		ID:                 "service-a-20260701-110000",
		ContainerName:      "service-a",
		ContainerID:        "container-id",
		ContainerStartedAt: "2026-07-01T09:55:00Z",
		ObservedFrom:       observedFrom,
		ObservedUntil:      observedUntil,
		Reason:             "container_exited",
		Count:              2,
		CurrentBytes:       120 * mebibyte,
		MinimumBytes:       100 * mebibyte,
		MaximumBytes:       120 * mebibyte,
		AverageBytes:       float64(110 * mebibyte),
		Histogram: []histogramBucket{
			{StartBytes: 100 * mebibyte, EndBytes: 110 * mebibyte, Count: 1, Percentage: 50},
			{StartBytes: 120 * mebibyte, EndBytes: 130 * mebibyte, Count: 1, Percentage: 50},
		},
	}

	if err := writer.Append(record); err != nil {
		t.Fatal(err)
	}

	file, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()

	rows, err := csv.NewReader(file).ReadAll()
	if err != nil {
		t.Fatal(err)
	}
	if len(rows) != 2 {
		t.Fatalf("expected header and one record, got %d rows", len(rows))
	}
	if rows[1][0] != record.ID || rows[1][6] != "container_exited" {
		t.Errorf("unexpected history row: %v", rows[1])
	}
	if rows[1][9] != "100.00" || rows[1][10] != "120.00" || rows[1][11] != "110.00" {
		t.Errorf("unexpected memory statistics: %v", rows[1][9:12])
	}
}
