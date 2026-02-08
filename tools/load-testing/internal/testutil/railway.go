package testutil

import (
	"os/exec"
	"regexp"
	"strings"
	"testing"
)

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
