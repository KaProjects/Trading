package main

import (
	"encoding/csv"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestCSVStatisticsStoreRewritesAndRestoresActiveRecord(t *testing.T) {
	path := filepath.Join(t.TempDir(), "data", "history.csv")
	store := newCSVStatisticsStore(path)
	record := exampleHistoryRecord()
	record.ID = activeRecordID(record.ContainerName)
	record.Reason = "active"

	if err := store.Checkpoint([]historyRecord{record}); err != nil {
		t.Fatal(err)
	}
	record.Count = 3
	record.CurrentBytes = 130 * mebibyte
	record.MaximumBytes = 130 * mebibyte
	if err := store.Checkpoint([]historyRecord{record}); err != nil {
		t.Fatal(err)
	}

	rows := readCSV(t, path)
	if len(rows) != 2 {
		t.Fatalf("expected one rewritten active record, got %d data rows", len(rows)-1)
	}
	if rows[1][0] != activeRecordID("service-a") || rows[1][8] != "3" {
		t.Errorf("unexpected active row: %v", rows[1])
	}

	reloaded := newCSVStatisticsStore(path)
	active, err := reloaded.LoadActive()
	if err != nil {
		t.Fatal(err)
	}
	if active["service-a"].Count != 3 || active["service-a"].MaximumBytes != 130*mebibyte {
		t.Errorf("unexpected restored statistics: %+v", active["service-a"])
	}
}

func TestCSVStatisticsStoreFinalizesOldRunAndCreatesNewActiveRecord(t *testing.T) {
	path := filepath.Join(t.TempDir(), "history.csv")
	store := newCSVStatisticsStore(path)
	record := exampleHistoryRecord()

	if err := store.Checkpoint([]historyRecord{record}); err != nil {
		t.Fatal(err)
	}

	finishedAt := time.Date(2026, time.July, 1, 11, 30, 0, 0, time.UTC)
	record.ID = finalizedRecordID(record.ContainerName, finishedAt)
	record.ObservedUntil = finishedAt
	record.Reason = "container_exited"
	if err := store.Finalize(record); err != nil {
		t.Fatal(err)
	}

	newRun := exampleHistoryRecord()
	newRun.ContainerID = "new-container-id"
	newRun.ContainerStartedAt = "2026-07-01T12:00:00Z"
	newRun.Count = 1
	if err := store.Checkpoint([]historyRecord{newRun}); err != nil {
		t.Fatal(err)
	}

	rows := readCSV(t, path)
	if len(rows) != 3 {
		t.Fatalf("expected finalized and active records, got %d data rows", len(rows)-1)
	}

	ids := map[string]bool{rows[1][0]: true, rows[2][0]: true}
	if !ids["service-a-202607011130"] || !ids[activeRecordID("service-a")] {
		t.Errorf("unexpected record identities: %v", ids)
	}
}

func TestCSVStatisticsStoreDoesNotOverwriteSameMinuteFinalRecord(t *testing.T) {
	path := filepath.Join(t.TempDir(), "history.csv")
	store := newCSVStatisticsStore(path)
	record := exampleHistoryRecord()
	finishedAt := time.Date(2026, time.July, 1, 11, 30, 0, 0, time.UTC)

	if err := store.Checkpoint([]historyRecord{record}); err != nil {
		t.Fatal(err)
	}
	record.ID = finalizedRecordID(record.ContainerName, finishedAt)
	record.Reason = "container_exited"
	if err := store.Finalize(record); err != nil {
		t.Fatal(err)
	}

	if err := store.Checkpoint([]historyRecord{record}); err != nil {
		t.Fatal(err)
	}
	if err := store.Finalize(record); err != nil {
		t.Fatal(err)
	}

	rows := readCSV(t, path)
	ids := map[string]bool{rows[1][0]: true, rows[2][0]: true}
	if !ids["service-a-202607011130"] || !ids["service-a-202607011130-2"] {
		t.Errorf("expected both same-minute records, got %v", ids)
	}
}

func TestCSVStatisticsStoreMigratesLegacyRowsOnCheckpoint(t *testing.T) {
	path := filepath.Join(t.TempDir(), "history.csv")
	file, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	writer := csv.NewWriter(file)
	if err := writer.Write(legacyHistoryHeader); err != nil {
		t.Fatal(err)
	}
	if err := writer.Write([]string{
		"service-a-20260701-110000",
		"service-a",
		"container-id",
		"2026-07-01T09:55:00Z",
		"2026-07-01T10:00:00Z",
		"2026-07-01T11:00:00Z",
		"container_exited",
		"2",
		"120.00",
		"100.00",
		"120.00",
		"110.00",
		`[{"range_mib":"100-<110","count":2,"percentage":100}]`,
	}); err != nil {
		t.Fatal(err)
	}
	writer.Flush()
	if err := writer.Error(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}

	store := newCSVStatisticsStore(path)
	if _, err := store.LoadActive(); err != nil {
		t.Fatal(err)
	}
	if err := store.Checkpoint([]historyRecord{exampleHistoryRecord()}); err != nil {
		t.Fatal(err)
	}

	rows := readCSV(t, path)
	if len(rows[0]) != len(historyHeader) {
		t.Fatalf("expected current header after migration, got %v", rows[0])
	}
	if len(rows) != 3 {
		t.Fatalf("expected legacy and active records, got %d data rows", len(rows)-1)
	}
}

func exampleHistoryRecord() historyRecord {
	observedFrom := time.Date(2026, time.July, 1, 10, 0, 0, 0, time.UTC)
	return historyRecord{
		ID:                 activeRecordID("service-a"),
		ContainerName:      "service-a",
		ContainerID:        "container-id",
		ContainerStartedAt: "2026-07-01T09:55:00Z",
		ObservedFrom:       observedFrom,
		ObservedUntil:      observedFrom.Add(time.Hour),
		LastSampleAt:       observedFrom.Add(55 * time.Minute),
		Reason:             "active",
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
}

func readCSV(t *testing.T, path string) [][]string {
	t.Helper()

	file, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()

	rows, err := csv.NewReader(file).ReadAll()
	if err != nil {
		t.Fatal(err)
	}
	return rows
}
