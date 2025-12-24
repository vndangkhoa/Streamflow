import os
import httpx
import hashlib
from PIL import Image
from io import BytesIO
from fastapi.responses import Response
from typing import Optional

CACHE_DIR = "cache/images"
os.makedirs(CACHE_DIR, exist_ok=True)

async def get_proxied_image(url: str, width: Optional[int] = None):
    """
    Fetch an image, resize it, convert to WebP, and cache it.
    """
    # Create a unique cache key based on URL and width
    cache_key = hashlib.md5(f"{url}_{width}".encode()).hexdigest()
    cache_path = os.path.join(CACHE_DIR, f"{cache_key}.webp")

    # 1. Check if cached version exists
    if os.path.exists(cache_path):
        with open(cache_path, "rb") as f:
            return Response(content=f.read(), media_type="image/webp")

    # 2. Fetch original image
    async with httpx.AsyncClient(follow_redirects=True) as client:
        try:
            response = await client.get(url, timeout=10.0)
            response.raise_for_status()
        except Exception as e:
            # Fallback or error
            return None

    # 3. Process image with Pillow
    try:
        img = Image.open(BytesIO(response.content))
        
        # Convert to RGB if necessary (e.g., from RGBA or CMYK)
        if img.mode in ("RGBA", "P"):
            img = img.convert("RGB")
        
        # Resize if width specified
        if width and img.width > width:
            ratio = width / float(img.width)
            height = int(float(img.height) * float(ratio))
            img = img.resize((width, height), Image.LANCZOS)

        # 4. Save to buffer as WebP
        output = BytesIO()
        img.save(output, format="WEBP", quality=80)
        webp_data = output.getvalue()

        # 5. Save to cache
        with open(cache_path, "wb") as f:
            f.write(webp_data)

        return Response(content=webp_data, media_type="image/webp")

    except Exception as e:
        print(f"Error processing image: {e}")
        return None
