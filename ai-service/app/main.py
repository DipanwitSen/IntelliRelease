from fastapi import FastAPI
from fastapi.responses import JSONResponse

from . import providers, service
from .schemas import AnalyzeRequest, AnalyzeResponse, SynthesizeRequest, SynthesizeResponse


class Utf8JSONResponse(JSONResponse):
    """Starlette's default JSONResponse omits charset=utf-8 from Content-Type.
    Spring's HTTP message converter treats a charset-less media type as
    ISO-8859-1, which corrupts any non-ASCII character (narration prose is
    free text and not guaranteed to stay ASCII)."""

    media_type = "application/json; charset=utf-8"


app = FastAPI(
    title="IntelliRelease AI Service",
    version="0.2.0",
    default_response_class=Utf8JSONResponse,
)


@app.get("/healthz")
def healthz() -> dict[str, object]:
    return {
        "status": "UP",
        "provider": providers.active_provider_name(),
        "model": providers.active_model_name(),
        "providerReachable": providers.is_healthy(),
    }


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    return service.analyze(request)


@app.post("/synthesize", response_model=SynthesizeResponse)
def synthesize(request: SynthesizeRequest) -> SynthesizeResponse:
    return service.synthesize(request)
