#!/usr/bin/env python3
"""Generate docker-compose override with operation workers from linear simulation JSON."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def is_store_operation(operation: dict) -> bool:
    name = operation.get("name", "").strip().lower()
    return name in {"startstore", "finishstore"}


def worker_service(operation: dict, operations_count: int) -> str:
    operation_id = int(operation["id"])
    service_name = f"productionline-operation{operation_id}-app"
    inbound_topic = f"line-op-{operation_id - 1}-to-{operation_id}"
    outbound_topic = f"line-op-{operation_id}-to-{operation_id + 1}"

    return f"""  {service_name}:
    build:
      context: .
      dockerfile: Dockerfile
    depends_on:
      kafka:
        condition: service_healthy
    environment:
      - SIMULATION_KAFKA_ENABLED=true
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_MAIN_WEB_APPLICATION_TYPE=none
      - SIMULATION_DISTRIBUTED_WORKER_ENABLED=true
      - SIMULATION_DISTRIBUTED_WORKER_GROUP_ID=productionline-operation-{operation_id}
      - SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC={inbound_topic}
      - SIMULATION_DISTRIBUTED_WORKER_OPERATION_ID={operation_id}
      - SIMULATION_DISTRIBUTED_WORKER_NEXT_OPERATION_ID={operation_id + 1}
      - SIMULATION_DISTRIBUTED_WORKER_TAU_MEAN={operation['tauMean']}
      - SIMULATION_DISTRIBUTED_WORKER_TAU_SIGMA={operation['tauSigma']}
      - SIMULATION_DISTRIBUTED_WORKER_RANDOM_SEED={operation.get('randomSeed', 0)}
"""


def finish_store_service(operations_count: int) -> str:
    finish_topic = f"line-op-{operations_count}-to-{operations_count + 1}"
    return f"""  productionline-finish-store-app:
    build:
      context: .
      dockerfile: Dockerfile
    depends_on:
      kafka:
        condition: service_healthy
    environment:
      - SIMULATION_KAFKA_ENABLED=true
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_MAIN_WEB_APPLICATION_TYPE=none
      - SIMULATION_DISTRIBUTED_FINISH_STORE_ENABLED=true
      - SIMULATION_DISTRIBUTED_FINISH_STORE_TOPIC={finish_topic}
      - SIMULATION_DISTRIBUTED_FINISH_STORE_GROUP_ID=productionline-finish-store
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Path to linear simulation JSON")
    parser.add_argument(
        "--output",
        default="docker-compose.operations.yml",
        help="Output docker-compose override file",
    )
    args = parser.parse_args()

    payload = json.loads(Path(args.input).read_text(encoding="utf-8"))
    operations_count = int(payload["operationsCount"])

    operations = [operation for operation in payload["operations"] if not is_store_operation(operation)]
    if len(operations) != operations_count:
        raise ValueError(
            f"operationsCount={operations_count} but found {len(operations)} non-store operations"
        )

    lines = ["services:\n"]
    for operation in operations:
        lines.append(worker_service(operation, operations_count))

    lines.append(finish_store_service(operations_count))

    Path(args.output).write_text("".join(lines), encoding="utf-8")
    print(f"Generated {args.output} with {len(operations)} operation workers")


if __name__ == "__main__":
    main()
