package main

import (
	"strings"
	"testing"

	"github.com/google/uuid"
)

func TestParseEventIDs_EmptyString(t *testing.T) {
	eventIDs, err := parseEventIDs("", 3)
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	if len(eventIDs) != 3 {
		t.Fatalf("Expected 3 event IDs, got %d", len(eventIDs))
	}

	for i, id := range eventIDs {
		if _, err := uuid.Parse(id); err != nil {
			t.Errorf("Event ID %d is not a valid UUID: %s", i, id)
		}
	}
}

func TestParseEventIDs_ValidSingleID(t *testing.T) {
	testID := uuid.New().String()
	eventIDs, err := parseEventIDs(testID, 1)
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	if len(eventIDs) != 1 {
		t.Fatalf("Expected 1 event ID, got %d", len(eventIDs))
	}

	if eventIDs[0] != testID {
		t.Errorf("Expected event ID %s, got %s", testID, eventIDs[0])
	}
}

func TestParseEventIDs_ValidMultipleIDs(t *testing.T) {
	testID1 := uuid.New().String()
	testID2 := uuid.New().String()
	testID3 := uuid.New().String()

	input := strings.Join([]string{testID1, testID2, testID3}, ",")
	eventIDs, err := parseEventIDs(input, 3)
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	if len(eventIDs) != 3 {
		t.Fatalf("Expected 3 event IDs, got %d", len(eventIDs))
	}

	if eventIDs[0] != testID1 || eventIDs[1] != testID2 || eventIDs[2] != testID3 {
		t.Errorf("Event IDs do not match expected values")
	}
}

func TestParseEventIDs_WithWhitespace(t *testing.T) {
	testID1 := uuid.New().String()
	testID2 := uuid.New().String()

	input := testID1 + " , " + testID2
	eventIDs, err := parseEventIDs(input, 2)
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	if len(eventIDs) != 2 {
		t.Fatalf("Expected 2 event IDs, got %d", len(eventIDs))
	}

	if eventIDs[0] != testID1 || eventIDs[1] != testID2 {
		t.Errorf("Event IDs do not match expected values (whitespace not trimmed)")
	}
}

func TestParseEventIDs_CountMismatch(t *testing.T) {
	testID1 := uuid.New().String()
	testID2 := uuid.New().String()

	input := strings.Join([]string{testID1, testID2}, ",")
	_, err := parseEventIDs(input, 3)

	if err == nil {
		t.Fatal("Expected error for count mismatch, got nil")
	}

	expectedMsg := "number of provided event IDs (2) does not match --events (3)"
	if !strings.Contains(err.Error(), expectedMsg) {
		t.Errorf("Expected error message to contain %q, got: %s", expectedMsg, err.Error())
	}
}

func TestParseEventIDs_InvalidUUID(t *testing.T) {
	input := "not-a-uuid"
	_, err := parseEventIDs(input, 1)

	if err == nil {
		t.Fatal("Expected error for invalid UUID, got nil")
	}

	expectedMsg := "invalid UUID format"
	if !strings.Contains(err.Error(), expectedMsg) {
		t.Errorf("Expected error message to contain %q, got: %s", expectedMsg, err.Error())
	}
}

func TestParseEventIDs_MultipleIDsOneInvalid(t *testing.T) {
	testID1 := uuid.New().String()
	invalidID := "invalid-uuid"
	testID3 := uuid.New().String()

	input := strings.Join([]string{testID1, invalidID, testID3}, ",")
	_, err := parseEventIDs(input, 3)

	if err == nil {
		t.Fatal("Expected error for invalid UUID, got nil")
	}

	expectedMsg := "invalid UUID format for event ID 2"
	if !strings.Contains(err.Error(), expectedMsg) {
		t.Errorf("Expected error message to contain %q, got: %s", expectedMsg, err.Error())
	}
}

func TestParseEventIDs_EmptyUUID(t *testing.T) {
	testID1 := uuid.New().String()
	input := testID1 + ",,"

	_, err := parseEventIDs(input, 3)

	if err == nil {
		t.Fatal("Expected error for empty UUID, got nil")
	}

	expectedMsg := "invalid UUID format"
	if !strings.Contains(err.Error(), expectedMsg) {
		t.Errorf("Expected error message to contain %q, got: %s", expectedMsg, err.Error())
	}
}