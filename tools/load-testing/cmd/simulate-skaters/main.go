package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"math"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"load-testing/internal/metrics"
	"load-testing/internal/skater"

	"github.com/google/uuid"
	"golang.org/x/time/rate"
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
	RateLimit       float64
	RampUpDuration  time.Duration
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
	flag.Float64Var(&config.RateLimit, "rate-limit", 0, "Optional maximum requests per second (0 = unlimited)")

	var rampUpStr string
	flag.StringVar(&rampUpStr, "ramp-up-duration", "", "Optional duration to gradually increase load (e.g., 5m, 10s)")

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

	if rampUpStr != "" {
		rampUp, err := time.ParseDuration(rampUpStr)
		if err != nil {
			log.Fatalf("Invalid ramp-up duration: %v", err)
		}
		if rampUp < time.Second {
			log.Fatalf("Ramp-up duration must be at least 1 second, got: %v", rampUp)
		}
		if rampUp > time.Hour {
			log.Fatalf("Ramp-up duration must be at most 1 hour, got: %v", rampUp)
		}
		config.RampUpDuration = rampUp
	}

	if config.RateLimit < 0 {
		log.Fatalf("Rate limit must be non-negative, got: %f", config.RateLimit)
	}

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

	if config.RateLimit > 0 {
		log.Printf("Rate limiting enabled: %.2f requests/second", config.RateLimit)
	}
	if config.RampUpDuration > 0 {
		log.Printf("Ramp-up enabled: %s", config.RampUpDuration)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var limiter *rate.Limiter
	if config.RateLimit > 0 || config.RampUpDuration > 0 {
		var initialRate, burstRate float64
		if config.RampUpDuration > 0 {
			totalSkaters := config.NumEvents * config.SkatersPerEvent
			naturalRate := float64(totalSkaters) / config.UpdateInterval.Seconds()
			targetRate := naturalRate
			if config.RateLimit > 0 && config.RateLimit < naturalRate {
				targetRate = config.RateLimit
			}
			initialRate = math.Max(targetRate*0.1, 0.1)
			burstRate = targetRate
		} else {
			initialRate = config.RateLimit
			burstRate = config.RateLimit
		}
		burst := int(math.Ceil(burstRate))
		if burst < 1 {
			burst = 1
		}
		limiter = rate.NewLimiter(rate.Limit(initialRate), burst)
	}

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

	if limiter != nil && config.RampUpDuration > 0 {
		go func() {
			totalSkaters := config.NumEvents * config.SkatersPerEvent
			naturalRate := float64(totalSkaters) / config.UpdateInterval.Seconds()
			targetRate := naturalRate
			if config.RateLimit > 0 && config.RateLimit < naturalRate {
				targetRate = config.RateLimit
			}

			initialRate := math.Max(targetRate*0.1, 0.1)
			steps := 100
			stepDuration := config.RampUpDuration / time.Duration(steps)
			rateIncrement := (targetRate - initialRate) / float64(steps)

			log.Printf("Ramping up from %.2f to %.2f requests/second over %s", initialRate, targetRate, config.RampUpDuration)

			for i := 0; i < steps; i++ {
				select {
				case <-ctx.Done():
					return
				case <-time.After(stepDuration):
					select {
					case <-ctx.Done():
						return
					default:
						newRate := initialRate + rateIncrement*float64(i+1)
						limiter.SetLimit(rate.Limit(newRate))
					}
				}
			}
			log.Printf("Ramp-up complete: %.2f requests/second", targetRate)
		}()
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
					if limiter != nil {
						if err := limiter.Wait(ctx); err != nil {
							return
						}
					}
					sk.Move()
					result := sk.UpdateLocation()
					results <- result
				case <-ctx.Done():
					return
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
	cancel()
	close(stopChan)

	skatersWg.Wait()
	close(results)
	metricsWg.Wait()

	log.Println("Simulation stopped")
	return nil
}
