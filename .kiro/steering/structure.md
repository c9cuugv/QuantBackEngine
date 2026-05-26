# Project Structure

This monorepo contains two independent sub-projects. Do not mix concerns between them.

---

## Top-level layout

```
/
├── FinceptTerminal/          # Sub-project 1: C++/Qt6 desktop app (the main product)
├── backend/                  # Sub-project 2: Spring Boot backtesting API
├── frontend/                 # Sub-project 2: Next.js backtesting UI
├── docker-compose.yml        # Orchestrates backend + frontend + postgres
├── start.sh                  # Quick-start script for QuantBackEngine
└── .kiro/                    # Kiro specs, steering, hooks
```

---

## Sub-project 1: FinceptTerminal/fincept-qt/

```
fincept-qt/
├── CMakeLists.txt            # Single root build file — all sources registered here
├── CMakePresets.json         # Platform presets (win/linux/macos x release/debug)
├── src/
│   ├── app/                  # Entry point (main.cpp, MainWindow, ScreenRouter)
│   ├── core/                 # Shared infrastructure
│   │   ├── config/           # AppConfig, AppPaths, ProfileManager
│   │   ├── events/           # EventBus (pub/sub for cross-module comms)
│   │   ├── logging/          # Logger — use LOG_INFO("tag", "msg")
│   │   ├── result/           # Result<T> error handling type
│   │   └── session/          # SessionManager, ScreenStateManager
│   ├── ui/                   # Reusable Qt widgets (Obsidian design system)
│   │   ├── theme/            # Color tokens, stylesheets
│   │   ├── widgets/          # Card, SearchBar, StatusBadge, TabHeader, etc.
│   │   ├── tables/           # DataTable
│   │   ├── charts/           # ChartFactory
│   │   └── navigation/       # NavigationBar, FKeyBar, StatusBar, ToolBar
│   ├── network/
│   │   ├── http/             # HttpClient (QNetworkAccessManager wrapper)
│   │   └── websocket/        # WebSocketClient
│   ├── storage/
│   │   ├── sqlite/           # Database, CacheDatabase, migrations/ (v001-v020+)
│   │   ├── cache/            # CacheManager, TabSessionStore
│   │   ├── secure/           # SecureStorage (encrypted credentials)
│   │   └── repositories/     # Data access objects (20+ repositories)
│   ├── auth/                 # AuthManager, AuthApi, SessionGuard, PinManager
│   ├── python/               # PythonRunner, PythonWorker, PythonSetupManager
│   ├── datahub/              # DataHub — in-process pub/sub data layer
│   ├── ai_chat/              # LlmService, AiChatScreen, AiChatBubble
│   ├── mcp/                  # MCP (Model Context Protocol) integration + tools
│   ├── trading/              # BrokerRegistry, UnifiedTrading, PaperTrading
│   │   ├── brokers/          # 16 broker implementations
│   │   ├── websocket/        # Broker WebSocket clients
│   │   └── instruments/      # InstrumentRepository, parsers
│   ├── services/             # Data services (MarketData, News, AkShare, etc.)
│   └── screens/              # All terminal screens (40+)
│       ├── auth/             # Login, Register, ForgotPassword, Pricing
│       ├── dashboard/        # Dashboard + widgets
│       ├── markets/          # Market data screens
│       ├── news/             # News aggregation
│       ├── crypto_trading/   # Crypto trading
│       └── ...               # watchlist, portfolio, settings, etc.
├── scripts/                  # Python analytics scripts (100+)
│   └── Analytics/            # CFA-level analytics modules
└── resources/                # App icons, component catalog, demo data
```

### Key architectural rules (C++ app)

- Screens render UI only — no HTTP calls, no business logic
- Services own all data fetching, caching, and processing
- Screens communicate with services via Qt signals/slots only
- DataHub (`src/datahub/`) is the single data distribution layer — screens subscribe, services publish; no widget should own a data-refresh `QTimer`
- Use `Result<T>` for error handling, not raw error codes or exceptions
- Use `LOG_INFO("tag", "msg")` / `LOG_ERROR(...)` for logging
- Use `EventBus::instance().publish(...)` for cross-module events
- Use `AppConfig::instance()` for constants — no magic strings
- UI code runs on the main thread only; background work via `QThread` or `QtConcurrent`
- All new source files must be registered in `CMakeLists.txt`

### Where to add new code (C++ app)

| What | Where |
|------|-------|
| New screen | `src/screens/<domain>/` + register in `CMakeLists.txt` |
| New data service | `src/services/<domain>/` + implement `Producer` interface for DataHub |
| New broker | `src/trading/brokers/<name>/` implementing `BrokerInterface` |
| New MCP tool | `src/mcp/tools/` |
| New reusable widget | `src/ui/widgets/` |
| New DB migration | `src/storage/sqlite/migrations/v0NN_<name>.cpp` |
| New Python analytics script | `scripts/Analytics/<domain>/` |

---

## Sub-project 2: QuantBackEngine (backend/ + frontend/)

```
backend/
├── src/main/java/com/quantbackengine/backend/
│   ├── config/               # Spring configuration (Security, OpenAPI)
│   ├── controller/           # REST controllers
│   ├── domain/               # JPA entities
│   ├── dto/                  # Request/Response objects
│   ├── exception/            # GlobalExceptionHandler (@RestControllerAdvice)
│   ├── service/              # Business logic (BacktestService, MarketDataService)
│   └── strategy/             # TradingStrategy interface + implementations
├── src/main/resources/
│   ├── application.properties
│   └── data/                 # Default CSV market data
└── src/test/java/            # JUnit 5 tests (mirrors main package structure)

frontend/
├── app/                      # Next.js App Router
│   ├── page.tsx              # Main dashboard
│   └── api/[...path]/        # Proxy to backend:8080
├── components/               # React components (TradingChart, EquityCurve, etc.)
└── lib/                      # Frontend utilities
```

### Where to add new code (QuantBackEngine)

| What | Where |
|------|-------|
| New trading strategy | `backend/.../strategy/` — implement `TradingStrategy`, annotate `@Component` |
| New API endpoint | `backend/.../controller/` |
| New UI component | `frontend/components/` |
| New page | `frontend/app/<name>/page.tsx` |
| Frontend utilities | `frontend/lib/` |
