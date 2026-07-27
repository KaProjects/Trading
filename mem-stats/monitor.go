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
	StartedAt      time.Time
	SampleInterval time.Duration
	BucketSize     uint64
	Containers     []containerSnapshot
}

type monitor struct {
	reader         memoryReader
	history        historyWriter
	sampleInterval time.Duration
	bucketSize     uint64
	startedAt      time.Time

	mu      sync.RWMutex
	metrics map[string]*memorySeries
}

func newMonitor(
	reader memoryReader,
	history historyWriter,
	sampleInterval time.Duration,
	bucketSize uint64,
) *monitor {
	return &monitor{
		reader:         reader,
		history:        history,
		sampleInterval: sampleInterval,
		bucketSize:     bucketSize,
		startedAt:      time.Now(),
		metrics:        make(map[string]*memorySeries),
	}
}

func (monitor *monitor) Add(containerName string) bool {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	if _, exists := monitor.metrics[containerName]; exists {
		return false
	}

	monitor.metrics[containerName] = &memorySeries{
		Name:           containerName,
		StartedAt:      time.Now(),
		HistogramCount: make(map[uint64]uint64),
	}
	return true
}

func (monitor *monitor) Remove(containerName string) (bool, error) {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	series, exists := monitor.metrics[containerName]
	if !exists {
		return false, nil
	}
	if err := monitor.archiveSeries(series, "removed", time.Now()); err != nil {
		return false, err
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

	ticker := time.NewTicker(monitor.sampleInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			monitor.sampleAll(ctx)
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
		if errors.As(err, &inactiveError) && series.Count > 0 {
			reason := "container_" + normalizeReason(inactiveError.State)
			if archiveErr := monitor.archiveSeries(series, reason, now); archiveErr != nil {
				series.LastError = archiveErr.Error()
				series.LastErrorAt = now
				return
			}
			resetMemorySeries(series, now)
		}
		series.LastError = err.Error()
		series.LastErrorAt = now
		return
	}

	runIdentity := sample.ContainerID + "|" + sample.ContainerStartedAt
	if series.RunIdentity != "" && series.RunIdentity != runIdentity {
		if archiveErr := monitor.archiveSeries(series, "container_restarted", now); archiveErr != nil {
			series.LastError = archiveErr.Error()
			series.LastErrorAt = now
			return
		}
		resetMemorySeries(series, now)
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
		StartedAt:      monitor.startedAt,
		SampleInterval: monitor.sampleInterval,
		BucketSize:     monitor.bucketSize,
		Containers:     make([]containerSnapshot, 0, len(monitor.metrics)),
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
			Histogram:    make([]histogramBucket, 0, len(series.HistogramCount)),
		}

		for index, count := range series.HistogramCount {
			percentage := 0.0
			if series.Count > 0 {
				percentage = float64(count) / float64(series.Count) * 100
			}
			container.Histogram = append(container.Histogram, histogramBucket{
				StartBytes: index * monitor.bucketSize,
				EndBytes:   (index + 1) * monitor.bucketSize,
				Count:      count,
				Percentage: percentage,
			})
		}
		sort.Slice(container.Histogram, func(i, j int) bool {
			return container.Histogram[i].StartBytes < container.Histogram[j].StartBytes
		})
		snapshot.Containers = append(snapshot.Containers, container)
	}

	sort.Slice(snapshot.Containers, func(i, j int) bool {
		return snapshot.Containers[i].Name < snapshot.Containers[j].Name
	})
	return snapshot
}

func (monitor *monitor) ArchiveAll(reason string) error {
	monitor.mu.Lock()
	defer monitor.mu.Unlock()

	var archiveErrors []error
	endedAt := time.Now()
	for _, series := range monitor.metrics {
		if series.Count == 0 {
			continue
		}
		if err := monitor.archiveSeries(series, reason, endedAt); err != nil {
			archiveErrors = append(archiveErrors, err)
		}
	}
	return errors.Join(archiveErrors...)
}

func (monitor *monitor) archiveSeries(series *memorySeries, reason string, endedAt time.Time) error {
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

	record := historyRecord{
		ID:                 series.Name + "-" + endedAt.UTC().Format("20060102-150405"),
		ContainerName:      series.Name,
		ContainerID:        series.ContainerID,
		ContainerStartedAt: series.ContainerStart,
		ObservedFrom:       series.StartedAt,
		ObservedUntil:      endedAt,
		Reason:             reason,
		Count:              series.Count,
		CurrentBytes:       series.CurrentBytes,
		MinimumBytes:       series.MinimumBytes,
		MaximumBytes:       series.MaximumBytes,
		AverageBytes:       series.AverageBytes,
		Histogram:          histogram,
	}
	if err := monitor.history.Append(record); err != nil {
		return fmt.Errorf("archive statistics for %q: %w", series.Name, err)
	}
	log.Printf("archived statistics for %q: %s", series.Name, reason)
	return nil
}

func resetMemorySeries(series *memorySeries, startedAt time.Time) {
	name := series.Name
	*series = memorySeries{
		Name:           name,
		StartedAt:      startedAt,
		HistogramCount: make(map[uint64]uint64),
	}
}

func normalizeReason(reason string) string {
	reason = strings.ToLower(strings.TrimSpace(reason))
	reason = strings.NewReplacer(" ", "_", "-", "_", "/", "_").Replace(reason)
	if reason == "" {
		return "unavailable"
	}
	return reason
}
