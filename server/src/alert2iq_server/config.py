from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    signing_key_b64: str
    redis_url: str | None
    fcm_project_id: str | None
    fcm_credentials: str | None
    min_publish_mag: float

    @classmethod
    def from_env(cls) -> "Config":
        key = os.environ.get("TDA_SIGNING_KEY")
        if not key:
            raise SystemExit("TDA_SIGNING_KEY missing (run scripts/gen_signing_key.py)")
        return cls(
            signing_key_b64=key,
            redis_url=os.environ.get("TDA_REDIS_URL"),
            fcm_project_id=os.environ.get("TDA_FCM_PROJECT_ID"),
            fcm_credentials=os.environ.get("TDA_FCM_CREDENTIALS"),
            min_publish_mag=float(os.environ.get("TDA_MIN_PUBLISH_MAG", "4.0")),
        )
