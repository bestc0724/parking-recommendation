import os
from dotenv import load_dotenv

load_dotenv()

JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8080")
INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY", "parking-internal-key-2024")
INTERNAL_HEADERS = {"X-Internal-Key": INTERNAL_API_KEY}
