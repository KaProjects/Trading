package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"sort"
	"strings"
	"sync"
	"time"
)

type memorySeries struct {
	Name           string
	StartedAt      time.Time
	LastSampleAt   time.Time
	LastErrorAt    time.Time
	LastError      string
	ContainerID    string
	ContainerStart string
	RunIdentity    string
	ActiveRecord   bool
	Count          uint64
	CurrentBytes   uint64
	MinimumBytes   uint64
	MaximumBytes   uint64
	AverageBytes   float64
	HistogramCount map[uint64]uint64
}

type histogramBucket struct {
	StartBytes uint64
	EndBytes   uint64
	Count      uint64
	Percentage float64
}

type containerSnapshot struct {
	Name         string
	StartedAt    time.Time
	LastSampleAt time.Time
	LastErrorAt  time.Time
	LastError    string
	ContainerID  string
	Count        uint64
	CurrentBytes uint64
	MinimumBytes uint64
	MaximumBytes uint64
	AverageBytes float64
	Histogram    []histogramBucket
}

type monitorSnapshot struct {
	StartedAt          time.Time
	SampleInterval     time.Duration
	CheckpointInterval time.Duration
	BucketSize         uint64
	Containers         []containerSnapshot
}

type monitor struct {
	reader             memoryReader
	store              statisticsStore
	sampleInterval     time.Duration
	checkpointInterval time.Duration
	bucketSize         uint64
	startedAt          time.Time

	mu      sync.RWMutex
	metrics map[string]*memorySeries
}

func newMonitor(
	reader memoryReader,
	store statisticsStore,
	sampleInterval time.Duration,
	checkpointInterval time.Duration,
	bucketSize uint64,
) *monitor {
	return &monitor{
		reader:             reader,
		store:              store,
		sampleInterval:     sampleInterval,
		checkpointInterval: checkpointInterval,
		bucketSize:         bucketSize,
		startedAt:          time.Now(),
		metrics:            make(map[string]*memorySeries),
	}
}

func (monitor *monitor) Add(containerName string, restored *historyRecord) (bool, error) {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	if _, exists := monitor.metrics[containerName]; exists {
		return false, nil
	}

	series := newMemorySeries(containerName, time.Now(), true)
	if restored != nil {
		series = monitor.restoreMemorySeries(*restored)
	}

	if err := monitor.store.Checkpoint([]historyRecord{
		monitor.activeHistoryRecord(series, time.Now()),
	}); err != nil {
		return false, fmt.Errorf("checkpoint statistics for %q: %w", containerName, err)
	}

	monitor.metrics[containerName] = series
	return true, nil
}

func (monitor *monitor) Remove(containerName string) (bool, error) {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	series, exists := monitor.metrics[containerName]
	if !exists {
		return false, nil
	}
	if series.ActiveRecord {
		if err := monitor.finalizeSeries(series, "removed", time.Now()); err != nil {
			return false, err
		}
	}
	delete(monitor.metrics, containerName)
	return true, nil
}

func (monitor *monitor) Has(containerName string) bool {
	monitor.mu.RLock()
	defer monitor.mu.RUnlock()

	_, exists := monitor.metrics[containerName]
	return exists
}

func (monitor *monitor) ValidateRunning(ctx context.Context, containerName string) error {
	return monitor.reader.ValidateRunning(ctx, containerName)
}

func (monitor *monitor) Names() []string {
	monitor.mu.RLock()
	defer monitor.mu.RUnlock()

	names := make([]string, 0, len(monitor.metrics))
	for name := range monitor.metrics {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func (monitor *monitor) Run(ctx context.Context) {
	monitor.sampleAll(ctx)

	sampleTicker := time.NewTicker(monitor.sampleInterval)
	checkpointTicker := time.NewTicker(monitor.checkpointInterval)
	defer sampleTicker.Stop()
	defer checkpointTicker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-sampleTicker.C:
			monitor.sampleAll(ctx)
		case <-checkpointTicker.C:
			if err := monitor.Checkpoint(); err != nil {
				log.Printf("statistics checkpoint failed: %v", err)
			}
		}
	}
}

