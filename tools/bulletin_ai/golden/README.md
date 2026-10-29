# Expected requirement trees

The application's independent golden examples live in `backend/src/test/resources/synthetic/`. Each HTML input has a complete expected JSON tree, specified in `scripts/build_synthetic_parser_fixtures.py`. The synthetic corpus is checked in CI.

This folder is an optional location for additional, authorized review examples. No scraped catalog content is included. Before adding an example, establish permission to redistribute its input and expected data. Write the expected structure independently and review course membership, choices, credit limits and policy retention. Do not treat unreviewed parser output as an authoritative expectation.
