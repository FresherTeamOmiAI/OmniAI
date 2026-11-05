# OmniAI – Trợ lý tư vấn mỹ phẩm đa kênh

**Một bộ não, nhiều luật kênh.** Một AI Brain dùng chung tư vấn cho người mua mỹ phẩm trên web của shop, Telegram, Shopee và TikTok Shop. Mỗi kênh có bộ luật riêng: ví dụ trên Shopee bot không được gửi link, không đưa số điện thoại, không nhắc kênh khác. Khi khách đồng ý liên kết tài khoản, bot nhớ khách xuyên kênh. Chất lượng của bot được đo bằng **bench**.

- Đề xuất & phản biện: [docs/de-xuat-OmniAI.md](docs/de-xuat-OmniAI.md)
- Kế hoạch, sprint backlog, bug log: [docs/tracking/OmniAI_Project_Tracking.xlsx](docs/tracking/OmniAI_Project_Tracking.xlsx)

> Cửa hàng trong dự án là **bản demo**: thương hiệu hư cấu, không bán, không thanh toán, không giao hàng thật.

## Bài toán

**Vấn đề.** Shop mỹ phẩm bán cùng lúc trên Shopee, TikTok Shop và web riêng, nhưng mỗi kênh chỉ nhớ phần của mình. Vì vậy khách phải khai lại loại da và dị ứng ở từng kênh, không ai cảnh báo khi đồ mua ở các kênh khác nhau xung đột hoạt chất, và tư vấn giữa các kênh không nhất quán.

**Khoảng trống.**
- Ghép một bộ não chung thì AI không biết luật riêng của từng kênh. Ví dụ Shopee cấm nhắc tới web, gửi link hay số điện thoại; bot vi phạm thì shop có thể bị khóa.
- Không có cách hợp pháp để tự nhận ra 3 tài khoản ở 3 kênh là cùng một người. Sàn che thông tin người mua, còn Luật Bảo vệ dữ liệu cá nhân 91/2025 yêu cầu phải có sự đồng ý của khách.
- Hầu như không ai đo chất lượng chatbot theo từng kênh: có bịa giá không, có vi phạm luật sàn không, có nhớ đúng khách không.

**Giải pháp: "Một bộ não, nhiều luật kênh".**
- **Adapter** cho mỗi kênh chuyển tin nhắn về một định dạng chung. **Policy engine** áp luật của kênh, chặn hoặc viết lại câu trả lời trước khi gửi cho khách.
- **AI Brain dùng chung:** RAG trên catalog, luật thành phần cố định (ví dụ retinol với AHA, khi mang thai), tư vấn săn sale theo kênh.
- **Liên kết danh tính có sự đồng ý của khách:** mã OMNI-xxxx, deep-link, mã đơn hàng. Hệ thống chỉ gợi ý gộp tài khoản, khách xác nhận mới gộp.
- **Bench** đo theo từng kênh: tỉ lệ vi phạm luật, tỉ lệ bịa số, độ nhớ khi đổi kênh, độ trễ. Đây là đóng góp chính của dự án.

## Team

| Thành viên | Vai trò | Phụ trách code |
|---|---|---|
| **HungDQ** | PO + Lead | `backend/.../policy`, `bench/`, CI, `tools/tracking` |
| **TuTM** | Dev | `backend/.../channel` (Gateway, adapter Web/Telegram/Shopee/TikTok, simulator), admin inbox |
| **LucVT** | Dev | `backend/.../identity`, `customer`, `catalog`, `promotion`, `order`, web store |
| **ChienPX** | Dev | `ai-brain/` (agent, RAG, luật mỹ phẩm, bộ nhớ) |

Mỗi người tự làm giao diện (`web/`) cho phần của mình.

## Kiến trúc

