# Đo hiệu năng US-43: trước / sau

> Cập nhật: 2026-11-18 · T-43.2 số **trước** khi tối ưu · Người đo: LucVT · Đặc tả: [dac-ta-hieu-nang-catalog.md](dac-ta-hieu-nang-catalog.md)
> Công cụ: `bench/perf/load_test.py`, `bench/perf/brain_db.py`, `bench/perf/seed_conversations.sql` (cách chạy ở [../specs/bench.md](../specs/bench.md), mục "Đo hiệu năng")

## Điều kiện đo
- Code: catalog, hội thoại, widget, ai-brain như `dev` 7281c44 (đo trên nhánh `feature/t-13-1-otp-jwt` 580c56a, chỉ khác ở identity/order).
- Backend chạy local (`mvn spring-boot:run`, JVM đã làm nóng), Postgres 16 trong Docker local. 364 SKU đang bán (370 tổng), 19 trang store.
- Dữ liệu hội thoại: seed 200 hội thoại × 50 tin = **10.000 tin nhắn** (`seed_conversations.sql`), cộng 3 hội thoại có sẵn.
- Load test: 200 request mỗi kịch bản, 10 luồng song song; widget đo 20 vòng tuần tự.
- Số câu SQL: bật tạm `log_statement = 'all'` trên Postgres local, đếm câu SELECT giữa 2 câu đánh dấu (không gồm BEGIN/COMMIT), request thứ 2 sau khi làm nóng.
- ai-brain đo cả trên Postgres local và Supabase (mục 3). API backend chỉ đo local: backend profile `supabase` cần DB `shopee` (T-21.5).

## 1. Độ trễ API (local, 10 luồng)
| Kịch bản | Request | Lỗi | p50 (ms) | p95 (ms) | max (ms) | KB/response | Mục tiêu p95 |
|---|---|---|---|---|---|---|---|
| `store_list` (`GET /api/products?page=0..18&size=20`) | 200 | 0 | 138,9 | **214,6** | 334,3 | 28,6 | < 300 |
| `product_detail` (`GET /api/products/{id}`) | 200 | 0 | 46,6 | 67,9 | 89,6 | 1,5 | < 300 |
| `conversations` (`GET /api/conversations?size=20`, Basic admin) | 200 | 0 | 378,4 | **530,3** | 656,4 | 4,2 | < 300 ✗ |
| `widget_catalog` (1 vòng = 4 request `size=100`) | 20 vòng | 0 | 287,1 | 327,5 | 418,1 | **542,9 / vòng** | widget không gọi `/api/products` |

`conversations` chậm **không phải do DB** (chỉ 2 câu SQL): request Basic sai mật khẩu (401, không đụng DB) cũng mất ~90 ms tuần tự, request đúng ~96 ms, trong khi `product_detail` tuần tự ~4 ms. Thời gian nằm ở kiểm mật khẩu bcrypt cho **mỗi** request HTTP Basic (`SecurityConfig.passwordEncoder` = `createDelegatingPasswordEncoder`); 10 luồng tranh CPU nên p95 lên 530 ms.

## 2. Số câu SQL mỗi request (local)
| Request | Câu SQL | KB | Mục tiêu |
|---|---|---|---|
| Store trang 0, 20 SP | **27** | 26,9 | ≤ 8 |
| Store trang 5, 20 SP | 16 | 27,8 | ≤ 8 |
| Store lọc `skinType=oily` | 28 | 28,9 | ≤ 8 |
| Chi tiết 1 SP | 7 | 1,3 | |
| Widget, 1 trang `size=100` (×4 mỗi vòng) | 48 | 146,3 | không gọi |
| `/api/conversations?size=20` | 2 | 4,2 | |

