# QuantBackEngine

A simple, Java-based backtesting engine for stock trading strategies using `ta4j`.

## Prerequisites

-   **Java 17+** installed.
-   **Maven** installed and available in your PATH.

## Setup

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd QuantBackEngine
    ```

2.  **Build the project:**
    ```bash
    mvn clean install
    ```

## Execution

### Run the Backtest
To run the backtest, generate the report, and open the Dashboard:

```bash
mvn clean compile exec:java "-Dexec.mainClass=com.quantbackengine.quantbackengine.Main"
```

**What happens:**
-   Loads data from `src/main/resources/data/AAPL.csv`.
-   Runs a Moving Average Crossover strategy (SMA 50/200).
-   Prints results to the console.
-   **Saves Report:** Text file saved to `reports/report_[TIMESTAMP].txt`.
-   **Saves Chart:** Equity curve image saved to `reports/equity_curve_[TIMESTAMP].png`.
-   **Opens Output:** Launches a GUI window with the chart and results.

### Run Tests
To verify the system and run unit tests:

```bash
mvn test
```

## Project Structure
-   `src/main/java`: Source code.
-   `src/main/resources/data`: CSV data files.
-   `reports/`: Generated results (charts and logs).
