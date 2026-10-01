import argparse
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POMS = [f"{module}/pom.xml" for module in
        ("folia-commons", "foliaboard", "foliagui", "folianpc", "integration", "benchmarks")]
GROUPS = {"net.foliacommons", "net.foliaboard", "com.foliagui", "net.folianpc"}
PARENT_GROUP = "io.github.spirtysprite"
NS = "{http://maven.apache.org/POM/4.0.0}"


class PublicationPreparationTest(unittest.TestCase):
    def test_coordinates_are_normalized_without_other_model_changes(self):
        originals = {name: (ROOT / name).read_bytes() for name in POMS}
        with tempfile.TemporaryDirectory(prefix="folia-jitpack-") as directory:
            checkout = Path(directory)
            for name in POMS + ["scripts/jitpack-prepare.sh"]:
                destination = checkout / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, destination)
            script = checkout / "scripts/jitpack-prepare.sh"
            subprocess.run([SHELL, str(script)], cwd=checkout, check=True)
            prepared = {name: (checkout / name).read_bytes() for name in POMS}
            for name in POMS:
                before = list(ET.fromstring(originals[name]).iter())
                after = list(ET.fromstring(prepared[name]).iter())
                self.assertEqual(len(before), len(after), name)
                for original, published in zip(before, after):
                    self.assertEqual(original.tag, published.tag, name)
                    self.assertEqual(original.attrib, published.attrib, name)
                    expected = PARENT_GROUP if original.tag == NS + "groupId" and original.text in GROUPS else original.text
                    self.assertEqual(expected, published.text, name)
                model = ET.fromstring(prepared[name])
                self.assertEqual(PARENT_GROUP, model.findtext(NS + "groupId") or model.findtext(NS + "parent/" + NS + "groupId"), name)
                for dependency in model.findall(NS + "dependencies/" + NS + "dependency"):
                    if dependency.findtext(NS + "artifactId") == "folia-commons":
                        self.assertEqual(PARENT_GROUP, dependency.findtext(NS + "groupId"), name)
            subprocess.run([SHELL, str(script)], cwd=checkout, check=True)
            for name in POMS:
                self.assertEqual(prepared[name], (checkout / name).read_bytes(), name)
                self.assertEqual(originals[name], (ROOT / name).read_bytes(), name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--shell", default="sh")
    SHELL = parser.parse_args().shell
    unittest.main(argv=[__file__], verbosity=2)
