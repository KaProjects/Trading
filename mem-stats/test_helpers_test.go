package main

import "sync"

type collectingHistoryWriter struct {
	mu      sync.Mutex
	records []historyRecord
	err     error
}

func (writer *collectingHistoryWriter) Append(record historyRecord) error {
	writer.mu.Lock()
	defer writer.mu.Unlock()

	if writer.err != nil {
		return writer.err
	}
	writer.records = append(writer.records, record)
	return nil
}

func (writer *collectingHistoryWriter) Records() []historyRecord {
	writer.mu.Lock()
	defer writer.mu.Unlock()

	records := make([]historyRecord, len(writer.records))
	copy(records, writer.records)
	return records
}
