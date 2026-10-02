import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("summarize", Path(__file__).with_name("summarize.py"))
summarize = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summarize)


class SummarizeTest(unittest.TestCase):
    def report(self, metrics):
        entry = {
            "benchmark": "net.foliabench.board.TextBenchmark.cacheHit",
            "primaryMetric": {"score": 12.5, "scoreError": 0.5, "scoreUnit": "ns/op"},
            "secondaryMetrics": metrics,
        }
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "result.json"
            source.write_text(json.dumps([entry]), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                summarize.main(source, "Namespace runner")
            return output.getvalue()

    def test_jmh_allocation_metric_is_published(self):
        report = self.report({"gc.alloc.rate.norm": {"score": 128}})
        self.assertIn("Allocated/op", report)
        self.assertIn("128 B", report)

    def test_prefixed_legacy_metric_remains_supported(self):
        report = self.report({"·gc.alloc.rate.norm": {"score": 64}})
        self.assertIn("64 B", report)

    def test_report_without_gc_profiler_has_no_allocation_column(self):
        report = self.report({})
        self.assertNotIn("Allocated/op", report)
        self.assertIn("12.5 ns/op", report)


if __name__ == "__main__":
    unittest.main()
