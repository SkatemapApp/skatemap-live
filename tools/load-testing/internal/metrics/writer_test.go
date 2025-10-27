package metrics

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/skater"
)

func TestNewWriter(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	w, err := NewWriter(filename)
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}
	defer w.Close()

	content, err := os.ReadFile(filename)
	if err != nil {
		t.Fatalf("failed to read file: %v", err)
	}

	expectedHeader := "timestamp,event_id,skater_id,response_time_ms,error\n"
	if string(content) != expectedHeader {
		t.Errorf("expected header %q, got %q", expectedHeader, string(content))
	}
}

func TestWriteResult_Success(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	w, err := NewWriter(filename)
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	timestamp := time.Date(2024, 10, 27, 12, 0, 0, 0, time.UTC)
	result := skater.UpdateResult{
		EventID:      "event-123",
		SkaterID:     "skater-456",
		Timestamp:    timestamp,
		ResponseTime: 150 * time.Millisecond,
		Error:        nil,
	}

	err = w.WriteResult(result)
	if err != nil {
		t.Errorf("failed to write result: %v", err)
	}

	w.Close()

	content, err := os.ReadFile(filename)
	if err != nil {
		t.Fatalf("failed to read file: %v", err)
	}

	lines := strings.Split(string(content), "\n")
	if len(lines) < 2 {
		t.Fatalf("expected at least 2 lines, got %d", len(lines))
	}

	expectedFields := []string{
		timestamp.Format(time.RFC3339),
		"event-123",
		"skater-456",
		"150.00",
		"",
	}

	dataLine := lines[1]
	fields := strings.Split(dataLine, ",")

	if len(fields) != len(expectedFields) {
		t.Errorf("expected %d fields, got %d", len(expectedFields), len(fields))
	}

	for i, expected := range expectedFields {
		if i < len(fields) && fields[i] != expected {
			t.Errorf("field %d: expected %q, got %q", i, expected, fields[i])
		}
	}
}

func TestWriteResult_WithError(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	w, err := NewWriter(filename)
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	timestamp := time.Date(2024, 10, 27, 12, 0, 0, 0, time.UTC)
	result := skater.UpdateResult{
		EventID:      "event-123",
		SkaterID:     "skater-456",
		Timestamp:    timestamp,
		ResponseTime: 0,
		Error:        fmt.Errorf("connection timeout"),
	}

	err = w.WriteResult(result)
	if err != nil {
		t.Errorf("failed to write result: %v", err)
	}

	w.Close()

	content, err := os.ReadFile(filename)
	if err != nil {
		t.Fatalf("failed to read file: %v", err)
	}

	if !strings.Contains(string(content), "connection timeout") {
		t.Error("expected error message in CSV output")
	}
}

func TestWriteResult_Concurrent(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	w, err := NewWriter(filename)
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}
	defer w.Close()

	numWrites := 100
	done := make(chan bool, numWrites)

	for i := 0; i < numWrites; i++ {
		go func(n int) {
			result := skater.UpdateResult{
				EventID:      fmt.Sprintf("event-%d", n),
				SkaterID:     fmt.Sprintf("skater-%d", n),
				Timestamp:    time.Now(),
				ResponseTime: time.Duration(n) * time.Millisecond,
				Error:        nil,
			}
			w.WriteResult(result)
			done <- true
		}(i)
	}

	for i := 0; i < numWrites; i++ {
		<-done
	}

	w.Close()

	content, err := os.ReadFile(filename)
	if err != nil {
		t.Fatalf("failed to read file: %v", err)
	}

	lines := strings.Split(string(content), "\n")
	dataLines := 0
	for _, line := range lines {
		if line != "" && !strings.HasPrefix(line, "timestamp") {
			dataLines++
		}
	}

	if dataLines != numWrites {
		t.Errorf("expected %d data lines, got %d", numWrites, dataLines)
	}
}
