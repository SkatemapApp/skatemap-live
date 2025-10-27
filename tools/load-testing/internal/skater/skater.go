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

// Location represents a geographic coordinate with latitude and longitude.
type Location struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
}

// Skater represents a simulated skater that sends location updates to the API.
// Each skater maintains its own HTTP client and current location.
type Skater struct {
	ID       string
	EventID  string
	Location Location
	client   *http.Client
	baseURL  string
}

// UpdateResult contains the result of a location update request,
// including timing information and any errors encountered.
type UpdateResult struct {
	EventID      string
	SkaterID     string
	Timestamp    time.Time
	ResponseTime time.Duration
	Error        error
}

// New creates a new Skater with a random starting location near London.
// The skater is initialised with its own HTTP client configured with a timeout.
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

// Move updates the skater's location by a small random amount,
// simulating realistic GPS movement of approximately 10 metres.
func (s *Skater) Move() {
	s.Location.Latitude += (rand.Float64() - 0.5) * movementDelta
	s.Location.Longitude += (rand.Float64() - 0.5) * movementDelta
}

// UpdateLocation sends the current location to the API via HTTP PUT.
// Returns an UpdateResult containing response time and any errors.
// The API expects a 202 Accepted response for successful updates.
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

