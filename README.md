# QuantBackEngine 2.0

A **secure, ultra-fast quantitative backtesting platform** for algorithmic trading strategies. Built with Spring Boot 3, Next.js 14, and Optimized Docker orchestration.

---

## ⚡ Quick Start: One-Command Startup

To start the entire engine (Database, Backend, and Frontend), simply run:

```bash
./start.sh
```

This script will:
1. Build optimized Docker images with dependency caching.
2. Initialize the PostgreSQL database.
3. Start the Spring Boot API (port 8080).
4. Start the Next.js Dashboard (port 3000).
5. Verify health and provide access credentials.

---

## 🔒 Security Architecture

The API is secured using **HTTP Basic Authentication** for all write operations:
- **Read-only (GET)**: Publicly accessible for the dashboard.
- **Write (POST/PUT/DELETE)**: Requires authentication.

**Default Credentials:**
- **Username**: `admin`
- **Password**: `quantpass123`

*CORS is strictly configured to allow communication only between the Frontend and Backend.*

---

## 🚀 Key Features

- **Optimized Docker Orchestration**: Multi-stage builds reduce image size and startup time.
- **Dependency Caching**: Subsequent builds are nearly instantaneous.
- **Interactive Dashboard**: Modern UI with TradingView charts.
- **Dynamic Strategies**: SMA Crossover, RSI, and more.
- **Full API Documentation**: Interactive Swagger UI.

---

## 🏗️ Project Structure

```
QuantBackEngine/
├── start.sh                 # ONE-COMMAND START SCRIPT 🚀
├── docker-compose.yml       # Production-ready orchestration
├── backend/                 # Spring Boot 3 API (Java 21)
│   ├── src/main/java/.../config/SecurityConfig.java  # Security Layer
│   └── Dockerfile           # Optimized 3-stage build
└── frontend/                # Next.js 14 Dashboard
    └── Dockerfile           # Optimized build
```

---

## 📡 Access Links

| Service | URL | Description |
|---------|-----|-------------|
| **Dashboard** | [http://localhost:3000](http://localhost:3000) | Main visual interface |
| **API Docs** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive documentation |
| **Database** | `localhost:5432` | PostgreSQL persistence |

---

## 🛠️ Advanced Usage

### Stopping the Services
```bash
docker compose down
```

### Viewing Logs
```bash
docker compose logs -f backend  # For API logs
docker compose logs -f frontend # For UI logs
```

---

**Built with ❤️ for Quantitative Trading Teams**
