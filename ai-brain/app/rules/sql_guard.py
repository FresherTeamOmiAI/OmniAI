"""Chặn SQL của chatbot trước khi tới database. Parser là SQLGlot, không phải regex."""

import sqlglot
from sqlglot import exp
from sqlglot.errors import SqlglotError

ALLOWED_TABLE = "chatbot_catalog"
ALLOWED_COLUMNS = frozenset(
    {
        "brand",
        "name",
        "category",
        "skin_types",
        "skin_concerns",
        "usage_time",
        "price_web",
        "price_shopee",
        "price_tiktok",
    }
)
MAX_ROWS = 100

CATALOG_SQL = (
    "SELECT brand, name, category, skin_types, skin_concerns, usage_time, "
    "price_web, price_shopee, price_tiktok "
    "FROM chatbot_catalog ORDER BY brand, name LIMIT 100"
)


class SqlRejected(ValueError):
    """Câu SQL không được thực thi."""


def validate_sql(sql: str) -> str:
    """Trả về một câu SELECT đã chuẩn hóa, hoặc ném SqlRejected."""
    try:
        statements = [item for item in sqlglot.parse(sql, read="postgres") if item is not None]
    except SqlglotError as error:
        raise SqlRejected("không phân tích được SQL") from error
    if len(statements) != 1 or not isinstance(statements[0], exp.Select):
        raise SqlRejected("chỉ một câu SELECT")
    statement = statements[0]
    if statement.args.get("joins") or statement.find(exp.Union):
        raise SqlRejected("không JOIN, không UNION")
    tables = list(statement.find_all(exp.Table))
    if len(tables) != 1:
        raise SqlRejected("đúng một bảng")
    table = tables[0]
    if table.name.lower() != ALLOWED_TABLE or (table.db and table.db.lower() not in {"", "public"}):
        raise SqlRejected("chỉ được đọc chatbot_catalog")
    for column in statement.find_all(exp.Column):
        if column.name.lower() not in ALLOWED_COLUMNS:
            raise SqlRejected("cột không thuộc catalog công khai")
    limit = statement.args.get("limit")
    if limit is None:
        statement = statement.limit(MAX_ROWS)
    else:
        amount = limit.expression
        if not isinstance(amount, exp.Literal) or not amount.is_int or int(amount.this) > MAX_ROWS:
            raise SqlRejected("LIMIT tối đa 100")
    return statement.sql(dialect="postgres")
