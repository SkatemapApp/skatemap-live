# Skatemap Live

Real-time location streaming for organised skating sessions.

## Repository Structure

```
skatemap-live/
├── services/api/          # Scala Play Framework service
├── tools/load-testing/    # Go load testing tools
└── docs/                  # Documentation and ADRs
```

### Services

- **`services/api/`**: Main location streaming API (Scala/Play/Pekko Streams)
  - [Service README](services/api/README.md)
  - WebSocket streaming, location storage, event isolation

### Tools

- **`tools/load-testing/`**: Load testing and simulation tools (Go)
  - Realistic skater behaviour simulation
  - Long-running stability tests (2-24 hours)

## Quick Start

### Run the API

```bash
cd services/api
sbt run
```

Server starts on `http://localhost:9000`

### Run Load Tests

```bash
cd tools/load-testing
make build
./bin/simulate-skaters --help
```

## Documentation

- [API Service Documentation](services/api/README.md)
- [System Design](docs/system-design/system-design.md)
- [Architecture Decision Records](docs/adr/)

## Contributing

This project follows a monorepo structure:
- **`services/`**: Production applications that serve users
- **`tools/`**: Development and operations tooling

Each directory owns its build system and dependencies.

## Development

### Git Hooks

Install git hooks for commit message validation and code formatting:

```bash
./scripts/setup-git-hooks.sh
```

This installs:
- **pre-commit**: Formats Scala, Go, and shell files automatically
- **commit-msg**: Validates conventional commit message format

### Branch Cleanup

Clean up local branches that have been merged:

```bash
./scripts/cleanup-merged-branches.sh
```

Preview without deleting:

```bash
./scripts/cleanup-merged-branches.sh --dry-run
```

**Requirements**: Git 2.10 or later (for `git for-each-ref` upstream tracking)
