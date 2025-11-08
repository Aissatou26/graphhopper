#!/usr/bin/env bash
set -euo pipefail

# Usage: ci/check-mutation-score.sh <module>
# Reads PIT report (XML preferred) and compares mutation score against
# baseline in ci/mutation-baseline.txt. Exits non-zero when score < baseline - epsilon.

MODULE=${1:-}
if [[ -z "$MODULE" ]]; then
	echo "Usage: $0 <module>" >&2
	exit 2
fi

REPORT_DIR="$MODULE/target/pit-reports"
XML_FILE="$REPORT_DIR/mutations.xml"
HTML_INDEX="$REPORT_DIR/index.html"
BASELINE_FILE="ci/mutation-baseline.txt"

EPSILON=0.1  # small tolerance (percent)

function read_baseline() {
	if [[ ! -f "$BASELINE_FILE" ]]; then
		echo "ERROR: baseline file '$BASELINE_FILE' not found" >&2
		exit 2
	fi
	# allow comments and whitespace
	baseline=$(grep -Eo '^[0-9]+(\.[0-9]+)?' "$BASELINE_FILE" | head -n1 || true)
	if [[ -z "$baseline" ]]; then
		echo "ERROR: no numeric baseline in $BASELINE_FILE" >&2
		exit 2
	fi
	printf "%s" "$baseline"
}

function parse_from_xml() {
	if [[ ! -f "$XML_FILE" ]]; then
		return 1
	fi
	# compute killed/total from mutations.xml
	# count total mutations and killed mutations
	total=$(grep -o "<mutation" "$XML_FILE" | wc -l || true)
	# Accept both status="KILLED" and status='KILLED' variants
	killed=$(grep -o "status=[\'\"]KILLED[\'\"]" "$XML_FILE" | wc -l || true)
	if [[ -z "$total" || "$total" -eq 0 ]]; then
		return 1
	fi
	# compute percentage as (killed / total) * 100 with one decimal
	printf "%.1f" "$(awk -v k="$killed" -v t="$total" 'BEGIN{printf (k/t)*100}')"
	return 0
}

function parse_from_html() {
	if [[ ! -f "$HTML_INDEX" ]]; then
		return 1
	fi
	# attempt to find the Mutation Coverage percentage from the HTML index
	# look for the table header 'Mutation Coverage' and capture the next numeric value
	# fallback to a heuristic: find the first occurrence of 'Mutation Coverage' and nearby percent
	score=$(grep -n "Mutation Coverage" -i "$HTML_INDEX" | head -n1 | cut -d: -f1 || true)
	if [[ -n "$score" ]]; then
		line=$(sed -n "$((score-2)),$((score+4))p" "$HTML_INDEX" | tr '\n' ' ')
		# find a number like 41.5% or 41%
		pct=$(echo "$line" | grep -Eo '[0-9]+(\.[0-9]+)?%' | head -n1 | tr -d '%') || true
		if [[ -n "$pct" ]]; then
			printf "%.1f" "$pct"
			return 0
		fi
	fi
	# final fallback: search for any 'Mutation Coverage' percentage in file
	pct=$(grep -Eo "Mutation Coverage[^<]*[0-9]+(\.[0-9]+)?%" -i "$HTML_INDEX" | grep -Eo '[0-9]+(\.[0-9]+)?%' | head -n1 | tr -d '%' || true)
	if [[ -n "$pct" ]]; then
		printf "%.1f" "$pct"
		return 0
	fi
	return 1
}

baseline=$(read_baseline)

current_score=""
if current_score=$(parse_from_xml); then
	echo "Parsed mutation score from XML: ${current_score}%"
elif current_score=$(parse_from_html); then
	echo "Parsed mutation score from HTML: ${current_score}%"
else
	echo "ERROR: could not find PIT report (tried $XML_FILE and $HTML_INDEX)" >&2
	exit 2
fi

# numeric compare
cur=$(printf "%.3f" "$current_score")
base=$(printf "%.3f" "$baseline")

# compute difference = base - cur
diff=$(awk -v b="$base" -v c="$cur" 'BEGIN{printf "%.3f", b - c}')

echo "Current mutation score: ${cur}%; baseline: ${base}%; diff = ${diff}%"

# fail if current < baseline - EPSILON
cmp=$(awk -v d="$diff" -v e="$EPSILON" 'BEGIN{print (d > e) ? 1 : 0}')
if [[ "$cmp" -eq 1 ]]; then
	echo "ERROR: Mutation score decreased: current=${cur}% < baseline=${base}% (epsilon=${EPSILON})" >&2
	exit 1
else
	echo "Mutation score OK (no regression beyond epsilon=${EPSILON})." >&2
	exit 0
fi

