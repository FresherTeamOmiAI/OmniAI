# bench (Owner: HungDQ)

Gửi từng ca trong `datasets/*.jsonl` vào backend, qua API simulator hoặc qua WebSocket `/ws/chat` giống chat widget (trường `transport` của từng ca). Sau đó chấm lại câu khách nhận (`finalText`) bằng **cùng bộ luật** `shared/policies/*.yaml`.

```bash
python -m venv .venv && .venv\Scripts\activate && pip install -r requirements.txt
python -m bench.run                       # cần backend (8080) + ai-brain (8000) đang chạy
python -m bench.run --dataset datasets/sample.jsonl --base-url http://localhost:8080
python -m bench.run --dataset datasets/web-flow.jsonl   # 6 ca luồng web qua WebSocket (US-06)
python -m pytest                          # test bộ chấm policy, không cần server
```

Báo cáo được ghi vào `reports/bench-<thời gian>.md` và `.json`. Lệnh trả mã thoát 1 nếu có ca fail, để dùng được trong CI.

Các cột trong báo cáo:
- **AI vi phạm, policy đã chặn**: câu gốc của AI có vi phạm, nhưng policy engine đã xử lý.
- **Còn lọt tới khách**: câu khách nhận vẫn vi phạm. Mục tiêu là 0.
