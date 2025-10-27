package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/metrics"
	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/skater"
	"github.com/google/uuid"
)

type Config struct {
	NumEvents       int
	SkatersPerEvent int
	UpdateInterval  time.Duration
	TargetURL       string
	MetricsFile     string
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

	flag.Parse()

	if config.TargetURL == "" {
		fmt.Println("Error: --target-url is required")
		flag.Usage()
		os.Exit(1)
	}

	interval, err := time.ParseDuration(intervalStr)
	if err != nil {
		log.Fatalf("Invalid update interval: %v", err)
	}
	config.UpdateInterval = interval

	return config
}

func run(config Config) error {
	log.Printf("Starting simulation with %d events, %d skaters per event, update interval: %s",
		config.NumEvents, config.SkatersPerEvent, config.UpdateInterval)

	metricsWriter, err := metrics.NewWriter(config.MetricsFile)
	if err != nil {
		return fmt.Errorf("failed to create metrics writer: %w", err)
	}
	defer metricsWriter.Close()

	results := make(chan skater.UpdateResult, config.NumEvents*config.SkatersPerEvent*10)
	var wg sync.WaitGroup

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	stopChan := make(chan struct{})

	wg.Add(1)
	go func() {
		defer wg.Done()
		for {
			select {
			case result := <-results:
				if err := metricsWriter.WriteResult(result); err != nil {
					log.Printf("Error writing metric: %v", err)
				}
				if result.Error != nil {
					log.Printf("Error updating location for skater %s in event %s: %v",
						result.SkaterID, result.EventID, result.Error)
				}
			case <-stopChan:
				for len(results) > 0 {
					result := <-results
					metricsWriter.WriteResult(result)
				}
				return
			}
		}
	}()

	eventIDs := make([]string, config.NumEvents)
	for i := 0; i < config.NumEvents; i++ {
		eventIDs[i] = uuid.New().String()
	}
	log.Printf("Generated event IDs: %v", eventIDs)

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
		wg.Add(1)
		go func(sk *skater.Skater) {
			defer wg.Done()
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

	wg.Wait()
	close(results)

	log.Println("Simulation stopped")
	return nil
}
