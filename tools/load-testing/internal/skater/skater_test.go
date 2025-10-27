package skater

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestNew(t *testing.T) {
	eventID := "event-123"
	skaterID := "skater-456"
	baseURL := "https://example.com"

	s := New(eventID, skaterID, baseURL)

	if s.ID != skaterID {
		t.Errorf("expected skater ID %s, got %s", skaterID, s.ID)
	}

	if s.EventID != eventID {
		t.Errorf("expected event ID %s, got %s", eventID, s.EventID)
	}

	if s.baseURL != baseURL {
		t.Errorf("expected base URL %s, got %s", baseURL, s.baseURL)
	}

	if s.Location.Latitude < londonLatBase || s.Location.Latitude > londonLatBase+locationSpread {
		t.Errorf("latitude %f out of expected range", s.Location.Latitude)
	}

	if s.Location.Longitude < londonLonBase || s.Location.Longitude > londonLonBase+locationSpread {
		t.Errorf("longitude %f out of expected range", s.Location.Longitude)
	}
}

func TestMove(t *testing.T) {
	s := New("event-1", "skater-1", "https://example.com")

	initialLat := s.Location.Latitude
	initialLon := s.Location.Longitude

	s.Move()

	if s.Location.Latitude == initialLat && s.Location.Longitude == initialLon {
		t.Error("location did not change after Move()")
	}

	latDiff := s.Location.Latitude - initialLat
	lonDiff := s.Location.Longitude - initialLon

	maxDiff := movementDelta * 0.5
	if latDiff < -maxDiff || latDiff > maxDiff {
		t.Errorf("latitude movement %f exceeds expected range", latDiff)
	}

	if lonDiff < -maxDiff || lonDiff > maxDiff {
		t.Errorf("longitude movement %f exceeds expected range", lonDiff)
	}
}

func TestUpdateLocation_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut {
			t.Errorf("expected PUT method, got %s", r.Method)
		}

		expectedPath := "/skatingEvents/event-1/skaters/skater-1"
		if r.URL.Path != expectedPath {
			t.Errorf("expected path %s, got %s", expectedPath, r.URL.Path)
		}

		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("expected Content-Type application/json, got %s", r.Header.Get("Content-Type"))
		}

		w.WriteHeader(http.StatusAccepted)
	}))
	defer server.Close()

	s := New("event-1", "skater-1", server.URL)
	result := s.UpdateLocation()

	if result.Error != nil {
		t.Errorf("unexpected error: %v", result.Error)
	}

	if result.EventID != "event-1" {
		t.Errorf("expected event ID event-1, got %s", result.EventID)
	}

	if result.SkaterID != "skater-1" {
		t.Errorf("expected skater ID skater-1, got %s", result.SkaterID)
	}

	if result.ResponseTime <= 0 {
		t.Error("expected positive response time")
	}
}

func TestUpdateLocation_ErrorStatusCode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	s := New("event-1", "skater-1", server.URL)
	result := s.UpdateLocation()

	if result.Error == nil {
		t.Error("expected error for non-202 status code")
	}
}

func TestUpdateLocation_NetworkError(t *testing.T) {
	s := New("event-1", "skater-1", "http://invalid-host-that-does-not-exist:9999")
	s.client.Timeout = 100 * time.Millisecond

	result := s.UpdateLocation()

	if result.Error == nil {
		t.Error("expected error for network failure")
	}
}
