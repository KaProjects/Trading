package main

import "sync"

type collectingStatisticsStore struct {
	mu      sync.Mutex
	records map[string]historyRecord
	err     error
}

func (store *collectingStatisticsStore) LoadActive() (map[string]historyRecord, error) {
	store.mu.Lock()
	defer store.mu.Unlock()

	if store.err != nil {
		return nil, store.err
	}
	store.initialize()
	active := make(map[string]historyRecord)
	for _, record := range store.records {
		if record.ID == activeRecordID(record.ContainerName) {
			active[record.ContainerName] = record
		}
	}
	return active, nil
}

func (store *collectingStatisticsStore) Checkpoint(records []historyRecord) error {
	store.mu.Lock()
	defer store.mu.Unlock()

	if store.err != nil {
		return store.err
	}
	store.initialize()
	for _, record := range records {
		record.ID = activeRecordID(record.ContainerName)
		record.Reason = "active"
		store.records[record.ID] = record
	}
	return nil
}

func (store *collectingStatisticsStore) Finalize(record historyRecord) error {
	store.mu.Lock()
	defer store.mu.Unlock()

	if store.err != nil {
		return store.err
	}
	store.initialize()
	delete(store.records, activeRecordID(record.ContainerName))
	store.records[record.ID] = record
	return nil
}

func (store *collectingStatisticsStore) Records() []historyRecord {
	store.mu.Lock()
	defer store.mu.Unlock()

	store.initialize()
	records := make([]historyRecord, 0, len(store.records))
	for _, record := range store.records {
		records = append(records, record)
	}
	return records
}

func (store *collectingStatisticsStore) initialize() {
	if store.records == nil {
		store.records = make(map[string]historyRecord)
	}
}
