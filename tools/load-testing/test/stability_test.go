package test

import (
	"testing"
	"time"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/testutil"
)

const (
	stabilityTestDuration     = 30 * time.Minute
	stabilityCheckInterval    = 5 * time.Minute
	updateIntervalSeconds     = 3
	approximateUpdatesPerMin  = 60 / updateIntervalSeconds
)

func (s *SmokeTestSuite) TestStability() {
	t := s.T()

	if testing.Short() {
		t.Skip("Skipping stability test in short mode")
	}

	skatersPerEvent := 5
	durationMinutes := int(stabilityTestDuration.Minutes())
	expectedRecordsPerEvent := skatersPerEvent * approximateUpdatesPerMin * durationMinutes
	recordAssertionDelta := expectedRecordsPerEvent / 10

	eventA := testutil.StartSkaters(t, s.railwayURL, 1, skatersPerEvent, "3s")
	eventB := testutil.StartSkaters(t, s.railwayURL, 1, skatersPerEvent, "3s")

	t.Logf("Event A ID: %s", eventA.EventIDs[0])
	t.Logf("Event B ID: %s", eventB.EventIDs[0])
	t.Logf("Running stability test for %v with %d skaters per event", stabilityTestDuration, skatersPerEvent)

	checksRemaining := int(stabilityTestDuration / stabilityCheckInterval)
	ticker := time.NewTicker(stabilityCheckInterval)
	defer ticker.Stop()

	for i := 1; i <= checksRemaining; i++ {
		<-ticker.C
		elapsed := time.Duration(i) * stabilityCheckInterval
		t.Logf("Stability test progress: %v / %v", elapsed, stabilityTestDuration)

		s.Assert().False(testutil.DetectCrash(t), "No crashes should occur at %v checkpoint", elapsed)
	}

	recordsA := testutil.CountRecords(t, eventA.MetricsFile)
	recordsB := testutil.CountRecords(t, eventB.MetricsFile)

	t.Logf("Event A records: %d (expected ~%d)", recordsA, expectedRecordsPerEvent)
	t.Logf("Event B records: %d (expected ~%d)", recordsB, expectedRecordsPerEvent)

	s.Assert().InDelta(expectedRecordsPerEvent, recordsA, float64(recordAssertionDelta), "Event A should have expected update count")
	s.Assert().InDelta(expectedRecordsPerEvent, recordsB, float64(recordAssertionDelta), "Event B should have expected update count")

	testutil.AssertNoErrors(t, eventA.MetricsFile)
	testutil.AssertNoErrors(t, eventB.MetricsFile)
}
