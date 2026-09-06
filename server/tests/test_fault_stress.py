from alert2iq_server.domain.fault_stress import FaultStressMonitor, MicroQuake, KNOWN_FAULTS

def test_fault_catalog():
    assert len(KNOWN_FAULTS) >= 4
    marmara = next(f for f in KNOWN_FAULTS if f.segment_id == "naf_marmara")
    assert marmara.last_major_rupture_year == 1766
    assert marmara.locked_status is True

def test_micro_swarm_detection():
    monitor = FaultStressMonitor(min_swarm_count=4)
    
    for i in range(3):
        monitor.add_micro_quake(MicroQuake(f"q_{i}", magnitude=2.1, latitude=40.8, longitude=28.5))
        
    eval1 = monitor.evaluate_swarm_alert()
    assert not eval1["is_swarm_active"]
    
    # 4th micro-quake triggers swarm alert
    monitor.add_micro_quake(MicroQuake("q_4", magnitude=2.4, latitude=40.82, longitude=28.55))
    eval2 = monitor.evaluate_swarm_alert()
    assert eval2["is_swarm_active"]
    assert eval2["attention_level"] == "ATTENTION_SWARM"