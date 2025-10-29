package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/metrics"
	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/viewer"
)

const (
	defaultBufferSize = 1000
)

type Config struct {
	ViewersPerEvent int
	EventIDs        []string
	TargetURL       string
	MetricsFile     string
	BufferSize      int
}

func main() {
	config := parseFlags()

	if err := run(config); err != nil {
		log.Fatal(err)
	}
}

func parseFlags() Config {
	var config Config
	var eventsStr string

	flag.IntVar(&config.ViewersPerEvent, "viewers-per-event", 1, "Number of viewers per event")
	flag.StringVar(&eventsStr, "events", "", "Comma-separated list of event IDs (required)")
	flag.StringVar(&config.TargetURL, "target-url", "", "Target URL for the API (required)")
	flag.StringVar(&config.MetricsFile, "metrics-file", "viewer-metrics.csv", "Output file for metrics")
	flag.IntVar(&config.BufferSize, "buffer-size", defaultBufferSize, "Size of results buffer")

	flag.Parse()

	if config.TargetURL == "" {
		fmt.Println("Error: --target-url is required")
		flag.Usage()
		os.Exit(1)
	}

	if eventsStr == "" {
		fmt.Println("Error: --events is required")
		flag.Usage()
		os.Exit(1)
	}

	if _, err := url.Parse(config.TargetURL); err != nil {
		log.Fatalf("Invalid target URL: %v", err)
	}

	if config.ViewersPerEvent <= 0 {
		log.Fatalf("Number of viewers per event must be positive, got: %d", config.ViewersPerEvent)
	}

	if config.BufferSize <= 0 {
		log.Fatalf("Buffer size must be positive, got: %d", config.BufferSize)
	}

	config.EventIDs = parseEventIDs(eventsStr)
	if len(config.EventIDs) == 0 {
		log.Fatal("At least one event ID must be provided")
	}

	return config
}

func parseEventIDs(eventsStr string) []string {
	parts := strings.Split(eventsStr, ",")
	eventIDs := make([]string, 0, len(parts))

	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed != "" {
			eventIDs = append(eventIDs, trimmed)
		}
	}

	return eventIDs
}

func run(config Config) error {
	totalViewers := len(config.EventIDs) * config.ViewersPerEvent
	log.Printf("Starting simulation with %d events, %d viewers per event (%d total viewers)",
		len(config.EventIDs), config.ViewersPerEvent, totalViewers)
	log.Printf("Event IDs: %v", config.EventIDs)

	metricsWriter, err := metrics.NewViewerWriter(config.MetricsFile)
	if err != nil {
		return fmt.Errorf("failed to create metrics writer: %w", err)
	}
	defer metricsWriter.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	results := make(chan viewer.ViewerResult, config.BufferSize)
	var viewersWg sync.WaitGroup
	var metricsWg sync.WaitGroup

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	metricsWg.Add(1)
	go func() {
		defer metricsWg.Done()
		for result := range results {
			if err := metricsWriter.WriteResult(result); err != nil {
				log.Printf("Error writing metric: %v", err)
			}
			if result.Error != nil {
				log.Printf("Error for viewer %d in event %s: %v",
					result.ViewerNumber, result.EventID, result.Error)
			} else {
				log.Printf("Viewer %d (event %s): received %d messages, latency %.2fms",
					result.ViewerNumber, result.EventID, result.MessageCount,
					float64(result.Latency.Microseconds())/1000.0)
			}
		}
	}()

	viewerNumber := 0
	for _, eventID := range config.EventIDs {
		for j := 0; j < config.ViewersPerEvent; j++ {
			viewerNumber++
			v := viewer.New(ctx, eventID, viewerNumber, config.TargetURL, results, &viewersWg)
			viewersWg.Add(1)
			go v.Start()
		}
	}

	log.Printf("Simulation running with %d viewers. Press Ctrl+C to stop.", totalViewers)
	log.Printf("Metrics being written to: %s", config.MetricsFile)

	<-sigChan
	log.Println("Shutting down...")
	cancel()

	viewersWg.Wait()
	close(results)
	metricsWg.Wait()

	log.Println("Simulation stopped")
	return nil
}
