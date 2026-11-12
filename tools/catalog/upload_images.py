"""Upload ảnh sản phẩm lên Supabase Storage (T-47.1, decisions #26). Owner: LucVT.

    python tools/catalog/upload_images.py --check        # upload 1 ảnh rồi mở lại URL public để thử cấu hình
    python tools/catalog/upload_images.py                # upload mọi ảnh trong shared/catalog/real/images/

Cần SUPABASE_URL, SUPABASE_BUCKET (mặc định product-images, bucket phải để Public) và SUPABASE_SERVICE_KEY
(khoá service_role) trong biến môi trường hoặc trong file .env ở gốc repo. Khoá chỉ dùng ở script này, không commit.
Ảnh đã có thì ghi đè (x-upsert), nên chạy lại bao nhiêu lần cũng được. Khoá ảnh = tên file = <SKU>.webp, khớp cột
image_key trong DB. Chỉ dùng thư viện chuẩn. Cách tạo project và bucket: docs/catalog/du-lieu-san-pham-that.md.
"""

import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IMAGE_DIR = ROOT / "shared" / "catalog" / "real" / "images"


def load_env() -> dict[str, str]:
    values: dict[str, str] = {}
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip('"').strip("'")
    for key in ("SUPABASE_URL", "SUPABASE_BUCKET", "SUPABASE_SERVICE_KEY"):
        if os.environ.get(key):
            values[key] = os.environ[key]
    return values


def upload(base: str, bucket: str, key: str, path: Path) -> None:
    request = urllib.request.Request(
        f"{base}/storage/v1/object/{bucket}/{path.name}", data=path.read_bytes(), method="POST",
        headers={"Authorization": f"Bearer {key}", "apikey": key, "Content-Type": "image/webp",
                 "x-upsert": "true", "Cache-Control": "max-age=86400"})
    with urllib.request.urlopen(request, timeout=60) as response:
        response.read()


def public_ok(base: str, bucket: str, name: str) -> bool:
    request = urllib.request.Request(f"{base}/storage/v1/object/public/{bucket}/{name}", method="GET")
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status == 200 and response.headers.get("Content-Type", "").startswith("image/")


def main(argv: list[str]) -> int:
    env = load_env()
    base = env.get("SUPABASE_URL", "").rstrip("/")
    bucket = env.get("SUPABASE_BUCKET") or "product-images"
    key = env.get("SUPABASE_SERVICE_KEY", "")
    if not base or not key:
        print("LỖI thiếu SUPABASE_URL hoặc SUPABASE_SERVICE_KEY (đặt trong .env). Xem docs/catalog/du-lieu-san-pham-that.md")
        return 1
    files = sorted(IMAGE_DIR.glob("*.webp"))
    if not files:
        print(f"LỖI chưa có ảnh trong {IMAGE_DIR.relative_to(ROOT)}, chạy `obf_fetch.py build` trước")
        return 1
    if "--check" in argv:
        files = files[:1]

    failed = []
    for i, path in enumerate(files, start=1):
        try:
            upload(base, bucket, key, path)
        except urllib.error.HTTPError as error:
            failed.append(f"{path.name}: HTTP {error.code} {error.read().decode('utf-8', 'replace')[:200]}")
        except urllib.error.URLError as error:
            failed.append(f"{path.name}: {error.reason}")
        if i % 50 == 0 or i == len(files):
            print(f"[{i}/{len(files)}] đã gửi", flush=True)
        time.sleep(0.05)

    sample = [p.name for p in files[:: max(1, len(files) // 5)]][:5]
    not_public = [name for name in sample if name not in {f.split(":")[0] for f in failed}
                  and not _safe_public_ok(base, bucket, name)]
    print(f"Upload {len(files) - len(failed)}/{len(files)} ảnh vào bucket '{bucket}'. "
          f"Mở thử {len(sample)} URL public: {len(sample) - len(not_public)} OK")
    for msg in failed:
        print("LỖI", msg)
    for name in not_public:
        print(f"LỖI URL public không mở được: {base}/storage/v1/object/public/{bucket}/{name} (bucket đã bật Public chưa?)")
    return 1 if failed or not_public else 0


def _safe_public_ok(base: str, bucket: str, name: str) -> bool:
    try:
        return public_ok(base, bucket, name)
    except urllib.error.URLError:
        return False


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main(sys.argv[1:]))
