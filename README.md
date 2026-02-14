# ProjectTracker

Web application for tracking projects and tasks, built with Spring Boot and PostgreSQL.

## DevSecOps Pipeline

The CI/CD pipeline is defined in `.github/workflows/` and runs automatically on every push to `main`, on pull requests targeting `main`, and on manual dispatch.

### Pipeline Overview

```
push / PR / manual dispatch
        │
        ▼
┌───────────────┐
│  Build & Test │─────────────────────────────────────┐
└───────┬───────┘                                     │
        │ (on success)                                │ (parallel, no build dependency)
        ├──────────────────┬─────────────────┐        │
        ▼                  ▼                 ▼        ▼
┌──────────────┐  ┌────────────────┐  ┌──────────┐  ┌──────────────┐
│  SAST        │  │ Vulnerability  │  │   SCA    │  │   Secret     │
│  (CodeQL)    │  │ Scan (Trivy)   │  │ (Dep.    │  │   Scanning   │
│              │  │                │  │  Review) │  │  (Gitleaks)  │
└──────────────┘  └────────────────┘  └──────────┘  └──────────────┘
                                       (PRs only)
```

### Workflow Files

#### `build.yml` — Build & Test (Reusable)

Reusable workflow that compiles the project and runs all tests.

- Sets up JDK 25 (Temurin) with Maven dependency caching.
- Runs `./mvnw verify` — compiles, runs unit/integration tests, and packages the application.
- Can be triggered independently via `workflow_dispatch`.

#### `main-pipeline.yml` — DevSecOps Pipeline (Orchestrator)

Calls the build workflow, then runs four security checks in parallel.

### Security Checks

#### 1. SAST — CodeQL (`codeql-sast`)

**Static Application Security Testing** using GitHub's CodeQL engine.

- Analyzes Java source code for security vulnerabilities such as SQL injection, XSS, path traversal, insecure deserialization, and authentication/authorization flaws.
- Builds the project with `mvn package` so CodeQL can trace data flows through compiled bytecode.
- Results are uploaded as SARIF to the **Security → Code scanning** tab in GitHub.
- Runs after the build job passes.

#### 2. SCA — Dependency Review (`dependency-review`)

**Software Composition Analysis** using GitHub's Dependency Review action.

- Compares dependency changes between the PR branch and `main` to detect newly introduced vulnerabilities.
- Fails the check if any new dependency has a **high** or **critical** severity CVE.
- Posts a summary comment on the pull request listing all flagged dependencies.
- **Runs only on pull requests.**

#### 3. Secret Scanning — Gitleaks (`gitleaks`)

Scans the full Git history for accidentally committed secrets.

- Detects API keys, tokens, passwords, private keys, and other sensitive strings.
- Uses `fetch-depth: 0` to clone the entire history and scan all commits.
- Runs in parallel with the build (no dependency) so secrets are caught as early as possible.

#### 4. Vulnerability Scan — Trivy (`trivy-scan`)

**Vulnerability and misconfiguration scanning** using Aqua Security's Trivy.

- Performs a filesystem scan of the repository to find known CVEs in dependencies and configuration issues.
- Reports only **critical** and **high** severity findings.
- Results are uploaded as SARIF to the **Security → Code scanning** tab in GitHub.
- Runs after the build job passes.

### Permissions

The pipeline follows the principle of least privilege. Each job requests only the permissions it needs:

- `contents: read` — all jobs (checkout code).
- `security-events: write` — CodeQL and Trivy (upload SARIF reports).
- `pull-requests: write` — Dependency Review (post PR comments).

### Viewing Results

- **CodeQL & Trivy findings** → GitHub repo → **Security** tab → **Code scanning alerts**.
- **Dependency Review** → comment on the pull request.
- **Gitleaks** → job logs in the **Actions** tab (fails the workflow if secrets are found).

### Prerequisites

- The repository must have **GitHub Advanced Security** enabled for SARIF uploads (free for public repos).
- `GITHUB_TOKEN` is provided automatically by GitHub Actions.
- Gitleaks requires no additional configuration for public or personal private repos. Organization private repos may require a `GITLEAKS_LICENSE` secret.

## Tech Stack

- **Java 25** / **Spring Boot 4.0**
- **Spring Security** with JWT authentication
- **Spring Data JPA** with **PostgreSQL**
- **Lombok** for boilerplate reduction
- **H2** in-memory database for tests
- **Maven** build system (wrapper included)
