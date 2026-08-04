# IntelliRelease v2
 
## What this project is
AI-Powered SAP Commerce Release Intelligence & Deployment Governance Platform.
Enterprise-level POC for the GyanSys AI Innovation Challenge 2026.
Customer scenario: Eli Lilly, SAP Commerce Cloud (Hybris).
 
## The philosophy — every architectural decision follows this chain
SYSTEM FACTS → SAP Commerce deterministic intelligence → Explainable Rule Engines → AI Explanation → Human Approval → Communication
 
AI NEVER makes deployment decisions.
AI NEVER approves releases.
AI NEVER replaces deterministic SAP Commerce knowledge.
AI explains. Humans approve.
 
## Tech stack (fixed — do not change)
- Frontend: Angular 21
- Backend: Java 21, Spring Boot 3.5, Maven
- AI Service: Python 3.12, FastAPI, Pydantic
- Database: PostgreSQL 17 (primary), H2 (local dev only)
- Email: MailHog (POC)
- API Docs: Swagger/OpenAPI (springdoc-openapi)
- Version Control: GitHub, GitHub Webhooks
 
## Architecture rules (enforced — do not violate)
1. Angular NEVER directly accesses PostgreSQL
2. Angular NEVER calls GitHub for business processing
3. Angular NEVER calls the Python AI service directly
4. GitHub webhooks land on Spring Boot, NOT Angular
5. Spring Boot orchestrates EVERYTHING — it is the single orchestrator
6. Python AI service is stateless — receives structured JSON, returns structured JSON
7. AI service has NO database access, NO GitHub access, NO email access, NO credentials
8. Human approval is enforced in Spring Boot backend, not in Angular UI
9. All risk scores are deterministic — AI only explains them in natural language
10. Every stored record carries a provenance class: FACT | DERIVED_FACT | RULE_OUTPUT | AI_INFERENCE | UNKNOWN
 
## Project structure
intellirelease/
├── frontend/          # Angular 21
├── backend/           # Java 21 / Spring Boot 3.5 / Maven
│   └── src/main/java/com/gyansys/intellirelease/
│       ├── api/       # REST controllers
│       ├── application/ # Service orchestration
│       ├── domain/    # Business logic (context, impact, risk, regression, drift, cleanup, readiness, release)
│       ├── adapters/  # External integrations (GitHub, Jira, AI service, notifications)
│       └── infra/     # Database, job queue, audit, tenant context
├── ai-service/        # Python 3.12 / FastAPI
├── database/migrations/ # Flyway SQL
├── sample-data/       # Seeded PRs, webhooks, configs
└── docker-compose.yml
 
## What NOT to introduce
- Kafka (PostgreSQL job queue is sufficient)
- Redis (no problem it solves today)
- Kubernetes runtime (Docker Compose only for POC)
- Microservices (all 12 modules are packages inside ONE Spring Boot JAR)
- Neo4j, Elasticsearch, MongoDB (PostgreSQL covers everything)
- Spring Cloud, Eureka, Config Server (single service — no service discovery needed)