#!/bin/sh
set -u

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_dir="$project_root/.test-build/java"
ad_rules="$project_root/app/src/main/java/com/clarotube/app/AdBlockRules.java"
url_policy="$project_root/app/src/main/java/com/clarotube/app/UrlPolicy.java"
script_test="$project_root/tests/protection.test.cjs"
status=0

for tool in javac java node; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Missing required tool: %s\n' "$tool" >&2
    exit 1
  fi
done

if [ ! -f "$ad_rules" ] || [ ! -f "$url_policy" ]; then
  printf 'Java tests cannot run: implementation files are missing.\n' >&2
  status=1
else
  mkdir -p "$build_dir"
  printf 'Compiling standalone Java tests...\n'
  if javac --release 17 -encoding UTF-8 -d "$build_dir" "$ad_rules" "$url_policy" \
    && find "$project_root/tests/java" -type f -name '*.java' \
      -exec javac --release 17 -encoding UTF-8 -cp "$build_dir" -d "$build_dir" {} +; then
    for test_class in AdBlockRulesTest UrlPolicyTest; do
      printf 'Running %s...\n' "$test_class"
      if ! java -cp "$build_dir" "com.clarotube.app.$test_class"; then status=1; fi
    done
  else
    status=1
  fi
fi

if [ -f "$script_test" ]; then
  printf 'Running protection.js tests...\n'
  if ! node --test "$script_test"; then status=1; fi
else
  printf 'JavaScript tests cannot run: missing %s\n' "$script_test" >&2
  status=1
fi

if [ "$status" -eq 0 ]; then printf 'All standalone tests passed.\n'; fi
exit "$status"
