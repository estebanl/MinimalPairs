import importlib.util
import math
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "validate_articulator_calibration.py"
spec = importlib.util.spec_from_file_location("calibration_report", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CalibrationReportTest(unittest.TestCase):
    def row(self, sample, reference, estimate, condition="frontal"):
        return dict(sample_id=str(sample), device="test-device", condition=condition,
                    reference_mm=str(reference), estimated_mm=str(estimate))

    def test_error_statistics_and_unavailable_coverage(self):
        report = module.summarize([self.row(1, 5, 6), self.row(2, 5, 3), self.row(3, 5, "")])
        totals = report["overall"]
        self.assertEqual(3, totals["attempted"])
        self.assertEqual(2, totals["available"])
        self.assertAlmostEqual(2 / 3, totals["coverage"])
        self.assertEqual(-.5, totals["bias_mm"])
        self.assertEqual(1.5, totals["mae_mm"])
        self.assertAlmostEqual(math.sqrt(2.5), totals["rmse_mm"])
        self.assertEqual(2, totals["p95_absolute_error_mm"])

    def test_bad_condition_cannot_hide_in_an_overall_average(self):
        report = module.summarize([self.row(1, 5, 5), self.row(2, 5, 15, "oblique"),
                                   self.row(3, 5, "", "dark")])
        groups = {item["condition"]: item for item in report["groups"]}
        self.assertEqual(10, groups["oblique"]["mae_mm"])
        self.assertEqual(0, groups["dark"]["coverage"])
        self.assertNotIn("mae_mm", groups["dark"])

    def test_invalid_or_duplicate_measurements_are_rejected(self):
        for rows in ([], [self.row(1, "nan", 3)], [self.row(1, 3, "inf")],
                     [self.row(1, 3, 3), self.row(1, 3, 4)], [{}],
                     [dict(self.row(1, 3, 3), estimated_mm=None)]):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                module.summarize(rows)


if __name__ == "__main__":
    unittest.main()