func (monitor *monitor) Sample(ctx context.Context, containerName string) {
	monitor.mu.RLock()
	expectedSeries, exists := monitor.metrics[containerName]
	monitor.mu.RUnlock()
	if !exists {
		return
	}

	sample, err := monitor.reader.MemoryUsage(ctx, containerName)
	now := time.Now()

	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	series, exists := monitor.metrics[containerName]
	if !exists || series != expectedSeries {
		return
	}

	if err != nil {
		var inactiveError *containerInactiveError
		if errors.As(err, &inactiveError) && series.ActiveRecord {
			reason := "container_" + normalizeReason(inactiveError.State)
			if finalizeErr := monitor.finalizeSeries(series, reason, now); finalizeErr != nil {
				series.LastError = finalizeErr.Error()
				series.LastErrorAt = now
				return
			}
			resetMemorySeries(series, now, false)
		}
		series.LastError = err.Error()
		series.LastErrorAt = now
		return
	}

	runIdentity := sample.ContainerID + "|" + sample.ContainerStartedAt
	checkpointAfterSample := series.Count == 0

	if series.ActiveRecord && series.RunIdentity != "" && series.RunIdentity != runIdentity {
		if finalizeErr := monitor.finalizeSeries(series, "container_restarted", now); finalizeErr != nil {
			series.LastError = finalizeErr.Error()
			series.LastErrorAt = now
			return
		}
		resetMemorySeries(series, now, true)
		checkpointAfterSample = true
	} else if !series.ActiveRecord {
		resetMemorySeries(series, now, true)
		checkpointAfterSample = true
	}

	series.ContainerID = sample.ContainerID
	series.ContainerStart = sample.ContainerStartedAt
	series.RunIdentity = runIdentity
	series.LastError = ""
	series.LastSampleAt = now
	series.CurrentBytes = sample.UsageBytes
	if series.Count == 0 {
		series.MinimumBytes = sample.UsageBytes
		series.MaximumBytes = sample.UsageBytes
	} else {
		if sample.UsageBytes < series.MinimumBytes {
			series.MinimumBytes = sample.UsageBytes
		}
		if sample.UsageBytes > series.MaximumBytes {
			series.MaximumBytes = sample.UsageBytes
		}
	}

	series.Count++
	series.AverageBytes += (float64(sample.UsageBytes) - series.AverageBytes) / float64(series.Count)
	series.HistogramCount[sample.UsageBytes/monitor.bucketSize]++

	if checkpointAfterSample {
		if checkpointErr := monitor.store.Checkpoint([]historyRecord{
			monitor.activeHistoryRecord(series, now),
		}); checkpointErr != nil {
			series.LastError = "checkpoint statistics: " + checkpointErr.Error()
			series.LastErrorAt = now
		}
	}
}

func (monitor *monitor) Checkpoint() error {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	now := time.Now()
	records := make([]historyRecord, 0, len(monitor.metrics))
	for _, series := range monitor.metrics {
		if series.ActiveRecord {
			records = append(records, monitor.activeHistoryRecord(series, now))
		}
	}
	if len(records) == 0 {
		return nil
	}
	if err := monitor.store.Checkpoint(records); err != nil {
		return fmt.Errorf("write active statistics: %w", err)
	}
	return nil
}

func (monitor *monitor) Shutdown(selfContainerName string) error {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	now := time.Now()
	var shutdownErrors []error
	var activeTargets []historyRecord

	for _, series := range monitor.metrics {
		if !series.ActiveRecord {
			continue
		}
		if series.Name == selfContainerName {
			if err := monitor.finalizeSeries(series, "monitor_shutdown", now); err != nil {
				shutdownErrors = append(shutdownErrors, err)
			}
			continue
		}
		activeTargets = append(activeTargets, monitor.activeHistoryRecord(series, now))
	}

	if len(activeTargets) > 0 {
		if err := monitor.store.Checkpoint(activeTargets); err != nil {
			shutdownErrors = append(shutdownErrors, fmt.Errorf("checkpoint targets during shutdown: %w", err))
		}
	}
	return errors.Join(shutdownErrors...)
}

func (monitor *monitor) sampleAll(ctx context.Context) {
	names := monitor.Names()
	var waitGroup sync.WaitGroup
	waitGroup.Add(len(names))

	for _, name := range names {
		go func(containerName string) {
			defer waitGroup.Done()
			monitor.Sample(ctx, containerName)
		}(name)
	}

	waitGroup.Wait()
}

