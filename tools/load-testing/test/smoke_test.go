package test

import (
	"net/http"
	"os"
	"testing"

	"github.com/stretchr/testify/suite"
)

type SmokeTestSuite struct {
	suite.Suite
	railwayURL string
}

func (s *SmokeTestSuite) SetupSuite() {
	s.railwayURL = os.Getenv("RAILWAY_URL")
	s.Require().NotEmpty(s.railwayURL, "RAILWAY_URL environment variable required")

	resp, err := http.Get(s.railwayURL + "/health")
	s.Require().NoError(err, "Failed to connect to Railway service")
	defer resp.Body.Close()

	s.Require().Equal(200, resp.StatusCode, "Railway service must be healthy")
}

func TestSmokeTestSuite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping smoke tests in short mode")
	}
	suite.Run(t, new(SmokeTestSuite))
}
