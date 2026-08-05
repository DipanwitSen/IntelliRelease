# IntelliRelease

AI-Powered SAP Commerce Release Intelligence & Deployment Governance Platform.
See [CLAUDE.md](CLAUDE.md) for the full architecture and philosophy.

## What is included

- Java backend for GitHub webhook ingestion, release summaries, risk scoring, and readiness scoring.
- Python AI service that explains structured, deterministic release facts via Ollama (local),
  with a repair-retry and a deterministic template fallback when the model is unavailable.
- Angular dashboard for release visibility, QA guidance, and approval workflow.
- Docker Compose wiring for local development and future deployment.

## Repository layout

- `backend/` Spring Boot API (Java 21)
- `ai-service/` FastAPI AI explanation service (Python)
- `frontend/` Angular dashboard

## Local run

### Backend

1. Install JDK 21.
2. From `backend/`, run `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` (Windows)
   or `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (macOS/Linux).
   The `dev` profile runs on an in-memory H2 database — no PostgreSQL, MailHog, or Ollama required to start.
3. Swagger UI: http://localhost:8080/swagger-ui/index.html
4. Health: http://localhost:8080/actuator/health

### AI service

1. Install Python 3.12.
2. From `ai-service/`, create a virtual environment and install `requirements.txt`.
3. Run `uvicorn app.main:app --reload --port 8000`.
4. Without Ollama running locally, every request deterministically falls back to
   templated narration (`fallback: true` in the response) — this is expected, not an error.
   To get real model output, install [Ollama](https://ollama.com), run `ollama pull llama3.1:8b`,
   and leave it running on its default port `11434`.

### Frontend

1. Install Node.js 20+ and npm.
2. From `frontend/`, run `npm install`.
3. Run `npm start`.
4. Open http://localhost:4200 — it calls the backend's `/actuator/health` to confirm connectivity.

## Default ports

- Backend: `8080`
- AI service: `8000`
- Frontend: `4200`
- Ollama (optional, local AI model): `11434`
- MailHog (optional, local SMTP capture): `1025` (SMTP), `8025` (UI)

## Backend endpoints (current)

- `POST /api/v1/webhooks/github` — GitHub webhook ingestion (event-driven entry point)

More endpoints (`/api/v1/releases/**`, `/api/v1/dashboard/**`, etc.) are planned per
[CLAUDE.md](CLAUDE.md) but not yet implemented.

## AI service endpoints

- `GET /healthz`
- `POST /analyze` — per-PR explanation of deterministic analysis
- `POST /synthesize` — release-level audience notes (developer/QA/business/client)
