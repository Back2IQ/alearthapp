from alert2iq_server.domain.guardian_circle import GuardianCircle, GuardianMember, SafetyStatus

def test_guardian_circle_creation():
    circle = GuardianCircle(circle_id="circle_123", owner_id="user_owner", circle_name="Kiran Familie")
    assert circle.circle_id == "circle_123"
    assert circle.circle_name == "Kiran Familie"
    assert len(circle.invite_code) == 8

def test_add_and_update_member_status():
    circle = GuardianCircle(circle_id="circle_123", owner_id="user_owner", circle_name="Kiran Familie")
    m1 = GuardianMember(member_id="m1", display_name="Mama", phone_summary="+49170***")
    m2 = GuardianMember(member_id="m2", display_name="Papa", phone_summary="+49171***")
    
    circle.add_member(m1)
    circle.add_member(m2)
    
    summary = circle.get_summary()
    assert summary["total_members"] == 2
    assert summary["safe_count"] == 0
    assert summary["unknown_count"] == 2
    
    # Update status to SAFE
    assert circle.update_member_status("m1", SafetyStatus.SAFE)
    summary2 = circle.get_summary()
    assert summary2["safe_count"] == 1
    assert summary2["unknown_count"] == 1

def test_member_needs_help():
    circle = GuardianCircle(circle_id="circle_123", owner_id="user_owner", circle_name="Kiran Familie")
    m1 = GuardianMember(member_id="m1", display_name="Mama")
    circle.add_member(m1)
    
    circle.update_member_status("m1", SafetyStatus.NEEDS_HELP, lat=37.0, lon=35.32)
    summary = circle.get_summary()
    assert summary["needs_help_count"] == 1
    assert summary["members"][0]["latitude"] == 37.0