#!/usr/bin/env python3
"""Summarize paired external-reference and estimated jaw displacement measurements.

This evaluates supplied measurements; it neither calibrates a camera nor certifies
accuracy. Missing estimates remain visible as coverage failures.
"""

import argparse
import csv
import json
import math
from pathlib import Path
import statistics


def summarize(rows):
    if not rows:
        raise ValueError("At least one measurement row is required")
    seen = set()
    groups = {}
    for row in rows:
        for key in ("sample_id", "device", "condition", "reference_mm", "estimated_mm"):
            if key not in row:
                raise ValueError(f"Missing column: {key}")
            if not isinstance(row[key], str):
                raise ValueError(f"Missing CSV value: {key}")
        identity = row["sample_id"].strip()
        if not identity or identity in seen:
            raise ValueError("sample_id must be nonempty and unique")
        seen.add(identity)
        device, condition = row["device"].strip(), row["condition"].strip()
        if not device or not condition:
            raise ValueError("device and condition must be nonempty")
        reference = float(row["reference_mm"])
        if not math.isfinite(reference):
            raise ValueError("reference_mm must be finite")
        estimate_text = row["estimated_mm"].strip()
        error = None
        if estimate_text:
            estimate = float(estimate_text)
            if not math.isfinite(estimate):
                raise ValueError("estimated_mm must be finite or blank for unavailable")
            error = estimate - reference
            if not math.isfinite(error):
                raise ValueError("Measurement difference is not finite")
        groups.setdefault((device, condition), []).append(error)

    def metrics(values):
        available = [error for error in values if error is not None]
        result = {"attempted": len(values), "available": len(available),
                  "coverage": len(available) / len(values)}
        if available:
            absolute = sorted(abs(error) for error in available)
            # Nearest-rank p95, defined even for small samples; report count too.
            result.update(bias_mm=statistics.mean(available),
                          mae_mm=statistics.mean(absolute),
                          rmse_mm=(absolute[-1] * math.sqrt(statistics.mean(
                              (error / absolute[-1]) ** 2 for error in available)) if absolute[-1] else 0.0),
                          p95_absolute_error_mm=absolute[math.ceil(.95 * len(absolute)) - 1],
                          max_absolute_error_mm=absolute[-1])
        return result

    grouped = [{"device": device, "condition": condition, **metrics(values)}
               for (device, condition), values in sorted(groups.items())]
    return {"scope": "Descriptive paired-measurement report; no accuracy approval or clinical validity is implied.",
            "overall": metrics([value for values in groups.values() for value in values]),
            "groups": grouped}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path, help="CSV with sample_id,device,condition,reference_mm,estimated_mm")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        with args.csv.open(newline="") as source:
            result = summarize(list(csv.DictReader(source)))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n")
    except (OSError, ValueError, TypeError) as error:
        parser.error(str(error))
    print(f"Measurement report written to {args.output}; review coverage and each condition before drawing conclusions.")


if __name__ == "__main__":
    main()
