package metrics

import (
	"encoding/csv"
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/skater"
)

type Writer struct {
	file   *os.File
	writer *csv.Writer
	mu     sync.Mutex
}

func NewWriter(filename string) (*Writer, error) {
	file, err := os.Create(filename)
	if err != nil {
		return nil, fmt.Errorf("failed to create metrics file: %w", err)
	}

	writer := csv.NewWriter(file)

	header := []string{"timestamp", "event_id", "skater_id", "response_time_ms", "error"}
	if err := writer.Write(header); err != nil {
		file.Close()
		return nil, fmt.Errorf("failed to write CSV header: %w", err)
	}
	writer.Flush()

	return &Writer{
		file:   file,
		writer: writer,
	}, nil
}

func (w *Writer) WriteResult(result skater.UpdateResult) error {
	w.mu.Lock()
	defer w.mu.Unlock()

	errorStr := ""
	if result.Error != nil {
		errorStr = result.Error.Error()
	}

	record := []string{
		result.Timestamp.Format(time.RFC3339),
		result.EventID,
		result.SkaterID,
		fmt.Sprintf("%.2f", float64(result.ResponseTime.Microseconds())/1000.0),
		errorStr,
	}

	if err := w.writer.Write(record); err != nil {
		return fmt.Errorf("failed to write CSV record: %w", err)
	}

	w.writer.Flush()
	return w.writer.Error()
}

func (w *Writer) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()

	w.writer.Flush()
	return w.file.Close()
}
