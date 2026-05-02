# ProjectTracker

Web application for tracking projects and tasks, built with Spring Boot and PostgreSQL.

## DevSecOps Pipeline

The CI/CD pipeline is defined in `.github/workflows/` and runs automatically on every push to `main`, on pull requests targeting `main`, and on manual dispatch.

### Pipeline Overview

```
push / PR / manual dispatch
        │
        ├────────────────────────────────────────────────────────┐
        ▼                                                        ▼
┌───────────────┐                                         ┌──────────────┐
│  Build & Test │                                         │   Secret     │
└───────┬───────┘                                         │   Scanning   │
        │ (on success)                                    │  (Gitleaks)  │
        ├──────────────────┬─────────────────┐            └──────────────┘
        ▼                  ▼                 ▼
┌──────────────┐   ┌──────────────┐  ┌───────────────┐
│     SAST     │   │     SCA      │  │ Vulnerability │
│  (CodeQL,    │   │    (Snyk)    │  │ Scan (Trivy   │
│  Semgrep,    │   │              │  │  Filesystem)  │
│  SpotBugs)   │   └───────┬──────┘  └───────────────┘
└───────┬──────┘           │ 
        └─────────┬────────┘ 
                  ▼
          ┌───────────────┐
          │   Container   │ (Docker Build + Trivy Image Scan)
          └───────┬───────┘
                  ▼
          ┌───────────────┐
          │     DAST      │ (OWASP ZAP)
          └───────────────┘
```

### Workflow Files

#### `build.yml` — Build & Test (Reusable)

Reusable workflow that compiles the project and runs all tests.

- Sets up JDK 25 (Temurin) with Maven dependency caching.
- Runs `./mvnw verify` — compiles, runs unit/integration tests, and packages the application.
- Can be triggered independently via `workflow_dispatch`.

#### `main-pipeline.yml` — DevSecOps Pipeline (Orchestrator)

Calls the build workflow, then runs multiple security checks.

### Security Checks

#### 1. SAST — Comprehensive Scan (`sast`)

**Static Application Security Testing** using multiple engines.

- **CodeQL**: Analyzes Java source code for security vulnerabilities. Uploads results as SARIF to GitHub Security.
- **Semgrep**: Scans source code using Semgrep's extensive rule registry. Uploads SARIF.
- **SpotBugs + FindSecBugs**: Analyzes compiled bytecode for security bugs and generates an HTML report.

#### 2. SCA — Snyk (`sca`)

**Software Composition Analysis** using Snyk.

- Checks project dependencies for vulnerabilities.
- Fails if issues with `high` or `critical` severity are found.
- Uploads results as SARIF to GitHub Security.
- Runs in parallel with the Java build for faster feedback.

#### 3. Container Build & DAST (`container-dast`)

Builds the Docker image, scans it for vulnerabilities, and runs Dynamic Application Security Testing.

- Uses Docker Buildx with GitHub Actions cache to build the image efficiently.
- Scans the generated image using **Trivy** for known OS and dependency vulnerabilities.
- Uploads container scanning results as SARIF to GitHub Security.
- Starts the application using Docker Compose with the freshly built image.
- Runs the OWASP ZAP baseline scan against the application URL, outputting HTML and JSON reports.
- Runs after the Build, SAST, and SCA jobs pass.

#### 5. Secret Scanning — Gitleaks (`gitleaks`)

Scans the full Git history for accidentally committed secrets.

- Detects API keys, tokens, passwords, private keys, and other sensitive strings.
- Uses `fetch-depth: 0` to clone the entire history and scan all commits.
- Runs in parallel with the build (no dependency) so secrets are caught as early as possible.

#### 6. Vulnerability Scan — Trivy Filesystem (`trivy-scan`)

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

## Run locally with PostgreSQL (Docker)

1. Start the database and app containers:

```
docker compose up -d --build
```

2. Open the app at:

```
http://localhost:8080
```

### Configuration

The app reads these environment variables (defaults shown):

- `DB_HOST` = `localhost`
- `DB_PORT` = `5432`
- `DB_NAME` = `projecttracker`
- `DB_USERNAME` = `postgres`
- `DB_PASSWORD` = `postgres`

You can also override the full JDBC URL via `DB_URL`.
