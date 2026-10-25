package main

import (
	"encoding/csv"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
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
	"last_sample_at",
	"reason",
	"observations",
	"current_mib",
	"minimum_mib",
	"maximum_mib",
	"average_mib",
	"histogram",
}

var legacyHistoryHeader = []string{
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
	LastSampleAt       time.Time
	Reason             string
	Count              uint64
	CurrentBytes       uint64
	MinimumBytes       uint64
	MaximumBytes       uint64
	AverageBytes       float64
	Histogram          []histogramBucket
}

type statisticsStore interface {
	LoadActive() (map[string]historyRecord, error)
	Checkpoint([]historyRecord) error
	Finalize(historyRecord) error
}

type historyReader interface {
	ListFinalized() ([]historyRecord, error)
}

type csvStatisticsStore struct {
	path string

	mu      sync.Mutex
	loaded  bool
	records map[string]historyRecord
}

type historyHistogramEntry struct {
	StartMiB   uint64  `json:"start_mib"`
	EndMiB     uint64  `json:"end_mib"`
	Count      uint64  `json:"count"`
	Percentage float64 `json:"percentage"`
}

type legacyHistoryHistogramEntry struct {
	Range      string  `json:"range_mib"`
	Count      uint64  `json:"count"`
	Percentage float64 `json:"percentage"`
}

func newCSVStatisticsStore(path string) *csvStatisticsStore {
	return &csvStatisticsStore{path: path}
}

func (store *csvStatisticsStore) LoadActive() (map[string]historyRecord, error) {
	store.mu.Lock()
	defer store.mu.Unlock()

	if err := store.loadLocked(); err != nil {
		return nil, err
	}

	active := make(map[string]historyRecord)
	for _, record := range store.records {
		if record.ID == activeRecordID(record.ContainerName) {
			active[record.ContainerName] = record
		}
	}
	return active, nil
}

func (store *csvStatisticsStore) ListFinalized() ([]historyRecord, error) {
	store.mu.Lock()
	defer store.mu.Unlock()

	if err := store.loadLocked(); err != nil {
		return nil, err
	}

	records := make([]historyRecord, 0, len(store.records))
	for _, record := range store.records {
		if record.ID == activeRecordID(record.ContainerName) {
			continue
		}
		records = append(records, cloneHistoryRecord(record))
	}
	sort.Slice(records, func(left, right int) bool {
		if records[left].ObservedUntil.Equal(records[right].ObservedUntil) {
			return records[left].ID > records[right].ID
		}
		return records[left].ObservedUntil.After(records[right].ObservedUntil)
	})
	return records, nil
}

func (store *csvStatisticsStore) Checkpoint(records []historyRecord) error {
	store.mu.Lock()
	defer store.mu.Unlock()

	if err := store.loadLocked(); err != nil {
		return err
	}
	previous := cloneHistoryRecords(store.records)
	for _, record := range records {
		record.ID = activeRecordID(record.ContainerName)
		record.Reason = "active"
		store.records[record.ID] = record
	}
	if err := store.writeLocked(); err != nil {
		store.records = previous
		return err
	}
	return nil
}

func (store *csvStatisticsStore) Finalize(record historyRecord) error {
	store.mu.Lock()
	defer store.mu.Unlock()

	if err := store.loadLocked(); err != nil {
		return err
	}

	previous := cloneHistoryRecords(store.records)
	delete(store.records, activeRecordID(record.ContainerName))
	record.ID = store.availableFinalIDLocked(record.ID)
	store.records[record.ID] = record
	if err := store.writeLocked(); err != nil {
		store.records = previous
		return err
	}
	return nil
}

