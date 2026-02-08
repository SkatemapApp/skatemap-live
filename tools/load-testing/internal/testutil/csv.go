package testutil

import (
	"encoding/csv"
	"fmt"
	"io"
	"os"
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

	require.Greater(t, len(records), 0, "CSV should have at least a header row")

	if len(records) == 1 {
		return
	}

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

func snapshotCSV(t *testing.T, csvPath string) string {
	t.Helper()

	sourceFile, err := os.Open(csvPath)
	require.NoError(t, err, "Failed to open CSV file for snapshot")
	defer sourceFile.Close()

	snapshotPath := fmt.Sprintf("%s.snapshot.%d", csvPath, os.Getpid())
	destFile, err := os.Create(snapshotPath)
	require.NoError(t, err, "Failed to create snapshot file")
	defer destFile.Close()

	_, err = io.Copy(destFile, sourceFile)
	require.NoError(t, err, "Failed to copy CSV to snapshot")

	return snapshotPath
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

func CountRecordsLive(t *testing.T, csvPath string) int {
	t.Helper()

	snapshotPath := snapshotCSV(t, csvPath)
	defer os.Remove(snapshotPath)

	return CountRecords(t, snapshotPath)
}

func ExtractSkaterIDs(t *testing.T, viewerCSVPath string) map[string]bool {
	t.Helper()

	file, err := os.Open(viewerCSVPath)
	require.NoError(t, err, "Failed to open viewer CSV")
	defer file.Close()

	reader := csv.NewReader(file)
	records, err := reader.ReadAll()
	require.NoError(t, err, "Failed to read CSV")

	require.Greater(t, len(records), 0, "CSV should have at least a header row")

	if len(records) == 1 {
		return make(map[string]bool)
	}

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
