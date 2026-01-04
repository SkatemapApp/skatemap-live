package testutil

import (
	"strings"
	"testing"
)

func TestEventIDsJoinedWithCommas(t *testing.T) {
	tests := []struct {
		name     string
		eventIDs []string
		want     string
	}{
		{
			name:     "single event ID",
			eventIDs: []string{"event-1"},
			want:     "event-1",
		},
		{
			name:     "multiple event IDs",
			eventIDs: []string{"event-1", "event-2", "event-3"},
			want:     "event-1,event-2,event-3",
		},
		{
			name:     "UUID format event IDs",
			eventIDs: []string{"123e4567-e89b-12d3-a456-426614174000", "223e4567-e89b-12d3-a456-426614174001"},
			want:     "123e4567-e89b-12d3-a456-426614174000,223e4567-e89b-12d3-a456-426614174001",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := strings.Join(tt.eventIDs, ",")
			if got != tt.want {
				t.Errorf("strings.Join() = %q, want %q", got, tt.want)
			}

			parsed := strings.Split(got, ",")
			if len(parsed) != len(tt.eventIDs) {
				t.Errorf("After split, got %d events, want %d", len(parsed), len(tt.eventIDs))
			}

			for i, eventID := range tt.eventIDs {
				if parsed[i] != eventID {
					t.Errorf("Event %d: got %q, want %q", i, parsed[i], eventID)
				}
			}
		})
	}
}