func (monitor *monitor) Snapshot() monitorSnapshot {
	monitor.mu.RLock()
	defer monitor.mu.RUnlock()

	snapshot := monitorSnapshot{
		StartedAt:          monitor.startedAt,
		SampleInterval:     monitor.sampleInterval,
		CheckpointInterval: monitor.checkpointInterval,
		BucketSize:         monitor.bucketSize,
		Containers:         make([]containerSnapshot, 0, len(monitor.metrics)),
	}

	for _, series := range monitor.metrics {
		container := containerSnapshot{
			Name:         series.Name,
			StartedAt:    series.StartedAt,
			LastSampleAt: series.LastSampleAt,
			LastErrorAt:  series.LastErrorAt,
			LastError:    series.LastError,
			ContainerID:  series.ContainerID,
			Count:        series.Count,
			CurrentBytes: series.CurrentBytes,
			MinimumBytes: series.MinimumBytes,
			MaximumBytes: series.MaximumBytes,
			AverageBytes: series.AverageBytes,
			Histogram:    monitor.histogram(series),
		}
		snapshot.Containers = append(snapshot.Containers, container)
	}

	sort.Slice(snapshot.Containers, func(i, j int) bool {
		return snapshot.Containers[i].Name < snapshot.Containers[j].Name
	})
	return snapshot
}

func (monitor *monitor) finalizeSeries(
	series *memorySeries,
	reason string,
	endedAt time.Time,
) error {
	record := monitor.historyRecord(series, reason, endedAt)
	record.ID = finalizedRecordID(series.Name, endedAt)
	if err := monitor.store.Finalize(record); err != nil {
		return fmt.Errorf("finalize statistics for %q: %w", series.Name, err)
	}
	series.ActiveRecord = false
	log.Printf("finalized statistics for %q: %s", series.Name, reason)
	return nil
}

func (monitor *monitor) activeHistoryRecord(
	series *memorySeries,
	checkpointAt time.Time,
) historyRecord {
	record := monitor.historyRecord(series, "active", checkpointAt)
	record.ID = activeRecordID(series.Name)
	return record
}

func (monitor *monitor) historyRecord(
	series *memorySeries,
	reason string,
	observedUntil time.Time,
) historyRecord {
	return historyRecord{
		ContainerName:      series.Name,
		ContainerID:        series.ContainerID,
		ContainerStartedAt: series.ContainerStart,
		ObservedFrom:       series.StartedAt,
		ObservedUntil:      observedUntil,
		LastSampleAt:       series.LastSampleAt,
		Reason:             reason,
		Count:              series.Count,
		CurrentBytes:       series.CurrentBytes,
		MinimumBytes:       series.MinimumBytes,
		MaximumBytes:       series.MaximumBytes,
		AverageBytes:       series.AverageBytes,
		Histogram:          monitor.histogram(series),
	}
}

func (monitor *monitor) histogram(series *memorySeries) []histogramBucket {
	histogram := make([]histogramBucket, 0, len(series.HistogramCount))
	for index, count := range series.HistogramCount {
		percentage := 0.0
		if series.Count > 0 {
			percentage = float64(count) / float64(series.Count) * 100
		}
		histogram = append(histogram, histogramBucket{
			StartBytes: index * monitor.bucketSize,
			EndBytes:   (index + 1) * monitor.bucketSize,
			Count:      count,
			Percentage: percentage,
		})
	}
	sort.Slice(histogram, func(i, j int) bool {
		return histogram[i].StartBytes < histogram[j].StartBytes
	})
	return histogram
}

func (monitor *monitor) restoreMemorySeries(record historyRecord) *memorySeries {
	series := newMemorySeries(record.ContainerName, record.ObservedFrom, true)
	series.LastSampleAt = record.LastSampleAt
	series.ContainerID = record.ContainerID
	series.ContainerStart = record.ContainerStartedAt
	if record.ContainerID != "" || record.ContainerStartedAt != "" {
		series.RunIdentity = record.ContainerID + "|" + record.ContainerStartedAt
	}
	series.Count = record.Count
	series.CurrentBytes = record.CurrentBytes
	series.MinimumBytes = record.MinimumBytes
	series.MaximumBytes = record.MaximumBytes
	series.AverageBytes = record.AverageBytes
	for _, bucket := range record.Histogram {
		series.HistogramCount[bucket.StartBytes/monitor.bucketSize] += bucket.Count
	}
	return series
}

func newMemorySeries(containerName string, startedAt time.Time, active bool) *memorySeries {
	return &memorySeries{
		Name:           containerName,
		StartedAt:      startedAt,
		ActiveRecord:   active,
		HistogramCount: make(map[uint64]uint64),
	}
}

func resetMemorySeries(series *memorySeries, startedAt time.Time, active bool) {
	name := series.Name
	*series = *newMemorySeries(name, startedAt, active)
}

func normalizeReason(reason string) string {
	reason = strings.ToLower(strings.TrimSpace(reason))
	reason = strings.NewReplacer(" ", "_", "-", "_", "/", "_").Replace(reason)
	if reason == "" {
		return "unavailable"
	}
	return reason
}
