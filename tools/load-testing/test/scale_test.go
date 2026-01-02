package test

import (
	"time"

	"load-testing/internal/testutil"
)

const (
	scaleTestInitialRunTime = 2 * time.Minute
	scaleTestFinalRunTime   = 5 * time.Minute
)

func (s *SmokeTestSuite) TestScale() {
	t := s.T()

	initialSkatersPerEvent := 5
	additionalSkaters := 5
	totalSkaters := initialSkatersPerEvent + additionalSkaters

	eventA := testutil.StartSkaters(t, s.railwayURL, 1, initialSkatersPerEvent, "3s")
	eventB := testutil.StartSkaters(t, s.railwayURL, 1, initialSkatersPerEvent, "3s")

	t.Logf("Event A ID: %s", eventA.EventIDs[0])
	t.Logf("Event B ID: %s", eventB.EventIDs[0])

	time.Sleep(scaleTestInitialRunTime)

	eventAMore := testutil.StartSkatersWithEventID(t, s.railwayURL, eventA.EventIDs[0], additionalSkaters, "3s")

	t.Logf("Added %d more skaters to Event A (now %d total)", additionalSkaters, totalSkaters)

	time.Sleep(scaleTestFinalRunTime)

	eventA.Stop(t)
	eventB.Stop(t)
	eventAMore.Stop(t)

	testutil.AssertNoErrors(t, eventA.MetricsFile)
	testutil.AssertNoErrors(t, eventB.MetricsFile)
	testutil.AssertNoErrors(t, eventAMore.MetricsFile)

	s.Assert().False(testutil.DetectCrash(t), "No crashes should occur during scale test")
}
