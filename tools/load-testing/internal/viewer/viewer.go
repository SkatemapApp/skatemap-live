package viewer

import (
	"context"
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
	streamPathTemplate = "/skatingEvents/%s/stream"
)

// Location represents a geographic coordinate with latitude and longitude.
type Location struct {
	SkaterID  string  `json:"skaterId"`
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Timestamp int64   `json:"timestamp"`
}

// LocationBatch represents a batch of location updates from the server.
type LocationBatch struct {
	Locations  []Location `json:"locations"`
	ServerTime int64      `json:"serverTime"`
}

// ViewerResult contains the result of receiving a WebSocket message,
// including timing information and any errors encountered.
type ViewerResult struct {
	EventID      string
	ViewerNumber int
	Timestamp    time.Time
	MessageCount int
	Latency      time.Duration
	Error        error
}

// Viewer represents a simulated viewer that receives location updates via WebSocket.
type Viewer struct {
	eventID      string
	viewerNumber int
	baseURL      string
	results      chan<- ViewerResult
	ctx          context.Context
	wg           *sync.WaitGroup
}

// New creates a new Viewer instance configured to connect to the specified event.
func New(ctx context.Context, eventID string, viewerNumber int, baseURL string, results chan<- ViewerResult, wg *sync.WaitGroup) *Viewer {
	return &Viewer{
		ctx:          ctx,
		eventID:      eventID,
		viewerNumber: viewerNumber,
		baseURL:      baseURL,
		results:      results,
		wg:           wg,
	}
}

// Start initiates the WebSocket connection and begins receiving messages.
// It runs until the context is cancelled or an error occurs.
func (v *Viewer) Start() {
	defer v.wg.Done()

	wsURL, err := v.buildWebSocketURL()
	if err != nil {
		v.sendResult(ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    time.Now(),
			MessageCount: 0,
			Latency:      0,
			Error:        fmt.Errorf("invalid URL: %w", err),
		})
		return
	}

	dialer := websocket.Dialer{
		HandshakeTimeout: connectTimeout,
	}

	conn, _, err := dialer.Dial(wsURL, nil)
	if err != nil {
		v.sendResult(ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    time.Now(),
			MessageCount: 0,
			Latency:      0,
			Error:        fmt.Errorf("connection failed: %w", err),
		})
		return
	}
	defer conn.Close()

	if err := conn.SetReadDeadline(time.Now().Add(pongWait)); err != nil {
		v.sendResult(ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    time.Now(),
			MessageCount: 0,
			Latency:      0,
			Error:        fmt.Errorf("failed to set read deadline: %w", err),
		})
		return
	}

	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(pongWait))
	})

	pingCtx, cancelPing := context.WithCancel(v.ctx)
	defer cancelPing()

	go v.pingLoop(pingCtx, conn)

	v.receiveLoop(conn)
}

func (v *Viewer) buildWebSocketURL() (string, error) {
	parsedURL, err := url.Parse(v.baseURL)
	if err != nil {
		return "", err
	}

	if parsedURL.Scheme != "http" && parsedURL.Scheme != "https" {
		return "", fmt.Errorf("invalid URL scheme: %s (must be http or https)", parsedURL.Scheme)
	}

	scheme := "ws"
	if parsedURL.Scheme == "https" {
		scheme = "wss"
	}

	wsURL := fmt.Sprintf("%s://%s%s", scheme, parsedURL.Host, fmt.Sprintf(streamPathTemplate, v.eventID))
	return wsURL, nil
}

func (v *Viewer) pingLoop(ctx context.Context, conn *websocket.Conn) {
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
		case <-ctx.Done():
			return
		}
	}
}

func (v *Viewer) receiveLoop(conn *websocket.Conn) {
	messageCount := 0

	for {
		select {
		case <-v.ctx.Done():
			return
		default:
		}

		receiveTime := time.Now()

		if err := conn.SetReadDeadline(time.Now().Add(readTimeout)); err != nil {
			v.sendResult(ViewerResult{
				EventID:      v.eventID,
				ViewerNumber: v.viewerNumber,
				Timestamp:    receiveTime,
				MessageCount: messageCount,
				Latency:      0,
				Error:        fmt.Errorf("failed to set read deadline: %w", err),
			})
			return
		}

		_, message, err := conn.ReadMessage()
		if err != nil {
			select {
			case <-v.ctx.Done():
				return
			default:
			}

			if websocket.IsCloseError(err, websocket.CloseNormalClosure, websocket.CloseGoingAway) {
				return
			}

			v.sendResult(ViewerResult{
				EventID:      v.eventID,
				ViewerNumber: v.viewerNumber,
				Timestamp:    receiveTime,
				MessageCount: messageCount,
				Latency:      0,
				Error:        fmt.Errorf("connection error: %w", err),
			})
			return
		}

		var batch LocationBatch
		if err := json.Unmarshal(message, &batch); err != nil {
			v.sendResult(ViewerResult{
				EventID:      v.eventID,
				ViewerNumber: v.viewerNumber,
				Timestamp:    receiveTime,
				MessageCount: messageCount,
				Latency:      0,
				Error:        fmt.Errorf("failed to parse message: %w", err),
			})
			continue
		}

		messageCount++
		latency := receiveTime.UnixMilli() - batch.ServerTime

		v.sendResult(ViewerResult{
			EventID:      v.eventID,
			ViewerNumber: v.viewerNumber,
			Timestamp:    receiveTime,
			MessageCount: messageCount,
			Latency:      time.Duration(latency) * time.Millisecond,
			Error:        nil,
		})
	}
}

func (v *Viewer) sendResult(result ViewerResult) {
	select {
	case v.results <- result:
	case <-v.ctx.Done():
	}
}
