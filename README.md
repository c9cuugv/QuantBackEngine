# QuantBackEngine 2.0

A **next-generation quantitative backtesting platform** for algorithmic trading strategies. Built with Spring Boot and Next.js for a modern, interactive experience.

![Dashboard Preview](docs/dashboard-preview.png)

## 🚀 Features

- **Interactive Web Dashboard** - Modern UI with real-time charting using TradingView's Lightweight Charts
- **Multiple Trading Strategies** - SMA Crossover, RSI, and easily extensible architecture
- **Dynamic Parameter Tuning** - Adjust strategy parameters without recompiling
- **Comprehensive Metrics** - Sharpe Ratio, Max Drawdown, Win Rate, and more
- **REST API** - Full OpenAPI/Swagger documentation
- **Docker Ready** - One-command deployment with Docker Compose

## 📋 Prerequisites

- **Java 21+** (for backend development)
- **Node.js 18+** (for frontend development)
- **Maven 3.9+** (for building backend)
- **Docker & Docker Compose** (for containerized deployment)

## 🏗️ Project Structure

```
QuantBackEngine/
├── backend/                 # Spring Boot API
│   ├── src/main/java/
│   │   └── com/quantbackengine/backend/
│   │       ├── controller/  # REST endpoints
│   │       ├── service/     # Business logic
│   │       ├── strategy/    # Trading strategies
│   │       ├── domain/      # JPA entities
│   │       └── dto/         # Data transfer objects
│   └── pom.xml
├── frontend/                # Next.js Dashboard
│   ├── app/                 # Pages (App Router)
│   ├── components/          # React components
│   └── package.json
├── docker-compose.yml       # Full-stack deployment
└── README.md
```

## 🛠️ Quick Start

### Option 1: Docker Compose (Recommended)

```bash
# Start all services (PostgreSQL, Backend, Frontend)
docker-compose up --build

# Access the dashboard at http://localhost:3000
# API docs at http://localhost:8080/swagger-ui.html
```

### Option 2: Local Development

**Backend:**
```bash
cd backend
mvn spring-boot:run
# API available at http://localhost:8080
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
# Dashboard at http://localhost:3000
```

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/backtest/run` | Run a backtest |
| `GET` | `/api/v1/backtest/strategies` | List available strategies |
| `GET` | `/api/v1/backtest/strategies/{id}` | Get strategy details |
| `GET` | `/api/v1/market-data/symbols` | List available symbols |

### Example: Run Backtest

```bash
curl -X POST http://localhost:8080/api/v1/backtest/run \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "strategy": "SMA_CROSSOVER",
    "parameters": { "shortPeriod": 50, "longPeriod": 200 },
    "startDate": "2020-01-01",
    "endDate": "2024-12-31",
    "initialCapital": 100000
  }'
```

## 📊 Available Strategies

| Strategy | Description | Parameters |
|----------|-------------|------------|
| **SMA Crossover** | Trend-following using moving average crossovers | `shortPeriod`, `longPeriod` |
| **RSI Momentum** | Mean-reversion using RSI indicator | `period`, `oversoldThreshold`, `overboughtThreshold` |

## 🔧 Adding New Strategies

1. Create a new class in `backend/src/main/java/.../strategy/`
2. Implement `TradingStrategy` interface
3. Add `@Component` annotation
4. The strategy will be auto-discovered by the registry!

```java
@Component
public class MyStrategy implements TradingStrategy {
    // Implement interface methods...
}
```

## 🧪 Testing

```bash
# Backend tests
cd backend && mvn test

# Frontend lint
cd frontend && npm run lint
```

## 📈 Roadmap

- [ ] User authentication
- [ ] Save/load backtest configurations
- [ ] Multiple asset portfolios
- [ ] Live market data integration
- [ ] Paper trading mode
- [ ] Strategy optimization (grid search)

## 🤝 Contributing

Contributions are welcome! Please read the contributing guidelines before submitting PRs.

## 📄 License

MIT License - see LICENSE file for details.

---

**Built with ❤️ using Spring Boot, Next.js, and ta4j**
