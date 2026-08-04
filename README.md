# IntelliRelease

Phase 1 base codebase for SAP Commerce release intelligence.

## What is included

- Java backend for GitHub webhook ingestion, release summaries, risk scoring, and readiness scoring.
- Python AI service that explains structured release facts and can fall back across OpenAI, Claude, Gemini, and Groq.
- Angular dashboard for release visibility, QA guidance, and approval workflow.
- Docker Compose wiring for local development and future deployment.

## Repository layout

- `backend-java/` Spring Boot API
- `ai-service/` FastAPI AI explanation service
- `frontend-angular/` Angular dashboard

## Local run

### Backend

1. Install Java 21 and Maven.
2. From `backend-java/`, run `mvn spring-boot:run`.

### AI service

1. Install Python 3.11+.
2. From `ai-service/`, create a virtual environment and install `requirements.txt`.
3. Run `uvicorn app.main:app --reload --port 8000`.

### Frontend

1. Install Node.js 22+.
2. From `frontend-angular/`, run `npm install`.
3. Run `npm start`.

## Default ports

- Backend: `8080`
- AI service: `8000`
- Frontend: `4200`

## Phase 1 endpoints

- `POST /api/webhooks/github`
- `GET /api/releases`
- `GET /api/releases/{releaseId}`
- `GET /api/releases/{releaseId}/narrative`
- `GET /api/health`
- `POST /ai/explain`
