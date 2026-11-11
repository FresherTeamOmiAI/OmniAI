# tools/systembat – chạy OmniAI bằng double-click (Windows)

Các script tự tìm thư mục gốc repo, nên chạy được từ bất kỳ đâu: double-click trong Explorer hoặc gõ trong cmd/PowerShell.

## Lần đầu

1. Cài **JDK 21**, **Maven 3.9+**, **Python 3.11+**, **Node.js 20.19+**, **Docker Desktop** (nhớ bật Docker).
2. `setup.bat`: kiểm tra công cụ, tạo `.env` từ `.env.example`, tạo venv + cài thư viện cho `ai-brain` và `bench`, chạy `npm install` cho `web`.
3. `run-all.bat`: bật Docker, mở 3 cửa sổ Brain / Backend / Web, đợi backend sẵn sàng rồi mở http://localhost:5173.

## Các script

| File | Việc |
|---|---|
| `setup.bat` | Cài đặt lần đầu. Chạy lại nhiều lần vẫn an toàn (không ghi đè `.env`). |
| `run-all.bat` | Chạy toàn bộ hệ thống. |
| `stop-all.bat` | Tắt Brain/Backend/Web (theo cổng 8000/8080/5173), sau đó hỏi có tắt Docker không. |
| `infra-up.bat` | Chỉ bật Postgres + pgvector, Redis, RabbitMQ và đợi tới khi healthy. |
| `infra-down.bat` | Tắt Docker. `infra-down.bat --reset` xóa luôn dữ liệu Postgres (volume `pgdata`). |
| `run-brain.bat` | Chỉ chạy AI Brain ở :8000 (tự reload khi sửa code). |
| `run-backend.bat` | Chỉ chạy backend ở :8080 (cần `infra-up.bat` trước). |
| `run-web.bat` | Chỉ chạy web ở :5173 (tự `npm install` nếu chưa có `node_modules`). |
| `run-bench.bat` | Chạy bench (cần backend + brain đang chạy). Tham số truyền thẳng cho `bench.run`, ví dụ `run-bench.bat --dataset datasets\x.jsonl`. |
| `test-all.bat` | Chạy test backend, ai-brain, bench và build web, cuối cùng in bảng PASS/FAIL. |
| `_env.bat`, `_pause.bat` | Helper dùng chung, không chạy trực tiếp. |

Ví dụ: chỉ làm backend thì chạy `infra-up.bat` + `run-brain.bat` + web, còn backend chạy trong IDE.

## Ghi chú

- Sau `setup.bat` phải mở `.env` và điền `LLM_API_KEY`. AI Brain chỉ chạy LLM thật (gemini hoặc groq) và đọc catalog từ Postgres thật, nên `run-brain.bat` dừng với thông báo lỗi nếu thiếu `LLM_API_KEY` hoặc `CHATBOT_DB_URL`.
- `_env.bat` nạp file `.env` thành biến môi trường. Vì vậy đổi `LLM_PROVIDER`, `LLM_API_KEY`, `DB_URL`... trong `.env` rồi chạy lại script là có hiệu lực, dù backend và ai-brain không tự đọc `.env`.
- Thông báo trong script viết **không dấu** để cmd không lỗi font. Log của ứng dụng vẫn hiển thị đúng tiếng Việt (script đã bật UTF-8).
- File `.bat` phải giữ **CRLF**. `.gitattributes` đã có `*.bat text eol=crlf`. Nếu sửa bằng editor, đừng đổi sang LF.
- Khi thêm hoặc đổi script, cập nhật bảng trên và mục "Chạy" trong [docs/specs/overview.md](../../docs/specs/overview.md).
