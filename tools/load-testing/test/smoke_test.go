package test

import (
	"net/http"
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

type SmokeTestSuite struct {
	suite.Suite
	railwayURL string
}

func (s *SmokeTestSuite) SetupSuite() {
	s.railwayURL = os.Getenv("RAILWAY_URL")
	s.Require().NotEmpty(s.railwayURL, "RAILWAY_URL environment variable required")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Get(s.railwayURL + "/health")
	s.Require().NoError(err, "Failed to connect to Railway service")
	defer resp.Body.Close()

	s.Require().Equal(200, resp.StatusCode, "Railway service must be healthy")
}

func TestSmokeTestSuite(t *testing.T) {
	suite.Run(t, new(SmokeTestSuite))
}
