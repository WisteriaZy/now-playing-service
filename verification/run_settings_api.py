"""Run isolated settings HTTP checks with JDK 11 and Maven's test classpath."""
import argparse
import os
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"), help="JDK 11 installation")
args = parser.parse_args()
if not args.java_home:
    parser.error("Set JAVA_HOME or pass --java-home pointing to JDK 11")
root = Path(__file__).resolve().parents[1]
classpath = root / "target/verification-classpath.txt"
if not classpath.exists():
    parser.error("First run: mvn dependency:build-classpath -Dmdep.outputFile=target/verification-classpath.txt")
dependencies = classpath.read_text().strip()
out = root / "target/settings-api-checks"
out.mkdir(parents=True, exist_ok=True)
jdk = Path(args.java_home) / "bin"
sources = ["verification/SettingsApiChecks.java"] + [
    "src/main/java/com/widdit/nowplaying/" + name + ".java"
    for name in ["controller/SettingsController", "entity/SettingsGeneral", "service/SettingsService",
                 "event/SettingsGeneralChangedEvent", "json/JacksonObjectMapper"]
]
compile_result = subprocess.run([str(jdk / "javac.exe"), "-encoding", "UTF-8", "-cp", dependencies,
    "-d", "target/settings-api-checks", *sources], cwd=root, capture_output=True)
(root / "target/settings-api-compile.log").write_bytes(compile_result.stdout + compile_result.stderr)
if compile_result.returncode:
    raise SystemExit("Compilation failed; see target/settings-api-compile.log")
# SettingsService's static defaults are initialized only in this isolated output directory.
result = subprocess.run([str(jdk / "java.exe"), "-cp", ".;" + dependencies, "SettingsApiChecks"],
    cwd=out, capture_output=True)
(root / "target/settings-api-checks.log").write_bytes(result.stdout + result.stderr)
if result.returncode:
    raise SystemExit("Settings checks failed; see target/settings-api-checks.log")
for line in result.stdout.decode("utf-8", errors="replace").splitlines():
    if line.startswith("PASS:"):
        print(line)
