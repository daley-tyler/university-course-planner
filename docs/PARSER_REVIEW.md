# Independent parser tests

The original review resolved 16 failing catalog snapshots. I kept that source evidence and its complete Git history in the restricted local archive because permission to redistribute the university-derived pages was not confirmed. This public copy contains none of those pages or their expected JSON files.

I replaced them with 30 independently specified HTML/JSON pairs under `backend/src/test/resources/synthetic/`. These are small fictional workshop scenarios, not renamed degree programs or shortened copies of catalog pages. The HTML uses the technical selectors that the parser accepts, but the course combinations, numbers, policy prose and complete expected trees were authored for these tests.

## How expectations are defined

`scripts/build_synthetic_parser_fixtures.py` declares each input and its expected tree separately. It uses only Python's standard library. It does not read historical fixtures, call the application parser or capture parser output. Expected nodes include ordered children, course membership, counts, credit bounds, source references, policy text and double-counting flags. The count field uses the existing DTO's JSON spelling, `nrequired`.

The golden test compares the complete serialized tree with the expected JSON. No fields are stripped and no approximate comparison replaces equality. Run `python scripts/build_synthetic_parser_fixtures.py --check` to verify the committed files against the authored specification without rewriting them. The specification is available for inspection alongside the examples.

## Coverage

| Behavior | Independent cases |
| --- | --- |
| Required courses, pairs and alternatives | Unheaded sections, paired links, OR rows, all-of choices and connector precedence |
| Course counts | One, two, three and four choices, parenthetical counts, counts combined with credit bounds |
| Credits and limits | Header hours, explicit ranges, comment minimums/maximums, totals, level filters and course/hour caps |
| Policies | Attached and unreferenced footnotes, restarted numbering, empty-selector pruning and double-counting constraints |
| Structure | Separate table headings, track options, plain code cells, cross-listed subjects and slash-separated numbers |

In a separate throwaway build, the replacement golden tests rejected reintroduced defects in footnote scope, duration counting, header counts, header credits, pruned-policy retention and explicit four-course instructions. The production parser in this export was not modified for those checks.

The six focused regression tests remain. Eighteen boundary checks and the combined-tree test use independent examples too. The corpus smoke test checks every synthetic page for valid node kinds and retention of all footnote definitions.

The former non-default live catalog scrape test has been replaced with `ProgramPipelineIntegrationTest`. It serves original pages over a local HTTP server, imports 32 invented programs into PostgreSQL, then repeats the import and checks that it creates no duplicates. It contacts no university service and runs in the normal Maven suite.

These examples preserve checks for the known failure mechanisms, while extending exact golden comparisons from 22 historical pages to 30 synthetic cases. They do not reproduce the historical catalog corpus's size or all its combinations. Passing them establishes the behaviors specified here, not correctness for every real catalog or academic policy. The original parser findings remain available privately for future authorized review.
