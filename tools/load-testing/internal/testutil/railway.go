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

	cleanupPattern := regexp.MustCompile(`(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z).*Cleanup completed`)
	matches := cleanupPattern.FindStringSubmatch(string(output))

	if len(matches) < 2 {
		t.Fatal("Failed to find cleanup timestamp in Railway logs")
	}

	timestamp, err := time.Parse(time.RFC3339Nano, matches[1])
	require.NoError(t, err, "Failed to parse cleanup timestamp")

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
		"exit code",
	}

	for _, indicator := range crashIndicators {
		if strings.Contains(logStr, indicator) {
			t.Logf("Detected crash indicator: %s", indicator)
			return true
		}
	}

	return false
}
