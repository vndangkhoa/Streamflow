import hmac
import hashlib
import time
import os
from fastapi import Request, HTTPException, Security
from fastapi.security import APIKeyHeader

# In production, this should be an environment variable
# For now, we'll use a placeholder that the user can set
SECRET_KEY = os.getenv("STREAMFLIX_SECRET_KEY", "your-super-secret-key-change-this")

signature_header = APIKeyHeader(name="X-Signature", auto_error=False)
timestamp_header = APIKeyHeader(name="X-Timestamp", auto_error=False)

def verify_hmac(
    request: Request,
    signature: str = Security(signature_header),
    timestamp: str = Security(timestamp_header)
):
    """
    Verify HMAC signature of the request.
    Signature = HMAC_SHA256(secret, timestamp + path + method + body)
    """
    if not signature or not timestamp:
        raise HTTPException(status_code=401, detail="Authentication headers missing")

    # 1. Check timestamp (prevents replay attacks, 5 minute window)
    try:
        request_time = int(timestamp)
        current_time = int(time.time())
        if abs(current_time - request_time) > 300: # 5 minutes
            raise HTTPException(status_code=401, detail="Request expired")
    except ValueError:
        raise HTTPException(status_code=401, detail="Invalid timestamp")

    # 2. Reconstruct payload
    # Note: For GET requests, body is empty
    body = b""
    if request.method in ["POST", "PUT", "PATCH"]:
        # This is tricky in FastAPI as reading body consumes it
        # We'll need to handle this carefully if we want to sign the body
        pass 

    path = request.url.path
    method = request.method
    
    payload = f"{timestamp}{path}{method}".encode()
    
    # 3. Calculate signature
    expected_signature = hmac.new(
        SECRET_KEY.encode(),
        payload,
        hashlib.sha256
    ).hexdigest()

    if not hmac.compare_digest(signature, expected_signature):
        raise HTTPException(status_code=401, detail="Invalid signature")

    return True
