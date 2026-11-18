# bench (Owner: HungDQ)

Gửi từng ca trong `datasets/*.jsonl` vào backend, qua API simulator hoặc qua WebSocket `/ws/chat` giống chat widget (trường `transport` của từng ca). Sau đó chấm lại câu khách nhận (`finalText`) bằng **cùng bộ luật** `shared/policies/*.yaml`.

Bench luôn đo trên LLM thật và Postgres thật, vì ai-brain không còn chế độ fake. `.env` phải có `LLM_API_KEY`, và mỗi ca tốn một lượt gọi LLM. Phương pháp viết ca: [docs/bench/huong-dan-benchmark.md](../docs/bench/huong-dan-benchmark.md).

```bash
python -m venv .venv && .venv\Scripts\activate && pip install -r requirements.txt
python -m bench.run                       # cần backend (8080) + ai-brain (8000) đang chạy
python -m bench.run --dataset datasets/sample.jsonl --base-url http://localhost:8080
python -m bench.run --dataset datasets/web-flow.jsonl   # 6 ca luồng web qua WebSocket (US-06)
python -m pytest                          # test bộ chấm policy, không cần server
```

Báo cáo được ghi vào `reports/bench-<thời gian>.md` và `.json`. Lệnh trả mã thoát 1 nếu có ca fail, để dùng được trong CI. Ca chậm hơn `max_latency_ms` (mặc định 5000) cũng tính là fail, nên khi đọc báo cáo cần tách ca fail vì chậm khỏi ca fail vì sai.

Các cột trong báo cáo:
- **AI vi phạm, policy đã chặn**: câu gốc của AI có vi phạm, nhưng policy engine đã xử lý.
- **Còn lọt tới khách**: câu khách nhận vẫn vi phạm. Mục tiêu là 0.
