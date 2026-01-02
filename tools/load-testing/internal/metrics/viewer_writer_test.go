package metrics

import (
	"encoding/csv"
	"os"
	"path/filepath"
	"testing"
	"time"

	"load-testing/internal/viewer"
)

func TestNewViewerWriter(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	writer, err := NewViewerWriter(filename)
	if err != nil {
		t.Fatalf("NewViewerWriter() error = %v", err)
	}
	defer writer.Close()

	if _, err := os.Stat(filename); os.IsNotExist(err) {
		t.Error("Expected file to be created")
	}

	file, err := os.Open(filename)
	if err != nil {
		t.Fatalf("Failed to open file: %v", err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	header, err := reader.Read()
	if err != nil {
		t.Fatalf("Failed to read header: %v", err)
	}

	expectedHeader := []string{"timestamp", "event_id", "viewer_number", "message_count", "latency_ms", "error"}
	if len(header) != len(expectedHeader) {
		t.Fatalf("Expected %d columns, got %d", len(expectedHeader), len(header))
	}

	for i, expected := range expectedHeader {
		if header[i] != expected {
			t.Errorf("Column %d: expected '%s', got '%s'", i, expected, header[i])
		}
	}
}

func TestViewerWriterWriteResult(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	writer, err := NewViewerWriter(filename)
	if err != nil {
		t.Fatalf("NewViewerWriter() error = %v", err)
	}

	timestamp := time.Date(2024, 1, 1, 12, 0, 0, 0, time.UTC)
	result := viewer.ViewerResult{
		EventID:      "test-event",
		ViewerNumber: 42,
		Timestamp:    timestamp,
		MessageCount: 10,
		Latency:      150 * time.Millisecond,
		Error:        nil,
	}

	if err := writer.WriteResult(result); err != nil {
		t.Fatalf("WriteResult() error = %v", err)
	}

	writer.Close()

	file, err := os.Open(filename)
	if err != nil {
		t.Fatalf("Failed to open file: %v", err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	if err != nil {
		t.Fatalf("Failed to read CSV: %v", err)
	}

	if len(records) != 2 {
		t.Fatalf("Expected 2 records (header + data), got %d", len(records))
	}

	record := records[1]
	if record[0] != "2024-01-01T12:00:00Z" {
		t.Errorf("Expected timestamp '2024-01-01T12:00:00Z', got '%s'", record[0])
	}
	if record[1] != "test-event" {
		t.Errorf("Expected event_id 'test-event', got '%s'", record[1])
	}
	if record[2] != "42" {
		t.Errorf("Expected viewer_number '42', got '%s'", record[2])
	}
	if record[3] != "10" {
		t.Errorf("Expected message_count '10', got '%s'", record[3])
	}
	if record[4] != "150.00" {
		t.Errorf("Expected latency_ms '150.00', got '%s'", record[4])
	}
	if record[5] != "" {
		t.Errorf("Expected empty error, got '%s'", record[5])
	}
}

func TestViewerWriterWriteResultWithError(t *testing.T) {
	tmpDir := t.TempDir()
	filename := filepath.Join(tmpDir, "test-metrics.csv")

	writer, err := NewViewerWriter(filename)
	if err != nil {
		t.Fatalf("NewViewerWriter() error = %v", err)
	}

	result := viewer.ViewerResult{
		EventID:      "test-event",
		ViewerNumber: 1,
		Timestamp:    time.Now(),
		MessageCount: 5,
		Latency:      0,
		Error:        &testError{"connection failed"},
	}

	if err := writer.WriteResult(result); err != nil {
		t.Fatalf("WriteResult() error = %v", err)
	}

	writer.Close()

	file, err := os.Open(filename)
	if err != nil {
		t.Fatalf("Failed to open file: %v", err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	if err != nil {
		t.Fatalf("Failed to read CSV: %v", err)
	}

	if len(records) != 2 {
		t.Fatalf("Expected 2 records (header + data), got %d", len(records))
	}

	errorStr := records[1][5]
	if errorStr != "connection failed" {
		t.Errorf("Expected error 'connection failed', got '%s'", errorStr)
	}
}

type testError struct {
	msg string
}

func (e *testError) Error() string {
	return e.msg
}
