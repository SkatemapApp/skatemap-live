package testutil

import (
	"bufio"
	"fmt"
	"net/url"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"testing"
	"time"
)

type Process struct {
	cmd         *exec.Cmd
	EventIDs    []string
	MetricsFile string
}

var eventIDPattern = regexp.MustCompile(`Generated event IDs: \[(.*?)\]`)

func validateURL(t *testing.T, targetURL string) {
	t.Helper()

	parsed, err := url.Parse(targetURL)
	if err != nil {
		t.Fatalf("Invalid target URL %q: %v", targetURL, err)
	}

	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		t.Fatalf("Invalid URL scheme %q: must be http or https", parsed.Scheme)
	}

	if parsed.Host == "" {
		t.Fatalf("Invalid target URL %q: missing host", targetURL)
	}
}

func StartSkaters(t *testing.T, targetURL string, events, skatersPerEvent int, interval string) *Process {
	t.Helper()
	validateURL(t, targetURL)

	tempDir := t.TempDir()
	metricsFile := filepath.Join(tempDir, "skaters.csv")

	cmd := exec.Command("../bin/simulate-skaters",
		"--target-url", targetURL,
		"--events", fmt.Sprintf("%d", events),
		"--skaters-per-event", fmt.Sprintf("%d", skatersPerEvent),
		"--update-interval", interval,
		"--metrics-file", metricsFile,
	)

	stderr, err := cmd.StderrPipe()
	if err != nil {
		t.Fatalf("Failed to create stderr pipe: %v", err)
	}

	if err := cmd.Start(); err != nil {
		t.Fatalf("Failed to start simulate-skaters: %v", err)
	}

	t.Cleanup(func() {
		if cmd.Process != nil {
			cmd.Process.Signal(syscall.SIGTERM)
			cmd.Wait()
		}
	})

	scanner := bufio.NewScanner(stderr)
	eventIDs := parseEventIDs(t, scanner)

	return &Process{
		cmd:         cmd,
		EventIDs:    eventIDs,
		MetricsFile: metricsFile,
	}
}

func StartSkatersWithEventID(t *testing.T, targetURL, eventID string, skatersPerEvent int, interval string) *Process {
	t.Helper()
	validateURL(t, targetURL)

	tempDir := t.TempDir()
	metricsFile := filepath.Join(tempDir, "skaters.csv")

	cmd := exec.Command("../bin/simulate-skaters",
		"--target-url", targetURL,
		"--event-id", eventID,
		"--skaters-per-event", fmt.Sprintf("%d", skatersPerEvent),
		"--update-interval", interval,
		"--metrics-file", metricsFile,
	)

	if err := cmd.Start(); err != nil {
		t.Fatalf("Failed to start simulate-skaters: %v", err)
	}

	t.Cleanup(func() {
		if cmd.Process != nil {
			cmd.Process.Signal(syscall.SIGTERM)
			cmd.Wait()
		}
	})

	return &Process{
		cmd:         cmd,
		EventIDs:    []string{eventID},
		MetricsFile: metricsFile,
	}
}

func StartViewers(t *testing.T, targetURL string, eventIDs []string) *Process {
	t.Helper()
	validateURL(t, targetURL)

	tempDir := t.TempDir()
	metricsFile := filepath.Join(tempDir, "viewers.csv")

	args := []string{
		"--target-url", targetURL,
		"--viewers-per-event", "1",
		"--metrics-file", metricsFile,
	}

	args = append(args, "--events", strings.Join(eventIDs, ","))

	cmd := exec.Command("../bin/simulate-viewers", args...)

	if err := cmd.Start(); err != nil {
		t.Fatalf("Failed to start simulate-viewers: %v", err)
	}

	t.Cleanup(func() {
		if cmd.Process != nil {
			cmd.Process.Signal(syscall.SIGTERM)
			cmd.Wait()
		}
	})

	return &Process{
		cmd:         cmd,
		EventIDs:    eventIDs,
		MetricsFile: metricsFile,
	}
}

func (p *Process) Stop(t *testing.T) {
	t.Helper()

	if p.cmd.Process != nil {
		if err := p.cmd.Process.Signal(syscall.SIGTERM); err != nil {
			t.Logf("Failed to send SIGTERM: %v", err)
		}
		p.cmd.Wait()
	}
}

func parseEventIDs(t *testing.T, scanner *bufio.Scanner) []string {
	t.Helper()

	timeout := time.After(10 * time.Second)
	done := make(chan []string, 1)
	quit := make(chan struct{})

	go func() {
		defer close(done)
		var foundEventIDs []string
		for scanner.Scan() {
			select {
			case <-quit:
				return
			default:
			}

			line := scanner.Text()
			t.Logf("simulate-skaters: %s", line)

			if foundEventIDs == nil {
				if matches := eventIDPattern.FindStringSubmatch(line); len(matches) > 1 {
					eventIDsStr := matches[1]

					eventIDExtract := regexp.MustCompile(`[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`)
					eventIDs := eventIDExtract.FindAllString(eventIDsStr, -1)

					if len(eventIDs) > 0 {
						foundEventIDs = eventIDs
						done <- eventIDs
					}
				}
			}
		}
		if foundEventIDs == nil {
			done <- nil
		}
	}()

	select {
	case eventIDs := <-done:
		if eventIDs == nil {
			t.Fatal("Failed to parse Event IDs from simulate-skaters output")
		}
		return eventIDs
	case <-timeout:
		close(quit)
		t.Fatal("Timeout waiting for Event IDs from simulate-skaters")
		return nil
	}
}
