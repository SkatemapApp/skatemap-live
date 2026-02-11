package test

import (
	"time"

	"load-testing/internal/testutil"
)

const (
	eventIsolationTestDuration = 15 * time.Second
)

func (s *SmokeTestSuite) TestEventIsolation() {
	t := s.T()

	eventA := testutil.StartSkaters(t, s.railwayURL, 1, 1, "2s")
	eventB := testutil.StartSkaters(t, s.railwayURL, 1, 1, "2s")

	viewerA := testutil.StartViewers(t, s.railwayURL, eventA.EventIDs)
	viewerB := testutil.StartViewers(t, s.railwayURL, eventB.EventIDs)

	t.Logf("Event A ID: %s", eventA.EventIDs[0])
	t.Logf("Event B ID: %s", eventB.EventIDs[0])

	time.Sleep(eventIsolationTestDuration)

	eventA.Stop(t)
	eventB.Stop(t)
	viewerA.Stop(t)
	viewerB.Stop(t)

	testutil.AssertNoErrors(t, eventA.MetricsFile)
	testutil.AssertNoErrors(t, eventB.MetricsFile)
	testutil.AssertNoErrors(t, viewerA.MetricsFile)
	testutil.AssertNoErrors(t, viewerB.MetricsFile)

	viewerACount := testutil.CountRecords(t, viewerA.MetricsFile)
	viewerBCount := testutil.CountRecords(t, viewerB.MetricsFile)

	s.Assert().Greater(viewerACount, 2, "Event A viewer should have received messages")
	s.Assert().Greater(viewerBCount, 2, "Event B viewer should have received messages")

	skaterIDsA := testutil.ExtractSkaterIDs(t, viewerA.MetricsFile)
	skaterIDsB := testutil.ExtractSkaterIDs(t, viewerB.MetricsFile)

	t.Logf("Event A viewer saw %d unique skaters", len(skaterIDsA))
	t.Logf("Event B viewer saw %d unique skaters", len(skaterIDsB))

	for skaterID := range skaterIDsA {
		s.Assert().False(skaterIDsB[skaterID], "Skater %s from Event A should not appear in Event B viewer", skaterID)
	}

	for skaterID := range skaterIDsB {
		s.Assert().False(skaterIDsA[skaterID], "Skater %s from Event B should not appear in Event A viewer", skaterID)
	}

	s.Assert().False(testutil.DetectCrash(t), "No crashes should occur during event isolation test")
}
