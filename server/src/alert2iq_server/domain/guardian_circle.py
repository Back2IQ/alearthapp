"""
Guardian Circle Domain Model for Alert2IQ.
Manages encrypted family/friend safety circles, invitation codes, and real-time safety status aggregation.
"""

import time
import secrets
from typing import Dict, List, Optional
from dataclasses import dataclass, field, asdict

class SafetyStatus:
    UNKNOWN = "UNKNOWN"
    SAFE = "SAFE"
    NEEDS_HELP = "NEEDS_HELP"

@dataclass
class GuardianMember:
    member_id: str
    display_name: str
    phone_summary: str = ""
    status: str = SafetyStatus.UNKNOWN
    last_updated_ts: float = field(default_factory=time.time)
    latitude: Optional[float] = None
    longitude: Optional[float] = None

@dataclass
class GuardianCircle:
    circle_id: str
    owner_id: str
    circle_name: str
    created_ts: float = field(default_factory=time.time)
    members: Dict[str, GuardianMember] = field(default_factory=dict)
    invite_code: str = field(default_factory=lambda: secrets.token_hex(4).upper())

    def add_member(self, member: GuardianMember) -> None:
        self.members[member.member_id] = member

    def update_member_status(self, member_id: str, status: str, lat: Optional[float] = None, lon: Optional[float] = None) -> bool:
        if member_id not in self.members:
            return False
        m = self.members[member_id]
        m.status = status
        m.last_updated_ts = time.time()
        if lat is not None:
            m.latitude = lat
        if lon is not None:
            m.longitude = lon
        return True

    def get_summary(self) -> Dict:
        total = len(self.members)
        safe = sum(1 for m in self.members.values() if m.status == SafetyStatus.SAFE)
        needs_help = sum(1 for m in self.members.values() if m.status == SafetyStatus.NEEDS_HELP)
        unknown = total - safe - needs_help
        return {
            "circle_id": self.circle_id,
            "circle_name": self.circle_name,
            "invite_code": self.invite_code,
            "total_members": total,
            "safe_count": safe,
            "needs_help_count": needs_help,
            "unknown_count": unknown,
            "members": [asdict(m) for m in self.members.values()]
        }