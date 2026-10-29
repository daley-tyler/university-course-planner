#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

MANIFEST_PATH="${SCRIPT_DIR}/fixtures/manifest.json"

PYTHON_BIN=${PYTHON_BIN:-python3}
if ! command -v "${PYTHON_BIN}" >/dev/null 2>&1; then
  PYTHON_BIN=python
fi

LIMIT=""
MODEL="llama3.1:8b"
SECONDARY_MODEL=""
MAX_WORKERS=2
SECONDARY_WORKERS=1
ONLY_FLAGGED=0
SKIP_LLM=0
FORCE=0
SKIP_REPAIR=0
CATALOG_YEAR=""
CATALOG_LABEL=""
RESUME_FROM=""
RESUME_STATE_FILE="${SCRIPT_DIR}/.pipeline_state"

usage() {
  cat <<USAGE
Usage: bash tools/bulletin_ai/run_pipeline.sh [options]

Options:
  --limit N               Limit number of pages (default: all)
  --model NAME            Ollama model for main pass (default: llama3.1:8b)
  --secondary-model NAME  Optional second pass model (runs only on flagged pages)
  --max-workers N         Workers for main pass (default: 2)
  --secondary-workers N   Workers for second pass (default: 1)
  --only-flagged          Run only on lint-flagged pages
  --skip-llm              Skip LLM pass
  --skip-repair           Skip LLM repair/apply pass
  --force                 Force re-run steps that cache
  --resume-from STEP      Resume from a named step (e.g. apply-repairs, aggregate-report, next)
  --catalog-year YEAR     Filter by catalog year
  --catalog-label LABEL   Filter by catalog label
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --limit)
      LIMIT="$2"; shift 2 ;;
    --model)
      MODEL="$2"; shift 2 ;;
    --secondary-model)
      SECONDARY_MODEL="$2"; shift 2 ;;
    --max-workers)
      MAX_WORKERS="$2"; shift 2 ;;
    --secondary-workers)
      SECONDARY_WORKERS="$2"; shift 2 ;;
    --only-flagged)
      ONLY_FLAGGED=1; shift ;;
    --skip-llm)
      SKIP_LLM=1; shift ;;
    --skip-repair)
      SKIP_REPAIR=1; shift ;;
    --force)
      FORCE=1; shift ;;
    --resume-from)
      RESUME_FROM="$2"; shift 2 ;;
    --catalog-year)
      CATALOG_YEAR="$2"; shift 2 ;;
    --catalog-label)
      CATALOG_LABEL="$2"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

LIMIT_ARGS=()
if [[ -n "${LIMIT}" ]]; then
  LIMIT_ARGS+=("--limit" "${LIMIT}")
fi

FORCE_ARGS=()
if [[ ${FORCE} -eq 1 ]]; then
  FORCE_ARGS+=("--force")
fi

CATALOG_ARGS=()
if [[ -n "${CATALOG_YEAR}" ]]; then
  CATALOG_ARGS+=("--catalog-year" "${CATALOG_YEAR}")
fi
if [[ -n "${CATALOG_LABEL}" ]]; then
  CATALOG_ARGS+=("--catalog-label" "${CATALOG_LABEL}")
fi

PYTHON="${PYTHON_BIN}"

declare -A STEP_ORDER=(
  [build-manifest]=1
  [download-html]=2
  [extract-snippets]=3
  [fetch-requirements]=4
  [cleanup-parsed]=5
  [lint-trees]=6
  [llm-audit]=7
  [llm-audit-secondary]=8
  [llm-repair]=9
  [apply-repairs]=10
  [aggregate-report]=11
  [compare-gold]=12
  [review-queue]=13
)

START_AT=1
if [[ -n "${RESUME_FROM}" ]]; then
  if [[ "${RESUME_FROM}" == "next" ]]; then
    if [[ -f "${RESUME_STATE_FILE}" ]]; then
      LAST_STEP=$(cat "${RESUME_STATE_FILE}" | tr -d '\n')
      if [[ -n "${STEP_ORDER[${LAST_STEP}]}" ]]; then
        START_AT=$((STEP_ORDER[${LAST_STEP}] + 1))
      fi
    fi
  elif [[ -n "${STEP_ORDER[${RESUME_FROM}]}" ]]; then
    START_AT=${STEP_ORDER[${RESUME_FROM}]}
  else
    echo "Unknown --resume-from step: ${RESUME_FROM}"
    echo "Valid steps:"
    printf '  %s\n' "${!STEP_ORDER[@]}" | sort
    exit 1
  fi
