# Bench

> Cập nhật: 2026-11-04 · commit 46e4a18 · Owner: HungDQ
> Nguồn: `bench/bench/run.py`, `bench/bench/policy_check.py`, `bench/datasets/*.jsonl`, `bench/tests/*`

Bench đo chất lượng bot theo từng kênh. Đây là **đóng góp chính** của dự án. Bench gọi API simulator của backend (cần backend + ai-brain đang chạy), rồi tự chấm lại `finalText` bằng cùng bộ YAML luật.

## Chạy
`python -m bench.run [--dataset datasets/x.jsonl] [--base-url http://localhost:8080] [--out reports]`, hoặc `tools\systembat\run-bench.bat`. Báo cáo ghi ra `bench/reports/bench-<thời gian>.md` và `.json` (thư mục này bị gitignore).

## Định dạng dataset (JSONL, mỗi dòng một ca)

```json
{"id": "POL-001", "category": "policy-trap", "channel": "SHOPEE", "text": "...",
 "expect": {"no_violation": true, "blocked": true, "must_contain_any": ["..."], "max_latency_ms": 5000}}
```
- `no_violation` mặc định true: `finalText` không được còn khớp luật nào của kênh.
- `blocked`: kiểm tra cờ blocked. `must_contain_any`: finalText phải chứa ít nhất một chuỗi (không phân biệt hoa thường).
- `max_latency_ms` mặc định 5000.
- Mỗi ca gửi `messageId = bench-<id>-<ns>` (luôn mới, không dính idempotency) và `userId = bench-user`.

## Kết quả mỗi ca
`id, category, channel, passed, failures[], latency_ms, raw_violations` (id các luật đã kích hoạt trên rawText, lấy từ `BotReply.violations`), `final_violations` (luật mà finalText vẫn còn vi phạm).

Summary (`summarize()`): `total`, `passed`, `latency_p95_ms`, và `by_channel` gồm `cases, passed, raw_caught` (số ca AI nói sai nhưng policy đã chặn) và `leaked` (số ca lọt tới khách). Chưa gộp theo category (US-12 sẽ thêm).

## Dataset hiện có
`datasets/sample.jsonl`: 10 ca (WEB-xxx, POL-xxx...), viết dựa trên câu trả lời của **FakeProvider**. Khi đổi sang provider thật, các ca `must_contain_any` có thể phải sửa.

## Test
`tests/test_policy_check.py`: nạp đủ kênh, Shopee bắt được link/SĐT/kênh khác, WEB cho phép link, claim y khoa bị bắt ở mọi kênh. Ngoài ra chạy bộ ca dùng chung `shared/policies/tests/cases.yaml` (18 ca × 5 kênh, cùng bộ với `PolicyEngineTest`), để chắc regex cho kết quả giống nhau ở Python và Java.

## Lộ trình
- S1: 5 ca luồng web (T-06.5).
- S2: 50 ca, ít nhất 5 nhóm, báo cáo theo kênh (US-12).
- S3: 20 ca giá/tồn kho, tỉ lệ bịa số = 0 (US-15). CI chạy bench (US-16).
- S5: bẫy policy + LLM-as-judge (US-24). S7: metric memory/conflict recall (US-32). S9: P/R/F1 danh tính (US-40). S10: trang báo cáo bench trên admin (US-41).
