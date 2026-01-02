package testutil

import (
	"os/exec"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func CheckRailwayLogs(t *testing.T, pattern string, expectFound bool) {
	t.Helper()

	cmd := exec.Command("railway", "logs", "--tail", "100")
	output, err := cmd.Output()
	require.NoError(t, err, "Failed to fetch Railway logs")

	found := regexp.MustCompile(pattern).Match(output)
	if expectFound {
		assert.True(t, found, "Expected to find pattern %q in Railway logs", pattern)
	} else {
		assert.False(t, found, "Expected NOT to find pattern %q in Railway logs", pattern)
	}
}

func ParseCleanupTime(t *testing.T) time.Time {
	t.Helper()

	cmd := exec.Command("railway", "logs", "--tail", "200")
	output, err := cmd.Output()
	require.NoError(t, err, "Failed to fetch Railway logs")

	cleanupPattern := regexp.MustCompile(`(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*Cleanup completed`)
	allMatches := cleanupPattern.FindAllStringSubmatch(string(output), -1)

	if len(allMatches) == 0 {
		t.Fatal("Failed to find cleanup timestamp in Railway logs")
	}

	lastMatch := allMatches[len(allMatches)-1]
	timestamp, err := time.Parse("2006-01-02 15:04:05", lastMatch[1])
	require.NoError(t, err, "Failed to parse cleanup timestamp")

	return timestamp
}

func ParseLastLocationUpdateTime(t *testing.T) time.Time {
	t.Helper()

	cmd := exec.Command("railway", "logs", "--tail", "500")
	output, err := cmd.Output()
	require.NoError(t, err, "Failed to fetch Railway logs")

	updatePattern := regexp.MustCompile(`(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*Received location update request`)
	allMatches := updatePattern.FindAllStringSubmatch(string(output), -1)

	if len(allMatches) == 0 {
		t.Fatal("Failed to find location update timestamp in Railway logs")
	}

	lastMatch := allMatches[len(allMatches)-1]
	timestamp, err := time.Parse("2006-01-02 15:04:05", lastMatch[1])
	require.NoError(t, err, "Failed to parse location update timestamp")

	return timestamp
}

func DetectCrash(t *testing.T) bool {
	t.Helper()

	cmd := exec.Command("railway", "logs", "--tail", "200")
	output, err := cmd.Output()
	if err != nil {
		t.Logf("Warning: Failed to fetch Railway logs: %v", err)
		return false
	}

	logStr := string(output)

	crashIndicators := []string{
		"OutOfMemoryError",
		"java.lang.OutOfMemoryError",
		"killed",
		"deployment restart",
		"crashed",
	}

	for _, indicator := range crashIndicators {
		if strings.Contains(logStr, indicator) {
			t.Logf("Detected crash indicator: %s", indicator)
			return true
		}
	}

	exitCodePattern := regexp.MustCompile(`exit code ([0-9]+)`)
	matches := exitCodePattern.FindAllStringSubmatch(logStr, -1)
	for _, match := range matches {
		if len(match) > 1 && match[1] != "0" {
			t.Logf("Detected crash indicator: exit code %s", match[1])
			return true
		}
	}

	return false
}
