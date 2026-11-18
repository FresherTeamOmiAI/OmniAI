# Review Sprint 2 (10–16/11/2026) và kế hoạch S3–S4

> Review ngày 2026-11-18 trên `dev` 27655ba (gồm PR #254). Kiểm bằng cách đọc code, test, issue và PR; **chưa chạy lại test**. Quyết định PO: decisions #36, #37.
> Đặc tả hiệu năng cho LucVT: [dac-ta-hieu-nang-catalog.md](dac-ta-hieu-nang-catalog.md).

## 1. Tóm tắt
- Cả 4 người đều có sản phẩm chạy được. LucVT và TuTM đạt gần hết tiêu chí, có test tốt. ChienPX có simulator Shopee và RAG nhưng nhiều tiêu chí chỉ đạt một phần. HungDQ còn nợ bench 50 ca và bảng luồng dữ liệu.
- Store chậm **không phải vì backend thiếu phân trang** (store lấy 20 SP/trang ở DB). Chậm vì chat widget tải cả 364 SP mỗi lần mở trang, ai-brain đọc lại DB mỗi câu chat bằng kết nối mới, và mỗi trang store chạy ~17 câu SQL. US-43 kéo về S3, LucVT làm.
- Thêm 11 task sửa lỗi và US-56 (giao diện). Mở lại US-10, US-21 vì chưa đạt AC.

## 2. Kết quả theo task
| Task | Người | Kết quả | Ghi chú |
|---|---|---|---|
| T-09.1, T-09.2, T-09.3 | LucVT | Đạt | `StoreFilterApiTest` 11, `DemoOrderApiTest` 10. TC trình duyệt mục C, D **chưa chạy** (US-09.md, dòng kết quả 13/11) → T-09.4 #255 |
| T-47.1, T-47.2 (+ T-47.3, T-04.1 V13/V15) | LucVT | Đạt | 366 SKU thật, ảnh Supabase, chuẩn hoá bảng. T-47.3 (profile supabase) chưa có test |
| T-11.1–T-11.3 | TuTM | Đạt | `ConversationStoreIT`, `ConversationApiTest` |
| T-06.8 | TuTM | Đạt | Thiếu TC tay cho timeout 12 s → T-11.4 #257 |
| T-25.1 | TuTM | Đạt phần lớn | Ký request, webhook, token chưa đối chiếu docv2 (đã ghi rõ) |
| T-25.2 | TuTM | 3/5, **PO chấp nhận** | 2 ca chỉ chậm do Gemini, 0 lọt vi phạm (decisions #36) |
| T-25.3 | TuTM | Một phần | Thiếu huỷ đơn, đánh giá, URL từng trang → T-25.5 #263 |
| T-10.1, T-10.2 | ChienPX | Một phần | "Embedding" là hash từ vựng CRC32, cosine tính bằng Python, index HNSW không dùng; "dưới 1 triệu" hiểu thành 1.000đ → T-10.4 #256 |
| T-10.3 | ChienPX | Đạt | `rag.jsonl` 10 ca |
| T-21.1 | ChienPX | Một phần | Nhận push + kiểm chữ ký đạt; **hub không gửi trả lời qua API send_message** → T-21.6 #260 |
| T-21.2, T-21.3 | ChienPX | Chưa có bằng chứng | Không có kết quả bench trước/sau, 5 ca Shopee không ghi kết quả → T-21.7 #261 |
| T-21.4 | ChienPX | Một phần | Sản phẩm, khuyến mãi, huỷ đơn, duyệt, đánh giá ghi "chưa có schema" |
| T-50.3 #212, T-51.1 #215 | ChienPX | Làm sớm, còn mở | Còn thiếu: số trên simulator khớp hub (cần T-50.2), simulator tự gửi webhook có thử lại, đơn nhiều dòng |
| T-12.1 #49 | HungDQ | Chưa | 37/50 ca; nhóm mỹ phẩm 2, injection 1, đa kênh 0 (cần ≥ 5 mỗi nhóm) |
| T-12.2 #50 | HungDQ | Một phần | Báo cáo chỉ theo kênh, chưa theo nhóm |
| T-12.3, T-12.4, T-48.1, T-48.2 | HungDQ | Đạt | |
| T-48.3 #203 | HungDQ | Chưa | Chưa có bảng loại dữ liệu × sàn × endpoint/webhook |

## 3. Lỗi tìm được, theo người
Mức độ: **Cao** = sai chức năng hoặc chặn người khác · **TB** = sai trong trường hợp thường gặp · **Thấp** = nợ kỹ thuật.

**LucVT** (phần hiệu năng gom trong US-43, xem đặc tả)
- TB: N+1 theo hãng (`Product.java:71-74`, `Brand.java`); 4 query bảng tra cứu mỗi lần gọi store (`ProductService.java:73-79,101`); không giới hạn `size`, không sort mặc định, trả nguyên entity, không gzip; đơn nạp sản phẩm từng dòng (`OrderService.java:51-54`) → T-43.1 #149.
- TB, tiềm ẩn: giá NULL (V13) làm ai-brain `int(None)` → T-43.4 #265.
- Thấp: tạo trùng hãng/INCI cùng lúc gây 500 (`ProductService.java:208-232`); sort trường lạ có thể 500.

**TuTM**
- TB: gửi tin lỗi **sau khi đã trả 200** thì chỉ log, DB vẫn ghi là đã trả lời, khách mất tin (`TiktokWebhookProcessor.java:55-59`) → T-25.4 #262.
- TB: `tiktok.md` hứa handoff với 45109001/không có quyền, code chỉ log (`TiktokWebhookProcessor.java:91-95`) → T-25.4.
- Thấp: chống trùng ở `MessageRouter` không nguyên tử (2 request trùng đồng thời đều gọi Brain, `MessageRouter.java:62-64,81`); `ConversationController` import `catalog.ProductController.PageResponse` → T-11.4 #257.
- Thấp: chống trùng chỉ trong bộ nhớ (US-29, S7); tắt app thì task đã ack trong hàng đợi bị bỏ; nhánh 503 hàng đợi đầy chưa có test.

**ChienPX**
- Cao: widget chat tải cả catalog mỗi lần mở trang (`ChatWidget.tsx:21-42`, commit 3946077). Giao cho LucVT sửa (T-43.3 #264).
- Cao: hub **không khởi động được nếu thiếu DB `shopee`**: `ShopeeSimulatorStore` là `@Component` + `@PostConstruct` không điều kiện, profile `supabase` không có cấu hình → T-21.5 #259.
- Cao: hub ghi câu trả lời thẳng vào DB simulator (`ShopeePushProcessor.java:43-46`), không đi qua `send_message` có ký → T-21.6 #260.
- TB: push xử lý đồng bộ, Brain tới 10 s trong khi Shopee timeout 2 s (`ShopeePushController.java:77-78`, `shopee.md:37`) → T-21.6.
- TB: "dưới 1 triệu", "1tr", "1.5tr" thành trần 1.000đ, bot trả "Shop chưa có sản phẩm…" (`search.py:35,88-96`) → T-10.4 #256.
- TB: prompt sàn viết cho Shopee nhưng dùng cả cho TikTok: cấm chữ "TikTok", không cấm "Shopee" (`graph.py:34-38`) → T-21.7 #261.
- TB: prompt và danh sách giá chỉ thấy 100/364 SKU (`sql_guard.py`, `LIMIT 100`) → T-43.4 #265 (LucVT).
- TB: simulator ghi dở khi validate lỗi, `failure_list` luôn rỗng (`ShopeeSimulatorController.java:66-71,90-105`); simulator không tự gửi push → comment ở #212, #215, T-21.6.
- Thấp: specs chưa cập nhật theo PR #254 (`backend.md:46`, `ai-brain.md` còn `fake.py`, `bench.md`, `overview.md` thiếu `SHOPEE_DB_*`, chưa có dòng decisions) → T-21.5.

**HungDQ**
- TB: p95 lệch 1 hạng, báo thấp hơn thật (`bench/bench/run.py:181`, vd. 3125 thay vì 6198 ms); "AI vi phạm" luôn 0 với transport tiktok/shopee (`run.py:84,111`); report bị `.gitignore` nên không có lịch sử so sánh → T-12.5 #258.
- Thấp: mỗi lần chạy chỉ 1 dataset; dataset chưa có trường nhóm → T-12.1, T-12.2, T-12.5.

## 4. Vấn đề quy trình (cả team)
- US-21 và T-21.1–T-21.4 bị đóng tay 8 phút **trước** khi PR #254 merge; PR không có `Closes #`, phần test plan để trống. T-25.2 đóng dù kết quả 3/5.
- Từ S3: chỉ đóng issue bằng `Closes #n` trong PR. Đóng tay thì comment lý do. PR phải dán kết quả test/bench (decisions #16 dựa vào việc này).
- TC trình duyệt viết nhưng chưa chạy (US-09 mục C, D). Đóng US khi TC đã có dòng kết quả.
- Đổi hợp đồng, cấu hình, thư mục thì sửa spec trong cùng PR (CLAUDE.md). PR #254 chưa làm.

## 5. Thứ tự việc S3 (17–23/11) → S4 (24–30/11)
Làm từ trên xuống. "sau X" = chờ X xong.

**LucVT**
- S3:
  1. T-43.1 #149 backend giảm query
  2. T-13.1 #52 API OTP + JWT. Theo SRS UC-06, từ S3 đơn gắn với khách đã đăng nhập.
  3. T-43.4 #265 ai-brain pool + cache
  4. T-13.2 #53 màn đăng ký/đăng nhập
  5. T-43.3 #264 widget, sau PR US-56
  6. T-43.2 #150 đo trước/sau
  7. T-49.3 #207 trạng thái đồng bộ, sau T-49.1
  8. T-09.4 #255 chạy TC trình duyệt
- S4: T-19.3 #73 và T-22.1 #82 làm trước, vì ChienPX và TuTM đang chờ. Sau đó T-22.2 #83, T-22.4 #208.

**TuTM**
- S3:
  1. T-11.4 #257
  2. T-25.4 #262
  3. T-14.2 #56 API lọc
  4. T-14.1 #55 màn inbox
  5. T-49.1 #205 khung đồng bộ, sau T-48.3
  6. T-25.5 #263
- S4:
  1. T-50.1 #210
  2. T-50.2 #211
  3. T-50.4 #213
  4. T-22.3 #84, sau T-22.1

**ChienPX**
- S3:
  1. T-21.5 #259 (gấp: hub không khởi động được trên máy chỉ có Supabase)
  2. T-10.4 #256
  3. T-21.7 #261
  4. T-15.1 #58
  5. T-15.2 #59
  6. T-21.6 #260
  7. T-49.2 #206
- S4:
  1. T-19.1 #71
  2. T-19.2 #72, sau T-19.3
  3. phần còn lại của T-50.3 #212, sau T-50.2
  4. phần còn lại của T-51.1 #215

**HungDQ**
- S3:
  1. T-48.3 #203 (TuTM cần cho T-49.1)
  2. T-12.5 #258
  3. T-12.1 #49
  4. T-12.2 #50
  5. xem PR US-56 #266
  6. T-16.1 #62 CI
  7. T-15.3 #60, sau T-15.1 và T-15.2
- S4: T-20.1 #75, rồi T-20.2 #76.

## 6. LucVT: task làm độc lập được (S3 → S12)
"Độc lập" = không chờ code của người khác. LucVT là bên cung cấp API cho người khác thì vẫn tính là độc lập.

| Sprint | Task | Độc lập? | Ghi chú |
|---|---|---|---|
| S3 | US-43 T-43.1–T-43.4 · T-13.1, T-13.2 | Có | T-43.3 đổi hợp đồng gợi ý: báo ChienPX, TuTM trước |
| S3 | T-49.3 trạng thái đồng bộ | Không | Cần T-49.1 (TuTM). Làm UI trước với dữ liệu giả |
| S4 | T-19.3, T-22.1, T-22.2, T-22.4 | Có | T-19.2 (ChienPX), T-22.3 (TuTM) chờ LucVT, chốt API sớm |
| S5 | T-33.1, T-33.2 khuyến mãi | Có | US-52 (TuTM, S6) cần dữ liệu này |
| S5 | T-23.1 đơn hợp nhất theo khách | Gần như có | Dựa trên `customer_order` (V14), test bằng đơn sàn seed tay |
| S5 | T-51.4 đơn về thì trừ tồn | Một nửa | Viết `InventoryService` + test trước; nối với T-51.3, T-49.1 sau |
| S6 | T-27.4 chuẩn hoá INCI toàn catalog | Có | Đã có bảng `ingredient` (V15), kéo lên S3–S4 được |
| S6 | T-26.2 màn nhập mã đơn · T-53.1 xác nhận/huỷ đơn sàn | Có · Một nửa | T-53.1: máy trạng thái + cộng lại tồn làm trước |
| S6 | T-26.1 đối chiếu mã đơn qua Order API | Không | Cần API tra đơn của 2 simulator |
| S7 | T-30.1, T-30.2 quyền dữ liệu cá nhân | Một nửa | Xoá hội thoại (TuTM) và vector bộ nhớ (ChienPX) cần thống nhất hàm xoá |
| S7 | T-54.3 · S8 T-55.3 | Không · Một nửa | Chờ sự kiện từ simulator; bảng + API đánh giá làm trước được |
| S9 | T-37.1, T-37.2 | Có | Thuần thuật toán + dữ liệu tổng hợp |

Kéo lên sớm được nếu còn thời gian: T-27.4, T-33.1/T-33.2, T-37.2.

## 7. Quyết định PO trong review
- #36: chấp nhận T-25.2 3/5 (như #21). Bench tách độ trễ khỏi pass/fail ở T-12.5.
- #37: kéo US-43 về S3, LucVT làm trọn widget, backend, ai-brain; nâng capacity (HungDQ 6, các bạn khác 10 md/sprint); thêm task sửa lỗi; mở lại US-10 #40, US-21 #77.
- Giao diện web làm lại theo design system (US-56 #266), PR riêng.
