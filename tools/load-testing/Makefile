.PHONY: build test fmt smoke-test install-tools help

install-tools:
	@echo "Installing development tools..."
	@grep _ tools.go | awk -F'"' '{print $$2}' | while read -r tool; do \
		base=$$(echo "$$tool" | grep -oE '^[^/]+/[^/]+/[^/]+'); \
		version=$$(go list -m -f '{{.Version}}' "$$base" 2>/dev/null || echo "latest"); \
		echo "  Installing $$tool@$$version..."; \
		go install "$$tool@$$version"; \
	done
	@echo "✓ All tools installed"

help:
	@echo "Available targets:"
	@echo "  install-tools - Install development tools (goimports, etc.)"
	@echo "  build         - Build simulation binaries"
	@echo "  test          - Run unit tests"
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
