package viewer

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

func TestBuildWebSocketURL(t *testing.T) {
	tests := []struct {
		name     string
		baseURL  string
		eventID  string
		expected string
	}{
		{
			name:     "HTTP URL converts to WS",
			baseURL:  "http://localhost:9000",
			eventID:  "test-event",
			expected: "ws://localhost:9000/skatingEvents/test-event/stream",
		},
		{
			name:     "HTTPS URL converts to WSS",
			baseURL:  "https://example.com",
			eventID:  "test-event",
			expected: "wss://example.com/skatingEvents/test-event/stream",
		},
		{
			name:     "URL with path",
			baseURL:  "http://example.com/api",
			eventID:  "test-event",
			expected: "ws://example.com/skatingEvents/test-event/stream",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &Viewer{
				eventID: tt.eventID,
				baseURL: tt.baseURL,
			}

			result, err := v.buildWebSocketURL()
			if err != nil {
				t.Fatalf("buildWebSocketURL() error = %v", err)
			}

			if result != tt.expected {
				t.Errorf("buildWebSocketURL() = %v, want %v", result, tt.expected)
			}
		})
	}
}

func TestBuildWebSocketURLInvalidURL(t *testing.T) {
	v := &Viewer{
		eventID: "test-event",
		baseURL: "://invalid",
	}

	_, err := v.buildWebSocketURL()
	if err == nil {
		t.Error("buildWebSocketURL() expected error for invalid URL, got nil")
	}
}

func TestViewerReceivesMessages(t *testing.T) {
	batch := LocationBatch{
		Locations: []Location{
			{
				SkaterID:  "skater-1",
				Latitude:  51.5074,
				Longitude: -0.1278,
				Timestamp: time.Now().UnixMilli(),
			},
		},
		ServerTime: time.Now().UnixMilli(),
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("Failed to upgrade connection: %v", err)
			return
		}
		defer conn.Close()

		time.Sleep(10 * time.Millisecond)

		msg, err := json.Marshal(batch)
		if err != nil {
			t.Errorf("Failed to marshal batch: %v", err)
			return
		}

		if err := conn.WriteMessage(websocket.TextMessage, msg); err != nil {
			t.Errorf("Failed to write message: %v", err)
			return
		}

		time.Sleep(50 * time.Millisecond)
	}))
	defer server.Close()

	baseURL := "http://" + server.Listener.Addr().String()

	results := make(chan ViewerResult, 10)
	stopChan := make(chan struct{})
	var wg sync.WaitGroup

	v := New("test-event", 1, baseURL, results, stopChan, &wg)
	wg.Add(1)

	go v.Start()

	select {
	case result := <-results:
		if result.Error != nil {
			t.Errorf("Unexpected error: %v", result.Error)
		}
		if result.MessageCount != 1 {
			t.Errorf("Expected message count 1, got %d", result.MessageCount)
		}
		if result.EventID != "test-event" {
			t.Errorf("Expected event ID 'test-event', got '%s'", result.EventID)
		}
		if result.ViewerNumber != 1 {
			t.Errorf("Expected viewer number 1, got %d", result.ViewerNumber)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for result")
	}

	close(stopChan)
	wg.Wait()
	close(results)
}

func TestViewerConnectionFailure(t *testing.T) {
	results := make(chan ViewerResult, 10)
	stopChan := make(chan struct{})
	var wg sync.WaitGroup

	v := New("test-event", 1, "http://localhost:0", results, stopChan, &wg)
	wg.Add(1)

	go v.Start()

	select {
	case result := <-results:
		if result.Error == nil {
			t.Error("Expected connection error, got nil")
		}
		if !strings.Contains(result.Error.Error(), "connection") {
			t.Errorf("Expected connection error, got: %v", result.Error)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for error result")
	}

	close(stopChan)
	wg.Wait()
	close(results)
}

func TestViewerInvalidJSON(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("Failed to upgrade connection: %v", err)
			return
		}
		defer conn.Close()

		time.Sleep(10 * time.Millisecond)

		if err := conn.WriteMessage(websocket.TextMessage, []byte("invalid json")); err != nil {
			t.Errorf("Failed to write message: %v", err)
			return
		}

		time.Sleep(50 * time.Millisecond)
	}))
	defer server.Close()

	baseURL := "http://" + server.Listener.Addr().String()

	results := make(chan ViewerResult, 10)
	stopChan := make(chan struct{})
	var wg sync.WaitGroup

	v := New("test-event", 1, baseURL, results, stopChan, &wg)
	wg.Add(1)

	go v.Start()

	select {
	case result := <-results:
		if result.Error == nil {
			t.Error("Expected JSON parse error, got nil")
		}
		if !strings.Contains(result.Error.Error(), "parse") {
			t.Errorf("Expected parse error, got: %v", result.Error)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for error result")
	}

	close(stopChan)
	wg.Wait()
	close(results)
}
