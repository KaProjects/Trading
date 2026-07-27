package main

import (
	"context"
	"errors"
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

	reader := newDockerMemoryReader(config.DockerSocket, config.RequestTimeout)
	history := newCSVHistoryWriter(config.HistoryPath)
	monitor := newMonitor(reader, history, config.SampleInterval, config.BucketSize)
	for _, container := range containers {
		monitor.Add(container)
	}

	application := newApplication(monitor, store)
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

	if err := monitor.ArchiveAll("monitor_shutdown"); err != nil {
		log.Printf("statistics archive failed during shutdown: %v", err)
	}
	if serverError != nil {
		log.Printf("HTTP server failed: %v", serverError)
		os.Exit(1)
	}
}
