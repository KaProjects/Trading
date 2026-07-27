package main

import (
	"testing"
	"time"
)

func TestFinalizeOrphanedRecordsKeepsConfiguredActiveRecords(t *testing.T) {
	store := &collectingStatisticsStore{}
	activeRecords := map[string]historyRecord{
		"service-a": {
			ID:            activeRecordID("service-a"),
			ContainerName: "service-a",
			ObservedFrom:  time.Now().Add(-time.Hour),
			Reason:        "active",
		},
		"service-b": {
			ID:            activeRecordID("service-b"),
			ContainerName: "service-b",
			ObservedFrom:  time.Now().Add(-time.Hour),
			Reason:        "active",
		},
	}
	if err := store.Checkpoint([]historyRecord{
		activeRecords["service-a"],
		activeRecords["service-b"],
	}); err != nil {
		t.Fatal(err)
	}

	if err := finalizeOrphanedRecords(store, activeRecords, []string{"service-a"}); err != nil {
		t.Fatal(err)
	}

	records := store.Records()
	if recordByID(records, activeRecordID("service-a")).ContainerName != "service-a" {
		t.Fatal("expected configured active record to remain")
	}
	if recordByReason(records, "not_monitored").ContainerName != "service-b" {
		t.Fatalf("expected orphaned record to be finalized, got %+v", records)
	}
}
