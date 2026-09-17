# Pull Request Checks Design

## Goal

Automatically validate the Java backend and scan it for common security issues on pull requests targeting `main`, then protect `main` so changes require a passing check and an approving review.

## Scope

- Add a GitHub Actions workflow that runs the backend Maven test suite with Java 21.
- Add a CodeQL workflow for the Java backend on pull requests, pushes to `main`, and manual runs.
- Configure `main` branch protection with one approving review and the two required checks.
- Keep application source code and runtime behavior unchanged.

## Design

### Backend check

The `Backend Check` workflow runs from the `backend` directory with `actions/setup-java@v4`, Java 21, Maven dependency caching, and `mvn -B -ntp verify`. The existing H2-backed Spring Boot context test can run without an external PostgreSQL service.

### Security scan

The `CodeQL` workflow analyzes Java using GitHub's supported CodeQL action. It uses manual build mode and runs Maven from the `backend` directory because the repository keeps its `pom.xml` below the repository root.

### Branch protection

The `main` branch requires:

- one approving pull-request review;
- the `Backend Check / Build and test` check;
- the `CodeQL / Analyze (java)` check;
- dismissal of stale approvals after new changes;
- conversation resolution before merge;
- administrator enforcement and no force-push or deletion.

## Acceptance criteria

- A pull request to `main` displays the backend test and CodeQL checks.
- The backend check passes with the current Java 21 project and H2 test configuration.
- `main` cannot be updated through an unreviewed direct push under the configured protection rules.
- The workflow files use least-privilege permissions and can be run manually where appropriate.
