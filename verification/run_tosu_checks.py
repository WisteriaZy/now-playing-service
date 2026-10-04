"""Run tosu integration/race checks after Maven compile and dependency:build-classpath."""
import argparse
import subprocess
import zipfile
from pathlib import Path
p = argparse.ArgumentParser()
p.add_argument("--java-home", required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
out = root / "target/tosu-checks"
out.mkdir(parents=True, exist_ok=True)
app = out / "app-classes.jar"
with zipfile.ZipFile(app, "w") as jar:
    for path in (root / "target/classes").rglob("*.class"):
        jar.write(path, path.relative_to(root / "target/classes").as_posix())
deps = (root / "target/verification-classpath.txt").read_text().strip()
cp = str(app) + ";" + deps
jdk = Path(a.java_home) / "bin"
compile = subprocess.run([str(jdk / "javac.exe"), "-encoding", "UTF-8", "-cp", cp,
    "-d", str(out), "verification/TosuChecks.java"], cwd=root, capture_output=True)
(root / "target/tosu-test-compile.log").write_bytes(compile.stdout + compile.stderr)
if compile.returncode: raise SystemExit("Compile failed: target/tosu-test-compile.log")
run = subprocess.run([str(jdk / "java.exe"), "-cp", ".;" + cp, "TosuChecks"], cwd=out, capture_output=True)
(root / "target/tosu-checks.log").write_bytes(run.stdout + run.stderr)
if run.returncode: raise SystemExit("Checks failed: target/tosu-checks.log")
for line in run.stdout.decode("utf-8", errors="replace").splitlines():
    if line.startswith("PASS:"): print(line)
