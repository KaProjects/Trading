package main

import (
	"context"
	"errors"
	"math"
	"testing"
	"time"
)

type sequenceMemoryReader struct {
	samples []containerMemorySample
	index   int
	err     error
}

func (reader *sequenceMemoryReader) ValidateRunning(context.Context, string) error {
	return nil
}

func (reader *sequenceMemoryReader) MemoryUsage(
	context.Context,
	string,
) (containerMemorySample, error) {
	if reader.err != nil {
		return containerMemorySample{}, reader.err
	}
	sample := reader.samples[reader.index]
	reader.index++
	return sample, nil
}

func TestMonitorComputesStreamingStatistics(t *testing.T) {
	reader := &sequenceMemoryReader{
		samples: []containerMemorySample{
			memorySample("container-id", "start-time", 100*mebibyte),
			memorySample("container-id", "start-time", 152*mebibyte),
			memorySample("container-id", "start-time", 80*mebibyte),
		},
	}
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(reader, statistics)
	addTestContainer(t, monitor, "service-a", nil)

	monitor.Sample(context.Background(), "service-a")
	monitor.Sample(context.Background(), "service-a")
	monitor.Sample(context.Background(), "service-a")

	container := monitor.Snapshot().Containers[0]
	if container.Count != 3 {
		t.Fatalf("expected 3 observations, got %d", container.Count)
	}
	if container.CurrentBytes != 80*mebibyte {
		t.Errorf("expected current value 80 MiB, got %d bytes", container.CurrentBytes)
	}
	if container.MinimumBytes != 80*mebibyte {
		t.Errorf("expected minimum 80 MiB, got %d bytes", container.MinimumBytes)
	}
	if container.MaximumBytes != 152*mebibyte {
		t.Errorf("expected maximum 152 MiB, got %d bytes", container.MaximumBytes)
	}

	expectedAverage := float64(332*mebibyte) / 3
	if math.Abs(container.AverageBytes-expectedAverage) > 0.001 {
		t.Errorf("expected average %.2f, got %.2f", expectedAverage, container.AverageBytes)
	}

	expectedBuckets := map[uint64]uint64{
		80 * mebibyte:  1,
		100 * mebibyte: 1,
		150 * mebibyte: 1,
	}
	for _, bucket := range container.Histogram {
		if expectedBuckets[bucket.StartBytes] != bucket.Count {
			t.Errorf("unexpected bucket %d with count %d", bucket.StartBytes, bucket.Count)
		}
		if math.Abs(bucket.Percentage-100.0/3.0) > 0.001 {
			t.Errorf("expected bucket percentage %.2f, got %.2f", 100.0/3.0, bucket.Percentage)
		}
		delete(expectedBuckets, bucket.StartBytes)
	}
	if len(expectedBuckets) != 0 {
		t.Errorf("missing histogram buckets: %v", expectedBuckets)
	}
}

func TestMonitorDoesNotCountFailedSamples(t *testing.T) {
	reader := &sequenceMemoryReader{err: errors.New("container unavailable")}
	monitor := newTestMonitor(reader, &collectingStatisticsStore{})
	addTestContainer(t, monitor, "service-a", nil)

	monitor.Sample(context.Background(), "service-a")

	container := monitor.Snapshot().Containers[0]
	if container.Count != 0 {
		t.Fatalf("expected no observations, got %d", container.Count)
	}
	if container.LastError != "container unavailable" {
		t.Errorf("expected error to be exposed, got %q", container.LastError)
	}
}

func TestMonitorArchivesAndResetsWhenContainerRestarts(t *testing.T) {
	reader := &sequenceMemoryReader{
		samples: []containerMemorySample{
			memorySample("first-id", "first-start", 100*mebibyte),
			memorySample("second-id", "second-start", 120*mebibyte),
		},
	}
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(reader, statistics)
	addTestContainer(t, monitor, "service-a", nil)

	monitor.Sample(context.Background(), "service-a")
	monitor.Sample(context.Background(), "service-a")

	records := statistics.Records()
	if len(records) != 2 {
		t.Fatalf("expected finalized and active records, got %d", len(records))
	}
	finalized := recordByReason(records, "container_restarted")
	if finalized.Count != 1 {
		t.Errorf("unexpected finalized record: %+v", finalized)
	}
	active := recordByID(records, activeRecordID("service-a"))
	if active.Count != 1 || active.CurrentBytes != 120*mebibyte {
		t.Errorf("unexpected active record: %+v", active)
	}

	current := monitor.Snapshot().Containers[0]
	if current.Count != 1 || current.CurrentBytes != 120*mebibyte {
		t.Errorf("expected reset statistics for second run, got %+v", current)
	}
}

