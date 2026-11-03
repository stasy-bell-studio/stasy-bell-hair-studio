"""Only explicit HTTPS browser origins are permitted. Loopback HTTP is for local dev."""
from urllib.parse import urlsplit


def trusted_web_origins(raw: str) -> list[str]:
    result = []
    for candidate in raw.split(","):
        origin = candidate.strip().rstrip("/")
        if not origin:
            continue
        parsed = urlsplit(origin)
        local_http = (parsed.scheme == "http" and parsed.hostname in
                      {"localhost", "127.0.0.1"})
        if (parsed.scheme != "https" and not local_http or not parsed.hostname
                or parsed.username is not None or parsed.password is not None
                or parsed.path or parsed.query or parsed.fragment or origin == "*"):
            raise ValueError("STASY_ALLOWED_WEB_ORIGINS must contain browser origins only")
        if origin not in result:
            result.append(origin)
    return result
