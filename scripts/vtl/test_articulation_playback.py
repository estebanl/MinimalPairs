"""Run pure playback tests with locally provisioned official Kotlin/JUnit jars.
Not a substitute for the project's Gradle/Compose/device build. Extracts the actual
current mesh types and decoder; no fake decoder is substituted for validation.
Set KOTLIN_DIAGNOSTIC_DIR to the directory containing compiler/runtime/test jars.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "composeApp/src/commonMain/kotlin/com/minimalpairs/app/ui/reference"
TEST = ROOT / "composeApp/src/commonTest/kotlin/com/minimalpairs/app/sdd/ArticulationPlaybackTest.kt"
JARS = Path(os.environ.get("KOTLIN_DIAGNOSTIC_DIR", "/tmp/minimalpairs-kotlinc"))
classpath = ":".join(str(p) for p in sorted(JARS.glob("*.jar")))
if not classpath:
    raise SystemExit("Missing locally provisioned Kotlin/JUnit jars; run the Gradle tests when available.")
with tempfile.TemporaryDirectory(prefix="articulation-tests-") as folder:
    build = Path(folder)
    model = (SRC / "OralCavityModel.kt").read_text()
    (build / "Types.kt").write_text(model[:model.index("/** Removes")])
    decoder = (SRC / "VocalTractLabMeshes.kt").read_text()
    (build / "Decoder.kt").write_text("package com.minimalpairs.app.ui.reference\nobject VocalTractLabMeshes {\n" + decoder[decoder.index("    fun decode(bytes:"):])
    subprocess.run(["java", "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", str(build / "tests.jar"),
        str(SRC / "OralCavityModel.kt"), str(SRC / "OralTongueSection.kt"), str(SRC / "OralRasterizer.kt"), str(build / "Decoder.kt"), str(SRC / "ArticulationPlayback.kt"), str(SRC / "OralRenderedFrame.kt"), str(TEST),
        str(ROOT / "composeApp/src/commonTest/kotlin/com/minimalpairs/app/sdd/OralRenderedFrameTest.kt"),
        str(ROOT / "composeApp/src/commonTest/kotlin/com/minimalpairs/app/sdd/AuthoredOralSectionsTest.kt"),
        str(ROOT / "composeApp/src/commonTest/kotlin/com/minimalpairs/app/sdd/OralCavityModelTest.kt"),
        str(ROOT / "composeApp/src/commonTest/kotlin/com/minimalpairs/app/sdd/OralTongueSectionTest.kt")], check=True)
    subprocess.run(["java", "-cp", str(build / "tests.jar") + ":" + classpath, "org.junit.runner.JUnitCore",
        "com.minimalpairs.app.sdd.ArticulationPlaybackTest", "com.minimalpairs.app.sdd.OralRenderedFrameTest", "com.minimalpairs.app.sdd.AuthoredOralSectionsTest",
        "com.minimalpairs.app.sdd.OralCavityModelTest", "com.minimalpairs.app.sdd.OralTongueSectionTest"], check=True)
    print(json.dumps({"scope": "Pure Kotlin only; Compose and devices not validated", "sha256": {
        str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in [SRC / "OralCavityModel.kt", SRC / "VocalTractLabMeshes.kt", SRC / "ArticulationPlayback.kt", SRC / "OralRenderedFrame.kt", TEST]
    }}, indent=2))
