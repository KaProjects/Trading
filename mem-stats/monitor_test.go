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
	history := &collectingHistoryWriter{}
	monitor := newMonitor(reader, history, time.Minute, 10*mebibyte)
	monitor.Add("service-a")

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
	monitor := newMonitor(reader, &collectingHistoryWriter{}, time.Minute, 10*mebibyte)
	monitor.Add("service-a")

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
	history := &collectingHistoryWriter{}
	monitor := newMonitor(reader, history, time.Minute, 10*mebibyte)
	monitor.Add("service-a")

	monitor.Sample(context.Background(), "service-a")
	monitor.Sample(context.Background(), "service-a")

	records := history.Records()
	if len(records) != 1 {
		t.Fatalf("expected one archived run, got %d", len(records))
	}
	if records[0].Reason != "container_restarted" || records[0].Count != 1 {
		t.Errorf("unexpected archived record: %+v", records[0])
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
	history := &collectingHistoryWriter{}
	monitor := newMonitor(reader, history, time.Minute, 10*mebibyte)
	monitor.Add("service-a")

	monitor.Sample(context.Background(), "service-a")
	monitor.Sample(context.Background(), "service-a")

	records := history.Records()
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
	history := &collectingHistoryWriter{}
	monitor := newMonitor(reader, history, time.Minute, 10*mebibyte)
	monitor.Add("self-monitor")
	monitor.Sample(context.Background(), "self-monitor")

	if err := monitor.ArchiveAll("monitor_shutdown"); err != nil {
		t.Fatal(err)
	}

	records := history.Records()
	if len(records) != 1 {
		t.Fatalf("expected one shutdown archive, got %d", len(records))
	}
	if records[0].ContainerName != "self-monitor" || records[0].Reason != "monitor_shutdown" {
		t.Errorf("unexpected shutdown archive: %+v", records[0])
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
