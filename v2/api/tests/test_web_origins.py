import pytest
from app.web_origins import trusted_web_origins


def test_only_explicit_secure_origins_are_allowed():
    assert trusted_web_origins("https://example.org, https://sub.example.org/") == [
        "https://example.org", "https://sub.example.org"
    ]
    assert trusted_web_origins("http://localhost:5173, http://127.0.0.1:5173") == [
        "http://localhost:5173", "http://127.0.0.1:5173"
    ]
    assert trusted_web_origins("") == []
    for invalid in ("*", "http://example.org", "https://example.org/path",
                    "https://user:pw@example.org", "https://example.org?q=1"):
        with pytest.raises(ValueError):
            trusted_web_origins(invalid)
