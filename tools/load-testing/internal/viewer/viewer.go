package viewer

import (
	"encoding/json"
	"fmt"
	"net/url"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	connectTimeout     = 10 * time.Second
	readTimeout        = 60 * time.Second
	writeTimeout       = 10 * time.Second
	pongWait           = 60 * time.Second
	pingPeriod         = 54 * time.Second
)

type Location struct {
	SkaterID  string  `json:"skaterId"`
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Timestamp int64   `json:"timestamp"`
}

type LocationBatch struct {
	Locations  []Location `json:"locations"`
	ServerTime int64      `json:"serverTime"`
}

type ViewerResult struct {
	EventID      string
	ViewerNumber int
	Timestamp    time.Time
	MessageCount int
	Latency      time.Duration
	Error        error
}

type Viewer struct {
	eventID      string
	viewerNumber int
	baseURL      string
	results      chan<- ViewerResult
	stopChan     <-chan struct{}
	wg           *sync.WaitGroup
}

func New(eventID string, viewerNumber int, baseURL string, results chan<- ViewerResult, stopChan <-chan struct{}, wg *sync.WaitGroup) *Viewer {
	return &Viewer{
		eventID:      eventID,
		viewerNumber: viewerNumber,
		baseURL:      baseURL,
		results:      results,
		stopChan:     stopChan,
		wg:           wg,
	}
}

func (v *Viewer) Start() {
	defer v.wg.Done()

	wsURL, err := v.buildWebSocketURL()
	if err != nil {
		v.results <- ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    time.Now(),
			MessageCount: 0,
			Latency:      0,
			Error:        fmt.Errorf("invalid URL: %w", err),
		}
		return
	}

	dialer := websocket.Dialer{
		HandshakeTimeout: connectTimeout,
	}

	conn, _, err := dialer.Dial(wsURL, nil)
	if err != nil {
		v.results <- ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    time.Now(),
			MessageCount: 0,
			Latency:      0,
			Error:        fmt.Errorf("connection failed: %w", err),
		}
		return
	}
	defer conn.Close()

	if err := conn.SetReadDeadline(time.Now().Add(pongWait)); err != nil {
		v.results <- ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    time.Now(),
			MessageCount: 0,
			Latency:      0,
			Error:        fmt.Errorf("failed to set read deadline: %w", err),
		}
		return
	}

	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(pongWait))
	})

	doneChan := make(chan struct{})
	defer close(doneChan)

	go v.pingLoop(conn, doneChan)

	v.receiveLoop(conn)
}

func (v *Viewer) buildWebSocketURL() (string, error) {
	parsedURL, err := url.Parse(v.baseURL)
	if err != nil {
		return "", err
	}

	scheme := "ws"
	if parsedURL.Scheme == "https" {
		scheme = "wss"
	}

	wsURL := fmt.Sprintf("%s://%s/skatingEvents/%s/stream", scheme, parsedURL.Host, v.eventID)
	return wsURL, nil
}

func (v *Viewer) pingLoop(conn *websocket.Conn, done <-chan struct{}) {
	ticker := time.NewTicker(pingPeriod)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			if err := conn.SetWriteDeadline(time.Now().Add(writeTimeout)); err != nil {
				return
			}
			if err := conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		case <-v.stopChan:
			return
		case <-done:
			return
		}
	}
}

func (v *Viewer) receiveLoop(conn *websocket.Conn) {
	messageCount := 0

	for {
		select {
		case <-v.stopChan:
			return
		default:
			receiveTime := time.Now()

			if err := conn.SetReadDeadline(time.Now().Add(readTimeout)); err != nil {
				v.results <- ViewerResult{
					EventID:      v.eventID,
					ViewerNumber: v.viewerNumber,
					Timestamp:    receiveTime,
					MessageCount: messageCount,
					Latency:      0,
					Error:        fmt.Errorf("failed to set read deadline: %w", err),
				}
				return
			}

			_, message, err := conn.ReadMessage()
			if err != nil {
				if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
					v.results <- ViewerResult{
						EventID:      v.eventID,
						ViewerNumber: v.viewerNumber,
						Timestamp:    receiveTime,
						MessageCount: messageCount,
						Latency:      0,
						Error:        fmt.Errorf("unexpected close: %w", err),
					}
				}
				return
			}

			var batch LocationBatch
			if err := json.Unmarshal(message, &batch); err != nil {
				v.results <- ViewerResult{
					EventID:      v.eventID,
					ViewerNumber: v.viewerNumber,
					Timestamp:    receiveTime,
					MessageCount: messageCount,
					Latency:      0,
					Error:        fmt.Errorf("failed to parse message: %w", err),
				}
				continue
			}

			messageCount++
			latency := receiveTime.UnixMilli() - batch.ServerTime

			v.results <- ViewerResult{
				EventID:      v.eventID,
				ViewerNumber: v.viewerNumber,
				Timestamp:    receiveTime,
				MessageCount: messageCount,
				Latency:      time.Duration(latency) * time.Millisecond,
				Error:        nil,
			}
		}
	}
}
