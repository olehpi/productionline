#!/usr/bin/env python3
"""Generate docker-compose override with operation workers from linear simulation JSON."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable


def normalize_operation_name(operation: dict) -> str:
    return operation.get("name", "").strip().lower()


def is_store_operation(operation: dict) -> bool:
    return normalize_operation_name(operation) in {"startstore", "finishstore"}


def validate_operation_shape(operation: dict) -> None:
    for required_key in ("id", "name", "tauMean", "tauSigma"):
        if required_key not in operation:
            raise ValueError(f"Operation must contain '{required_key}': {operation}")


def validate_linear_flow(payload: dict) -> list[dict]:
    if "operationsCount" not in payload:
        raise ValueError("Missing required field: operationsCount")
    if "operations" not in payload:
        raise ValueError("Missing required field: operations")

    operations_count = int(payload["operationsCount"])
    operations = payload["operations"]
    if not isinstance(operations, list) or not operations:
        raise ValueError("'operations' must be a non-empty list")

    for operation in operations:
        validate_operation_shape(operation)

    start_candidates = [operation for operation in operations if normalize_operation_name(operation) == "startstore"]
    finish_candidates = [operation for operation in operations if normalize_operation_name(operation) == "finishstore"]

    if len(start_candidates) != 1:
        raise ValueError(f"Expected exactly one startStore operation, found {len(start_candidates)}")
    if len(finish_candidates) != 1:
        raise ValueError(f"Expected exactly one finishStore operation, found {len(finish_candidates)}")

    start_operation = start_candidates[0]
    finish_operation = finish_candidates[0]
    if int(start_operation["id"]) != 0:
        raise ValueError("startStore must have id=0")

    expected_finish_id = operations_count + 1
    if int(finish_operation["id"]) != expected_finish_id:
        raise ValueError(
            f"finishStore must have id={expected_finish_id} when operationsCount={operations_count}"
        )

    worker_operations = [operation for operation in operations if not is_store_operation(operation)]
    if len(worker_operations) != operations_count:
        raise ValueError(
            f"operationsCount={operations_count} but found {len(worker_operations)} non-store operations"
        )

    actual_ids = sorted(int(operation["id"]) for operation in worker_operations)
    expected_ids = list(range(1, operations_count + 1))
    if actual_ids != expected_ids:
        raise ValueError(
            f"Worker operation IDs must be consecutive from 1 to {operations_count}: got {actual_ids}"
        )

    return sorted(worker_operations, key=lambda operation: int(operation["id"]))


def worker_service(operation: dict) -> str:
    operation_id = int(operation["id"])
    service_name = f"productionline-operation{operation_id}-app"
    inbound_topic = f"line-op-{operation_id - 1}-to-{operation_id}"

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


def generate_services(worker_operations: Iterable[dict], operations_count: int) -> str:
    lines = ["services:\n"]
    for operation in worker_operations:
        lines.append(worker_service(operation))

    lines.append(finish_store_service(operations_count))
    return "".join(lines)


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
    worker_operations = validate_linear_flow(payload)

    compose_content = generate_services(worker_operations, operations_count)
    Path(args.output).write_text(compose_content, encoding="utf-8")
    print(f"Generated {args.output} with {len(worker_operations)} operation workers")


if __name__ == "__main__":
    main()
