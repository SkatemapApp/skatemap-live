package skater

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"time"
)

type Location struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
}

type Skater struct {
	ID       string
	EventID  string
	Location Location
	client   *http.Client
	baseURL  string
}

type UpdateResult struct {
	EventID      string
	SkaterID     string
	Timestamp    time.Time
	ResponseTime time.Duration
	Error        error
}

func New(eventID, skaterID, baseURL string) *Skater {
	return &Skater{
		ID:      skaterID,
		EventID: eventID,
		Location: Location{
			Latitude:  51.5074 + rand.Float64()*0.1,
			Longitude: -0.1278 + rand.Float64()*0.1,
		},
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
		baseURL: baseURL,
	}
}

func (s *Skater) Move() {
	s.Location.Latitude += (rand.Float64() - 0.5) * 0.0001
	s.Location.Longitude += (rand.Float64() - 0.5) * 0.0001
}

func (s *Skater) UpdateLocation() UpdateResult {
	start := time.Now()

	payload := map[string]interface{}{
		"coordinates": []float64{s.Location.Longitude, s.Location.Latitude},
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return UpdateResult{
			EventID:      s.EventID,
			SkaterID:     s.ID,
			Timestamp:    start,
			ResponseTime: time.Since(start),
			Error:        err,
		}
	}

	url := fmt.Sprintf("%s/skatingEvents/%s/skaters/%s", s.baseURL, s.EventID, s.ID)
	req, err := http.NewRequest(http.MethodPut, url, bytes.NewReader(body))
	if err != nil {
		return UpdateResult{
			EventID:      s.EventID,
			SkaterID:     s.ID,
			Timestamp:    start,
			ResponseTime: time.Since(start),
			Error:        err,
		}
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := s.client.Do(req)
	if err != nil {
		return UpdateResult{
			EventID:      s.EventID,
			SkaterID:     s.ID,
			Timestamp:    start,
			ResponseTime: time.Since(start),
			Error:        err,
		}
	}
	defer resp.Body.Close()

	responseTime := time.Since(start)

	if resp.StatusCode != http.StatusAccepted {
		return UpdateResult{
			EventID:      s.EventID,
			SkaterID:     s.ID,
			Timestamp:    start,
			ResponseTime: responseTime,
			Error:        fmt.Errorf("unexpected status code: %d", resp.StatusCode),
		}
	}

	return UpdateResult{
		EventID:      s.EventID,
		SkaterID:     s.ID,
		Timestamp:    start,
		ResponseTime: responseTime,
		Error:        nil,
	}
}

func (s *Skater) Run(interval time.Duration, results chan<- UpdateResult) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for range ticker.C {
		s.Move()
		result := s.UpdateLocation()
		results <- result
	}
}