func (store *csvStatisticsStore) loadLocked() error {
	if store.loaded {
		return nil
	}
	store.records = make(map[string]historyRecord)

	file, err := os.Open(store.path)
	if errors.Is(err, os.ErrNotExist) {
		store.loaded = true
		return nil
	}
	if err != nil {
		return fmt.Errorf("open statistics file %q: %w", store.path, err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	header, err := reader.Read()
	if errors.Is(err, io.EOF) {
		store.loaded = true
		return nil
	}
	if err != nil {
		return fmt.Errorf("read statistics header: %w", err)
	}
	currentFormat := strings.Join(header, ",") == strings.Join(historyHeader, ",")
	legacyFormat := strings.Join(header, ",") == strings.Join(legacyHistoryHeader, ",")
	if !currentFormat && !legacyFormat {
		return fmt.Errorf("statistics file %q has an unsupported header", store.path)
	}

	for {
		row, err := reader.Read()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return fmt.Errorf("read statistics record: %w", err)
		}
		var record historyRecord
		if currentFormat {
			record, err = parseHistoryRow(row)
		} else {
			record, err = parseLegacyHistoryRow(row)
		}
		if err != nil {
			return fmt.Errorf("parse statistics record: %w", err)
		}
		store.records[record.ID] = record
	}

	store.loaded = true
	return nil
}

func (store *csvStatisticsStore) writeLocked() error {
	directory := filepath.Dir(store.path)
	if err := os.MkdirAll(directory, 0755); err != nil {
		return fmt.Errorf("create statistics directory %q: %w", directory, err)
	}

	tempFile, err := os.CreateTemp(directory, ".history-*.csv")
	if err != nil {
		return fmt.Errorf("create temporary statistics file: %w", err)
	}
	tempPath := tempFile.Name()
	defer os.Remove(tempPath)

	if err := tempFile.Chmod(0644); err != nil {
		tempFile.Close()
		return fmt.Errorf("set statistics file permissions: %w", err)
	}

	writer := csv.NewWriter(tempFile)
	if err := writer.Write(historyHeader); err != nil {
		tempFile.Close()
		return fmt.Errorf("write statistics header: %w", err)
	}

	ids := make([]string, 0, len(store.records))
	for id := range store.records {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	for _, id := range ids {
		if err := writer.Write(historyRow(store.records[id])); err != nil {
			tempFile.Close()
			return fmt.Errorf("write statistics record: %w", err)
		}
	}
	writer.Flush()
	if err := writer.Error(); err != nil {
		tempFile.Close()
		return fmt.Errorf("flush statistics file: %w", err)
	}
	if err := tempFile.Sync(); err != nil {
		tempFile.Close()
		return fmt.Errorf("sync statistics file: %w", err)
	}
	if err := tempFile.Close(); err != nil {
		return fmt.Errorf("close statistics file: %w", err)
	}
	if err := os.Rename(tempPath, store.path); err != nil {
		return fmt.Errorf("replace statistics file %q: %w", store.path, err)
	}
	return nil
}

func (store *csvStatisticsStore) availableFinalIDLocked(requestedID string) string {
	if _, exists := store.records[requestedID]; !exists {
		return requestedID
	}
	for suffix := 2; ; suffix++ {
		candidate := requestedID + "-" + strconv.Itoa(suffix)
		if _, exists := store.records[candidate]; !exists {
			return candidate
		}
	}
}

func cloneHistoryRecords(records map[string]historyRecord) map[string]historyRecord {
	clone := make(map[string]historyRecord, len(records))
	for id, record := range records {
		clone[id] = cloneHistoryRecord(record)
	}
	return clone
}

func cloneHistoryRecord(record historyRecord) historyRecord {
	record.Histogram = append([]histogramBucket(nil), record.Histogram...)
	return record
}

func activeRecordID(containerName string) string {
	return containerName + "-active"
}

func finalizedRecordID(containerName string, endedAt time.Time) string {
	return containerName + "-" + endedAt.UTC().Format("200601021504")
}

func historyRow(record historyRecord) []string {
	histogramEntries := make([]historyHistogramEntry, 0, len(record.Histogram))
	for _, bucket := range record.Histogram {
		histogramEntries = append(histogramEntries, historyHistogramEntry{
			StartMiB:   bucket.StartBytes / mebibyte,
			EndMiB:     bucket.EndBytes / mebibyte,
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
		formatOptionalCSVTime(record.LastSampleAt),
		record.Reason,
		strconv.FormatUint(record.Count, 10),
		"",
		"",
		"",
		"",
		string(histogram),
	}
	if record.Count > 0 {
		row[9] = formatDecimalMiB(float64(record.CurrentBytes))
		row[10] = formatDecimalMiB(float64(record.MinimumBytes))
		row[11] = formatDecimalMiB(float64(record.MaximumBytes))
		row[12] = formatDecimalMiB(record.AverageBytes)
	}
	return row
}

func parseHistoryRow(row []string) (historyRecord, error) {
	if len(row) != len(historyHeader) {
		return historyRecord{}, fmt.Errorf("expected %d columns, got %d", len(historyHeader), len(row))
	}

	record := historyRecord{
		ID:                 row[0],
		ContainerName:      row[1],
		ContainerID:        row[2],
		ContainerStartedAt: row[3],
		Reason:             row[7],
	}

	var err error
	if record.ObservedFrom, err = time.Parse(time.RFC3339, row[4]); err != nil {
		return historyRecord{}, fmt.Errorf("observed_from: %w", err)
	}
	if record.ObservedUntil, err = time.Parse(time.RFC3339, row[5]); err != nil {
		return historyRecord{}, fmt.Errorf("observed_until: %w", err)
	}
	if row[6] != "" {
		if record.LastSampleAt, err = time.Parse(time.RFC3339, row[6]); err != nil {
			return historyRecord{}, fmt.Errorf("last_sample_at: %w", err)
		}
	}
	if record.Count, err = strconv.ParseUint(row[8], 10, 64); err != nil {
		return historyRecord{}, fmt.Errorf("observations: %w", err)
	}
	if record.Count > 0 {
		if record.CurrentBytes, err = parseMiB(row[9]); err != nil {
			return historyRecord{}, fmt.Errorf("current_mib: %w", err)
		}
		if record.MinimumBytes, err = parseMiB(row[10]); err != nil {
			return historyRecord{}, fmt.Errorf("minimum_mib: %w", err)
		}
		if record.MaximumBytes, err = parseMiB(row[11]); err != nil {
			return historyRecord{}, fmt.Errorf("maximum_mib: %w", err)
		}
		if record.AverageBytes, err = parseAverageMiB(row[12]); err != nil {
			return historyRecord{}, fmt.Errorf("average_mib: %w", err)
		}
	}

	var histogram []historyHistogramEntry
	if err := json.Unmarshal([]byte(row[13]), &histogram); err != nil {
		return historyRecord{}, fmt.Errorf("histogram: %w", err)
	}
	for _, bucket := range histogram {
		record.Histogram = append(record.Histogram, histogramBucket{
			StartBytes: bucket.StartMiB * mebibyte,
			EndBytes:   bucket.EndMiB * mebibyte,
			Count:      bucket.Count,
			Percentage: bucket.Percentage,
		})
	}
	return record, nil
}

func parseLegacyHistoryRow(row []string) (historyRecord, error) {
	if len(row) != len(legacyHistoryHeader) {
		return historyRecord{}, fmt.Errorf(
			"expected %d legacy columns, got %d",
			len(legacyHistoryHeader),
			len(row),
		)
	}

	var legacyHistogram []legacyHistoryHistogramEntry
	if err := json.Unmarshal([]byte(row[12]), &legacyHistogram); err != nil {
		return historyRecord{}, fmt.Errorf("legacy histogram: %w", err)
	}
	histogram := make([]historyHistogramEntry, 0, len(legacyHistogram))
	for _, bucket := range legacyHistogram {
		start, end, err := parseLegacyRange(bucket.Range)
		if err != nil {
			return historyRecord{}, err
		}
		histogram = append(histogram, historyHistogramEntry{
			StartMiB:   start,
			EndMiB:     end,
			Count:      bucket.Count,
			Percentage: bucket.Percentage,
		})
	}
	histogramJSON, _ := json.Marshal(histogram)

	return parseHistoryRow([]string{
		row[0],
		row[1],
		row[2],
		row[3],
		row[4],
		row[5],
		"",
		row[6],
		row[7],
		row[8],
		row[9],
		row[10],
		row[11],
		string(histogramJSON),
	})
}

func parseLegacyRange(value string) (uint64, uint64, error) {
	startValue, endValue, found := strings.Cut(value, "-<")
	if !found {
		return 0, 0, fmt.Errorf("invalid legacy histogram range %q", value)
	}
	start, err := strconv.ParseUint(startValue, 10, 64)
	if err != nil {
		return 0, 0, fmt.Errorf("legacy histogram range start: %w", err)
	}
	end, err := strconv.ParseUint(endValue, 10, 64)
	if err != nil {
		return 0, 0, fmt.Errorf("legacy histogram range end: %w", err)
	}
	return start, end, nil
}

func parseMiB(value string) (uint64, error) {
	number, err := strconv.ParseFloat(value, 64)
	if err != nil {
		return 0, err
	}
	return uint64(number * float64(mebibyte)), nil
}

func parseAverageMiB(value string) (float64, error) {
	number, err := strconv.ParseFloat(value, 64)
	if err != nil {
		return 0, err
	}
	return number * float64(mebibyte), nil
}

func formatDecimalMiB(bytes float64) string {
	return strconv.FormatFloat(bytes/float64(mebibyte), 'f', 2, 64)
}

func formatOptionalCSVTime(value time.Time) string {
	if value.IsZero() {
		return ""
	}
	return formatTime(value)
}
