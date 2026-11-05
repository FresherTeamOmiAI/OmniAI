# Báo cáo chọn LLM (T-05.2)

Ngày chạy: 2026-11-05. Người chạy: ChienPX. Một lượt chấm theo rubric 0–2, lưu ở `scores.jsonl`. Chưa có người chấm thứ hai. Điểm dưới đây là điểm của lượt này, chưa phải trung bình hai người.

Cùng `system_prompt_for()`, temperature 0,2 và `max_tokens` 400. `gpt-oss` thêm `reasoning_effort=low` vì nếu không thì câu trả lời để trống. Gemini 3.8 Flash trả 503 nên không có file kết quả. Model Gemini trong báo cáo là `gemini-3.6-flash`.

p95 lấy trên latency tăng dần, vị trí `round(0,95 × n) - 1`. Vi phạm kênh đếm bằng `bench.policy_check.violations()` trên câu thô.

## Cổng

| Model | Gói | G1 | G2 | G3 | G4 | G5 | G6 |
|---|---|---|---|---|---|---|---|
| `qwen/qwen3.8-27b` | Groq free | Đạt | 19/20 câu ≥ 1, trung bình 1,70 | Không đạt | Đạt, p95 1.433 ms | Không đạt | Không đạt |
| `gemini-3.6-flash` | AI Studio free | Đạt | 20/20 câu ≥ 1, trung bình 1,55 | Đạt | Không đạt, p95 7.650 ms | Không đạt | Không đạt |
| `openai/gpt-oss-20b` | Groq free | Đạt | 7/20, trung bình 0,60 | Không đạt | Đạt, p95 1.624 ms | Không đạt | Đạt một lần, khi khai báo tool |
| `openai/gpt-oss-120b` | Groq free | Đạt | 8/20, trung bình 0,75 | Không đạt | Đạt, p95 1.567 ms | Không đạt | Không đạt |

G1: không gắn thẻ, không gói trả phí. Dữ liệu gửi đi là câu hỏi và catalog hư cấu.

G3 không đạt khi câu trả lời tự nêu số. Qwen câu q13 viết còn hàng, giá 450.000đ và giảm 10%. `gpt-oss-20b` bịa nhiều bảng giá, kể cả câu q19 trên Shopee (1.200đ, tồn 15, giảm 10%). `gpt-oss-120b` câu q07 bịa giá và tồn. Gemini không khuyên dùng retinol khi mang thai và không nêu số tiền hay số tồn.

G4 mốc là 5.000 ms. Gemini có q01, q03, q07 và q20 vượt mốc. Nhiều câu Gemini bị cắt vì token suy nghĩ nằm chung hạn 400 token.

G5: lần gọi dồn trên Groq bị HTTP 429. Thông báo lúc đó ghi gpt-oss TPM 8.000 và qwen OTPM 1.000. Lần cách 15 giây thì 20 câu không bị 429. Chưa có lượt 50 request liên tiếp không lỗi. Chưa ghi RPM/RPD từ trang quota.

G6: `results/tool-call.json` là một vòng `get_price_and_stock` thành công của `gpt-oss-20b`, SKU `LUMI-SUN-01`, giá 289.000đ và tồn 120 lấy từ catalog. Trong bộ 20 câu, tool bị tắt, nên model này vẫn trả HTTP 400 ở q14 và q15. Qwen và Gemini chưa có log gọi tool thật. Qwen câu q13 giả vờ gọi tool rồi tự điền số.

## Điểm thành phần

Chỉ model qua hết cổng mới được tính tổng để xếp hạng. Chưa model nào qua hết, nên bảng này không phải bảng xếp hạng.

| Tiêu chí | Qwen | Gemini 3.6 Flash | gpt-oss-20b | gpt-oss-120b |
|---|---|---|---|---|
| Chất lượng (trung bình / 2) | 0,85 | 0,78 | 0,30 | 0,38 |
| Kênh, 4 câu bẫy | 0,75 (q18) | 0,50 (q17, q18) | 1,00 | 0,75 (q18) |
| Độ trễ (p95 tốt nhất / p95 này) | 1,00 | 0,19 | 0,88 | 0,91 |
| Quota | Không chấm | Không chấm | Không chấm | Không chấm |
| Tích hợp | 0,50 | 0,50 | 0,50 | 0,50 |

Câu bẫy dính luật vì câu từ chối vẫn chứa chữ `Zalo` hoặc `website`. `factory.py` hiện chỉ nhận `LLM_PROVIDER=fake`, nên mọi model thật đều phải sửa nhỏ, điểm tích hợp 0,50.

Qwen câu q06 và q07, và `gpt-oss-20b` câu q07, dính `no-medical-claim` vì có chữ “điều trị” dù câu đang phủ định.

## Đề xuất

Chưa đủ điều kiện duyệt thẳng. Đề nghị duyệt có điều kiện.

Model chính:

- `LLM_PROVIDER=gemini`
- `LLM_MODEL=gemini-3.6-flash`

Đây là model duy nhất qua G3 trong lượt chấm này và đủ 20 câu trả lời. Điều kiện trước khi vào US-06: đo lại p95 sau khi tách token suy nghĩ khỏi hạn 400, và có một log gọi tool thật. Hạn: trước sprint 1.

Model dự phòng, nhà khác:

- `LLM_PROVIDER=groq`
- `LLM_MODEL=qwen/qwen3.8-27b`

Qwen qua G2 và G4, p95 thấp nhất. Không được đưa câu chữ của model ra cho khách khi câu đó chứa giá hoặc tồn. Số chỉ được lấy từ tool. Lý do: q13 đã bịa 450.000đ. Hạn xử lý: cùng US-06, trước khi bật provider này trên luồng chat.

`gpt-oss` không dùng làm model chính. Model từ chối nhiều câu tư vấn, bịa giá, và gọi tool cả khi tool đang tắt.

## Rủi ro free tier

Free tier của Groq và Google có thể dùng prompt để cải thiện sản phẩm. Chỉ gửi persona và catalog hư cấu. Quota có thể đổi. Không gọi dồn: Groq đã trả 429. Bench với model thật phải giãn request, chưa chứng minh nổi 50 request liên tiếp. `gemini-3.8-flash` trong ngày chạy này trả 503.
