# IntelliRelease

AI-Powered SAP Commerce Release Intelligence & Deployment Governance Platform.
See [CLAUDE.md](CLAUDE.md) for the full architecture and philosophy.

## What is included

- **Backend** (Spring Boot / Java 21): GitHub webhook ingestion, the SAP Commerce
  Context Engine (knowledge-base driven), risk/impact/regression scoring, release
  build-from-Git, changelog generation, human-approval-gated notification dispatch.
- **AI service** (FastAPI / Python): explains structured, deterministic facts in
  natural language via Groq (or Ollama locally), with a repair-retry and a
  deterministic template fallback when the model is unavailable.
- **Frontend** (Angular): login, PR dashboard with SAP Commerce classification,
  release management (build / notes / approve / notify / deploy confirmation).

## Repository layout

- `backend/` Spring Boot API (Java 21)
- `ai-service/` FastAPI AI explanation service (Python)
- `frontend/` Angular dashboard

## Prerequisites

| Tool | Needed for | Notes |
|---|---|---|
| JDK 21 | Backend | `mvnw`/`mvnw.cmd` is committed, no separate Maven install needed |
| Node.js 20+ and npm | Frontend | |
| Python 3.10+ | AI service | 3.12 is the target, 3.10 also works |
| [MailHog](https://github.com/mailhog/MailHog/releases) | Email notifications | No Docker needed — grab `MailHog_windows_amd64.exe`, rename/run directly. Fake local SMTP trap; does not deliver to the real internet. |
| [ngrok](https://ngrok.com) | Real GitHub webhooks | Only needed to receive webhooks from a real GitHub repo. Not needed to run the app itself. |
| A Groq API key (free) | Real AI narration | Get one at console.groq.com. Without it, every AI call deterministically falls back to templated narration — the app still works, just with plainer prose. |

## Environment variables

Set these before starting the **backend**. All are optional except where noted —
without them, the corresponding feature degrades gracefully rather than failing.

| Variable | Purpose | Default if unset |
|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | HMAC verification for real GitHub webhooks | blank → unsigned demo mode |
| `GITHUB_TOKEN` | GitHub API reads (fetch changed files, resolve commits for release build) | unset → falls back to webhook payload data only |
| `DEVELOPER_DISTRIBUTION`, `QA_DISTRIBUTION`, `BUSINESS_DISTRIBUTION`, `CLIENT_DISTRIBUTION` | Where each audience's release note email goes | `dev-team@demo.local` etc. |
| `TEAMS_WEBHOOK_URL` | Teams channel notification on release notify | unset → Teams post skipped |

Set this before starting the **AI service**:

| Variable | Purpose | Default if unset |
|---|---|---|
| `GROQ_API_KEY` | Real AI-generated narration via Groq | unset → falls back to Ollama, then to deterministic templates |
| `GROQ_MODEL` | Which Groq model | `llama-3.1-8b-instant` |

## Running it yourself

Start these **in order**, each in its own terminal. PowerShell shown; adjust paths for your shell.

### 1. MailHog (optional — only needed to test email notifications)

```powershell
.\tools\MailHog.exe
```
- SMTP: `localhost:1025` · Web UI: http://localhost:8025

### 2. AI service

```powershell
cd ai-service
python -m venv .venv          # first time only
.\.venv\Scripts\pip install -r requirements.txt   # first time only
$env:GROQ_API_KEY = "gsk_..."                       # optional but recommended
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
```
Check: http://localhost:8000/healthz should show `"provider":"groq"` (or `"ollama"` if no key set) and `"providerReachable":true`.

### 3. Backend

```powershell
cd backend
$env:GITHUB_WEBHOOK_SECRET = "..."   # optional, needed for real GitHub webhooks
$env:GITHUB_TOKEN = "..."            # optional, needed to resolve real PR/commit data
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```
The `dev` profile runs on an **in-memory H2 database — data is wiped every restart.**
No PostgreSQL, MailHog, or AI service required just to boot.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

### 4. Frontend

```powershell
cd frontend
npm install     # first time only
npm start
```
Open http://localhost:4200. Sign in with a seeded account:

| Username | Password | Role |
|---|---|---|
| `developer` | `developer` | DEVELOPER |
| `qa` | `qa` | QA |
| `releasemanager` | `releasemanager` | RELEASE_MANAGER, APPROVER |
| `approver` | `approver` | APPROVER |

## Using it

### Path A — real GitHub webhooks (needs ngrok)

1. `ngrok http 8080` → copy the public HTTPS URL it prints.
2. In your GitHub repo: Settings → Webhooks → Add webhook
   - Payload URL: `<ngrok-url>/api/v1/webhooks/github`
   - Content type: `application/json`
   - Secret: same value as `GITHUB_WEBHOOK_SECRET`
   - Events: **Pull requests** only
3. Merge a PR. It's captured, queued, and analyzed automatically within a couple seconds —
   watch it appear on the dashboard's Pull Requests list.

### Path B — releases, from the dashboard or the API directly

1. **Create** a release (repo, version, and a real `fromRef`/`toRef` — branch names, tags, or
   commit SHAs; not `HEAD~5`-style relative syntax, since it goes through GitHub's compare API).
2. **Build from Git** — resolves the true PR set via the commit graph (cherry-pick aware,
   revert-netted) and attaches every PR IntelliRelease has already captured.
3. **Generate release notes** — AI-narrated changelog, one bullet per PR, grounded in each
   PR's title/description/changed files/SAP Commerce classification.
4. **Approve** — the governance gate. Nothing sends before this.
5. **Send to Outlook + Teams** — emails the four audience notes to their distribution lists
   and posts to Teams if configured. Blocked with `409 APPROVAL_REQUIRED` before step 4.
6. **Mark deployed** — human confirmation that the release is actually live. Independent of
   steps 3–5; there is no CI/CD integration behind this, it's a manual attestation.

## Default ports

- Backend: `8080`
- AI service: `8000`
- Frontend: `4200`
- MailHog: `1025` (SMTP), `8025` (UI)
- Ollama (optional, local AI model fallback): `11434`

## Backend endpoints (current)

- `POST /api/v1/webhooks/github` — GitHub webhook ingestion
- `GET /api/v1/pull-requests`, `GET /api/v1/pull-requests/{id}` — captured PRs + analysis
- `POST /api/v1/releases`, `GET /api/v1/releases`, `GET /api/v1/releases/{id}`
- `POST /api/v1/releases/{id}/build` — resolve contents from Git
- `GET /api/v1/releases/{id}/notes` — AI-generated changelog preview
- `POST /api/v1/releases/{id}/approve` — governance gate
- `POST /api/v1/releases/{id}/notify` — send email + Teams (409 if not approved)
- `POST /api/v1/releases/{id}/deploy` — manual deployment confirmation

Not yet built: the aggregate release-level risk/impact/regression pass, a dashboard summary
endpoint, and an audit-log viewer endpoint (the audit table itself is written to already).

## AI service endpoints

- `GET /healthz`
- `POST /analyze` — per-PR explanation of deterministic analysis
- `POST /synthesize` — release-level audience notes + per-PR changelog bullets