```
[Web widget]  [Telegram]  [Shopee]  [TikTok Shop]  [Simulator]
      \            |          |           |            /
       ---------- Channel adapter -> CanonicalMessage ----------      backend (Spring Boot 4, Java 21)
                              |
                       MessageRouter (idempotency)
                              |
                 AI Brain  POST /chat                                  ai-brain (FastAPI, Python 3.11)
                              |
             PolicyEngine: luật của kênh (shared/policies/*.yaml)
                              |
                  BotReply { rawText, finalText, violations }
                              |
                     trả về đúng kênh  ·  bench chấm lại finalText bằng cùng bộ YAML
```

Hạ tầng (`docker-compose.yml`): PostgreSQL 16 + pgvector, Redis, RabbitMQ.

## Cấu trúc thư mục

```
backend/      Spring Boot modular monolith: channel, policy, brain, catalog, customer, identity, promotion, order
ai-brain/     FastAPI: /chat, LLMProvider (fake mặc định), agent, rules/ingredients.py
bench/        Bench chạy qua API simulator, chấm lại theo shared/policies
web/          React + Vite + TS, modular theo feature (app/, shared/, features/): store, chat, thử luật kênh
shared/       policies/*.yaml (luật kênh, dùng chung Java + Python), schemas/ (CanonicalMessage), catalog/ (80 SKU dạng CSV)
docs/         đề xuất, file tracking Excel, specs/ (bản đồ hệ thống cho người và agent)
tools/        tracking/ (sinh Excel, đồng bộ GitHub), systembat/ (script .bat chạy dự án), agent/ (hook kiểm tra specs), catalog/ (kiểm tra file catalog)
```

## Cài đặt

Cần: **JDK 21**, **Maven 3.9+**, **Python 3.11+**, **Node.js 20.19+**, **Docker Desktop** (bắt buộc: Postgres khi chạy app, Testcontainers khi chạy test).

```bash
cp .env.example .env          # sửa nếu cần; giá trị mặc định đã chạy được
docker compose up -d          # postgres, redis, rabbitmq
```

Backend không tự đọc file `.env`. Giá trị mặc định trong `application.yml` đã khớp `.env.example`. Muốn đổi thì đặt biến môi trường hoặc cấu hình trong IDE.

### Chạy nhanh trên Windows (không cần gõ lệnh)

Double-click các file trong `tools/systembat/` (chi tiết ở [tools/systembat/README.md](tools/systembat/README.md)):
- Lần đầu: `setup.bat`, để cài venv, thư viện Python, npm và tạo `.env`.
- Chạy tất cả: `run-all.bat`. Script bật Docker, mở 3 cửa sổ Brain/Backend/Web rồi mở trình duyệt. Tắt bằng `stop-all.bat`.
- Test: `test-all.bat`. Bench: `run-bench.bat`.

Các script này nạp `.env` thành biến môi trường, nên đổi giá trị trong `.env` là có hiệu lực.

### Chạy từng phần (mỗi phần 1 terminal)

**1. AI Brain** (cổng 8000)
```bash
cd ai-brain
python -m venv .venv
.venv\Scripts\activate            # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

**2. Backend** (cổng 8080)
```bash
cd backend
mvn spring-boot:run              # cần docker compose up -d trước
```

**3. Web** (cổng 5173)
```bash
cd web
npm install
npm run dev
```
Mở http://localhost:5173 để vào cửa hàng có chat widget. Mở http://localhost:5173/admin/policy để vào trang "Thử luật kênh": gửi 1 câu vào 4 kênh và so sánh câu AI nói với câu khách nhận.

**4. Bench**
```bash
cd bench
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m bench.run              # cần backend + ai-brain đang chạy; báo cáo ở bench/reports/
```

### Chạy test

```bash
cd backend  && mvn test                      # PolicyEngine, MessageRouter, khởi động Spring trên Postgres thật (Testcontainers, cần Docker)
cd ai-brain && .venv\Scripts\python -m pytest
cd bench    && .venv\Scripts\python -m pytest
cd web      && npm run build
```

### Thử nhanh bằng API

```bash
curl -X POST http://localhost:8080/api/simulator/shopee/messages \
  -H "Content-Type: application/json" -d '{"userId":"u1","text":"cho minh xin link web"}'
