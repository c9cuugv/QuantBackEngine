# Product: Fincept Terminal

Fincept Terminal is an open-source financial intelligence platform — a native C++20 desktop application targeting Bloomberg-terminal-class performance. It is dual-licensed (AGPL-3.0 + commercial).

## What it does

- CFA-level analytics (DCF, portfolio optimization, VaR, Sharpe, derivatives pricing) via embedded Python
- 100+ data connectors (Yahoo Finance, FRED, DBnomics, IMF, World Bank, Polygon, Kraken, AkShare, government APIs)
- Real-time trading with 16 broker integrations (Zerodha, Angel One, Upstox, Fyers, Dhan, Groww, Kotak, IIFL, 5paisa, AliceBlue, Shoonya, Motilal, IBKR, Alpaca, Tradier, Saxo)
- 37 AI agents across Trader/Investor/Economic/Geopolitics frameworks; multi-provider LLM support (OpenAI, Anthropic, Gemini, Groq, DeepSeek, Ollama, etc.)
- QuantLib suite (18 quant modules), global intelligence (maritime, geopolitics), node editor for automation pipelines, MCP tool integration

## Repo structure (two distinct sub-projects)

1. **FinceptTerminal/** — the main C++20/Qt6 desktop application (the product)
2. **backend/ + frontend/** — a separate Spring Boot + Next.js backtesting web app ("QuantBackEngine") that lives in the same monorepo

These two sub-projects are independent. Do not conflate them.

## Current version

v4.0.2 — shipped on Windows, macOS, Linux.
