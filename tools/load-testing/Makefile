.PHONY: build test test-unit test-load fmt smoke-test install-tools help

install-tools:
	@echo "Installing development tools..."
	@tools=$$(grep _ tools.go | awk -F'"' '{print $$2}'); \
	failed=0; \
	for tool in $$tools; do \
		base=$$(echo "$$tool" | grep -oE '^[^/]+/[^/]+/[^/]+'); \
		version=$$(go list -m -f '{{.Version}}' "$$base" 2>/dev/null || echo "latest"); \
		echo "  Installing $$tool@$$version..."; \
		if ! go install "$$tool@$$version"; then \
			echo "  ✗ Failed to install $$tool"; \
			failed=1; \
		fi; \
	done; \
	if [ $$failed -eq 1 ]; then \
		echo "✗ Tool installation failed"; \
		exit 1; \
	fi
	@echo "✓ All tools installed"

help:
	@echo "Available targets:"
	@echo "  install-tools - Install development tools (goimports, etc.)"
	@echo "  build         - Build simulation binaries"
	@echo "  test          - Run unit tests"
	@echo "  test-unit     - Run unit/integration tests (excludes load tests)"
	@echo "  test-load     - Run load tests only (requires RAILWAY_URL)"
	@echo "  fmt           - Format Go files with goimports"
	@echo "  smoke-test    - Run smoke tests (requires RAILWAY_URL)"
	@echo "  help          - Show this help message"

build:
	@echo "Building simulation binaries..."
	@mkdir -p bin
	go build -o bin/simulate-skaters ./cmd/simulate-skaters
	go build -o bin/simulate-viewers ./cmd/simulate-viewers
	@echo "Built: bin/simulate-skaters, bin/simulate-viewers"

test:
	@echo "Running unit tests..."
	go test ./internal/... -v

test-unit: build
	@if [ -z "$(RAILWAY_URL)" ]; then \
		echo "Error: RAILWAY_URL environment variable required"; \
		echo "Example: RAILWAY_URL=<railway-url> make test-unit"; \
		exit 1; \
	fi
	@echo "Running unit/integration tests (excluding load tests)..."
	go test ./test/... -v -timeout 30m

test-load: build
	@if [ -z "$(RAILWAY_URL)" ]; then \
		echo "Error: RAILWAY_URL environment variable required"; \
		echo "Example: RAILWAY_URL=<railway-url> make test-load"; \
		exit 1; \
	fi
	@echo "Running load tests..."
	go test -tags=load ./test/load/... -v -timeout 2h

fmt:
	@echo "Formatting Go files..."
	@if ! command -v goimports > /dev/null 2>&1; then \
		echo "Error: goimports not found in PATH"; \
		echo "Install with: make install-tools"; \
		exit 1; \
	fi
	goimports -w .
	@echo "Go files formatted successfully"

smoke-test: build
	@if [ -z "$(RAILWAY_URL)" ]; then \
		echo "Error: RAILWAY_URL environment variable required"; \
		echo "Example: RAILWAY_URL=<railway-url> make smoke-test"; \
		exit 1; \
	fi
	@echo "Running smoke tests against $(RAILWAY_URL)..."
	go test ./test/... -v -timeout 2h
