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
- [Architecture Decision Records](docs/adr/)

## Contributing

This project follows a monorepo structure:
- **`services/`**: Production applications that serve users
- **`tools/`**: Development and operations tooling

Each directory owns its build system and dependencies.
