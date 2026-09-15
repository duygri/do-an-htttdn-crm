# Pull Request Workflow Scope Design

## Goal

Run each GitHub Actions workflow only when the changed files are relevant to that workflow. Backend changes should trigger the backend build/test and Java CodeQL checks; frontend-only or documentation-only changes should not trigger backend checks.

## Scope

- Keep two independent workflows: `Backend Check` and `CodeQL`.
- Scope the backend workflow to `backend/**` and its own workflow file.
- Scope the CodeQL workflow to `backend/**` and its own workflow file.
- Preserve pull-request targeting of `main`, pushes to `main`, and manual execution.
- Preserve the weekly CodeQL schedule.
- Keep the existing job names so any future status-check mapping remains stable.
- Do not change the current `main` branch approval or conversation-resolution rules.

## Design

### Backend check

The `Backend Check` workflow runs `mvn -B -ntp verify` with Java 21 from the `backend` directory when a pull request or push changes backend files or the backend workflow configuration. It does not run for frontend-only or documentation-only changes.

### Security scan

The `CodeQL` workflow analyzes Java with manual build mode and runs the Maven package step from the `backend` directory when backend files or the CodeQL workflow configuration change. Its scheduled and manually dispatched runs remain available independently of path filters.

### Path-to-workflow mapping

| Changed area | Backend Check | CodeQL |
|---|---:|---:|
| `backend/**` | Run | Run |
| `.github/workflows/backend-check.yml` | Run | Skip |
| `.github/workflows/codeql.yml` | Skip | Run |
| `frontend/**` | Skip | Skip |
| `docs/**` | Skip | Skip |

### Branch protection

The current repository configuration does not require the two status checks for merging. The workflow changes in this design do not re-enable those requirements; the existing approving-review and conversation-resolution rules remain unchanged.

## Acceptance criteria

- A backend-only pull request runs `Backend Check / Build and test` and `CodeQL / Analyze (java)`.
- A frontend-only or documentation-only pull request does not run either backend workflow.
- A change to one workflow file runs that workflow so its configuration is exercised.
- The workflow files use least-privilege read permissions and retain Java 21.
- `git diff --check` passes and both workflow files remain valid YAML.