Phân tích store trang 0 (26 SELECT ở lần tách chi tiết): **16 câu `select … from brand`** (N+1 theo hãng, mục 1 #6 của đặc tả), 4 câu bảng tra cứu `category`/`skin_type`/`skin_concern`/`sales_channel` (#5), 1 `count`, 1 trang `product`, 5 câu nạp collection (giá kênh, loại da, vấn đề da, ảnh, INCI). Đặc tả ước tính ~17 câu, đo thật 16–28 tuỳ số hãng trên trang.

## 3. ai-brain: phần đọc DB mỗi câu chat (không gồm LLM)
`brain_db.py` gọi đúng 2 hàm `/chat` dùng (`load_public_catalog`, `load_search_rows`), 10 lượt, Postgres local:

| Chỉ số | Trước |
|---|---|
| Kết nối DB mới mỗi câu chat | **5** (1 catalog + 4 trang search, không pool) |
| Dòng đọc về mỗi câu chat | 464 (100 catalog + 364 search kèm vector) |
| Dòng catalog đưa vào prompt | **100 / 364** SKU |
| Thời gian DB p50 / p95 | 219,7 / 247,3 ms (catalog 33,8 ms, search 173,2 ms) |

Cùng phép đo trên **Supabase** (pooler Sydney, cấu hình `.env` thật, đo lại sau khi sửa mật khẩu `chatbot_reader`):

| Chỉ số | Local | Supabase |
|---|---|---|
| Kết nối DB mới mỗi câu chat | 5 | 5 |
| Thời gian catalog p50 | 33,8 ms | 2.199 ms |
| Thời gian search p50 | 173,2 ms | 8.676 ms |
| **Tổng phần DB p50 / p95** | 219,7 / 247,3 ms | **10.876 / 11.273 ms** |

Trên Supabase, riêng phần đọc DB đã ~11 s mỗi câu chat, trước khi gọi LLM. Đây là nguyên nhân chính bot chậm khi chạy với Supabase, không phải Gemini. T-43.4 (pool + cache) là việc có tác dụng lớn nhất.

## 4. Mở trang store (đếm request)
- Code `dev` hiện tại (sau US-56): widget **chỉ tải catalog khi mở chat lần đầu** (`ChatWidget.tsx`, `catalogWanted`), không còn tải ngay khi mở trang như lúc review S2. Mở trang store khi chat đang thu nhỏ: 1 request `/api/products`. Mở chat lần đầu: thêm **4 request, ~543 KB, ~290 ms** (local), 192 câu SQL. Đặc tả và comment issue #264 viết trước khi đo nên còn ghi "mỗi lần mở trang", đã sửa.
- Mục tiêu T-43.3: mở chat cũng không gọi `/api/products`.

## Vấn đề gặp khi đo
- **Supabase chặn role `chatbot_reader`** (đã xử lý 18/11): pooler trả `FATAL: (ECIRCUITBREAKER) too many authentication failures`. Kiểm bằng tài khoản `postgres`: role vẫn LOGIN và đang có 1 kết nối `chatbot_reader` từ máy khác, tức mật khẩu trên Supabase đã bị đổi khác `.env` của LucVT. Nguyên nhân: `ChatbotReaderSetup` của backend chạy `ALTER ROLE chatbot_reader … PASSWORD` mỗi lần khởi động, nên máy nào dùng chung Supabase với `CHATBOT_DB_PASSWORD` khác là ghi đè; ai-brain của người còn lại thử sai 5 lần mỗi câu chat tới khi bị chặn. Đã đặt lại mật khẩu theo `.env` của LucVT (chủ project), đăng nhập lại được ngay. Sửa tận gốc: backend không ghi đè mật khẩu trên Supabase (nhánh `bugfix/chatbot-reader-supabase`), ai-brain ngừng thử sau lỗi đăng nhập (T-43.4).
- Hibernate 7 không in "Session Metrics" với `generate_statistics` qua `SPRING_APPLICATION_JSON` như mong đợi, nên đếm SQL ở phía Postgres. Test tự động ở T-43.1 vẫn có thể dùng `Statistics.getPrepareStatementCount()` trực tiếp.

## Gợi ý cho T-43.1 từ số đo
- N+1 hãng chiếm hơn nửa số câu của trang store: `default_batch_fetch_size` là việc đầu tiên.
- `/api/conversations` cần xử lý chi phí bcrypt của HTTP Basic (ví dụ đổi encoder của tài khoản admin trong bộ nhớ sang loại rẻ hơn, hoặc cache kết quả xác thực) trước khi bàn index; DB chỉ chiếm vài ms.

## Sau khi tối ưu
_Chưa đo. Chạy lại đúng các lệnh trên sau T-43.1, T-43.4, T-43.3 và điền bảng so sánh ở đây._
