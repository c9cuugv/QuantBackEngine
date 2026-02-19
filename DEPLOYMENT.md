# QuantBackEngine – Deployment & Clean Install Guide

## Prerequisites

| Tool          | Version  | Check Command         |
|---------------|----------|-----------------------|
| Java (JDK)    | 21+      | `java -version`       |
| Maven         | 3.9+     | `mvn -version` (or use the included `./mvnw` wrapper) |
| Node.js       | 20 LTS+  | `node -v`             |
| npm           | 10+      | `npm -v`              |
| Docker        | 24+      | `docker --version`    |
| Docker Compose| v2+      | `docker compose version` |

---

## Option A – Docker (Recommended for Production)

The easiest way to spin up the entire stack:

```bash
# From the project root:
docker compose up --build --force-recreate -d
```

To tear everything down (including volumes):

```bash
docker compose down -v
```

---

## Option B – Local Development (Without Docker)

### 1. Backend (Java / Spring Boot)

```bash
cd backend

# Clean install – downloads all Maven dependencies and builds the JAR
./mvnw clean install

# Run the application
./mvnw spring-boot:run
```

The API will be available at **http://localhost:8080**.

### 2. Frontend (TypeScript / Next.js)

```bash
cd frontend

# Clean install – installs exact versions from the lockfile
npm ci

# Start the development server
npm run dev
```

The UI will be available at **http://localhost:3000**.

#### Production build (optional)

```bash
npm run build   # Creates an optimized production bundle
npm start       # Serves the production build
```

---

## One-Command Startup (Local)

You can also use the included helper script:

```bash
chmod +x start.sh
./start.sh
```

---

## Project Structure

```
QuantBackEngine/
├── backend/                  # Java / Spring Boot API
│   ├── src/                  # Application source code
│   │   ├── main/java/...     #   Controllers, services, models
│   │   └── main/resources/   #   application.properties
│   ├── pom.xml               # Maven dependency manifest
│   ├── mvnw / .mvn/          # Maven wrapper (no global install needed)
│   └── Dockerfile            # Backend container image
├── frontend/                 # TypeScript / Next.js UI
│   ├── app/                  # Next.js App Router pages
│   ├── components/           # React components
│   ├── package.json          # npm dependency manifest
│   └── Dockerfile            # Frontend container image
├── docker-compose.yml        # Orchestrates all services
├── start.sh                  # Helper startup script
├── .gitignore                # VCS ignore rules
└── README.md                 # Project overview
```

---

## Dependency Manifests

| Stack    | Manifest File          | Install Command       |
|----------|------------------------|-----------------------|
| Backend  | `backend/pom.xml`      | `./mvnw clean install`|
| Frontend | `frontend/package.json`| `npm ci`              |

> **Tip:** Use `npm ci` instead of `npm install` for reproducible builds – it installs exact versions from `package-lock.json` and is faster in CI/CD pipelines.
