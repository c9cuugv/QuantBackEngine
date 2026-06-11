#!/usr/bin/env python3
"""
ML Lab qlib runner — invoked detached by the Java backend.

Pipeline: CSVs (exported from DB cache) -> dump_bin -> qlib init ->
Alpha158 + LGBModel -> TopkDropoutStrategy daily backtest -> result.json.

Exit 0 + result.json on success; non-zero exit otherwise (log in runner.log).
"""
import argparse
import json
import math
import subprocess
import sys
from pathlib import Path


def split_dates(start, end):
    """60/20/20 train/valid/test split by calendar position."""
    import pandas as pd
    dates = pd.date_range(start, end)
    t1 = dates[int(len(dates) * 0.6)].strftime("%Y-%m-%d")
    t2 = dates[int(len(dates) * 0.8)].strftime("%Y-%m-%d")
    return t1, t2


def dump_bin(params):
    dump_script = Path(params["qlib_scripts"]) / "dump_bin.py"
    subprocess.run(
        [sys.executable, str(dump_script), "dump_all",
         "--csv_path", params["csv_dir"],
         "--qlib_dir", params["qlib_dir"],
         "--include_fields", "open,high,low,close,volume",
         "--date_field_name", "date"],
        check=True,
    )


def run_pipeline(params):
    import qlib
    from qlib.constant import REG_US
    from qlib.contrib.data.handler import Alpha158
    from qlib.data.dataset import DatasetH
    from qlib.contrib.model.gbdt import LGBModel
    from qlib.contrib.strategy import TopkDropoutStrategy
    from qlib.backtest import backtest as q_backtest

    start, end = params["start"], params["end"]
    symbols = params["symbols"]
    t1, t2 = split_dates(start, end)

    qlib.init(provider_uri=params["qlib_dir"], region=REG_US)

    handler = Alpha158(
        instruments="all",
        start_time=start, end_time=end,
        fit_start_time=start, fit_end_time=t1,
    )
    dataset = DatasetH(handler, segments={
        "train": (start, t1),
        "valid": (t1, t2),
        "test": (t2, end),
    })

    model = LGBModel(loss="mse", early_stopping_rounds=50, num_boost_round=200)
    model.fit(dataset)
    pred = model.predict(dataset, segment="test")

    topk = min(5, max(1, len(symbols) // 4))
    strategy = TopkDropoutStrategy(signal=pred, topk=topk, n_drop=1)

    portfolio_metric_dict, _ = q_backtest(
        start_time=t2, end_time=end,
        strategy=strategy,
        executor={
            "class": "SimulatorExecutor",
            "module_path": "qlib.backtest.executor",
            "kwargs": {
                "time_per_step": "day",
                "generate_portfolio_metrics": True,
            },
        },
        account=100000,
        benchmark=symbols[0],
        exchange_kwargs={
            "freq": "day",
            "deal_price": "close",
            "open_cost": 0.0005,
            "close_cost": 0.0005,
            "min_cost": 0,
            "limit_threshold": None,
            "trade_unit": None,
        },
    )

    report_df, _positions = portfolio_metric_dict["1day"]
    returns = report_df["return"] - report_df["cost"]

    mean, std = returns.mean(), returns.std()
    cum = (1 + returns).cumprod()
    drawdown = (cum / cum.cummax() - 1).min()
    metrics = {
        "annualizedReturn": float(mean * 252),
        "sharpeRatio": float(mean / std * math.sqrt(252)) if std > 0 else 0.0,
        "maxDrawdownPercent": float(drawdown),
        "totalReturn": float(cum.iloc[-1] - 1),
        "volatility": float(std * math.sqrt(252)),
        "topk": topk,
        "universeSize": len(symbols),
        "testStart": t2,
        "testEnd": end,
    }
    equity_curve = [
        {"timestamp": int(ts.timestamp()), "value": float(100000 * v)}
        for ts, v in cum.items()
    ]
    return {"metrics": metrics, "equityCurve": equity_curve}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--params", required=True)
    args = parser.parse_args()

    with open(args.params) as f:
        params = json.load(f)

    dump_bin(params)
    result = run_pipeline(params)

    with open(params["out_file"], "w") as f:
        json.dump(result, f)
    print("RESULT_WRITTEN")


if __name__ == "__main__":
    main()