```
Trên Windows, curl có thể gửi tiếng Việt sai mã hóa (lỗi 400 "Invalid UTF-8"). Khi đó dùng trang `/admin/policy`, bench, hoặc `-d @file.json`.

## Làm việc với code

Trước khi sửa code, đọc [docs/specs/](docs/specs/README.md): hợp đồng API, mô hình dữ liệu, trạng thái từng module. Nếu đổi hợp đồng, DB, luật, cách chạy hoặc trạng thái tính năng thì cập nhật spec trong cùng PR.

**Thêm / sửa luật kênh:** sửa `shared/policies/<kênh>.yaml` (`action`: `block` | `redact` | `replace`). Sau đó thêm test vào `backend/src/test/.../PolicyEngineTest.java` và `bench/tests/test_policy_check.py`. Khi thêm luật phải có cả test khớp đúng và test không khớp nhầm (vd. giá tiền không bị tưởng là SĐT, "giá trị" không bị tưởng là "trị").

**Thêm kênh mới:**
1. Thêm giá trị vào enum `ChannelType`.
2. Tạo file `shared/policies/<kênh>.yaml`.
3. Viết class implement `ChannelAdapter<P>`, chuyển payload của kênh về `CanonicalMessage` rồi gọi `MessageRouter.handle(...)`.

**Thêm tool cho AI:** đặt trong `ai-brain/app/agent/`. Mọi con số (giá, tồn kho, khuyến mãi) phải lấy qua tool gọi API backend, không để LLM tự nói.

**Thêm ca bench:** thêm 1 dòng JSON vào `bench/datasets/*.jsonl`. Định dạng xem đầu file `bench/bench/run.py`.

## Quy ước Git

- Nhánh: `main` (bản ổn định, demo, gắn tag), `dev` (tích hợp), nhánh tạm `<type>/<mã>-<mô-tả>` tách từ `dev`, ví dụ `feature/us-17-telegram`. Chi tiết: [docs/TEAM_GIT_WORKFLOW.md](docs/TEAM_GIT_WORKFLOW.md).
- Commit theo Conventional Commits, kèm mã task trong file tracking: `feat(channel): telegram webhook adapter [T-17.1]`, `fix(policy): ...`, `test(bench): ...`, `docs: ...`.
- Merge vào `dev` qua Pull Request (không push thẳng lên `main`/`dev`), cần **1 người review**; test và bench phải pass.
- Không commit `.env`, API key, `node_modules`, `.venv`, `target`.
- File Excel tracking: đưa lên Google Drive/OneDrive để cả team sửa cùng lúc. Bản trong repo là baseline, chỉ HungDQ commit file này.

## Quản lý dự án

File [docs/tracking/OmniAI_Project_Tracking.xlsx](docs/tracking/OmniAI_Project_Tracking.xlsx). Sheet `Hướng dẫn` trong file mô tả chi tiết; tóm tắt:

| Ai | Khi nào | Làm gì |
|---|---|---|
| Mọi người | Hằng ngày | Cập nhật Status / Actual / Kết quả test / Bằng chứng của task mình trong sheet `Sprint n`. Ghi `Report progress` |
| Mọi người | Khi gặp bug | Thêm dòng vào `Bug Log` (Severity, Loại, Bước tái hiện...) |
| HungDQ | Cuối sprint | Nghiệm thu từng User Story theo Acceptance Criteria (Accepted/Rejected), điền Sprint Review |
| HungDQ | Bất kỳ lúc nào | Xem `Dashboard`: velocity, workload từng người, bug đang mở |

Sinh lại file từ đầu (sẽ **ghi đè**, mất dữ liệu đã nhập; dùng `--out` để ghi ra file khác):

```bash
python -m venv tools/.venv && tools/.venv/Scripts/pip install "openpyxl>=3.1,<4"
tools/.venv/Scripts/python tools/tracking/generate_tracking.py --out docs/tracking/ban-moi.xlsx
```
Backlog (User Story → Task, phân công, ước lượng) nằm trong `tools/tracking/backlog.py`.
