import unittest

from scripts.generate_compose_for_linear_flow import generate_services, validate_linear_flow


class GenerateComposeForLinearFlowTest(unittest.TestCase):
    def test_validate_linear_flow_accepts_expected_payload(self):
        payload = {
            "operationsCount": 2,
            "operations": [
                {"id": 0, "name": "startStore", "tauMean": 0.0, "tauSigma": 0.0},
                {"id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.1},
                {"id": 2, "name": "Op02", "tauMean": 12.0, "tauSigma": 0.2},
                {"id": 3, "name": "finishStore", "tauMean": 0.0, "tauSigma": 0.0},
            ],
        }

        workers = validate_linear_flow(payload)

        self.assertEqual([1, 2], [operation["id"] for operation in workers])

    def test_validate_linear_flow_rejects_non_consecutive_worker_ids(self):
        payload = {
            "operationsCount": 2,
            "operations": [
                {"id": 0, "name": "startStore", "tauMean": 0.0, "tauSigma": 0.0},
                {"id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.1},
                {"id": 5, "name": "Op05", "tauMean": 12.0, "tauSigma": 0.2},
                {"id": 3, "name": "finishStore", "tauMean": 0.0, "tauSigma": 0.0},
            ],
        }

        with self.assertRaises(ValueError):
            validate_linear_flow(payload)

    def test_generate_services_contains_worker_and_finish_store(self):
        workers = [
            {"id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 101},
            {"id": 2, "name": "Op02", "tauMean": 11.0, "tauSigma": 0.5, "randomSeed": 202},
        ]

        compose = generate_services(workers, operations_count=2)

        self.assertIn("productionline-operation1-app", compose)
        self.assertIn("SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC=line-op-0-to-1", compose)
        self.assertIn("SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC=line-op-1-to-2", compose)
        self.assertIn("productionline-finish-store-app", compose)
        self.assertIn("SIMULATION_DISTRIBUTED_FINISH_STORE_TOPIC=line-op-2-to-3", compose)


if __name__ == "__main__":
    unittest.main()
