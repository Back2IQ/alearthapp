import json
from alert2iq_server.adapters.cap_exporter import CapExporter

def test_cap_export_structure():
    payload = {
        "id": "eq_2026_istanbul",
        "mag": 6.8,
        "tier": "P2",
        "lat": 41.01,
        "lon": 28.98,
        "cityName": "Istanbul",
        "originTs": 1799999999000,
        "test": False
    }
    
    cap_json = CapExporter.alert_to_cap_json(payload)
    cap_data = json.loads(cap_json)
    
    assert "cap" in cap_data
    assert cap_data["cap"]["version"] == "1.2"
    assert cap_data["cap"]["status"] == "Actual"
    
    info = cap_data["cap"]["info"][0]
    assert info["severity"] == "Severe"
    assert info["urgency"] == "Immediate"
    assert "Istanbul" in info["headline"]
    assert info["area"][0]["circle"].startswith("41.0100,28.9800")