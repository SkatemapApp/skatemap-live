package test

import (
	"time"

	"load-testing/internal/testutil"
)

const (
	locationTTL           = 30 * time.Second
	cleanupInterval       = 10 * time.Second
	messageCollectionTime = 30 * time.Second
	cleanupWaitTime       = 45 * time.Second
)

func (s *SmokeTestSuite) TestLocationExpiry() {
	t := s.T()

	skaters := testutil.StartSkaters(t, s.railwayURL, 1, 1, "2s")
	eventID := skaters.EventIDs[0]
	t.Logf("Event ID: %s", eventID)

	time.Sleep(messageCollectionTime)

	skaters.Stop(t)
	t.Logf("Stopped skaters at %s", time.Now().Format(time.RFC3339))

	time.Sleep(cleanupWaitTime)
	t.Logf("Waited %s for cleanup", cleanupWaitTime)

	viewer := testutil.StartViewers(t, s.railwayURL, []string{eventID})
	time.Sleep(10 * time.Second)
	viewer.Stop(t)

	skaterIDs := testutil.ExtractSkaterIDs(t, viewer.MetricsFile)
	s.Assert().Equal(0, len(skaterIDs), "Should see 0 skaters after expiry - all locations should be cleaned up")
	t.Logf("Verified 0 skaters visible after expiry")

	testutil.AssertNoErrors(t, skaters.MetricsFile)
	testutil.AssertNoErrors(t, viewer.MetricsFile)

	s.Assert().False(testutil.DetectCrash(t), "No crashes should occur during location expiry test")
}
