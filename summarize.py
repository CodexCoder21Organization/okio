"""Print per-suite counts and every failure from JUnit XML files under the working directory."""
import glob, re, sys, xml.etree.ElementTree as ET
total = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
failures = []
for path in sorted(glob.glob("**/build/test-results/**/*.xml", recursive=True)):
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
    for suite in suites:
        counts = {k: int(suite.get(k, 0)) for k in total}
        for k in total:
            total[k] += counts[k]
        print(f"{path}: {suite.get('name')} tests={counts['tests']} failures={counts['failures']} "
              f"errors={counts['errors']} skipped={counts['skipped']} time={suite.get('time')}")
        for case in suite.findall("testcase"):
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is not None:
                    failures.append((suite.get("name"), case.get("name"), kind, node.get("message")))
        out = suite.findtext("system-out") or ""
        for line in out.splitlines():
            if "RESULT" in line:
                print("   ", line)
print("TOTAL", " ".join(f"{k}={v}" for k, v in total.items()),
      f"passed={total['tests'] - total['failures'] - total['errors'] - total['skipped']}")
for suite, case, kind, message in failures:
    print(f"{kind.upper()}: {suite}.{case}: {message}")
