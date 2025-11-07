#!/usr/bin/env bash
set -euo pipefail

# Usage: ci/check-mutation-score.sh <module-dir>
MODULE_DIR=${1:-core}
REPORT_DIR="$MODULE_DIR/target/pit-reports"
BASE_FILE="ci/mutation-baseline.txt"
EPSILON=0.1

if [ ! -d "$REPORT_DIR" ]; then
  echo "ERROR: PIT report directory not found: $REPORT_DIR"
  exit 2
fi

INDEX_HTML="$REPORT_DIR/index.html"
SCORE=""

XML_REPORT="$REPORT_DIR/mutations.xml"

# Prefer structured XML parsing: count <mutation ...> entries and those with status='KILLED'
if [ -f "$XML_REPORT" ]; then
  TOTAL=$(grep -c "<mutation" "$XML_REPORT" || true)
  KILLED=$(grep -c "status=['\"]KILLED['\"]" "$XML_REPORT" || true)
  # Defensive: if grep failed or counts are empty, fallback to HTML parsing below
  if [ -n "$TOTAL" ] && [ "$TOTAL" -gt 0 ] 2>/dev/null; then
    # compute percentage with one decimal
    SCORE=$(awk -v k="$KILLED" -v t="$TOTAL" 'BEGIN{if(t==0){print "0"; exit} printf("%.1f", (k/t)*100)}') || true
  fi
fi

# If XML didn't yield a value, try targeted HTML extraction (find the Mutation Coverage column)
if [ -z "${SCORE:-}" ] && [ -f "$INDEX_HTML" ]; then
  # Find the header row and determine which column index contains 'Mutation Coverage'
  # Then extract the corresponding <td> from the first data row.
  # This is more robust than grabbing the first percent in the file.
  HEADER_LINE=$(grep -n "<thead>" -n "$INDEX_HTML" | cut -d: -f1 2>/dev/null || true)
  # fallback simple approach: find header positions by scanning the <th> row
  COL_IDX=$(awk 'BEGIN{FS="<th>|</th>"}{for(i=1;i<=NF;i++) if($i ~ /Mutation Coverage/) {print i; exit}}' "$INDEX_HTML" || true)
  if [ -n "$COL_IDX" ]; then
    # Convert the th-field index to a td field position. The crude awk split above gives a
    # token index; we instead parse the first <tr> inside <tbody> and pick the Nth <td>.
    SCORE=$(awk -v col=$COL_IDX 'BEGIN{RS="<tbody"; OFS=""} NR==2{ # second record contains tbody
      # find first <tr> ... </tr>
      tr = $0
      n=split(tr, parts, /<td[^>]*>/)
      # parts[1] is the text before first td, so the first td content is in parts[2]
      if(col+0>1 && col+0<=n) {
        # extract content up to </td>
        sub(/<[^"]*?>/, "", parts[col+1])
        match(parts[col+1], /([0-9]+(\.[0-9]+)?)%/, m)
        if(m[1]!="") print m[1]
      }
    }' "$INDEX_HTML" || true)
  fi
  # Last-resort fallback: explicit 'Mutation Coverage' label search
  if [ -z "${SCORE:-}" ]; then
    SCORE=$(grep -oE "Mutation[[:space:]]Coverage[^0-9%]*[0-9]+(\.[0-9]+)?%" "$INDEX_HTML" | head -n1 | grep -oE "[0-9]+(\.[0-9]+)?" ) || true
  fi
fi

if [ -z "$SCORE" ]; then
  echo "ERROR: Unable to parse mutation score from $REPORT_DIR. Ensure PIT produced index.html or mutations XML." >&2
  exit 3
fi

echo "Current mutation score: $SCORE%"

if [ ! -f "$BASE_FILE" ]; then
  echo "Baseline file $BASE_FILE not found. Creating it with current value ($SCORE). Please commit this file to establish a baseline." >&2
  mkdir -p "$(dirname "$BASE_FILE")"
  printf "%s" "$SCORE" > "$BASE_FILE"
  exit 0
fi

BASE=$(cat "$BASE_FILE" | tr -d '\r' | tr -d '%')

# Numeric comparison with epsilon
less=$(awk -v cur="$SCORE" -v base="$BASE" -v eps="$EPSILON" 'BEGIN{if ((cur+0) + 0 < (base+0) - eps) print 1; else print 0}')
if [ "$less" -eq 1 ]; then
  echo "ERROR: Mutation score decreased: current=${SCORE}% < baseline=${BASE}% (epsilon=${EPSILON})" >&2
  exit 1
else
  echo "Mutation score OK: current=${SCORE}% >= baseline=${BASE}% (epsilon=${EPSILON})"
  exit 0
fi
