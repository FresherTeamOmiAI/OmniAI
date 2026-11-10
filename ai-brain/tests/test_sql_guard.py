import pytest

from app.rules.sql_guard import CATALOG_SQL, SqlRejected, validate_sql


def test_fixed_catalog_query_is_allowed():
    sql = validate_sql(CATALOG_SQL)
    assert "chatbot_catalog" in sql
    assert "LIMIT 100" in sql


def test_adds_limit_when_missing():
    sql = validate_sql("SELECT brand, name FROM chatbot_catalog")
    assert "LIMIT 100" in sql


def test_rejects_customer_table_and_writes():
    with pytest.raises(SqlRejected):
        validate_sql("SELECT email, phone FROM customer")
    with pytest.raises(SqlRejected):
        validate_sql("SELECT brand FROM chatbot_catalog; DROP TABLE product")
    with pytest.raises(SqlRejected):
        validate_sql("UPDATE chatbot_catalog SET name = 'x'")
    with pytest.raises(SqlRejected):
        validate_sql(
            "SELECT brand FROM chatbot_catalog WHERE name IN (SELECT email FROM customer)"
        )
    with pytest.raises(SqlRejected):
        validate_sql("SELECT brand, stock FROM chatbot_catalog")
