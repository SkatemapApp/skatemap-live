package skater

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"time"
)

const (
	londonLatBase     = 51.5074
	londonLonBase     = -0.1278
	locationSpread    = 0.1
	movementDelta     = 0.0001
	httpClientTimeout = 10 * time.Second
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
			Latitude:  londonLatBase + rand.Float64()*locationSpread,
			Longitude: londonLonBase + rand.Float64()*locationSpread,
		},
		client: &http.Client{
			Timeout: httpClientTimeout,
		},
		baseURL: baseURL,
	}
}

func (s *Skater) Move() {
	s.Location.Latitude += (rand.Float64() - 0.5) * movementDelta
	s.Location.Longitude += (rand.Float64() - 0.5) * movementDelta
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

