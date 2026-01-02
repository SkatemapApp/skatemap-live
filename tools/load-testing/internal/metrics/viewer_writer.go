package metrics

import (
	"encoding/csv"
	"fmt"
	"os"
	"sync"
	"time"

	"load-testing/internal/viewer"
)

const (
	flushBatchSize = 10
)

type ViewerWriter struct {
	file        *os.File
	writer      *csv.Writer
	mu          sync.Mutex
	recordCount int
}

func NewViewerWriter(filename string) (*ViewerWriter, error) {
	file, err := os.Create(filename)
	if err != nil {
		return nil, fmt.Errorf("failed to create metrics file: %w", err)
	}

	writer := csv.NewWriter(file)

	header := []string{"timestamp", "event_id", "viewer_number", "message_count", "latency_ms", "skater_ids", "error"}
	if err := writer.Write(header); err != nil {
		file.Close()
		return nil, fmt.Errorf("failed to write CSV header: %w", err)
	}
	writer.Flush()

	return &ViewerWriter{
		file:   file,
		writer: writer,
	}, nil
}

func (w *ViewerWriter) WriteResult(result viewer.ViewerResult) error {
	w.mu.Lock()
	defer w.mu.Unlock()

	errorStr := ""
	if result.Error != nil {
		errorStr = result.Error.Error()
	}

	skaterIDsStr := ""
	if len(result.SkaterIDs) > 0 {
		skaterIDsStr = result.SkaterIDs[0]
		for i := 1; i < len(result.SkaterIDs); i++ {
			skaterIDsStr += "|" + result.SkaterIDs[i]
		}
	}

	record := []string{
		result.Timestamp.Format(time.RFC3339),
		result.EventID,
		fmt.Sprintf("%d", result.ViewerNumber),
		fmt.Sprintf("%d", result.MessageCount),
		fmt.Sprintf("%.2f", float64(result.Latency.Microseconds())/1000.0),
		skaterIDsStr,
		errorStr,
	}

	if err := w.writer.Write(record); err != nil {
		return fmt.Errorf("failed to write CSV record: %w", err)
	}

	w.recordCount++
	if w.recordCount%flushBatchSize == 0 {
		w.writer.Flush()
		if err := w.writer.Error(); err != nil {
			return err
		}
	}

	return nil
}

func (w *ViewerWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()

	w.writer.Flush()
	return w.file.Close()
}