func TestMonitorArchivesWhenContainerStops(t *testing.T) {
	reader := &changingMemoryReader{
		results: []memoryReadResult{
			{sample: memorySample("container-id", "start-time", 100*mebibyte)},
			{err: &containerInactiveError{State: "exited"}},
		},
	}
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(reader, statistics)
	addTestContainer(t, monitor, "service-a", nil)

	monitor.Sample(context.Background(), "service-a")
	monitor.Sample(context.Background(), "service-a")

	records := statistics.Records()
	if len(records) != 1 || records[0].Reason != "container_exited" {
		t.Fatalf("expected exited container to be archived, got %+v", records)
	}
	current := monitor.Snapshot().Containers[0]
	if current.Count != 0 {
		t.Errorf("expected a fresh measurement period, got %d observations", current.Count)
	}
}

func TestMonitorArchivesActiveStatisticsOnShutdown(t *testing.T) {
	reader := &sequenceMemoryReader{
		samples: []containerMemorySample{
			memorySample("container-id", "start-time", 90*mebibyte),
		},
	}
	statistics := &collectingStatisticsStore{}
	monitor := newTestMonitor(reader, statistics)
	addTestContainer(t, monitor, "self-monitor", nil)
	monitor.Sample(context.Background(), "self-monitor")

	if err := monitor.Shutdown("self-monitor"); err != nil {
		t.Fatal(err)
	}

	records := statistics.Records()
	if len(records) != 1 {
		t.Fatalf("expected one shutdown archive, got %d", len(records))
	}
	if records[0].ContainerName != "self-monitor" || records[0].Reason != "monitor_shutdown" {
		t.Errorf("unexpected shutdown archive: %+v", records[0])
	}
}

func TestMonitorRestoresAndCheckpointsActiveStatistics(t *testing.T) {
	statistics := &collectingStatisticsStore{}
	restored := historyRecord{
		ID:                 activeRecordID("service-a"),
		ContainerName:      "service-a",
		ContainerID:        "container-id",
		ContainerStartedAt: "start-time",
		ObservedFrom:       time.Now().Add(-time.Hour),
		Reason:             "active",
		Count:              2,
		CurrentBytes:       110 * mebibyte,
		MinimumBytes:       100 * mebibyte,
		MaximumBytes:       110 * mebibyte,
		AverageBytes:       float64(105 * mebibyte),
		Histogram: []histogramBucket{
			{StartBytes: 100 * mebibyte, EndBytes: 110 * mebibyte, Count: 1, Percentage: 50},
			{StartBytes: 110 * mebibyte, EndBytes: 120 * mebibyte, Count: 1, Percentage: 50},
		},
	}
	reader := &sequenceMemoryReader{
		samples: []containerMemorySample{
			memorySample("container-id", "start-time", 120*mebibyte),
		},
	}
	monitor := newTestMonitor(reader, statistics)
	addTestContainer(t, monitor, "service-a", &restored)

	monitor.Sample(context.Background(), "service-a")
	if err := monitor.Checkpoint(); err != nil {
		t.Fatal(err)
	}

	active := recordByID(statistics.Records(), activeRecordID("service-a"))
	if active.Count != 3 {
		t.Fatalf("expected 3 restored observations, got %d", active.Count)
	}
	if active.MinimumBytes != 100*mebibyte || active.MaximumBytes != 120*mebibyte {
		t.Errorf("unexpected restored range: %+v", active)
	}
}

type memoryReadResult struct {
	sample containerMemorySample
	err    error
}

type changingMemoryReader struct {
	results []memoryReadResult
	index   int
}

func (reader *changingMemoryReader) ValidateRunning(context.Context, string) error {
	return nil
}

func (reader *changingMemoryReader) MemoryUsage(
	context.Context,
	string,
) (containerMemorySample, error) {
	result := reader.results[reader.index]
	reader.index++
	return result.sample, result.err
}

func memorySample(containerID, startedAt string, usage uint64) containerMemorySample {
	return containerMemorySample{
		ContainerID:        containerID,
		ContainerStartedAt: startedAt,
		UsageBytes:         usage,
	}
}

func newTestMonitor(reader memoryReader, store statisticsStore) *monitor {
	return newMonitor(reader, store, time.Minute, time.Hour, 10*mebibyte)
}

func addTestContainer(
	t *testing.T,
	monitor *monitor,
	containerName string,
	restored *historyRecord,
) {
	t.Helper()
	if _, err := monitor.Add(containerName, restored); err != nil {
		t.Fatal(err)
	}
}

func recordByID(records []historyRecord, id string) historyRecord {
	for _, record := range records {
		if record.ID == id {
			return record
		}
	}
	return historyRecord{}
}

func recordByReason(records []historyRecord, reason string) historyRecord {
	for _, record := range records {
		if record.Reason == reason {
			return record
		}
	}
	return historyRecord{}
}
