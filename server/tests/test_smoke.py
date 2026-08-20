import tda_server


def test_package_importable():
    assert tda_server.__version__ == "0.1.0"
