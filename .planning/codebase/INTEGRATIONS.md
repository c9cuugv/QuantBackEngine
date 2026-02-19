# External Integrations

**Analysis Date:** 2024-03-21

## APIs & External Services

**Market Data:**
- **Yahoo Finance API** - Client library included for historical/live market data retrieval.
  - SDK/Client: `com.yahoofinance-api:YahooFinanceAPI:3.17.0`
  - Implementation: Not yet active in `MarketDataService.java`.
- **Finnhub API** - External provider for market data.
  - Auth: `FINNHUB_API_KEY` env var.
  - Base URL: `https://finnhub.io/api/v1`
  - Implementation: Configuration present in `backend/src/main/resources/application.properties`.

**Technical Analysis:**
- **ta4j-core** - Core engine for strategy building and technical indicators.
  - SDK: `org.ta4j:ta4j-core:0.15`
  - Location: `backend/src/main/java/com/quantbackengine/backend/service/BacktestService.java`

**Data Visualization:**
- **TradingView Lightweight Charts** - Used for interactive price and equity charts.
  - Package: `lightweight-charts`
  - Location: `frontend/components/TradingChart.tsx`, `frontend/components/EquityCurve.tsx`

## Data Storage

**Databases:**
- **PostgreSQL (Production)**
  - Connection: `SPRING_DATASOURCE_URL`
  - Client: Spring Data JPA (Hibernate)
  - Config: `backend/src/main/resources/application-docker.properties`
- **H2 (Development)**
  - Connection: `jdbc:h2:mem:quantdb`
  - Config: `backend/src/main/resources/application.properties`

**File Storage:**
- **Local Filesystem**
  - Purpose: Storage for uploaded CSV market data files.
  - Location: `/uploads` (container root) or `backend/uploads` (dev).
  - Config: `app.upload.dir` in `application.properties`.

## Authentication & Identity

**Auth Provider:**
- **Custom / None**
  - Implementation: Authentication is currently disabled in `backend/src/main/java/com/quantbackengine/backend/config/SecurityConfig.java` (`anyRequest().permitAll()`).
  - Security Model: Backend is protected by network isolation (Docker) and proxied by the Next.js API layer.

## API Proxying

**Next.js Proxy:**
- **Server-Side API Route**
  - Implementation: All browser requests to `/api/*` are intercepted by Next.js and forwarded to the Spring Boot backend.
  - Logic: `frontend/app/api/[...path]/route.ts`
  - Destination: `http://backend:8080` (inside Docker network).

## Monitoring & Observability

**Logs:**
- **SLF4J / Logback**
  - Approach: Standard Spring Boot logging using Lombok's `@Slf4j`.

**API Documentation:**
- **SpringDoc OpenAPI (Swagger)**
  - UI: `/swagger-ui.html`
  - JSON Docs: `/api-docs`
  - Config: `backend/src/main/java/com/quantbackengine/backend/config/OpenApiConfig.java`

## CI/CD & Deployment

**Hosting:**
- **Docker / Docker Compose**
  - Orchestrates `backend`, `frontend`, and `db` (postgres) services.
  - File: `docker-compose.yml`

## Environment Configuration

**Required env vars:**
- `SPRING_DATASOURCE_URL`: PostgreSQL connection string.
- `SPRING_DATASOURCE_USERNAME`: PostgreSQL username.
- `SPRING_DATASOURCE_PASSWORD`: PostgreSQL password.
- `FINNHUB_API_KEY`: API key for market data (optional).
- `API_USERNAME`: Admin username for backend access.
- `API_PASSWORD`: Admin password for backend access.

**Secrets location:**
- Not explicitly configured in code; expected to be provided via environment variables in `docker-compose.yml` or a `.env` file (not committed).

---

*Integration audit: 2024-03-21*
