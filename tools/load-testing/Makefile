.PHONY: build test smoke-test help

help:
	@echo "Available targets:"
	@echo "  build       - Build simulation binaries"
	@echo "  test        - Run unit tests"
	@echo "  smoke-test  - Run smoke tests (requires RAILWAY_URL)"
	@echo "  help        - Show this help message"

build:
	@echo "Building simulation binaries..."
	@mkdir -p bin
	go build -o bin/simulate-skaters ./cmd/simulate-skaters
	go build -o bin/simulate-viewers ./cmd/simulate-viewers
	@echo "Built: bin/simulate-skaters, bin/simulate-viewers"

test:
	@echo "Running unit tests..."
	go test ./internal/... -v

smoke-test: build
	@if [ -z "$(RAILWAY_URL)" ]; then \
		echo "Error: RAILWAY_URL environment variable required"; \
		echo "Example: RAILWAY_URL=<railway-url> make smoke-test"; \
		exit 1; \
	fi
	@echo "Running smoke tests against $(RAILWAY_URL)..."
	go test ./test/... -v -timeout 2h
