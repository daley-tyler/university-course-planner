# Bulletin AI Audit Toolkit

This folder is a dev-only pipeline to audit the full bulletin catalog with a local LLM (Ollama).
It is intentionally separate from the main app so you can remove it from production deployments.

## What it does
- Builds a manifest of program pages (from the DB).
- Downloads HTML fixtures and extracts requirement-only snippets.
- Fetches requirement trees from your local backend API.
- Runs a deterministic lint pass.
- Uses a local LLM to spot structural issues and suggest parser rules.
- Aggregates issues across the catalog.

## Requirements
- Python 3.10+ (CI validates Python 3.12)
- Ollama running locally (http://localhost:11434)
- Backend running locally (http://localhost:8080)
- DB access (for manifest generation)

## Setup

Create a virtual environment and install dependencies:

```
python -m venv .venv
source .venv/bin/activate
python -m pip install -r tools/bulletin_ai/requirements.txt
```

The scripts auto-load `tools/bulletin_ai/.env` if present.
Useful environment overrides:
- `OLLAMA_NUM_CTX=8192` (raise context window on the Ollama server)
- `OLLAMA_PROMPT_MAX_CHARS=9000` (hard cap on prompt size)

## Step 1: Build a manifest from the DB

This pulls program IDs, catalog labels, and source URLs from `program_catalog_policy`.

```
python tools/bulletin_ai/01_build_manifest_from_db.py \
  --output tools/bulletin_ai/fixtures/manifest.json
```

Optional filters:
- `--catalog-year 2023`
- `--catalog-label 2023-2024`

DB connection is read from `DATABASE_URL` or standard `PG*` environment variables.

## One-command pipeline

You can run the entire pipeline with a single script:

```
bash tools/bulletin_ai/run_pipeline.sh
```

Note: `run_pipeline.sh` uses your system Python; install deps first with `pip install -r tools/bulletin_ai/requirements.txt`.

Common options:
- `--limit 25` (sample run)
- `--model llama3.1:8b`
- `--secondary-model llama3.1:70b`
- `--only-flagged`
- `--skip-llm`
- `--skip-repair`
- `--force`
- `--resume-from apply-repairs`
- `--resume-from next` (resume after the last completed step stored in `tools/bulletin_ai/.pipeline_state`)

## Step 2: Download HTML fixtures

```
python tools/bulletin_ai/02_fetch_html.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Step 3: Extract requirement-only snippets

```
python tools/bulletin_ai/03_extract_snippets.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

If you hit a segmentation fault with `lxml`, use the safe built-in parser:

```
python tools/bulletin_ai/03_extract_snippets.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json \
  --parser html.parser
```

## Step 4: Fetch requirement trees from the API

```
python tools/bulletin_ai/04_fetch_requirements_api.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Step 5: Cleanup parsed trees (deterministic)

This removes duplicate notes and flattens unnamed wrapper blocks before lint/LLM runs.

```
python tools/bulletin_ai/04b_cleanup_parsed.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Step 6: Run deterministic lint

```
python tools/bulletin_ai/05_lint_requirements.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Step 7: Run the LLM audit (Ollama)

```
python tools/bulletin_ai/06_run_llm.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json \
  --model llama3.1:8b \
  --max-workers 2
```

Optional: run 70B only on lint-flagged pages

```
python tools/bulletin_ai/06_run_llm.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json \
  --model llama3.1:70b \
  --only-flagged
```

## Step 8: LLM repair suggestions (optional)

This produces patch suggestions to repair the parsed tree (dev-only).

```
python tools/bulletin_ai/10_llm_repair.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json \
  --model llama3.1:8b
```

## Step 9: Apply repairs (optional)

```
python tools/bulletin_ai/11_apply_repairs.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Step 10: Aggregate results

```
python tools/bulletin_ai/07_aggregate_reports.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Step 11: Compare against golden fixtures

This compares parsed trees against human-verified fixtures (from
`backend/src/test/resources/synthetic` and `tools/bulletin_ai/golden/expected`).

```
python tools/bulletin_ai/08_compare_to_gold.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

Optional gold gates (env):
- `GOLD_MIN_PASS_RATE=0.95`
- `GOLD_MAX_MISMATCHES=2`

## Step 12: Build the review queue

This combines lint, LLM findings, and golden diffs into a single prioritized list.

```
python tools/bulletin_ai/09_build_review_queue.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json
```

## Feedback loop (golden review)

1. Open `tools/bulletin_ai/review/queue.json` and pick the highest-score items.
2. Compare the snippet HTML, parsed tree, and LLM/lint notes.
3. If the parsed tree is correct, add it to `tools/bulletin_ai/golden/expected/<slug>.expected.json`.
4. Re-run Step 8 to confirm the diff is clean.
5. Add an "LLM Guidance Note" in the GUI to steer a re-run of the LLM audit.

To re-run the LLM with guidance notes:

```
python tools/bulletin_ai/06_run_llm.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json \
  --review-queue tools/bulletin_ai/review/queue.json \
  --only-flagged
```

To re-run the LLM repair pass with guidance notes:

```
python tools/bulletin_ai/10_llm_repair.py \
  --manifest tools/bulletin_ai/fixtures/manifest.json \
  --review-queue tools/bulletin_ai/review/queue.json \
  --only-flagged
```

## Quick review GUI

Spin up a lightweight GUI to browse the review queue and inspect snippets/JSON:

```
python tools/bulletin_ai/review_gui/server.py
```

Then open http://127.0.0.1:5175 in your browser.

## Output locations
- HTML fixtures: `tools/bulletin_ai/fixtures/html/<catalogLabel>/<slug>.html`
- Snippets: `tools/bulletin_ai/fixtures/snippets/<catalogLabel>/<slug>.html`
- Parsed trees: `tools/bulletin_ai/parsed/<catalogLabel>/<slug>.json`
- Lint results: `tools/bulletin_ai/lint/<catalogLabel>/<slug>.json`
- LLM results: `tools/bulletin_ai/llm/<catalogLabel>/<slug>.json`
- LLM repairs: `tools/bulletin_ai/repairs/<catalogLabel>/<slug>.json`
- Repaired trees: `tools/bulletin_ai/repaired/<catalogLabel>/<slug>.json`
- Aggregate summary: `tools/bulletin_ai/aggregate/report.json`
- Golden diffs: `tools/bulletin_ai/golden_diff/<catalogLabel>/<slug>.json`
- Golden summary: `tools/bulletin_ai/golden_diff/summary.json`
- Review queue: `tools/bulletin_ai/review/queue.json`
- Review summary: `tools/bulletin_ai/review/summary.json`

## Notes
- All outputs are cached. Re-running a step skips files unless you pass `--force`.
- Most stages write local analysis files. The explicitly invoked repair application stage can update database requirement trees. Use a dedicated review database and only data you are authorized to process. No scraped HTML, model weights or generated review outputs are included in this source export.
