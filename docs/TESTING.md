# Testing the public export

The clean export was validated on 29 October 2026 with Java 21, Node.js 24.11.1, Python 3.12 and real PostgreSQL 16 Testcontainers. The application API is Java; Python supports optional utilities and repository validation.

## Frontend

From `course_planner_frontend/`:

```bash
npm ci
npm test
npm run lint
npm run build
```

The fresh install, all 18 tests, lint and production build pass. Tests cover copied schedules and reloads, independent plan arrays, prerequisite ordering, unscheduled courses, credit caps, audit wording, requirement choices, ALL_OF bundles, policy-note exclusion and term ordering. The build still reports a roughly 671 kB minified JavaScript chunk; its warning threshold has not been raised.

Catalog search and requirement data in the routed application come from the API. Legacy prototype data now uses invented courses rather than a university catalog. The PostgreSQL demonstration loader still contains the same six invented courses, one program and simple prerequisite edges.

## Backend and parser

With Docker running, use Java 21 from `backend/`:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Then, from the repository root:

```bash
python3 scripts/verify_backend_reports.py
```

The full run discovers 80 tests: **79 pass, none fail and one existing manual export utility skips**. It includes 30 exact synthetic golden cases, six focused parser regressions, 18 parser boundary cases and corpus policy-retention checks. [Parser testing](PARSER_REVIEW.md) documents the independent replacements.

PostgreSQL tests run real Flyway migrations and validate the JPA schema. They cover catalog search, requirement serialization, prerequisite details, local login, demo loading and course normalization. Authenticated HTTP checks use CSRF tokens and verify rejection without CSRF. The new local HTTP program-pipeline test imports 32 invented programs twice and checks database contents and idempotence. No university service is contacted.

`ReqTreeExportTest` is a manually invoked utility that writes JSON from a configured database. Its existing skip is the only one CI permits. Testcontainers can skip application tests locally without Docker, but the report verifier rejects that incomplete result. It also rejects failures, missing suites, missing reports and duplicate suite reports.

On macOS with Docker Desktop, the review used:

```bash
DOCKER_HOST=unix:///Users/tyler/.docker/run/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
./mvnw --batch-mode --no-transfer-progress clean verify
```

These machine-specific paths are examples, not additional setup requirements on Linux CI.

## Python utilities and CI

From the repository root:

```bash
python3.12 -m venv .venv
. .venv/bin/activate
python -m pip install -r tools/bulletin_ai/requirements.txt
python -m pip check
python -m unittest discover -s tests -v
python -m compileall -q scripts tools tests
python -c "import bs4, lxml.etree, requests, tqdm, psycopg"
for script in tools/bulletin_ai/*.py tools/media/*.py tools/bulletin_ai/review_gui/server.py; do
  python "$script" --help > /dev/null
done
```

Nine Python tests check specification drift, a complete backend report and seven incomplete or invalid report scenarios. All tests, dependency checks, compilation and 15 CLI help checks pass. There is no Python API test suite. Live scraping, local-model inference and video regeneration require additional services and are outside these checks.

`.github/workflows/ci.yml` runs the frontend, full backend and Python checks in three jobs. It uses pinned official actions, dependency caches, timeouts and read-only repository permissions. Docker is mandatory for the backend job. The workflow passes actionlint validation; no hosted GitHub Actions run has been dispatched and it performs no publishing or deployment.

## Screenshots and limits

The four checked-in screenshots were captured from the reviewed running application with invented demonstration data. They remain unchanged in this export. The backend application, database migrations, active React route components, planner logic, styling and licensed media remain unchanged. The export replaces test data and legacy mock content, not the application's features.

The earlier browser review exercised local login, course selection, scheduling, copied plans after reload, prerequisite warnings, requirement choices and stale-audit clearing. Both Docker images and the complete Compose demo were also verified before export. The export itself was checked with fresh dependency installs, the production frontend build and the complete Maven package/test lifecycle; the earlier browser and Compose sessions are not presented as new export runs.

Plans use browser storage and do not sync between accounts or devices. Full prerequisite alternatives, concurrent enrollment, grades, residency, timetable conflicts and course availability are not fully evaluated. Live authentication, external scraping and production HTTPS remain unverified. Synthetic parser tests specify supported behavior; they do not prove every real catalog or academic policy is interpreted correctly.
