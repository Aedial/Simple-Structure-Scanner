import argparse
import json

from pathlib import Path
from typing import Any


class Arguments(argparse.Namespace):
    input: list[Path]


parser = argparse.ArgumentParser(description="Sort Spark sampler JSON files by time spent")
parser.add_argument("input", type=Path, nargs="+", help="Path to the Spark JSON file(s)")


def get_duration(document: dict[str, Any]) -> float | None:
    if not isinstance(document, dict):
        return None

    if "timeWindowStatistics" not in document:
        return None

    stats = document["timeWindowStatistics"]
    if not isinstance(stats, dict) or not stats:
        return None

    stat_key = next(iter(stats), None)
    stat = stats[stat_key]
    if "duration" not in stat:
        return None

    return stat["duration"]


def format_time(value: float) -> tuple[float, str]:
    if value >= 60 * 10_000:
        return round(value / (60 * 1000), 2), "min"
    elif value >= 10_000:
        return round(value / 1000, 2), "s"
    else:
        return value, "ms"


def main(argv: list[str] | None = None) -> int:
    args: Arguments = parser.parse_args(argv, Arguments)
    input_paths = [path.expanduser() for path in args.input]

    times: dict[str, float] = {}
    for input_path in input_paths:
        try:
            document = json.loads(input_path.read_text("utf-8"))
            total_time_value = get_duration(document)
            if total_time_value is None:
                continue

            times[input_path.name] = total_time_value
        except Exception as e:
            pass

    times = dict(sorted(times.items(), key=lambda e: e[1], reverse=True))
    for key, value in times.items():
        value, unit = format_time(value)
        print(f"{key}: {value} {unit}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())