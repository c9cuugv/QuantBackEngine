# Technology Stack

**Analysis Date:** 2025-01-20

## Languages

**Primary:**
- Java 21 - Backend API and trading engine logic.
- TypeScript 5.3.3 - Frontend Next.js application.

**Secondary:**
- SQL - Data storage (PostgreSQL/H2).
- Shell (Bash) - Setup and deployment scripts (`start.sh`).

## Runtime

**Environment:**
- JVM (OpenJDK 21) - Backend runtime.
- Node.js (v18+) - Frontend runtime for Next.js 14.
- Docker & Docker Compose - Containerized orchestration for all services.

**Package Manager:**
- Maven 3.9+ (using `mvnw`) - Backend dependencies and build.
  - Lockfile: `pom.xml` (versioned dependencies).
- npm (Node Package Manager) - Frontend dependencies.
  - Lockfile: `package-lock.json` (present).

## Frameworks

**Core:**
- Spring Boot 3.2.0 - REST API and backend service framework.
- Next.js 14.2.0 - Full-stack React framework for frontend.
- React 18.2.0 - UI library.
- Tailwind CSS 3.4.0 - Utility-first CSS framework for UI styling.

**Testing:**
- Spring Boot Starter Test (JUnit 5, AssertJ, Mockito) - Backend unit and integration testing.
- Test files located in `backend/src/test/java/`.

**Build/Dev:**
- Maven Wrapper (`mvnw`) - Reproducible backend builds.
- Dockerfile - Container definition for backend and frontend.
- Docker Compose - Multi-container setup (DB, Backend, Frontend).

## Key Dependencies

**Critical:**
- `ta4j-core` 0.15 - Technical analysis library used for trading strategy evaluation in `backend/src/main/java/com/quantbackengine/backend/service/BacktestService.java`.
- `lightweight-charts` 4.1.0 - TradingView charting library for interactive equity and price charts in `frontend/components/TradingChart.tsx`.

**Infrastructure:**
- `postgresql` 16-alpine (Docker) - Relational database for persistent storage.
- `h2` - In-memory database for local development and integration tests.
- `springdoc-openapi-starter-webmvc-ui` 2.3.0 - Swagger/OpenAPI documentation generator.

## Configuration

**Environment:**
- Configured via `application.properties` and `application-docker.properties`.
- Environment variables in `docker-compose.yml` (e.g., `SPRING_DATASOURCE_URL`, `POSTGRES_PASSWORD`).

**Build:**
- `backend/pom.xml`: Maven configuration.
- `frontend/package.json`: Node dependencies and scripts.
- `frontend/tsconfig.json`: TypeScript configuration.

## Platform Requirements

**Development:**
- Java 21, Node.js v18+, Docker Desktop.

**Production:**
- Linux container environment (Docker Compose).
- Minimum 2GB RAM (1GB for backend, 512MB for frontend and DB).

---

*Stack analysis: 2025-01-20*
