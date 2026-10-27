"""Adapter tests require the optional Azure Functions dependency."""
import asyncio
import json

import pytest

func = pytest.importorskip('azure.functions')
from function_app import app


def test_function_trigger_requires_key_and_preserves_api_paths():
    function = app.get_functions()[0]
    trigger = function.get_bindings_dict()['bindings'][0]
    assert trigger['authLevel'] == func.AuthLevel.FUNCTION
    assert trigger['route'] == '/{*route}'
    request = func.HttpRequest(method='GET', url='https://example.test/health', body=b'')
    response = asyncio.run(function.get_user_function()(request, None))
    assert response.status_code == 200
    assert json.loads(response.get_body())['service'] == 'phoneme-scorer'
