package main

import (
	"flag"
	"fmt"
	"log"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/metrics"
	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/skater"
	"github.com/google/uuid"
)

const (
	maxResultsBufferSize = 1000
)

type Config struct {
	NumEvents       int
	SkatersPerEvent int
	UpdateInterval  time.Duration
	TargetURL       string
	MetricsFile     string
	EventIDs        string
}

func main() {
	config := parseFlags()

	if err := run(config); err != nil {
		log.Fatal(err)
	}
}

func parseFlags() Config {
	var config Config

	flag.IntVar(&config.NumEvents, "events", 1, "Number of events to simulate")
	flag.IntVar(&config.SkatersPerEvent, "skaters-per-event", 10, "Number of skaters per event")

	var intervalStr string
	flag.StringVar(&intervalStr, "update-interval", "3s", "Interval between location updates (e.g., 3s, 1m)")

	flag.StringVar(&config.TargetURL, "target-url", "", "Target URL for the API (required)")
	flag.StringVar(&config.MetricsFile, "metrics-file", "metrics.csv", "Output file for metrics")
	flag.StringVar(&config.EventIDs, "event-id", "", "Comma-separated list of event IDs to use (optional, generates random if not provided)")

	flag.Parse()

	if config.TargetURL == "" {
		fmt.Println("Error: --target-url is required")
		flag.Usage()
		os.Exit(1)
	}

	if _, err := url.Parse(config.TargetURL); err != nil {
		log.Fatalf("Invalid target URL: %v", err)
	}

	if config.NumEvents <= 0 {
		log.Fatalf("Number of events must be positive, got: %d", config.NumEvents)
	}

	if config.SkatersPerEvent <= 0 {
		log.Fatalf("Number of skaters per event must be positive, got: %d", config.SkatersPerEvent)
	}

	interval, err := time.ParseDuration(intervalStr)
	if err != nil {
		log.Fatalf("Invalid update interval: %v", err)
	}
	config.UpdateInterval = interval

	return config
}

func parseEventIDs(eventIDsStr string, numEvents int) ([]string, error) {
	if eventIDsStr == "" {
		eventIDs := make([]string, numEvents)
		for i := 0; i < numEvents; i++ {
			eventIDs[i] = uuid.New().String()
		}
		return eventIDs, nil
	}

	eventIDs := strings.Split(eventIDsStr, ",")
	for i := range eventIDs {
		eventIDs[i] = strings.TrimSpace(eventIDs[i])
	}

	if len(eventIDs) != numEvents {
		return nil, fmt.Errorf("number of provided event IDs (%d) does not match --events (%d)", len(eventIDs), numEvents)
	}

	for i, id := range eventIDs {
		if id == "" {
			return nil, fmt.Errorf("empty event ID at position %d", i+1)
		}
		if _, err := uuid.Parse(id); err != nil {
			return nil, fmt.Errorf("invalid UUID format for event ID %d (%s): %w", i+1, id, err)
		}
	}

	return eventIDs, nil
}

func run(config Config) error {
	log.Printf("Starting simulation with %d events, %d skaters per event, update interval: %s",
		config.NumEvents, config.SkatersPerEvent, config.UpdateInterval)

	metricsWriter, err := metrics.NewWriter(config.MetricsFile)
	if err != nil {
		return fmt.Errorf("failed to create metrics writer: %w", err)
	}
	defer metricsWriter.Close()

	results := make(chan skater.UpdateResult, maxResultsBufferSize)
	var skatersWg sync.WaitGroup
	var metricsWg sync.WaitGroup

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	stopChan := make(chan struct{})

	metricsWg.Add(1)
	go func() {
		defer metricsWg.Done()
		for {
			select {
			case result, ok := <-results:
				if !ok {
					return
				}
				if err := metricsWriter.WriteResult(result); err != nil {
					log.Printf("Error writing metric: %v", err)
				}
				if result.Error != nil {
					log.Printf("Error updating location for skater %s in event %s: %v",
						result.SkaterID, result.EventID, result.Error)
				}
			case <-stopChan:
				for {
					select {
					case result, ok := <-results:
						if !ok {
							return
						}
						if err := metricsWriter.WriteResult(result); err != nil {
							log.Printf("Error writing metric during shutdown: %v", err)
						}
					default:
						return
					}
				}
			}
		}
	}()

	eventIDs, err := parseEventIDs(config.EventIDs, config.NumEvents)
	if err != nil {
		return err
	}

	if config.EventIDs != "" {
		log.Printf("Using provided event IDs: %v", eventIDs)
	} else {
		log.Printf("Generated event IDs: %v", eventIDs)
	}

	skaters := make([]*skater.Skater, 0, config.NumEvents*config.SkatersPerEvent)
	for _, eventID := range eventIDs {
		for j := 0; j < config.SkatersPerEvent; j++ {
			skaterID := uuid.New().String()
			s := skater.New(eventID, skaterID, config.TargetURL)
			skaters = append(skaters, s)
		}
	}

	log.Printf("Starting %d skaters...", len(skaters))
	for _, s := range skaters {
		skatersWg.Add(1)
		go func(sk *skater.Skater) {
			defer skatersWg.Done()
			ticker := time.NewTicker(config.UpdateInterval)
			defer ticker.Stop()

			for {
				select {
				case <-ticker.C:
					sk.Move()
					result := sk.UpdateLocation()
					results <- result
				case <-stopChan:
					return
				}
			}
		}(s)
	}

	log.Printf("Simulation running. Press Ctrl+C to stop.")
	log.Printf("Metrics being written to: %s", config.MetricsFile)

	<-sigChan
	log.Println("Shutting down...")
	close(stopChan)

	skatersWg.Wait()
	close(results)
	metricsWg.Wait()

	log.Println("Simulation stopped")
	return nil
}
