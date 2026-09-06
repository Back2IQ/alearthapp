from alert2iq_server.serve.enterprise_webhooks import EnterpriseWebhookDispatcher

def test_enterprise_webhook_registration_and_signing():
    dispatcher = EnterpriseWebhookDispatcher()
    dispatcher.register_endpoint("factory_adana_01", "https://api.factory-adana.com/quake-webhook", "secret_adana_key_123")
    
    alert = {
        "id": "eq_adana_01",
        "mag": 6.2,
        "lat": 37.00,
        "lon": 35.32,
        "tier": "P2"
    }
    
    dispatches = dispatcher.prepare_dispatch_payloads(alert)
    assert len(dispatches) == 1
    
    d = dispatches[0]
    assert d["client_id"] == "factory_adana_01"
    assert d["signature"].startswith("sha256=")
    assert "CLOSE_GAS_VALVES" in d["body"]