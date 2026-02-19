# Architecture

**Analysis Date:** 2025-02-14

## Pattern Overview

**Overall:** Client-Server architecture with a decoupled React/Next.js frontend and a Spring Boot REST API backend.

**Key Characteristics:**
- **Layered Backend:** Follows standard Spring Boot layering (Controller -> Service -> Strategy/Data).
- **Plugin-based Strategy System:** Trading strategies are auto-discovered components implementing a common interface.
- **API Proxy:** The frontend uses a Next.js API route as a proxy to avoid CORS issues and simplify internal networking in Docker.

## Layers

**Frontend (UI Layer):**
- Purpose: Provides the user interface for configuring and viewing backtests.
- Location: `frontend/`
- Contains: Next.js pages, Tailwind CSS components, and charting logic.
- Depends on: Backend REST API (via Proxy).
- Used by: End users.

**API Proxy Layer:**
- Purpose: Forwards client requests to the backend service.
- Location: `frontend/app/api/[...path]/route.ts`
- Contains: Next.js Route Handlers.
- Depends on: Backend service (`http://backend:8080`).
- Used by: Frontend client-side code.

**REST API Layer:**
- Purpose: Defines the external interface for the backend logic.
- Location: `backend/src/main/java/com/quantbackengine/backend/controller/`
- Contains: Spring `@RestController` classes.
- Depends on: Service Layer.
- Used by: Frontend (via Proxy).

**Service Layer:**
- Purpose: Orchestrates business logic (backtesting, data loading).
- Location: `backend/src/main/java/com/quantbackengine/backend/service/`
- Contains: Spring `@Service` classes like `BacktestService.java` and `MarketDataService.java`.
- Depends on: Strategy Engine, TA4J Library, File System.
- Used by: REST API Layer.

**Strategy Engine:**
- Purpose: Implements specific trading logic and signal generation.
- Location: `backend/src/main/java/com/quantbackengine/backend/strategy/`
- Contains: `TradingStrategy.java` interface and its implementations (e.g., `SmaStrategy.java`).
- Depends on: TA4J indicators and rules.
- Used by: Service Layer (via `StrategyRegistry.java`).

## Data Flow

**Backtest Execution:**

1. **Request:** The user submits a backtest configuration from `frontend/app/page.tsx`.
2. **Proxy:** Next.js Route Handler `frontend/app/api/[...path]/route.ts` forwards the POST request to the backend.
3. **Controller:** `BacktestController.java` receives the request and validates the input.
4. **Service:** `BacktestService.java` retrieves the requested strategy from `StrategyRegistry.java`.
5. **Data Loading:** `MarketDataService.java` loads historical CSV data from `uploads/` or `src/main/resources/data/`.
6. **Execution:** `BacktestService.java` uses the TA4J library to execute the strategy rules against the bar series.
7. **Processing:** The service calculates performance metrics (Sharpe ratio, drawdown, etc.) and generates the equity curve.
8. **Response:** A `BacktestResponse` DTO is returned through the layers back to the frontend.

**State Management:**
- **Frontend:** Primarily local component state and React hooks for fetching data.
- **Backend:** Stateless REST API. Market data is cached in memory within `MarketDataService.java` for performance.

## Key Abstractions

**TradingStrategy:**
- Purpose: Interface for defining trading logic, parameters, and metadata.
- Examples: `SmaStrategy.java`, `RsiStrategy.java`, `BollingerBandsStrategy.java`.
- Pattern: Strategy Pattern with auto-discovery via Spring's DI.

**BarSeries (TA4J):**
- Purpose: Represents a series of OHLCV bars.
- Pattern: Time-series container.

**BacktestRun:**
- Purpose: Represents the results and metadata of a completed backtest.
- Examples: `BacktestResponse.java`.

## Entry Points

**Backend Application:**
- Location: `backend/src/main/java/com/quantbackengine/backend/QuantBackEngineApplication.java`
- Triggers: Main execution via Spring Boot.
- Responsibilities: Bootstrapping the Spring context, auto-scanning components, starting the embedded Tomcat server.

**Frontend Application:**
- Location: `frontend/app/page.tsx`
- Triggers: User navigation to the root URL.
- Responsibilities: Main dashboard layout and initial data fetching (available strategies and symbols).

## Error Handling

**Strategy:** Global centralized exception handling for the REST API.

**Patterns:**
- **RestControllerAdvice:** `GlobalExceptionHandler.java` catches exceptions and converts them to standardized JSON error responses.
- **Client-Side:** Frontend handles error status codes and displays alerts or messages to the user.

## Cross-Cutting Concerns

**Logging:** Uses SLF4J with Logback (Spring default) in the backend. Proxy requests are logged in the Next.js server logs.
**Validation:** JSR-303 Bean Validation in backend DTOs (e.g., `BacktestRequest.java`).
**Authentication:** Permissive `SecurityConfig.java` for the current development phase (can be extended with JWT/OAuth2).
**API Documentation:** OpenAPI 3.0 (Swagger) available via `backend/src/main/java/com/quantbackengine/backend/config/OpenApiConfig.java`.

---

*Architecture analysis: 2025-02-14*