fi

render_progress() {
  local current=$1
  local total=$2
  local label=$3
  local width=24
  local filled=$((current * width / total))
  local empty=$((width - filled))
  local bar
  local space
  bar=$(printf '%*s' "${filled}" '' | tr ' ' '#')
  space=$(printf '%*s' "${empty}" '' | tr ' ' '-')
  printf '[%d/%d] %s [%s%s]\n' "${current}" "${total}" "${label}" "${bar}" "${space}"
}

STEP_INDEX=0
TOTAL_STEPS=9
if [[ ${SKIP_LLM} -eq 0 ]]; then
  TOTAL_STEPS=$((TOTAL_STEPS + 1))
  if [[ -n "${SECONDARY_MODEL}" ]]; then
    TOTAL_STEPS=$((TOTAL_STEPS + 1))
  fi
  if [[ ${SKIP_REPAIR} -eq 0 ]]; then
    TOTAL_STEPS=$((TOTAL_STEPS + 2))
  fi
fi

run_step() {
  local label=$1
  shift
  STEP_INDEX=$((STEP_INDEX + 1))
  render_progress "${STEP_INDEX}" "${TOTAL_STEPS}" "${label}"
  "$@"
  if [[ -n "${CURRENT_STEP_NAME:-}" ]]; then
    echo "${CURRENT_STEP_NAME}" > "${RESUME_STATE_FILE}"
  fi
}

should_run() {
  local step_name=$1
  local step_index=${STEP_ORDER[${step_name}]}
  if [[ -z "${step_index}" ]]; then
    return 0
  fi
  if [[ ${step_index} -lt ${START_AT} ]]; then
    return 1
  fi
  return 0
}

STEP_INDEX=$((START_AT - 1))

if should_run "build-manifest"; then
  CURRENT_STEP_NAME="build-manifest"
  run_step "Build manifest" "${PYTHON}" "${SCRIPT_DIR}/01_build_manifest_from_db.py" \
    --output "${MANIFEST_PATH}" \
    "${CATALOG_ARGS[@]}"
fi

if should_run "download-html"; then
  CURRENT_STEP_NAME="download-html"
  run_step "Download HTML" "${PYTHON}" "${SCRIPT_DIR}/02_fetch_html.py" \
    --manifest "${MANIFEST_PATH}" \
    "${LIMIT_ARGS[@]}" \
    "${FORCE_ARGS[@]}"
fi

if should_run "extract-snippets"; then
  CURRENT_STEP_NAME="extract-snippets"
  run_step "Extract snippets" "${PYTHON}" "${SCRIPT_DIR}/03_extract_snippets.py" \
    --manifest "${MANIFEST_PATH}" \
    "${LIMIT_ARGS[@]}" \
    "${FORCE_ARGS[@]}"
fi

if should_run "fetch-requirements"; then
  CURRENT_STEP_NAME="fetch-requirements"
  run_step "Fetch requirements" "${PYTHON}" "${SCRIPT_DIR}/04_fetch_requirements_api.py" \
    --manifest "${MANIFEST_PATH}" \
    "${LIMIT_ARGS[@]}" \
    "${FORCE_ARGS[@]}"
fi

if should_run "cleanup-parsed"; then
  CURRENT_STEP_NAME="cleanup-parsed"
  run_step "Cleanup parsed" "${PYTHON}" "${SCRIPT_DIR}/04b_cleanup_parsed.py" \
    --manifest "${MANIFEST_PATH}" \
    "${LIMIT_ARGS[@]}" \
    "${FORCE_ARGS[@]}"
fi

if should_run "lint-trees"; then
  CURRENT_STEP_NAME="lint-trees"
  run_step "Lint trees" "${PYTHON}" "${SCRIPT_DIR}/05_lint_requirements.py" \
    --manifest "${MANIFEST_PATH}" \
    "${LIMIT_ARGS[@]}" \
    "${FORCE_ARGS[@]}"
fi

