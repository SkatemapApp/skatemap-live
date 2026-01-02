package test

import (
	"time"

	"load-testing/internal/testutil"
)

const (
	locationTTL              = 30 * time.Second
	cleanupInterval          = 10 * time.Second
	messageCollectionTime    = 30 * time.Second
	cleanupWaitTime          = 45 * time.Second
	expectedCleanupTime      = 35 * time.Second
	cleanupTimeAssertionDelta = 10 * time.Second
)

func (s *SmokeTestSuite) TestLocationExpiry() {
	t := s.T()

	skaters := testutil.StartSkaters(t, s.railwayURL, 1, 3, "2s")

	t.Logf("Event ID: %s", skaters.EventIDs[0])

	time.Sleep(messageCollectionTime)

	skaters.Stop(t)
	t.Logf("Stopped skaters")

	time.Sleep(cleanupWaitTime)

	lastUpdateTime := testutil.ParseLastLocationUpdateTime(t)
	cleanupTime := testutil.ParseCleanupTime(t)
	elapsed := cleanupTime.Sub(lastUpdateTime).Seconds()

	t.Logf("Last location update at %s (server time)", lastUpdateTime.Format(time.RFC3339))
	t.Logf("Cleanup occurred at %s (%.1f seconds after last update)", cleanupTime.Format(time.RFC3339), elapsed)

	s.Assert().InDelta(expectedCleanupTime.Seconds(), elapsed, cleanupTimeAssertionDelta.Seconds(), "Cleanup should occur within TTL + cleanup interval")

	testutil.CheckRailwayLogs(t, "removed 3 locations", true)

	s.Assert().False(testutil.DetectCrash(t), "No crashes should occur during location expiry test")
}
