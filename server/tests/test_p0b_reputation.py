from tda_server.p0b.reputation import ReputationStore, trigger_weight


def test_new_device_starts_low():
    s = ReputationStore(base=0.1)
    assert s.weight("newdev", attest_ok=True) == 0.1

def test_unattested_is_capped_at_base():
    s = ReputationStore(base=0.1)
    for _ in range(100):
        s.reward("dev")
    assert s.weight("dev", attest_ok=False) == 0.1     # no attest -> capped low
    assert s.weight("dev", attest_ok=True) == 1.0      # attested -> earned rep

def test_reward_and_cap():
    s = ReputationStore(base=0.1, cap=1.0, gain=0.3)
    for _ in range(10):
        s.reward("dev")
    assert s.weight("dev", attest_ok=True) == 1.0      # capped

def test_penalize_reduces_weight():
    s = ReputationStore(base=0.1, gain=0.3)
    for _ in range(5):
        s.reward("dev")
    before = s.weight("dev", attest_ok=True)
    s.penalize("dev", factor=0.5)
    assert s.weight("dev", attest_ok=True) < before

def test_reputation_survives_reinstall_key_is_attest_id():
    s = ReputationStore()
    for _ in range(5):
        s.reward("hw-attest-42")
    # a reinstall keeps the same hardware attest id -> same (non-reset) score
    assert s.weight("hw-attest-42", attest_ok=True) > 0.1
