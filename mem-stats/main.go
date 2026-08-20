package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	config, err := loadRuntimeConfig()
	if err != nil {
		log.Fatal(err)
	}

	store := newConfigStore(config.ConfigPath)
	containers, err := store.Load(config.SelfContainer)
	if err != nil {
		log.Fatal(err)
	}

	statistics := newCSVStatisticsStore(config.HistoryPath)
	activeRecords, err := statistics.LoadActive()
	if err != nil {
		log.Fatal(err)
	}
	if err := finalizeOrphanedRecords(statistics, activeRecords, containers); err != nil {
		log.Fatal(err)
	}

	reader := newDockerMemoryReader(config.DockerSocket, config.RequestTimeout)
	monitor := newMonitor(
		reader,
		statistics,
		config.SampleInterval,
		config.CheckpointInterval,
		config.BucketSize,
	)
	for _, container := range containers {
		var restored *historyRecord
		if record, exists := activeRecords[container]; exists {
			recordCopy := record
			restored = &recordCopy
		}
		if _, err := monitor.Add(container, restored); err != nil {
			log.Fatal(err)
		}
	}

	application := newApplication(monitor, store, statistics)
	server := &http.Server{
		Addr:              config.HTTPAddress,
		Handler:           application.routes(),
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	monitorDone := make(chan struct{})
	go func() {
		defer close(monitorDone)
		monitor.Run(ctx)
	}()

	serverErrors := make(chan error, 1)
	go func() {
		log.Printf(
			"monitoring %d container(s) every %s; report available on %s",
			len(containers),
			config.SampleInterval,
			config.HTTPAddress,
		)
		serverErrors <- server.ListenAndServe()
	}()

	var serverError error
	select {
	case <-ctx.Done():
	case err := <-serverErrors:
		if !errors.Is(err, http.ErrServerClosed) {
			serverError = err
		}
	}

	stop()
	shutdownContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownContext); err != nil {
		log.Printf("HTTP server shutdown failed: %v", err)
	}
	<-monitorDone

	if err := monitor.Shutdown(config.SelfContainer); err != nil {
		log.Printf("statistics shutdown checkpoint failed: %v", err)
	}
	if serverError != nil {
		log.Printf("HTTP server failed: %v", serverError)
		os.Exit(1)
	}
}

func finalizeOrphanedRecords(
	store statisticsStore,
	activeRecords map[string]historyRecord,
	containers []string,
) error {
	monitored := make(map[string]struct{}, len(containers))
	for _, container := range containers {
		monitored[container] = struct{}{}
	}

	now := time.Now()
	for containerName, record := range activeRecords {
		if _, exists := monitored[containerName]; exists {
			continue
		}
		record.ID = finalizedRecordID(containerName, now)
		record.ObservedUntil = now
		record.Reason = "not_monitored"
		if err := store.Finalize(record); err != nil {
			return fmt.Errorf("finalize orphaned statistics for %q: %w", containerName, err)
		}
		delete(activeRecords, containerName)
	}
	return nil
}
