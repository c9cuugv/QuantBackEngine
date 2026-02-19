# Codebase Structure

**Analysis Date:** 2025-02-14

## Directory Layout

```
QuantBackEngine/
├── backend/                  # Spring Boot Java application
│   ├── src/main/java/        # Source code
│   │   └── com/.../backend/  # Root package
│   │       ├── config/       # Spring configuration
│   │       ├── controller/   # REST API controllers
│   │       ├── domain/       # Data models/Entities
│   │       ├── dto/          # Request/Response objects
│   │       ├── exception/    # Global error handling
│   │       ├── service/      # Business logic services
│   │       └── strategy/     # Trading strategies & registry
│   ├── src/main/resources/   # Application config & assets
│   │   ├── application.properties
│   │   └── data/             # Default CSV market data
│   ├── src/test/java/        # JUnit tests
│   ├── pom.xml               # Maven configuration
│   └── Dockerfile            # Container build for backend
├── frontend/                 # Next.js React application
│   ├── app/                  # Next.js App Router (pages & API proxy)
│   ├── components/           # Reusable UI components
│   ├── lib/                  # Frontend utilities & clients
│   ├── package.json          # Node.js dependencies
│   ├── tsconfig.json         # TypeScript configuration
│   └── Dockerfile            # Container build for frontend
├── uploads/                  # User-uploaded market data (CSV)
├── docker-compose.yml        # Multi-container orchestration
└── README.md                 # Project documentation
```

## Directory Purposes

**backend/src/main/java/.../strategy/:**
- Purpose: Contains the core trading logic and plugin system.
- Contains: `TradingStrategy.java` interface and implementations.
- Key files: `StrategyRegistry.java`, `SmaStrategy.java`.

**backend/src/main/java/.../service/:**
- Purpose: High-level orchestration and integration.
- Contains: Business logic services.
- Key files: `BacktestService.java`, `MarketDataService.java`.

**frontend/app/:**
- Purpose: Defines the routing structure and server-side API proxy.
- Contains: `page.tsx` (dashboard) and `api/` (proxy).
- Key files: `app/page.tsx`, `app/api/[...path]/route.ts`.

**frontend/components/:**
- Purpose: Modular UI elements.
- Contains: React components for charts, tables, and forms.
- Key files: `TradingChart.tsx`, `EquityCurve.tsx`.

## Key File Locations

**Entry Points:**
- `backend/src/main/java/.../QuantBackEngineApplication.java`: Spring Boot main.
- `frontend/app/page.tsx`: Main user interface.

**Configuration:**
- `backend/src/main/resources/application.properties`: Backend environment settings.
- `frontend/tailwind.config.js`: UI styling configuration.
- `docker-compose.yml`: Infrastructure and networking setup.

**Core Logic:**
- `backend/src/main/java/.../service/BacktestService.java`: Main backtest runner.
- `backend/src/main/java/.../strategy/TradingStrategy.java`: Strategy contract.

**Testing:**
- `backend/src/test/java/com/.../service/BacktestServiceTest.java`: Unit tests for backtesting.
- `backend/src/test/java/com/.../service/BacktestServiceBenchmarkTest.java`: Performance benchmarks.

## Naming Conventions

**Files:**
- **Backend:** PascalCase for classes (`SmaStrategy.java`), kebab-case for resources (`application-docker.properties`).
- **Frontend:** PascalCase for components (`TradingChart.tsx`), camelCase for utilities, kebab-case for styling.

**Directories:**
- **Backend:** Package-style dot notation (internally) or lowercase for folders (`controller`, `service`).
- **Frontend:** Lowercase for system folders (`app`, `lib`), PascalCase or lowercase for component folders.

## Where to Add New Code

**New Trading Strategy:**
- Implementation: Create a new class implementing `TradingStrategy` in `backend/src/main/java/.../strategy/` and annotate it with `@Component`.

**New UI Feature:**
- Dashboard components: Add to `frontend/components/`.
- New Page: Add a new folder and `page.tsx` in `frontend/app/`.

**New API Endpoint:**
- Backend: Create/Modify a `@RestController` in `backend/src/main/java/.../controller/`.
- Frontend: Use the proxy at `/api/v1/...` in client-side code.

**Utilities:**
- Backend helpers: Add to a new package `backend/src/main/java/.../util/`.
- Frontend helpers: Add to `frontend/lib/`.

## Special Directories

**uploads/:**
- Purpose: Persistent storage for user-uploaded CSV market data.
- Generated: No (initialized with sample data).
- Committed: No (usually ignored in `.gitignore`, but sample data may be in `backend/src/main/resources/data`).

**backend/target/ and frontend/.next/:**
- Purpose: Build artifacts and cache.
- Generated: Yes.
- Committed: No.

---

*Structure analysis: 2025-02-14*
