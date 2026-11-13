"""Ghi embedding của sản phẩm đang bán vào cột product.embedding. Owner: ChienPX. T-10.1.

Chạy một lần (và khi catalog đổi), bằng role ghi được bảng product, không phải chatbot_reader:

    set CATALOG_WRITE_URL=postgresql://omniai:omniai@localhost:5432/omniai
    python -m app.rag.index_catalog
"""

import os

import psycopg

from app.rag.embed import embed, to_literal
from app.rag.search import ProductRow, product_text


def index_catalog(url: str) -> int:
    with psycopg.connect(url) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT sku, brand, name, category, skin_types, skin_concerns, usage_time,
                       price_web, price_shopee, price_tiktok, description
                FROM chatbot_catalog_search
                """
                # Giá theo kênh không còn là cột của product từ V13 (decisions #32); view đã lọc active
                # và ghép giá từ product_channel với đúng tên cột cũ.
            )
            rows = cursor.fetchall()
            for sku, brand, name, category, skin_types, concerns, usage_time, web, shopee, tiktok, description in rows:
                product = ProductRow(
                    sku=sku,
                    brand=brand,
                    name=name,
                    category=category,
                    skin_types=skin_types or "",
                    skin_concerns=concerns or "",
                    usage_time=usage_time or "",
                    price_web=int(web),
                    price_shopee=int(shopee),
                    price_tiktok=int(tiktok),
                    description=description or "",
                )
                cursor.execute(
                    "UPDATE product SET embedding = %s::vector WHERE sku = %s",
                    (to_literal(embed(product_text(product))), sku),
                )
        connection.commit()
    return len(rows)


def main() -> None:
    url = os.getenv("CATALOG_WRITE_URL", "").strip()
    if not url:
        raise SystemExit("Thiếu CATALOG_WRITE_URL (role ghi được bảng product, không dùng chatbot_reader)")
    count = index_catalog(url)
    print(f"Đã ghi embedding cho {count} sản phẩm đang bán")


if __name__ == "__main__":
    main()
