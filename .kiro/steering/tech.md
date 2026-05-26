# Tech Stack

## Sub-project 1: FinceptTerminal (C++/Qt Desktop App)

### Languages & Runtime
- C++20 (primary application code)
- Python 3.11+ (embedded analytics — spawned via `QProcess`, not linked)

### UI & Framework
- Qt 6.8.3 (pinned exactly — do not drift)
  - Qt6::Widgets, Charts, Network, Sql, Concurrent, Multimedia
  - Qt6::WebSockets (optional, feature-flagged)
  - Qt6::TextToSpeech (optional)
- Qt Advanced Docking System (QtADS 4.5.0) — detachable panels
- SingleApplication v3.5.4 — single-process enforcement

### Third-party (via CMake FetchContent)
- `md4c` 0.5.2 — Markdown to HTML parser
- `QGeoView` — interactive map widget (OSM tiles)
- `QXlsx` v1.4.9 — Excel I/O (requires Qt6::GuiPrivate)

### Build System
- CMake 3.27.7 + Ninja 1.11.1
- CMake presets defined in `CMakePresets.json`
- Unity build enabled (`CMAKE_UNITY_BUILD ON`, batch size 14)
- `compile_commands.json` exported for clang-tidy/clangd
- ccache/sccache auto-detected and used if present

### Compiler requirements (pinned — enforced at configure time)
| Platform | Compiler |
|----------|----------|
| Windows  | MSVC 19.38+ (VS 2022 17.8+) |
| Linux    | GCC 12.3+ |
| macOS    | Apple Clang 15.0+ (Xcode 15.2+) |

### Build commands
```bash
# From FinceptTerminal/fincept-qt/

# Configure (once, or after CMakeLists.txt changes)
cmake --preset linux-release    # or macos-release / win-release
cmake --preset linux-debug      # debug variant

# Build (every code change)
cmake --build --preset linux-release

# Run
./build/linux-release/FinceptTerminal

# Tests (opt-in)
cmake --preset linux-release -DFINCEPT_BUILD_TESTS=ON
ctest --test-dir build/linux-release
```

### Key CMake flags
- `-DFINCEPT_ALLOW_QT_DRIFT=ON` — allow non-pinned Qt version (local only, never CI/release)
- `-DFINCEPT_BUILD_TESTS=ON` — build test suite (off by default)

---

## Sub-project 2: QuantBackEngine (Spring Boot + Next.js Backtesting App)

### Backend
- Java 21, Spring Boot 3.2.0
- Maven 3.9+ (use `./mvnw`)
- ta4j-core 0.15 — technical analysis / strategy engine
- SpringDoc OpenAPI 2.3.0 — Swagger UI at `/swagger-ui.html`
- H2 (dev) / PostgreSQL 16 (production)

### Frontend
- Next.js 14.2.0, React 18.2.0, TypeScript 5.3.3
- Tailwind CSS 3.4.0
- lightweight-charts 4.1.0 (TradingView charts)

### Infrastructure
- Docker + Docker Compose (orchestrates backend, frontend, postgres)

### Build & run commands
```bash
# Backend
cd backend
./mvnw test               # run tests
./mvnw spring-boot:run    # run locally

# Frontend
cd frontend
npm install
npm run lint              # lint
npm run build             # production build

# Full stack
docker-compose up --build
```
