package testutil

import (
	"encoding/csv"
	"os"
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func AssertNoErrors(t *testing.T, csvPath string) {
	t.Helper()

	file, err := os.Open(csvPath)
	require.NoError(t, err, "Failed to open metrics file")
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	require.NoError(t, err, "Failed to read CSV")

	require.Greater(t, len(records), 1, "CSV should have header + data rows")

	errorColumnIndex := len(records[0]) - 1

	errorCount := 0
	for i, record := range records {
		if i == 0 {
			continue
		}
		if len(record) != len(records[0]) {
			t.Fatalf("Invalid CSV row %d: expected %d columns, got %d", i, len(records[0]), len(record))
		}
		if record[errorColumnIndex] != "" {
			errorCount++
			t.Logf("Error in row %d: %s", i, record[errorColumnIndex])
		}
	}

	assert.Equal(t, 0, errorCount, "Expected zero errors in metrics file")
}

func AssertAverageLatency(t *testing.T, csvPath string, maxMs int) {
	t.Helper()

	file, err := os.Open(csvPath)
	require.NoError(t, err, "Failed to open metrics file")
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	require.NoError(t, err, "Failed to read CSV")

	require.Greater(t, len(records), 1, "CSV should have header + data rows")

	latencyColumnIndex := len(records[0]) - 2

	var totalLatency float64
	count := 0

	for i, record := range records {
		if i == 0 {
			continue
		}
		if len(record) != len(records[0]) {
			t.Fatalf("Invalid CSV row %d: expected %d columns, got %d", i, len(records[0]), len(record))
		}

		latency, err := strconv.ParseFloat(record[latencyColumnIndex], 64)
		require.NoError(t, err, "Failed to parse latency in row %d", i)

		totalLatency += latency
		count++
	}

	avgLatency := totalLatency / float64(count)
	assert.LessOrEqual(t, avgLatency, float64(maxMs), "Average latency should be <= %dms", maxMs)
}

func CountRecords(t *testing.T, csvPath string) int {
	t.Helper()

	file, err := os.Open(csvPath)
	require.NoError(t, err, "Failed to open metrics file")
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	require.NoError(t, err, "Failed to read CSV")

	if len(records) <= 1 {
		return 0
	}

	return len(records) - 1
}

func ExtractSkaterIDs(t *testing.T, viewerCSVPath string) map[string]bool {
	t.Helper()

	file, err := os.Open(viewerCSVPath)
	require.NoError(t, err, "Failed to open viewer CSV")
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	require.NoError(t, err, "Failed to read CSV")

	require.Greater(t, len(records), 1, "CSV should have header + data rows")

	skaterIDColumnIndex := -1
	for i, header := range records[0] {
		if header == "skater_ids" {
			skaterIDColumnIndex = i
			break
		}
	}
	require.NotEqual(t, -1, skaterIDColumnIndex, "skater_ids column not found in viewer CSV")

	skaterIDs := make(map[string]bool)
	for i, record := range records {
		if i == 0 {
			continue
		}
		if len(record) != len(records[0]) {
			t.Fatalf("Invalid CSV row %d: expected %d columns, got %d", i, len(records[0]), len(record))
		}

		skaterIDsStr := record[skaterIDColumnIndex]
		if skaterIDsStr != "" {
			ids := strings.Split(skaterIDsStr, "|")
			for _, id := range ids {
				skaterIDs[id] = true
			}
		}
	}

	return skaterIDs
}
