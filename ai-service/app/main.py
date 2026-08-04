from fastapi import FastAPI, HTTPException

from .schemas import ExplainRequest, ExplainResponse
from .service import explain_release

app = FastAPI(title="IntelliRelease AI Service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/ai/explain", response_model=ExplainResponse)
def explain(request: ExplainRequest) -> ExplainResponse:
    try:
        return explain_release(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc