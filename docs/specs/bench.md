# Bench

> Cập nhật: 2026-11-11 · commit 38afec6 · Owner: HungDQ
> Nguồn: `bench/bench/run.py`, `bench/bench/policy_check.py`, `bench/datasets/*.jsonl`, `bench/tests/*`

Bench đo chất lượng bot theo từng kênh. Đây là **đóng góp chính** của dự án. Bench gửi từng ca vào backend qua API simulator, hoặc qua WebSocket `/ws/chat` giống chat widget (cần backend + ai-brain đang chạy, tức là luôn đo trên **LLM thật và Postgres thật**, decisions #25: `.env` phải có `LLM_API_KEY` và mỗi lần chạy tốn quota free tier), rồi tự chấm lại `finalText` bằng cùng bộ YAML luật. Phương pháp, cách áp vào bài toán và tài liệu tham khảo: [../bench/huong-dan-benchmark.md](../bench/huong-dan-benchmark.md).

## Chạy
`python -m bench.run [--dataset datasets/x.jsonl] [--base-url http://localhost:8080] [--out reports]`, hoặc `tools\systembat\run-bench.bat`. Báo cáo ghi ra `bench/reports/bench-<thời gian>.md` và `.json` (thư mục này bị gitignore). URL WebSocket suy ra từ `--base-url` (`http` → `ws`, `https` → `wss`, thêm `/ws/chat`). Thư viện: httpx, pyyaml, pytest, websockets (client sync). Khi `requirements.txt` đổi thì chạy lại `setup.bat`.

## Định dạng dataset (JSONL, mỗi dòng một ca)

```json
{"id": "POL-001", "category": "policy-trap", "channel": "SHOPEE", "text": "...", "transport": "simulator",
 "expect": {"no_violation": true, "blocked": true, "must_contain_any": ["..."], "must_not_match": "regex",
            "max_latency_ms": 5000}}
```
- `transport`: `simulator` (mặc định, `POST /api/simulator/{channel}/messages`) hoặc `ws` (WebSocket `/ws/chat`, chỉ dùng cho kênh WEB). Với `ws`, độ trễ đo từ lúc gửi tới lúc nhận `BotReply`, vì widget đã kết nối sẵn. Giá trị khác, hoặc `ws` cho kênh khác WEB, thì bench báo lỗi dataset.
- `no_violation` mặc định true: `finalText` không được còn khớp luật nào của kênh.
- `blocked`: kiểm tra cờ blocked. `must_contain_any`: finalText phải chứa ít nhất một chuỗi (không phân biệt hoa thường).
- `must_not_match`: finalText không được khớp regex này (Python `re`, không phân biệt hoa thường). Dùng cho ca "không bịa tồn kho/khuyến mãi" (T-06.6). Lỗi ghi đúng đoạn bị khớp.
- `max_latency_ms` mặc định 5000.
- Mỗi ca gửi `messageId = bench-<id>-<ns>` (luôn mới, không dính idempotency) và `userId = bench-user`.

## Kết quả mỗi ca
`id, category, channel, transport, passed, failures[], latency_ms, raw_violations` (id các luật đã kích hoạt trên rawText, lấy từ `BotReply.violations`), `final_violations` (luật mà finalText vẫn còn vi phạm).

Summary (`summarize()`): `total`, `passed`, `latency_p95_ms`, và `by_channel` gồm `cases, passed, raw_caught` (số ca AI nói sai nhưng policy đã chặn) và `leaked` (số ca lọt tới khách). Chưa gộp theo category (US-12 sẽ thêm).

## Dataset hiện có
- `datasets/sample.jsonl`: 11 ca qua simulator. Ca bẫy (POL-001..003, INJ-001) không ghi `expect`: bot tự từ chối hay policy chặn cả câu đều pass, miễn finalText không vi phạm. Ca hỏi bình thường (WEB-xxx, POL-004, GRD-001) có `blocked: false` để bắt chặn nhầm. COS-001, COS-002 dựa vào câu cảnh báo tất định. GRD-001 (SHOPEE) hỏi giá/tồn: finalText không được có số tồn kho, khuyến mãi; giá thì được phép vì `grounding.py` chỉ giữ giá khớp `chatbot_catalog`.
- `datasets/web-flow.jsonl`: 6 ca luồng web qua WebSocket (WEBF-001..006), là phần tự động của `docs/testcases/US-06.md`. WEBF-005 (luật thành phần) và WEBF-006 (không bịa tồn kho/khuyến mãi) dựa vào code tất định. Mỗi ca giữ `max_latency_ms: 5000` theo NFR-01, nên với Gemini (p95 6,7 s, decisions #21) sẽ có ca fail chỉ vì chậm.
- Bench chưa đối chiếu con số giá trong finalText với DB. Việc đó làm ở US-15 (20 ca giá/tồn kho, S3).

## Test
`tests/test_policy_check.py`: nạp đủ kênh, Shopee bắt được link/SĐT/kênh khác, WEB cho phép link, claim y khoa bị bắt ở mọi kênh. Ngoài ra chạy bộ ca dùng chung `shared/policies/tests/cases.yaml` (18 ca × 3 kênh, cùng bộ với `PolicyEngineTest`), để chắc regex cho kết quả giống nhau ở Python và Java.

`tests/test_run.py`: hàm chấm `grade()` (pass, lọt luật, sai `blocked`, thiếu chuỗi, khớp mẫu cấm, quá chậm) và cách suy URL WebSocket từ `--base-url`. Không cần server.

## Lộ trình
- S1: 5 ca luồng web qua WebSocket (T-06.5) và ca không bịa giá (T-06.6): đã có. Ngày 11/11 bỏ FakeProvider, sửa kỳ vọng để không phụ thuộc câu chữ của model (T-12.4).
- S2: 50 ca, ít nhất 5 nhóm, báo cáo theo kênh (US-12).
- S3: 20 ca giá/tồn kho, tỉ lệ bịa số = 0 (US-15). CI chạy bench (US-16).
- S5: bẫy policy + LLM-as-judge (US-24). S7: metric memory/conflict recall (US-32) và 10 ca nội dung đẩy ra sàn (US-54). S9: P/R/F1 danh tính (US-40). S10: trang báo cáo bench trên admin (US-41).
