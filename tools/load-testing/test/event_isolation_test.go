package test

import (
	"time"

	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/testutil"
)

const (
	eventIsolationTestDuration = 30 * time.Second
)

func (s *SmokeTestSuite) TestEventIsolation() {
	t := s.T()

	eventA := testutil.StartSkaters(t, s.railwayURL, 1, 3, "2s")
	eventB := testutil.StartSkaters(t, s.railwayURL, 1, 3, "2s")

	viewerA := testutil.StartViewers(t, s.railwayURL, eventA.EventIDs)
	viewerB := testutil.StartViewers(t, s.railwayURL, eventB.EventIDs)

	t.Logf("Event A ID: %s", eventA.EventIDs[0])
	t.Logf("Event B ID: %s", eventB.EventIDs[0])

	time.Sleep(eventIsolationTestDuration)

	viewerA.Stop(t)
	viewerB.Stop(t)

	testutil.AssertNoErrors(t, eventA.MetricsFile)
	testutil.AssertNoErrors(t, eventB.MetricsFile)
	testutil.AssertNoErrors(t, viewerA.MetricsFile)
	testutil.AssertNoErrors(t, viewerB.MetricsFile)

	viewerACount := testutil.CountRecords(t, viewerA.MetricsFile)
	viewerBCount := testutil.CountRecords(t, viewerB.MetricsFile)

	s.Assert().Greater(viewerACount, 5, "Event A viewer should have received messages")
	s.Assert().Greater(viewerBCount, 5, "Event B viewer should have received messages")

	s.Assert().False(testutil.DetectCrash(t), "No crashes should occur during event isolation test")
}