if [[ ${SKIP_LLM} -eq 0 ]]; then
  REVIEW_QUEUE_PATH="${SCRIPT_DIR}/review/queue.json"
  LLM_ARGS=(
    "${PYTHON}" "${SCRIPT_DIR}/06_run_llm.py"
    --manifest "${MANIFEST_PATH}"
    --model "${MODEL}"
    --max-workers "${MAX_WORKERS}"
  )
  if [[ -f "${REVIEW_QUEUE_PATH}" ]]; then
    LLM_ARGS+=("--review-queue" "${REVIEW_QUEUE_PATH}")
  fi
  if [[ ${ONLY_FLAGGED} -eq 1 ]]; then
    LLM_ARGS+=("--only-flagged")
  fi
  LLM_ARGS+=("${LIMIT_ARGS[@]}" "${FORCE_ARGS[@]}")
  if should_run "llm-audit"; then
    CURRENT_STEP_NAME="llm-audit"
    run_step "LLM audit (${MODEL})" "${LLM_ARGS[@]}"
  fi

  if [[ -n "${SECONDARY_MODEL}" ]]; then
    if should_run "llm-audit-secondary"; then
      CURRENT_STEP_NAME="llm-audit-secondary"
      run_step "LLM audit (${SECONDARY_MODEL})" "${PYTHON}" "${SCRIPT_DIR}/06_run_llm.py" \
        --manifest "${MANIFEST_PATH}" \
        --model "${SECONDARY_MODEL}" \
        --max-workers "${SECONDARY_WORKERS}" \
        --only-flagged \
        "${LIMIT_ARGS[@]}" \
        "${FORCE_ARGS[@]}"
    fi
  fi

  if [[ ${SKIP_REPAIR} -eq 0 ]]; then
    REPAIR_ARGS=(
      "${PYTHON}" "${SCRIPT_DIR}/10_llm_repair.py"
      --manifest "${MANIFEST_PATH}"
      --model "${MODEL}"
      --max-workers "${MAX_WORKERS}"
    )
    if [[ -f "${REVIEW_QUEUE_PATH}" ]]; then
      REPAIR_ARGS+=("--review-queue" "${REVIEW_QUEUE_PATH}")
    fi
    REPAIR_ARGS+=("${LIMIT_ARGS[@]}" "${FORCE_ARGS[@]}")
    if should_run "llm-repair"; then
      CURRENT_STEP_NAME="llm-repair"
      run_step "LLM repair (${MODEL})" "${REPAIR_ARGS[@]}"
    fi

    if should_run "apply-repairs"; then
      CURRENT_STEP_NAME="apply-repairs"
      run_step "Apply repairs" "${PYTHON}" "${SCRIPT_DIR}/11_apply_repairs.py" \
        --manifest "${MANIFEST_PATH}" \
        "${LIMIT_ARGS[@]}" \
        "${FORCE_ARGS[@]}"
    fi
  fi
fi

if should_run "aggregate-report"; then
  CURRENT_STEP_NAME="aggregate-report"
  run_step "Aggregate report" "${PYTHON}" "${SCRIPT_DIR}/07_aggregate_reports.py" \
    --manifest "${MANIFEST_PATH}" \
    "${LIMIT_ARGS[@]}"
fi

COMPARE_ARGS=(
  "${PYTHON}" "${SCRIPT_DIR}/08_compare_to_gold.py"
  --manifest "${MANIFEST_PATH}"
)
if [[ -n "${CATALOG_LABEL}" ]]; then
  COMPARE_ARGS+=("--catalog-label" "${CATALOG_LABEL}")
fi
if should_run "compare-gold"; then
  CURRENT_STEP_NAME="compare-gold"
  run_step "Compare to gold" "${COMPARE_ARGS[@]}"
fi

QUEUE_ARGS=(
  "${PYTHON}" "${SCRIPT_DIR}/09_build_review_queue.py"
  --manifest "${MANIFEST_PATH}"
)
if [[ -n "${CATALOG_LABEL}" ]]; then
  QUEUE_ARGS+=("--catalog-label" "${CATALOG_LABEL}")
fi
QUEUE_ARGS+=("${LIMIT_ARGS[@]}")
if should_run "review-queue"; then
  CURRENT_STEP_NAME="review-queue"
  run_step "Build review queue" "${QUEUE_ARGS[@]}"
fi

if [[ ${SKIP_LLM} -eq 0 ]]; then
  LLM_COUNT=$(find "${SCRIPT_DIR}/llm" -type f -name '*.json' 2>/dev/null | wc -l | tr -d ' ')
  echo "LLM output files: ${LLM_COUNT}"
fi

echo "Done. Report: tools/bulletin_ai/aggregate/report.json"
echo "Gold summary: tools/bulletin_ai/golden_diff/summary.json"
echo "Review queue: tools/bulletin_ai/review/queue.json"
