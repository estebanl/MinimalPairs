"""Azure Functions entry point; reuse the same locally tested FastAPI routes."""
import azure.functions as func

from app.main import app as api

app = func.AsgiFunctionApp(app=api, http_auth_level=func.AuthLevel.FUNCTION)
