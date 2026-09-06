from alert2iq_server.domain.bio_anomaly import BioAnomalyClusterEngine, PetReport

def test_bio_anomaly_cluster_threshold():
    engine = BioAnomalyClusterEngine(min_reports_threshold=3, radius_km=15.0)
    
    # Add reports within 5km of Adana
    r1 = PetReport(report_id="r1", user_id="u1", latitude=37.00, longitude=35.32, animal_type="DOG")
    r2 = PetReport(report_id="r2", user_id="u2", latitude=37.02, longitude=35.30, animal_type="CAT")
    r3 = PetReport(report_id="r3", user_id="u3", latitude=37.01, longitude=35.33, animal_type="BIRD")
    
    engine.add_report(r1)
    engine.add_report(r2)
    eval1 = engine.evaluate_clusters(37.00, 35.32)
    assert not eval1["is_elevated_attention"]
    assert eval1["unique_user_count"] == 2

    engine.add_report(r3)
    eval2 = engine.evaluate_clusters(37.00, 35.32)
    assert eval2["is_elevated_attention"]
    assert eval2["status"] == "ATTENTION_ELEVATED"

def test_bio_anomaly_deduplicates_same_user():
    engine = BioAnomalyClusterEngine(min_reports_threshold=3)
    # 3 reports from SAME user should count as 1 unique user
    r1 = PetReport(report_id="r1", user_id="u1", latitude=37.00, longitude=35.32)
    r2 = PetReport(report_id="r2", user_id="u1", latitude=37.00, longitude=35.32)
    r3 = PetReport(report_id="r3", user_id="u1", latitude=37.00, longitude=35.32)
    
    engine.add_report(r1)
    engine.add_report(r2)
    engine.add_report(r3)
    
    eval_res = engine.evaluate_clusters(37.00, 35.32)
    assert eval_res["unique_user_count"] == 1
    assert not eval_res["is_elevated_attention"]