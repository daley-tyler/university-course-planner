# Third-party notices

These notices apply to the named upstream files and dependencies. They do not select a license for the team's application code.

## UI components

The components in `course_planner_frontend/src/components/ui/` include code generated or adapted from [shadcn/ui](https://github.com/shadcn-ui/ui). The original MIT notice, Copyright (c) 2023 shadcn, is retained in [shadcn-ui-MIT.txt](docs/licenses/shadcn-ui-MIT.txt). Components have been adapted for this application.

Radix UI, React, Lucide and other installed frontend packages retain their own package licenses. Their versions are recorded in `course_planner_frontend/package-lock.json`; installed dependencies and production bundles are excluded from this source repository.

## Maven Wrapper

`backend/mvnw` and `backend/mvnw.cmd` retain their upstream Apache headers. The accompanying [Apache License](docs/licenses/maven-wrapper-LICENSE.txt) and [Maven Wrapper notice](docs/licenses/maven-wrapper-NOTICE.txt) are copied from Apache Maven Wrapper 3.3.4. These scripts have not been presented as original team work.

The backend dependencies in `backend/pom.xml` and Python dependencies in `tools/bulletin_ai/requirements.txt` retain their upstream licenses. Downloaded JARs, Python environments and dependency caches are excluded from the repository.

## Media

The campus footage and derived poster are covered separately by the Pexels License. [Media credits](docs/MEDIA.md) identify the creator, source and preparation steps. No font binaries are bundled; the stylesheet requests fonts through Google Fonts.
