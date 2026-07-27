package main

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"time"
)

var historyHeader = []string{
	"id",
	"container_name",
	"container_id",
	"container_started_at",
	"observed_from",
	"observed_until",
	"reason",
	"observations",
	"current_mib",
	"minimum_mib",
	"maximum_mib",
	"average_mib",
	"histogram",
}

type historyRecord struct {
	ID                 string
	ContainerName      string
	ContainerID        string
	ContainerStartedAt string
	ObservedFrom       time.Time
	ObservedUntil      time.Time
	Reason             string
	Count              uint64
	CurrentBytes       uint64
	MinimumBytes       uint64
	MaximumBytes       uint64
	AverageBytes       float64
	Histogram          []histogramBucket
}

type historyWriter interface {
	Append(historyRecord) error
}

type csvHistoryWriter struct {
	path string
	mu   sync.Mutex
}

type historyHistogramEntry struct {
	Range      string  `json:"range_mib"`
	Count      uint64  `json:"count"`
	Percentage float64 `json:"percentage"`
}

func newCSVHistoryWriter(path string) *csvHistoryWriter {
	return &csvHistoryWriter{path: path}
}

func (writer *csvHistoryWriter) Append(record historyRecord) error {
	writer.mu.Lock()
	defer writer.mu.Unlock()

	directory := filepath.Dir(writer.path)
	if err := os.MkdirAll(directory, 0755); err != nil {
		return fmt.Errorf("create history directory %q: %w", directory, err)
	}

	file, err := os.OpenFile(writer.path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("open history file %q: %w", writer.path, err)
	}
	defer file.Close()

	info, err := file.Stat()
	if err != nil {
		return fmt.Errorf("inspect history file %q: %w", writer.path, err)
	}

	csvWriter := csv.NewWriter(file)
	if info.Size() == 0 {
		if err := csvWriter.Write(historyHeader); err != nil {
			return fmt.Errorf("write history header: %w", err)
		}
	}
	if err := csvWriter.Write(historyRow(record)); err != nil {
		return fmt.Errorf("write history record: %w", err)
	}
	csvWriter.Flush()
	if err := csvWriter.Error(); err != nil {
		return fmt.Errorf("flush history record: %w", err)
	}
	if err := file.Sync(); err != nil {
		return fmt.Errorf("sync history file %q: %w", writer.path, err)
	}
	return nil
}

func historyRow(record historyRecord) []string {
	histogramEntries := make([]historyHistogramEntry, 0, len(record.Histogram))
	for _, bucket := range record.Histogram {
		histogramEntries = append(histogramEntries, historyHistogramEntry{
			Range: fmt.Sprintf(
				"%.0f-<%.0f",
				float64(bucket.StartBytes)/float64(mebibyte),
				float64(bucket.EndBytes)/float64(mebibyte),
			),
			Count:      bucket.Count,
			Percentage: bucket.Percentage,
		})
	}
	histogram, _ := json.Marshal(histogramEntries)
	row := []string{
		record.ID,
		record.ContainerName,
		record.ContainerID,
		record.ContainerStartedAt,
		formatTime(record.ObservedFrom),
		formatTime(record.ObservedUntil),
		record.Reason,
		strconv.FormatUint(record.Count, 10),
		"",
		"",
		"",
		"",
		string(histogram),
	}
	if record.Count > 0 {
		row[8] = formatDecimalMiB(float64(record.CurrentBytes))
		row[9] = formatDecimalMiB(float64(record.MinimumBytes))
		row[10] = formatDecimalMiB(float64(record.MaximumBytes))
		row[11] = formatDecimalMiB(record.AverageBytes)
	}
	return row
}

func formatDecimalMiB(bytes float64) string {
	return strconv.FormatFloat(bytes/float64(mebibyte), 'f', 2, 64)
}
