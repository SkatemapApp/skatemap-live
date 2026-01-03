package test

import (
	"time"

	"load-testing/internal/testutil"
)

const (
	websocketIdleTime            = 95 * time.Second
	initialMessageCollectionTime = 30 * time.Second
	resumeVerificationTime       = 15 * time.Second
)

func (s *SmokeTestSuite) TestWebSocketTimeout() {
	t := s.T()

	skaters := testutil.StartSkaters(t, s.railwayURL, 1, 3, "2s")
	eventID := skaters.EventIDs[0]

	viewer := testutil.StartViewers(t, s.railwayURL, skaters.EventIDs)

	t.Logf("Event ID: %s", eventID)

	time.Sleep(initialMessageCollectionTime)

	skaters.Stop(t)
	t.Logf("Stopped skaters, WebSocket now idle")

	beforeIdleCount := testutil.CountRecordsLive(t, viewer.MetricsFile)
	t.Logf("Message count before idle period: %d", beforeIdleCount)

	time.Sleep(websocketIdleTime)
	t.Logf("Waited %v (exceeds old 75s timeout)", websocketIdleTime)

	skaters2 := testutil.StartSkatersWithEventID(t, s.railwayURL, eventID, 3, "2s")
	defer skaters2.Stop(t)

	t.Logf("Restarted skaters with same Event ID")

	time.Sleep(resumeVerificationTime)

	afterResumeCount := testutil.CountRecordsLive(t, viewer.MetricsFile)
	t.Logf("Message count after resume: %d", afterResumeCount)

	s.Assert().Greater(afterResumeCount, beforeIdleCount, "Viewer should receive new messages through existing connection")

	viewer.Stop(t)
	testutil.AssertNoErrors(t, viewer.MetricsFile)

	s.Assert().False(testutil.DetectCrash(t), "No crashes should occur during websocket timeout test")
}
