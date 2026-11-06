# Đặc tả yêu cầu phần mềm (SRS): OmniAI

> Phiên bản **1.0** · 2026-11-06 · Tác giả: HungDQ (PO) · Task T-01.1, T-01.5 (US-01)
> Góp ý: comment trên issue US-01 (#1) hoặc mở PR sửa thẳng. PO quyết định, không cần ký duyệt. Xem [mục 10](#10-góp-ý-và-thay-đổi).

| Phiên bản | Ngày | Người sửa | Nội dung |
|---|---|---|---|
| 0.1 | 2026-11-04 | HungDQ | Bản nháp đầu: chốt 34 US P0 và 21 UC P0 để cả team review |
| 0.1.1 | 2026-11-05 | LucVT | Review T-01.3: chốt Q-01, Q-02 (mục 9), điền ký duyệt (mục 10) |
| 1.0 | 2026-11-06 | HungDQ | T-01.5: trả lời 15 góp ý (mục 9.2), chốt Q-03 đến Q-08, bỏ ký duyệt (decisions #16). Bản chính thức |

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này mô tả OmniAI cần làm gì và đạt tới mức nào để được coi là xong. Đây là căn cứ chung để cả team hiểu cùng một phạm vi, để PO nghiệm thu từng User Story cuối sprint, và để giảng viên đánh giá. Phần "làm như thế nào" nằm trong [docs/specs/](../specs/README.md).

### 1.2 Phạm vi
OmniAI là **trợ lý tư vấn mỹ phẩm đa kênh** theo nguyên tắc "một bộ não, nhiều luật kênh". Một AI Brain dùng chung trả lời người mua trên web của shop, Telegram, Shopee và TikTok Shop. Mỗi kênh có bộ luật riêng được áp trước khi câu trả lời tới khách. Khi khách đồng ý liên kết tài khoản, bot nhớ khách xuyên kênh. Chất lượng bot được đo bằng **bench**, và đây là đóng góp chính của dự án.

**Trong phạm vi:** 4 trụ (epic) A–D ở mục 2.3; shop demo với thương hiệu hư cấu; trang admin cho sản phẩm, khuyến mãi, hội thoại, danh tính và báo cáo bench.

**Ngoài phạm vi:** bán hàng thật (thanh toán, giao hàng, hóa đơn); khách hàng thật; đồng bộ tồn kho với sàn; app di động; ngôn ngữ khác tiếng Việt; tự huấn luyện mô hình ML. Kênh Messenger hiện chưa có US (xem Q-03).

### 1.3 Thuật ngữ
| Thuật ngữ | Nghĩa |
|---|---|
| Kênh sở hữu (owned) | Kênh shop tự quản: WEB, TELEGRAM (MESSENGER nếu làm). Được gửi link, được so sánh giá giữa các kênh |
| Kênh sàn | SHOPEE, TIKTOK. Cấm điều hướng giao dịch ra ngoài sàn: không link, không SĐT, không nhắc kênh khác |
| Adapter | Thành phần chuyển tin của một kênh về định dạng chung `CanonicalMessage`, và gửi câu trả lời ngược lại kênh đó |
| Luật kênh, policy | Luật trong `shared/policies/*.yaml`, áp cho câu trả lời theo kênh: `block` (thay cả câu bằng câu mẫu), `redact` (ẩn đoạn vi phạm), `replace` (thay bằng cụm khác) |
| rawText / finalText | Câu AI Brain sinh ra / câu khách thực sự nhận sau khi áp policy |
| Handoff | Chuyển hội thoại cho nhân viên khi bot lỗi hoặc khách yêu cầu |
| Simulator theo schema thật | Giả lập sàn bằng đúng định dạng JSON trong tài liệu của sàn, để khi sàn duyệt app chỉ cần đổi cấu hình (decisions #12) |
| Bench | Bộ ca kiểm thử tự động đo chất lượng bot theo kênh (`bench/`) |
| Customer / danh tính kênh | Khách hợp nhất / một tài khoản của khách trên một kênh (`channel_identity`) |
| INCI, PAO | Tên thành phần theo chuẩn quốc tế / số tháng dùng được sau khi mở nắp |
| P0 / P1 / P2 | Bắt buộc / nên có / cắt được. MoSCoW: Must / Should / Could have |

### 1.4 Tài liệu tham chiếu
- [docs/de-xuat-OmniAI.md](../de-xuat-OmniAI.md): đề xuất, phản biện, kiến trúc, cách bench.
- [README.md](../../README.md): bài toán, cách chạy. [docs/specs/](../specs/README.md): hợp đồng API, dữ liệu, luật kênh, trạng thái.
- `tools/tracking/backlog.py` và `docs/tracking/OmniAI_Project_Tracking.xlsx`: backlog, sprint, rủi ro. GitHub Issues: mỗi US là một issue, mỗi task là một sub-issue.
- [docs/specs/decisions.md](../specs/decisions.md): #12 (kênh sàn dùng simulator), #13 (chỉ dùng LLM miễn phí).
- Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 (hiệu lực 01/01/2026), Nghị định 356/2025.

## 2. Mô tả tổng quan

### 2.1 Bài toán
Shop mỹ phẩm bán cùng lúc trên Shopee, TikTok Shop và web riêng, nhưng mỗi kênh chỉ nhớ phần của mình. Khách phải khai lại loại da và dị ứng ở từng kênh. Không ai cảnh báo khi đồ mua ở các kênh khác nhau xung đột hoạt chất, và tư vấn giữa các kênh không nhất quán. Ghép một bộ não chung thì AI lại không biết luật riêng của từng kênh: trên Shopee, nhắc tới web hay gửi số điện thoại có thể khiến shop bị khóa. Nhận ra 3 tài khoản là cùng một người thì cần sự đồng ý của khách (Luật 91/2025). Hiện cũng hầu như không ai đo chất lượng chatbot theo từng kênh.

### 2.2 Góc nhìn sản phẩm
```
Kênh (web WS, Telegram, Shopee/TikTok qua simulator, simulator chung)
  -> Adapter -> CanonicalMessage -> MessageRouter (chống trùng tin)
  -> AI Brain (FastAPI: agent, RAG, luật thành phần, tool gọi backend)
  -> PolicyEngine (luật của kênh) -> BotReply {rawText, finalText, violations, blocked, handoff} -> kênh
Bench gửi ca kiểm thử qua simulator hoặc WebSocket, rồi chấm lại finalText bằng cùng bộ YAML.
```
Backend Spring Boot (modular monolith), AI Brain Python, web React, PostgreSQL + pgvector. Chi tiết ở [overview.md](../specs/overview.md).

### 2.3 Chức năng chính (4 trụ)
| Trụ | Nội dung | US P0 |
|---|---|---|
| COMMON: Nền tảng & vận hành | Môi trường, chat web, store demo, lưu và xem hội thoại, handoff, kiểm thử E2E, demo | 10 |
| A: Adapter & luật kênh + Bench | Adapter Telegram/Shopee/TikTok, policy engine, độ bền adapter, bench và metric | 10 |
| B: Chuyên gia mỹ phẩm | Catalog có INCI, RAG gợi ý, giá/tồn kho từ DB, luật thành phần, routine an toàn | 6 |
| C: Danh tính & bộ nhớ xuyên kênh | OTP, deep-link, mã OMNI, hồ sơ da, lịch sử đơn, quyền dữ liệu cá nhân | 6 |
| D: Săn sale & chọn kênh | Khuyến mãi theo kênh, cố vấn mua ngay hay đợi | 2 |

### 2.4 Nhóm người dùng
| Actor | Mô tả | Dùng qua |
|---|---|---|
| Người mua | Hỏi tư vấn, xem sản phẩm, đặt hàng demo, liên kết tài khoản, quản lý dữ liệu của mình | Web, Telegram, Shopee, TikTok Shop |
| Admin shop | Quản lý sản phẩm, khuyến mãi; xem hội thoại; trả lời khi handoff; duyệt gộp/tách danh tính | Trang admin trên web |
| Lead / PO | Thử luật kênh, chạy bench, xem báo cáo, nghiệm thu | `/admin/policy`, `bench/`, trang báo cáo bench |
| Hệ thống | Tác vụ định kỳ (nhắc mua lại, P2) | |
| Kênh ngoài | Telegram Bot API, Shopee Open Platform, TikTok Shop API (gửi webhook, nhận tin trả lời) | |

### 2.5 Môi trường vận hành
Máy dev Windows chạy bằng `tools/systembat/*.bat`. Cần JDK 21, Maven 3.9+, Python 3.11+, Node 20.19+, Docker Desktop. Hạ tầng (Postgres 16 + pgvector, Redis, RabbitMQ) chạy bằng Docker Compose. Cổng: web 5173, backend 8080, AI Brain 8000.

### 2.6 Ràng buộc
| # | Ràng buộc | Nguồn |
|---|---|---|
| C-01 | Dữ liệu cá nhân: phải có consent trước khi liên kết; khách được xem, xuất, xóa dữ liệu; không bao giờ tự gộp danh tính | Luật 91/2025/QH15, decisions #9 |
| C-02 | Trên kênh sàn: không link ngoài, không SĐT, không nhắc kênh khác. YAML luật sàn hiện là bản nháp, đối chiếu quy định ở US-20 | Quy định người bán Shopee/TikTok |
| C-03 | Không quảng cáo mỹ phẩm như thuốc (điều trị, chữa, đặc trị); không chẩn đoán bệnh | Luật quảng cáo mỹ phẩm, `common.yaml` |
| C-04 | Shop là bản demo: thương hiệu hư cấu, không thanh toán, không giao hàng; banner demo luôn hiển thị | Đề xuất, mục 1 |
| C-05 | Chỉ dùng LLM miễn phí (free tier hoặc model mở chạy local); chỉ gửi dữ liệu tổng hợp | decisions #13 |
| C-06 | Shopee và TikTok chạy simulator theo schema thật cho tới khi sàn duyệt app | decisions #12 |
| C-07 | Mọi con số (giá, tồn kho, khuyến mãi) phải lấy từ DB qua tool, LLM không tự nói số | Đề xuất, mục 1 |
| C-08 | Luật an toàn mỹ phẩm (xung đột, mang thai, dị ứng) là code tất định có test; LLM chỉ diễn giải | decisions, `ai-brain.md` |
| C-09 | 4 người, 13 sprint × 1 tuần (03/11/2026 – 01/02/2027) | `backlog.py` |

### 2.7 Giả định và phụ thuộc
- A-01: Shopee/TikTok duyệt app trước S5/S6. Nếu không kịp, demo kênh sàn là giả lập (decisions #12).
- A-02: Có ít nhất một model miễn phí đạt tiêu chí trong `docs/llm-eval/README.md` (US-05).
- A-03: Không có khách thật. Dùng persona tổng hợp và team đóng vai; dataset bench tự xây. Báo cáo cuối phải nêu rõ giới hạn này.
- A-04: Telegram webhook cần URL HTTPS công khai; khi dev dùng tunnel hoặc long polling (Q-04).
- A-05: Catalog 80 SKU tham khảo dữ liệu mở (Open Beauty Facts), dùng thương hiệu hư cấu.
- A-06: Luật thành phần cần người có chuyên môn (dược sĩ hoặc bác sĩ da liễu) review trước khi demo.

## 3. Yêu cầu chức năng (User Story)
Mỗi yêu cầu là một User Story. Mục này **chốt 34 US mức P0**: Acceptance Criteria (AC) là căn cứ PO nghiệm thu cuối sprint. Dòng đầu mỗi US ghi: ưu tiên · sprint · owner · kênh · Use Case liên quan. Những chỗ đã sửa được đánh dấu *(sửa 04/11)* hoặc *(sửa 06/11, G-xx)*, với G-xx là góp ý ở mục 9.2. `backlog.py` đã được cập nhật theo.

### 3.1 COMMON: Nền tảng & vận hành

**US-01 · Tài liệu yêu cầu (SRS, US, UC)**: P0 · S0 · HungDQ · All
Là PO, tôi muốn có SRS khung, User Story và Use Case để cả team hiểu cùng một phạm vi.
- Given SRS và danh sách US/UC · When PO chốt mọi câu hỏi mở và góp ý · Then mọi US P0 có Acceptance Criteria; SRS 1.0 được công bố cho cả team. *(sửa 06/11: bỏ ký duyệt, decisions #16)*

**US-02 · Môi trường dev chạy được trên mọi máy**: P0 · S0 · cả 4 người · WEB
Là dev, tôi muốn chạy được toàn bộ hệ thống trên máy mình theo README.
- Given máy đã cài JDK 21, Maven 3.9+, Python 3.11, Node 20.19+, Docker Desktop · When làm theo README (hoặc chạy `tools/systembat/setup.bat` rồi `run-all.bat`) · Then backend, ai-brain, web chạy và chat widget nhận được phản hồi. *(sửa 04/11: thêm Maven, Node 20.19+ là bản tối thiểu Vite 8 yêu cầu, thêm cách chạy bằng script)*

**US-05 · Chọn LLM provider**: P0 · S0 · ChienPX · All
Là team, tôi muốn chọn LLM dựa trên chất lượng tiếng Việt và chi phí để dùng cho AI Brain.
- Given 20 câu hỏi tiếng Việt về mỹ phẩm · When chạy trên ≥ 3 model miễn phí (free tier hoặc chạy local) · Then có bảng so sánh chất lượng, độ trễ, giới hạn quota theo `docs/llm-eval/README.md` và đề xuất 1 model chính + 1 model dự phòng. *(sửa 04/11 theo decisions #13)*

**US-06 · Chat trên web**: P0 · S1 · TuTM · WEB · UC-01
Là người mua, tôi muốn chat với shop ngay trên web để hỏi sản phẩm.
- Given mở trang store · When gửi "kem chống nắng cho da dầu" · Then nhận phản hồi < 5 s; mất kết nối thì widget báo rõ.

**US-09 · Web store demo**: P0 · S2 · LucVT · WEB · UC-05, UC-06
Là người mua, tôi muốn xem danh sách và chi tiết sản phẩm trên web demo.
- Given vào store · When lọc theo loại da và mở 1 sản phẩm · Then thấy giá, thành phần; banner "Cửa hàng thử nghiệm - không giao hàng" luôn hiển thị.

**US-11 · Lưu hội thoại mọi kênh**: P0 · S2 · TuTM · All · UC-01–04, UC-22
Là admin, tôi muốn hội thoại ở mọi kênh được lưu để xem lại.
- Given khách chat ở bất kỳ kênh nào · When tin vào/ra · Then lưu đủ kênh, conversation_id, rawText, finalText, luật vi phạm.

**US-14 · Admin xem hội thoại**: P0 · S3 · TuTM · All · UC-22
Là admin, tôi muốn xem hội thoại theo kênh và thấy chỗ nào policy đã can thiệp.
- Given có hội thoại ở 2 kênh · When lọc theo kênh · Then thấy rawText và finalText; tin bị chặn được tô màu.

**US-35 · Chuyển nhân viên (handoff)**: P0 · S8 · TuTM · All · UC-19
Là người mua, tôi muốn được chuyển sang nhân viên khi bot không trả lời được.
- Given bot lỗi hoặc khách yêu cầu gặp người · When handoff · Then admin thấy hội thoại chờ, trả lời thay bot trên đúng kênh.

**US-45 · Kiểm thử end-to-end**: P0 · S11 · HungDQ · All · mọi UC P0
Là PO, tôi muốn toàn hệ thống chạy đúng kịch bản demo "Lan" trên mọi kênh.
- Given kịch bản 7 bước (web → Telegram → Shopee → cảnh báo retinol → bench → fault → xóa dữ liệu) · When chạy thử · Then 7/7 bước pass; 0 bug Critical/Major còn mở.

**US-46 · Tài liệu & demo cuối**: P0 · S12 · HungDQ · All
Là giảng viên/hội đồng, tôi muốn xem demo và tài liệu đầy đủ của dự án.
- Given báo cáo cuối, user guide, slide · When demo · Then chạy kịch bản trong ≤ 15 phút, trả lời được câu hỏi về bench.

### 3.2 Trụ A: Adapter & luật kênh + Bench

**US-03 · Spike: API chat sàn trên sandbox**: P0 · S0 · TuTM · SHOPEE, TIKTOK · UC-03, UC-04
Là team, tôi muốn biết chat API của Shopee/TikTok Shop có dùng được trên sandbox không để chốt cách làm adapter.
- Given tài khoản developer Shopee Open Platform và TikTok Shop Partner Center · When gọi API chat/customer service trên test shop · Then có báo cáo kết luận Dùng được / Không / Đang chờ sàn duyệt, kèm bằng chứng (ảnh, log). *(sửa 04/11: thêm kết quả "Đang chờ sàn duyệt"; quyết định adapter ở decisions #12)*

**US-07 · Policy engine v0**: P0 · S1 · HungDQ · All · UC-24
Là chủ shop, tôi muốn câu trả lời trên Shopee/TikTok bị lọc link, SĐT, tên kênh khác để shop không bị phạt.
- Given câu trả lời có URL · When gửi trên kênh SHOPEE · Then URL bị ẩn; cùng câu trên kênh WEB giữ nguyên.

**US-12 · Bộ bench 50 ca**: P0 · S2 · HungDQ · All · UC-25
Là Lead, tôi muốn có 50 ca bench để đo chất lượng mỗi lần đổi prompt/model.
- Given dataset 50 ca phủ ≥ 5 nhóm (sản phẩm, bẫy policy, mỹ phẩm, injection, đa kênh) · When chạy `python -m bench.run` · Then có báo cáo .md/.json theo kênh.

**US-17 · Kênh Telegram**: P0 · S4 · TuTM · TELEGRAM · UC-02
Là người mua, tôi muốn chat với shop qua Telegram và nhận cùng chất lượng tư vấn như trên web.
- Given bot Telegram đã cấu hình webhook · When gửi tin · Then nhận phản hồi < 5 s; Telegram gửi lại cùng update_id thì không trả lời 2 lần.

**US-20 · Luật đủ 4 kênh**: P0 · S4 · HungDQ · All · UC-24
Là chủ shop, tôi muốn mỗi kênh có bộ luật riêng đã đối chiếu với quy định hiện hành.
- Given 4 file YAML WEB, TELEGRAM, SHOPEE, TIKTOK + common · When chạy test · Then mỗi luật có ≥ 2 test (khớp và không khớp nhầm).

**US-21 · Kênh Shopee**: P0 · S5 · TuTM · SHOPEE · UC-03
Là người mua trên Shopee, tôi muốn được tư vấn ngay trong chat Shopee.
- Given adapter Shopee (sandbox hoặc simulator theo schema thật) · When buyer gửi tin · Then nhận phản hồi đã qua luật Shopee, không vượt độ dài tối đa.

**US-24 · Bẫy policy + LLM-as-judge**: P0 · S5 · HungDQ · SHOPEE, TIKTOK · UC-25
Là Lead, tôi muốn đo được bot có bị dụ vi phạm luật kênh không.
- Given 20 ca bẫy (hỏi web rẻ hơn, xin Zalo, injection) · When chạy bench · Then 0 ca lọt tới khách; có điểm judge theo rubric.

**US-25 · Kênh TikTok Shop**: P0 · S6 · TuTM · TIKTOK · UC-04
Là người mua trên TikTok Shop, tôi muốn được tư vấn ngay trong chat TikTok Shop.
- Given adapter TikTok (sandbox hoặc simulator) · When buyer gửi tin · Then nhận phản hồi đã qua luật TikTok.

**US-29 · Độ bền adapter (fault injection)**: P0 · S7 · TuTM · All · UC-02, UC-03, UC-04
Là chủ shop, tôi muốn bot không trả lời trùng hay mất tin khi kênh gửi webhook lỗi.
- Given bật fault injection (trùng, trễ, sai thứ tự) · When gửi 1 tin 3 lần · Then khách nhận đúng 1 phản hồi; tin trễ vẫn được xử lý.

**US-32 · Metric bộ nhớ & xung đột**: P0 · S7 · HungDQ · All · UC-25
Là Lead, tôi muốn đo được bot nhớ đúng khi đổi kênh và bắt đúng xung đột hoạt chất.
- Given dataset nhiều lượt, đổi kênh giữa chừng (có ca khai hồ sơ trên WEB rồi hỏi trên SHOPEE đã liên kết) · When chạy bench · Then báo cáo memory recall và conflict recall. *(sửa 06/11, G-13)*

### 3.3 Trụ B: Chuyên gia mỹ phẩm

**US-04 · Catalog đợt 1 (80 SKU)**: P0 · S0 · LucVT · All · UC-05, UC-20
Là người mua, tôi muốn shop có danh mục sản phẩm đủ thông tin thành phần để được tư vấn đúng.
- Given file catalog · When kiểm tra · Then có ≥ 80 SKU với tên, giá theo 3 kênh, INCI, loại da, dung tích, PAO; thương hiệu hư cấu; file CSV import được, không trùng SKU. Kênh sở hữu (WEB, TELEGRAM) dùng chung giá web. *(sửa 06/11, G-11, G-15)*

**US-08 · Quản lý sản phẩm (admin)**: P0 · S1 · LucVT · WEB · UC-20
Là admin shop, tôi muốn thêm/sửa/ẩn sản phẩm để catalog luôn đúng.
- Given đăng nhập admin · When thêm/sửa/ẩn sản phẩm · Then GET /api/products phản ánh ngay; sản phẩm ẩn không hiện ở store và `GET /api/products/{id}` trả 404. *(sửa 06/11, G-06; đăng nhập admin theo Q-01)*

**US-10 · Gợi ý sản phẩm bằng RAG**: P0 · S2 · ChienPX · All · UC-01
Là người mua, tôi muốn hỏi "kem chống nắng cho da dầu dưới 300k" và được gợi ý đúng sản phẩm có trong shop.
- Given catalog 80 SKU · When hỏi kèm ràng buộc loại da/giá · Then top-3 gợi ý đều có thật trong catalog và đúng ràng buộc; không bịa tên sản phẩm; chỉ gợi ý sản phẩm đang bán (`active`). *(sửa 06/11, G-06)*

**US-15 · Giá và tồn kho đúng từ DB**: P0 · S3 · ChienPX · All · UC-16
Là người mua, tôi muốn bot báo giá và tồn kho đúng với dữ liệu của shop.
- Given 20 ca hỏi giá/tồn kho · When chạy bench · Then 0 câu bịa giá; hết hàng thì nói hết hàng.

**US-27 · Luật thành phần & hồ sơ da**: P0 · S6 · ChienPX · All · UC-14
Là người mua, tôi muốn được cảnh báo khi kết hợp hoạt chất không nên dùng cùng lúc.
- Given hỏi dùng retinol cùng AHA · When bot trả lời · Then có cảnh báo; 30 ca bench xung đột recall 100%.

**US-31 · Routine an toàn (mang thai, dị ứng)**: P0 · S7 · ChienPX · All · UC-15
Là người mua, tôi muốn được gợi ý routine sáng/tối phù hợp và an toàn với tình trạng của tôi.
- Given hồ sơ "mang thai, da dầu mụn" · When xin routine · Then không có retinoid; có lời khuyên hỏi bác sĩ; dị ứng thành phần nào thì loại sản phẩm đó; mọi sản phẩm trong routine có trong catalog và đang bán. *(sửa 06/11, G-14)*

### 3.4 Trụ C: Danh tính & bộ nhớ xuyên kênh

**US-13 · Tài khoản web + OTP**: P0 · S3 · LucVT · WEB · UC-07
Là người mua, tôi muốn đăng ký/đăng nhập web bằng email + OTP để hệ thống nhớ tôi.
- Given nhập email · When nhập OTP 6 số trong 5 phút · Then đăng nhập thành công; sai 5 lần khóa 15 phút; gửi lại OTP cách nhau ≥ 60 s, tối đa 5 lần/giờ. *(sửa 06/11, G-02; OTP gửi qua email theo Q-02)*

**US-18 · Liên kết Telegram bằng deep-link**: P0 · S4 · LucVT · TELEGRAM · UC-09
Là người mua, tôi muốn liên kết Telegram với tài khoản web chỉ bằng 1 lần bấm.
- Given đã đăng nhập web · When bấm deep-link bot gửi · Then 2 danh tính được gắn cùng 1 khách; link dùng 1 lần, hết hạn 10 phút.

**US-19 · Bộ nhớ hồ sơ da**: P0 · S4 · ChienPX · WEB, TELEGRAM · UC-13
Là người mua, tôi muốn bot nhớ loại da và dị ứng tôi đã khai, kể cả khi tôi đổi kênh.
- Given khai "da dầu, dị ứng hương liệu" trên web · When hỏi trên Telegram đã liên kết · Then bot dùng đúng hồ sơ; 10 ca bench memory recall ≥ 90%.

**US-22 · Mã liên kết OMNI-xxxx**: P0 · S5 · LucVT · SHOPEE, TIKTOK · UC-08
Là người mua, tôi muốn liên kết tài khoản Shopee với tài khoản web mà không cần bot gửi link.
- Given lấy mã `OMNI-XXXXXX` (6 ký tự) trên web · When gõ mã vào chat Shopee trong 15 phút, rồi xác nhận và tích consent trên web · Then 2 danh tính được liên kết; mã chỉ dùng 1 lần; một danh tính kênh gõ sai 5 lần trong 1 giờ thì bị khóa nhập mã. *(sửa 06/11, G-01)*

**US-23 · Lịch sử đơn mọi kênh**: P0 · S5 · ChienPX · All · UC-01, UC-14
Là người mua, tôi muốn bot biết tôi đã mua gì ở mọi kênh đã liên kết để tư vấn phù hợp.
- Given khách có đơn ở web và Shopee đã liên kết · When hỏi "mình đã mua gì" · Then bot liệt kê đúng; không lộ đơn của kênh chưa liên kết.

**US-30 · Quyền dữ liệu cá nhân**: P0 · S7 · LucVT · WEB · UC-12
Là người mua, tôi muốn xem, tải về, xóa dữ liệu và tách liên kết kênh của mình.
- Given đã đăng nhập · When yêu cầu xóa · Then hồ sơ, hồ sơ da, hội thoại và vector bộ nhớ bị xóa; đơn hàng được ẩn danh; liên kết kênh bị gỡ; log consent chỉ giữ id ẩn danh, thời điểm, loại consent. *(sửa 06/11, G-03)*

### 3.5 Trụ D: Săn sale & chọn kênh

**US-33 · Khuyến mãi theo kênh**: P0 · S8 · LucVT · All · UC-21
Là admin shop, tôi muốn nhập voucher, flash sale, giá theo từng kênh và xem lịch sử giá.
- Given nhập voucher Shopee 12.12 · When bot được hỏi trên Shopee · Then bot dùng đúng voucher đó; lịch sử giá lưu mỗi lần đổi.

**US-34 · Cố vấn săn sale**: P0 · S8 · ChienPX · All · UC-17
Là người mua, tôi muốn biết nên mua ngay hay đợi sale và (trên kênh của shop) kênh nào lợi nhất.
- Given dữ liệu khuyến mãi · When hỏi trên WEB · Then so sánh đúng giá các kênh; hỏi trên SHOPEE thì chỉ nói voucher/giờ sale Shopee; số liệu khớp DB 100%; chỉ khuyên "đợi" khi có khuyến mãi của đúng kênh bắt đầu trong ≤ 7 ngày và rẻ hơn ≥ 10% (luật ở UC-17). *(sửa 06/11, G-12)*

### 3.6 Ngoài P0 (chưa chốt trong đợt này)
AC của các US dưới đây xem trong `backlog.py`. Chúng sẽ được chốt ở Sprint Review trước sprint tương ứng, và có thể bị cắt nếu velocity < 80%.

| US | Tên | Ưu tiên | Sprint | Owner |
|---|---|---|---|---|
| US-16 | CI: build + test + bench | P1 | S3 | HungDQ |
| US-26 | Liên kết bằng mã đơn sàn | P1 | S6 | LucVT |
| US-28 | Viết lại thay vì chặn | P1 | S6 | HungDQ |
| US-36 | So sánh 2–3 model | P1 | S8 | HungDQ |
| US-37 | Gợi ý liên kết xác suất | P1 | S9 | LucVT |
| US-38 | Admin duyệt gộp/tách danh tính | P1 | S9 | TuTM |
| US-39 | Nhắc mua lại & hạn dùng | P2 | S9 | ChienPX |
| US-40 | Metric danh tính | P1 | S9 | HungDQ |
| US-41 | Báo cáo bench trên admin | P1 | S10 | HungDQ |
| US-42 | Đo độ trễ & chi phí | P1 | S10 | TuTM |
| US-43 | Tối ưu hiệu năng dữ liệu | P1 | S10 | LucVT |
| US-44 | Tối ưu prompt theo bench | P1 | S10 | ChienPX |

## 4. Use Case

### 4.1 Danh sách
| UC | Tên | Actor | US | Ưu tiên |
|---|---|---|---|---|
| UC-01 | Chat tư vấn trên web | Người mua | US-06, 10, 11, 23 | P0 |
| UC-02 | Chat tư vấn trên Telegram | Người mua | US-17, 29 | P0 |
| UC-03 | Chat tư vấn trên Shopee | Người mua | US-21, 29 | P0 |
| UC-04 | Chat tư vấn trên TikTok Shop | Người mua | US-25, 29 | P0 |
| UC-05 | Xem & lọc sản phẩm | Người mua | US-09, 04 | P0 |
| UC-06 | Đặt hàng ảo | Người mua | US-09 | P0 |
| UC-07 | Đăng ký/đăng nhập OTP | Người mua | US-13 | P0 |
| UC-08 | Liên kết kênh sàn bằng mã OMNI | Người mua | US-22 | P0 |
| UC-09 | Liên kết Telegram bằng deep-link | Người mua | US-18 | P0 |
| UC-10 | Liên kết bằng mã đơn sàn | Người mua | US-26 | P1 |
| UC-11 | Xác nhận gợi ý liên kết | Người mua | US-37 | P1 |
| UC-12 | Quản lý dữ liệu cá nhân | Người mua | US-30 | P0 |
| UC-13 | Khai & cập nhật hồ sơ da | Người mua | US-19 | P0 |
| UC-14 | Kiểm tra xung đột hoạt chất | Người mua | US-27, 23 | P0 |
| UC-15 | Gợi ý routine | Người mua | US-31 | P0 |
| UC-16 | Hỏi giá & tồn kho | Người mua | US-15 | P0 |
| UC-17 | Tư vấn săn sale | Người mua | US-34 | P0 |
| UC-18 | Nhắc mua lại | Hệ thống | US-39 | P2 |
| UC-19 | Chuyển nhân viên | Người mua, Admin | US-35 | P0 |
| UC-20 | Quản lý sản phẩm | Admin | US-08, 04 | P0 |
| UC-21 | Quản lý khuyến mãi | Admin | US-33 | P0 |
| UC-22 | Xem hội thoại | Admin | US-14, 11 | P0 |
| UC-23 | Duyệt gộp/tách danh tính | Admin | US-38 | P1 |
| UC-24 | Thử luật kênh (simulator) | Admin, Lead | US-07, 20 | P0 |
| UC-25 | Chạy bench & xem báo cáo | Lead, PO | US-12, 24, 32 | P0 |

### 4.2 Đặc tả UC P0
Mỗi UC gồm: tiền điều kiện → hậu điều kiện, luồng chính, luồng phụ, ngoại lệ, business rule (BR). UC P1/P2 chỉ có mô tả trong `backlog.py`.

**UC-01 · Chat tư vấn trên web** (Người mua). Tiền điều kiện: mở trang store → Hậu điều kiện: hội thoại được lưu.
1. Khách mở trang; widget kết nối `/ws/chat` và hiện trạng thái kết nối.
2. Khách gõ câu hỏi và gửi.
3. Hệ thống chuyển tin thành `CanonicalMessage` kênh WEB rồi gửi tới AI Brain.
4. AI Brain tư vấn (tra catalog; lấy giá, tồn kho qua tool) và trả lời.
5. Policy áp luật kênh WEB; widget hiện câu trả lời trong < 5 s.
- Luồng phụ: khách chưa đăng nhập thì được tư vấn chung, không dùng hồ sơ.
- Ngoại lệ: AI Brain lỗi hoặc quá 10 s thì trả câu chờ và chuyển nhân viên (UC-19). Mất kết nối thì widget báo "Mất kết nối" và khóa nút Gửi.
- BR: giá, tồn kho chỉ lấy từ tool (C-07).

**UC-02 · Chat tư vấn trên Telegram** (Người mua). Tiền điều kiện: bot đã cấu hình webhook → Hậu điều kiện: hội thoại được lưu.
1. Khách nhắn bot.
2. Telegram gọi webhook; adapter kiểm `update_id` rồi chuyển tin thành `CanonicalMessage`.
3. AI Brain trả lời; policy áp luật TELEGRAM.
4. Bot gửi câu trả lời qua Bot API trong < 5 s.
- Luồng phụ: khách đã liên kết thì bot dùng hồ sơ hợp nhất (UC-13).
- Ngoại lệ: Telegram gửi lại cùng `update_id` thì không trả lời lần 2.
- BR: chống trùng theo `update_id`.

**UC-03 · Chat tư vấn trên Shopee** (Người mua). Tiền điều kiện: shop đã ủy quyền app (simulator theo schema thật cho tới khi Shopee duyệt, decisions #12) → Hậu điều kiện: hội thoại được lưu.
1. Buyer nhắn trong chat Shopee.
2. Webhook (thật hoặc simulator) tới adapter Shopee; adapter kiểm chữ ký, chuyển tin thành `CanonicalMessage`.
3. AI Brain trả lời với system prompt của kênh sàn.
4. Policy áp luật SHOPEE: ẩn link và SĐT, chặn câu nhắc kênh khác, cắt theo độ dài tối đa.
5. Adapter gửi finalText qua API gửi tin của Shopee.
- Luồng phụ: khách gõ mã OMNI thì chạy UC-08.
- Ngoại lệ: câu trả lời vi phạm luật thì bị ẩn hoặc thay bằng câu mẫu (viết lại là US-28, P1).
- BR: không link ngoài, không SĐT, không nhắc kênh khác (C-02).

**UC-04 · Chat tư vấn trên TikTok Shop** (Người mua). Giống UC-03, áp luật TIKTOK. BR: không nhắc Shopee hay kênh khác.

**UC-05 · Xem & lọc sản phẩm** (Người mua). Tiền điều kiện: không.
1. Khách mở store, thấy danh sách sản phẩm đang bán và banner demo.
2. Lọc theo loại da, danh mục, khoảng giá.
3. Mở chi tiết: giá, thành phần INCI, dung tích, PAO.
- Ngoại lệ: không tải được dữ liệu thì hiện thông báo lỗi.
- BR: banner demo luôn hiển thị; sản phẩm ẩn không xuất hiện; lọc và hiển thị theo giá web (`price_web`). *(sửa 06/11, G-09)*

**UC-06 · Đặt hàng ảo** (Người mua). Tiền điều kiện: có sản phẩm còn hàng → Hậu điều kiện: đơn trạng thái DEMO được tạo.
1. Khách chọn sản phẩm và số lượng.
2. Khách xác nhận đặt hàng.
3. Hệ thống tạo đơn trạng thái DEMO và báo rõ "không thanh toán, không giao hàng".
- Luồng phụ: ở S2 cho đặt mà không cần đăng nhập (`customer_id` để trống); từ S3 đơn gắn với khách đã đăng nhập.
- Ngoại lệ: hết hàng thì không cho đặt.
- BR: không gọi cổng thanh toán, không giao hàng thật (C-04); đơn DEMO không trừ tồn kho. *(sửa 06/11, G-04)*

**UC-07 · Đăng ký/đăng nhập OTP** (Người mua). Tiền điều kiện: có email → Hậu điều kiện: có phiên đăng nhập.
1. Khách nhập email.
2. Hệ thống gửi OTP 6 số qua email (khi dev thì in mã ra log, Q-02).
3. Khách nhập OTP trong 5 phút.
4. Đăng nhập thành công; chưa có tài khoản thì tạo mới.
- Ngoại lệ: OTP hết hạn thì yêu cầu mã mới; sai 5 lần thì khóa 15 phút.
- BR: OTP hết hạn sau 5 phút; gửi lại cách nhau ≥ 60 s, tối đa 5 lần/giờ. *(sửa 06/11, G-02)*

**UC-08 · Liên kết kênh sàn bằng mã OMNI** (Người mua). Tiền điều kiện: đã đăng nhập web → Hậu điều kiện: danh tính sàn gắn với khách (`linked_by = LINK_CODE`).
1. Khách lấy mã `OMNI-XXXXXX` (6 ký tự A–Z và 2–9, bỏ O, I) trên web; màn hình hiện thời gian còn lại.
2. Khách gõ mã vào chat Shopee hoặc TikTok.
3. Adapter nhận ra mã; hệ thống kiểm tra mã.
4. Trang web của khách hiện yêu cầu "Liên kết tài khoản Shopee ***abc?"; khách tích đồng ý xử lý dữ liệu và xác nhận.
5. Danh tính sàn được liên kết; bot xác nhận đã liên kết.
- Ngoại lệ: mã hết hạn, đã dùng hoặc sai thì báo lỗi; một danh tính kênh gõ sai 5 lần trong 1 giờ thì bị khóa nhập mã tới hết giờ đó.
- BR: mã dùng 1 lần, hết hạn sau 15 phút; chỉ gắn sau khi khách xác nhận và có consent trên web (C-01); bot không gửi link trên sàn. *(sửa 06/11, G-01)*

**UC-09 · Liên kết Telegram bằng deep-link** (Người mua). Tiền điều kiện: khách có tài khoản web → Hậu điều kiện: đã liên kết, có consent.
1. Khách yêu cầu liên kết trong Telegram.
2. Bot gửi deep-link.
3. Khách bấm, mở trang xác nhận trên web.
4. Khách tích đồng ý xử lý dữ liệu và xác nhận.
5. Hai danh tính được gắn cùng một khách (`linked_by = DEEP_LINK`).
- Luồng phụ: trình duyệt chưa đăng nhập web thì chuyển sang UC-07, xong quay lại trang xác nhận.
- Ngoại lệ: link hết hạn hoặc đã dùng thì yêu cầu link mới.
- BR: link dùng 1 lần, hết hạn sau 10 phút; token trong link gắn với đúng Telegram chat id đã yêu cầu; bắt buộc có consent (C-01). *(sửa 06/11, G-05)*

**UC-12 · Quản lý dữ liệu cá nhân** (Người mua). Tiền điều kiện: đã đăng nhập → Hậu điều kiện: dữ liệu cập nhật theo yêu cầu, có log.
1. Khách mở trang dữ liệu cá nhân.
2. Xem hoặc tải về dữ liệu; hoặc chọn xóa, tách liên kết kênh, rút consent.
3. Với yêu cầu xóa: xác nhận 2 bước.
4. Hệ thống xóa hoặc ẩn danh dữ liệu, gỡ liên kết, ghi log.
- Ngoại lệ: hủy ở bước xác nhận thì không có gì thay đổi.
- BR: Luật 91/2025/QH15 (C-01). Khi xóa: xóa hồ sơ, hồ sơ da, hội thoại, vector bộ nhớ; ẩn danh đơn hàng (giữ cho thống kê); log consent chỉ giữ id ẩn danh, thời điểm, loại consent. *(sửa 06/11, G-03)*

**UC-13 · Khai & cập nhật hồ sơ da** (Người mua). Hậu điều kiện: hồ sơ lưu theo khách.
1. Khách khai loại da, vấn đề da, dị ứng, mang thai qua form hoặc qua chat.
2. Nếu khai qua chat: AI trích hồ sơ và hỏi lại để khách xác nhận.
3. Hồ sơ được lưu; mọi kênh đã liên kết dùng chung hồ sơ này.
- BR: thông tin sức khỏe là dữ liệu nhạy cảm, cần consent. Hồ sơ khai qua chat chỉ được lưu sau khi khách xác nhận ở bước 2; chưa xác nhận thì chỉ dùng trong phiên đó. Kênh sàn đã liên kết cũng dùng chung hồ sơ, nghiệm thu ở US-32. *(sửa 06/11, G-13)*

**UC-14 · Kiểm tra xung đột hoạt chất** (Người mua). Hậu điều kiện: có cảnh báo nếu xung đột.
1. Khách hỏi về việc kết hợp hoạt chất hoặc về routine.
2. Agent gọi tool `check_ingredients` với thành phần đang hỏi và đồ khách đã mua ở mọi kênh đã liên kết.
3. Luật tất định trả cảnh báo; LLM giải thích lại cho dễ hiểu.
- BR: luật nằm trong `ai-brain/app/rules/ingredients.py`; LLM chỉ giải thích (C-08).

**UC-15 · Gợi ý routine** (Người mua). Tiền điều kiện: có hồ sơ da.
1. Khách xin routine.
2. Hệ thống đọc hồ sơ, chọn sản phẩm cho buổi sáng và buổi tối.
3. Lọc theo luật an toàn.
4. Trả routine kèm lưu ý.
- Luồng phụ: thiếu hồ sơ thì hỏi thêm.
- BR: mang thai thì loại retinoid và khuyên hỏi bác sĩ; dị ứng thành phần nào thì loại sản phẩm chứa thành phần đó; mọi sản phẩm trong routine phải lấy từ catalog và đang bán. *(sửa 06/11, G-14)*

**UC-16 · Hỏi giá & tồn kho** (Người mua). Tiền điều kiện: sản phẩm có trong catalog.
1. Khách hỏi giá hoặc tồn kho.
2. Agent gọi tool `get_price_and_stock` theo kênh hiện tại.
3. Bot trả lời đúng số trong DB.
- Ngoại lệ: hết hàng thì báo hết và gợi ý sản phẩm thay thế.
- BR: số liệu chỉ lấy từ DB, giá theo đúng kênh (C-07); kênh sở hữu (WEB, TELEGRAM, MESSENGER) dùng giá web; sản phẩm thay thế phải có trong catalog, đang bán và còn hàng. *(sửa 06/11, G-14, G-15)*

**UC-17 · Tư vấn săn sale** (Người mua). Tiền điều kiện: có dữ liệu khuyến mãi.
1. Khách hỏi nên mua ngay hay đợi.
2. Agent gọi tool `get_promotions`.
3. Trên kênh sở hữu: so sánh giá giữa các kênh và gợi ý mua ngay hay đợi.
- Luồng phụ: trên kênh sàn chỉ nói voucher và giờ sale của chính sàn đó.
- BR: không so sánh kênh trên Shopee/TikTok; số liệu khớp DB. Chỉ khuyên "đợi" khi DB có khuyến mãi của đúng kênh, bắt đầu trong ≤ 7 ngày và giá sau khuyến mãi thấp hơn giá hiện tại ≥ 10%; còn lại khuyên mua ngay. Quyết định này do code tất định, LLM chỉ diễn giải (C-07). *(sửa 06/11, G-12)*

**UC-19 · Chuyển nhân viên** (Người mua, Admin). Hậu điều kiện: hội thoại nằm trong hàng chờ.
1. Bot lỗi, hoặc khách yêu cầu gặp người.
2. Hội thoại được gắn cờ handoff và đưa vào hàng chờ.
3. Admin mở hội thoại và trả lời thay bot.
4. Tin của admin đi qua policy rồi được gửi đúng kênh.
- BR: tin nhân viên gửi vẫn phải qua policy của kênh.

**UC-20 · Quản lý sản phẩm** (Admin). Tiền điều kiện: đăng nhập admin (Q-01) → Hậu điều kiện: catalog cập nhật.
1. Admin mở danh sách sản phẩm.
2. Thêm hoặc sửa (giá niêm yết 3 kênh, INCI, loại da, dung tích, PAO), hoặc ẩn sản phẩm.
3. Hệ thống kiểm tra dữ liệu và lưu; `GET /api/products` phản ánh ngay.
- Ngoại lệ: trùng SKU thì báo lỗi.
- BR: sản phẩm ẩn không hiện ở store và AI không được gợi ý; không xóa cứng, chỉ ẩn (`active=false`); UC-20 chỉ quản lý giá niêm yết, khuyến mãi thuộc UC-21. *(sửa 06/11, G-07, G-08)*

**UC-21 · Quản lý khuyến mãi** (Admin). Tiền điều kiện: đăng nhập admin.
1. Admin nhập voucher, flash sale theo kênh, chồng lên giá niêm yết của UC-20.
2. Hệ thống lưu và ghi lịch sử giá.
- BR: lưu lịch sử giá mỗi lần đổi, tính từ S8 (US-33); voucher hết hạn thì tự tắt; mọi mốc giờ khuyến mãi lưu theo `Asia/Ho_Chi_Minh`. *(sửa 06/11, G-08, G-10)*

**UC-22 · Xem hội thoại** (Admin). Tiền điều kiện: đăng nhập admin.
1. Admin mở inbox, lọc theo kênh, khoảng ngày, có vi phạm hay không.
2. Mở một hội thoại: thấy rawText, finalText và các luật đã kích hoạt; tin bị chặn được tô màu.
- BR: thông tin nhạy cảm được ẩn theo quyền.

**UC-24 · Thử luật kênh (simulator)** (Admin, Lead). Tiền điều kiện: backend đang chạy.
1. Mở `/admin/policy`.
2. Nhập một câu và chọn các kênh.
3. Hệ thống gửi câu đó qua simulator của từng kênh.
4. Màn hình hiện rawText, finalText, luật vi phạm và cờ blocked theo từng kênh.
- BR: dùng chung YAML với bench.

**UC-25 · Chạy bench & xem báo cáo** (Lead, PO). Tiền điều kiện: dataset sẵn sàng, backend và AI Brain đang chạy.
1. Chạy `run-bench.bat` (hoặc `python -m bench.run`) với một dataset.
2. Bench gửi từng ca qua simulator hoặc WebSocket, rồi chấm lại finalText bằng YAML luật.
3. Bench ghi báo cáo .md/.json theo kênh vào `bench/reports/`.
- Ngoại lệ: backend không chạy thì bench báo lỗi kết nối.
- BR: bench không được giảm thì mới merge vào `dev` *(sửa 04/11: trước ghi `develop`)*.

## 5. Yêu cầu giao diện ngoài
- **Người dùng:** store `/` (danh sách, lọc, chi tiết, đặt hàng demo, banner demo); chat widget trên mọi trang của store; trang admin (`/admin/policy` đã có; sản phẩm, khuyến mãi, inbox, danh tính, báo cáo bench sẽ thêm theo sprint).
- **Kênh:** WEB qua WebSocket `/ws/chat`; TELEGRAM qua Bot API webhook (S4); SHOPEE, TIKTOK qua simulator theo schema thật, chuyển sang API thật khi sàn duyệt; simulator chung `POST /api/simulator/{channel}/messages` cho bench và trang thử luật.
- **Phần mềm:** backend gọi AI Brain qua `POST /chat` (JSON snake_case, timeout 10 s, lỗi thì handoff); AI Brain gọi LLM miễn phí qua interface `LLMProvider`; PostgreSQL 16 + pgvector; Redis và RabbitMQ dùng từ S7.
- Hợp đồng chi tiết: [api-contracts.md](../specs/api-contracts.md).

## 6. Yêu cầu phi chức năng
| ID | Nhóm | Yêu cầu | Đo bằng | US |
|---|---|---|---|---|
| NFR-01 | Hiệu năng | Bot trả lời một tin với p95 < 5 s | Latency p95 trong báo cáo bench | US-06, 17 |
| NFR-02 | Hiệu năng dữ liệu | API p95 < 300 ms với 300 SKU và 10.000 tin | Load test | US-43 (P1) |
| NFR-03 | Tuân thủ kênh | 0 ca vi phạm luật lọt tới khách trên kênh sàn | Bench: `leaked = 0` | US-07, 20, 24 |
| NFR-04 | Đúng số liệu | Tỉ lệ bịa giá, tồn kho = 0 | 20 ca bench giá/tồn kho | US-15 |
| NFR-05 | Độ tin cậy kênh | Không trả lời trùng khi kênh gửi lại; ≥ 99% thành công khi bật fault injection | Bench adapter | US-17, 29 |
| NFR-06 | Khả dụng | AI Brain lỗi hoặc quá 10 s thì trả câu chờ và handoff; tin vẫn được lưu | Test router | US-11, 35 |
| NFR-07 | Dữ liệu cá nhân | Consent trước khi liên kết; xem, xuất, xóa được; không tự gộp; hồ sơ sức khỏe là dữ liệu nhạy cảm | Test API, review | US-18, 22, 30 |
| NFR-08 | Xác thực | OTP 6 số hết hạn sau 5 phút, sai 5 lần khóa 15 phút, gửi lại cách nhau ≥ 60 s và ≤ 5 lần/giờ; deep-link 1 lần, 10 phút; mã OMNI 6 ký tự, 1 lần, 15 phút, sai 5 lần/giờ thì khóa *(sửa 06/11, G-01, G-02)* | Test API | US-13, 18, 22 |
| NFR-09 | An toàn nội dung | Không chẩn đoán, không claim y khoa; bắt được 100% xung đột và chống chỉ định trong bench | Bench xung đột, routine | US-27, 31 |
| NFR-10 | Bí mật | Không commit `.env`, API key; key đọc từ biến môi trường | Review PR | All |
| NFR-11 | Kiểm thử được | Mỗi luật kênh có ≥ 2 test (khớp đúng và không khớp nhầm), chạy giống nhau ở Java và Python | `PolicyEngineTest`, `test_policy_check.py` | US-07, 20 |
| NFR-12 | Bảo trì | `docs/specs` cập nhật trong cùng PR khi đổi hợp đồng; CI chạy build, test, bench | Review PR, CI | US-16 (P1) |
| NFR-13 | Triển khai | Chạy được trên Windows bằng `tools/systembat`; hạ tầng bằng Docker Compose | Setup trên 4 máy | US-02 |
| NFR-14 | Chi phí | Chi phí LLM 0đ | Báo cáo US-05 | US-05 |

## 7. Bench và tiêu chí đo
| Nhóm | Metric | Mục tiêu | US · Sprint |
|---|---|---|---|
| Luật kênh | Tỉ lệ vi phạm theo kênh; số ca lọt tới khách | Lọt = 0 trên kênh sàn | US-07 S1, US-12 S2, US-24 S5 |
| Luồng web | 5 ca qua WebSocket, độ trễ < 5 s | 5/5 pass | US-06 S1 (T-06.5) |
| Grounding | Tỉ lệ bịa giá/tồn kho | 0 | US-15 S3 |
| Gợi ý sản phẩm | Top-3 đúng ràng buộc, không bịa tên | 10/10 ca RAG | US-10 S2 |
| Mỹ phẩm | Recall xung đột; recall chống chỉ định | 100% (30 ca); 100% | US-27 S6, US-31 S7 |
| Bộ nhớ | Memory recall khi đổi kênh | ≥ 90% (10 ca); ≥ 15 ca nhiều lượt | US-19 S4, US-32 S7 |
| Adapter | Tỉ lệ thành công dưới fault injection | ≥ 99% | US-29 S7 |
| Judge | Tỉ lệ judge đồng ý với người chấm | ≥ 80% trên 20 ca | US-24 S5 |
| Danh tính | Precision/Recall/F1 theo ngưỡng | Precision ≥ 0,9 ở ngưỡng mặc định | US-37, 40 S9 (P1) |
| Vận hành | Latency p95 theo kênh, chi phí mỗi hội thoại | p95 < 5 s, chi phí 0đ | US-42 S10 (P1) |

Cách chạy và định dạng dataset: [bench.md](../specs/bench.md).

## 8. Ma trận truy vết (US P0)
| US | Sprint | Owner | UC | Kiểm chứng khi nghiệm thu |
|---|---|---|---|---|
| US-01 | S0 | HungDQ | | SRS 1.0; mục 9 không còn câu hỏi mở |
| US-02 | S0 | Cả 4 | | Ảnh chụp chat widget có phản hồi trên máy từng người |
| US-03 | S0 | TuTM | UC-03, 04 | Báo cáo spike kèm bằng chứng; decisions #12 |
| US-04 | S0 | LucVT | UC-05, 20 | File CSV ≥ 80 SKU, không trùng SKU |
| US-05 | S0 | ChienPX | | `docs/llm-eval/report.md`, PO duyệt (T-05.3) |
| US-06 | S1 | TuTM | UC-01 | 5 ca bench luồng web (T-06.5), `docs/testcases/US-06.md` |
| US-07 | S1 | HungDQ | UC-24 | `PolicyEngineTest` và bộ ca dùng chung Java/Python |
| US-08 | S1 | LucVT | UC-20 | Test API CRUD, test lỗi validate |
| US-09 | S2 | LucVT | UC-05, 06 | Test 6 tổ hợp lọc; đơn trạng thái DEMO |
| US-10 | S2 | ChienPX | UC-01 | 10 ca bench RAG |
| US-11 | S2 | TuTM | UC-01–04, 22 | Test lưu tin khi AI Brain lỗi |
| US-12 | S2 | HungDQ | UC-25 | 50 ca, ≥ 5 nhóm, báo cáo theo kênh |
| US-13 | S3 | LucVT | UC-07 | Test OTP hết hạn, sai OTP, khóa, giới hạn gửi lại |
| US-14 | S3 | TuTM | UC-22 | Test 3 bộ lọc |
| US-15 | S3 | ChienPX | UC-16 | 20 ca bench, bịa số = 0 |
| US-17 | S4 | TuTM | UC-02 | Gửi lại webhook 3 lần → 1 phản hồi |
| US-18 | S4 | LucVT | UC-09 | Test link hết hạn, dùng lại link |
| US-19 | S4 | ChienPX | UC-13 | 10 ca bench, memory recall ≥ 90% |
| US-20 | S4 | HungDQ | UC-24 | Mỗi luật ≥ 2 test, có ghi nguồn quy định |
| US-21 | S5 | TuTM | UC-03 | 5 ca bench kênh Shopee |
| US-22 | S5 | LucVT | UC-08 | Test mã hết hạn, dùng lại, sai mã, khóa sau 5 lần sai; chưa xác nhận trên web thì chưa gắn |
| US-23 | S5 | ChienPX | UC-01, 14 | 5 ca bench |
| US-24 | S5 | HungDQ | UC-25 | 20 ca bẫy, 0 lọt; judge ≥ 80% |
| US-25 | S6 | TuTM | UC-04 | 5 ca bench kênh TikTok |
| US-27 | S6 | ChienPX | UC-14 | 30 ca bench, recall 100% |
| US-29 | S7 | TuTM | UC-02–04 | Bench adapter dưới lỗi ≥ 99% |
| US-30 | S7 | LucVT | UC-12 | Test từng quyền; xóa xác nhận 2 bước |
| US-31 | S7 | ChienPX | UC-15 | 10 hồ sơ mẫu; 100% ca chống chỉ định bị loại |
| US-32 | S7 | HungDQ | UC-25 | ≥ 15 ca nhiều lượt; báo cáo có 2 metric mới |
| US-33 | S8 | LucVT | UC-21 | Test voucher hết hạn |
| US-34 | S8 | ChienPX | UC-17 | 10 ca bench sale theo kênh |
| US-35 | S8 | TuTM | UC-19 | Test khi AI Brain lỗi; tin admin vẫn qua policy |
| US-45 | S11 | HungDQ | mọi UC P0 | Kịch bản E2E 7/7 bước |
| US-46 | S12 | HungDQ | | Diễn tập demo ≤ 15 phút |

## 9. Câu hỏi mở và góp ý review
Mọi câu hỏi và góp ý của đợt review S0 đã được PO chốt ngày 06/11/2026 (T-01.5). Câu hỏi mới thì thêm dòng từ Q-09 và ghi người chốt.

### 9.1 Câu hỏi mở
| # | Câu hỏi | Kết luận |
|---|---|---|
| Q-01 | US-08 (S1) cần "đăng nhập admin" nhưng chưa có US nào cho đăng nhập admin | **Chốt 05/11 (LucVT).** S1–S2 dùng một tài khoản admin cấu hình qua biến môi trường (Spring Security in-memory), làm đầy đủ ở S3 cùng US-13. API quản trị nằm dưới `/api/admin/**`, xác thực HTTP Basic bằng `ADMIN_USERNAME`/`ADMIN_PASSWORD` (thêm vào `.env.example` ở T-08.1). API đọc của store vẫn mở |
| Q-02 | OTP (US-13) gửi qua đâu? SMS thường tốn phí | **Chốt 05/11 (LucVT).** Chỉ gửi qua email (SMTP miễn phí), khi dev in mã ra log. SĐT chỉ là trường hồ sơ, không dùng để đăng nhập trong P0 |
| Q-03 | Messenger đã có trong `ChannelType` và YAML nhưng không có US | **Chốt 06/11 (PO).** Ngoài phạm vi P0; giữ enum và YAML cho simulator và bench; chỉ làm nếu Telegram bị chặn |
| Q-04 | Telegram webhook cần HTTPS công khai | **Chốt 06/11 (PO).** Khi dev dùng long polling hoặc tunnel (ngrok/cloudflared); demo dùng tunnel |
| Q-05 | Model miễn phí có thể không đạt p95 < 5 s (US-06, US-17) | **Chốt 06/11 (PO, T-05.3).** Tạm dùng `gemini-3.6-flash`, duyệt có điều kiện (decisions #14). Giữ NFR-01. T-06.4 đo lại p95 trước Sprint Review S1 (16/11); vẫn > 5 s thì PO chọn nới NFR-01 hoặc đổi model chính sang model dự phòng |
| Q-06 | LLM-as-judge (US-24) cũng phải là model miễn phí | **Chốt 06/11 (PO).** Judge là model trong shortlist US-05 nhưng khác model chính đang dùng; hiện là `qwen/qwen3.8-27b` (Groq) |
| Q-07 | BR của UC-25 cần CI (US-16, P1, S3). Trước S3 thì sao? | **Chốt 06/11 (PO).** Người mở PR dán kết quả bench/test vào mô tả PR (đã ghi trong `docs/TEAM_GIT_WORKFLOW.md`) |
| Q-08 | Thầy chưa trả lời câu "simulator theo schema thật có được chấp nhận không" | **Chốt tạm 06/11 (PO).** Làm theo decisions #12. HungDQ hỏi thầy trước S5 (08/12); nếu thầy không chấp nhận thì Sprint Review gần nhất điều chỉnh phạm vi US-21, US-25. Không chặn SRS 1.0 |

### 9.2 Góp ý review và quyết định của PO
G-01 đến G-11 do LucVT góp ý (PR #174), G-12 đến G-15 do ChienPX góp ý (comment trên issue #1). PO đồng ý tất cả và đã sửa vào chỗ ghi ở cột cuối.

| # | Người góp ý | Chỗ | Quyết định | Đã sửa |
|---|---|---|---|---|
| G-01 | LucVT | UC-08, US-22 | Mã 4 chữ số dễ bị dò. Đổi thành `OMNI-XXXXXX` (6 ký tự A–Z và 2–9, bỏ O, I); mỗi danh tính kênh sai tối đa 5 lần/giờ; gõ đúng mã thì khách phải xác nhận và tích consent trên web mới gắn | US-22, UC-08, NFR-08 |
| G-02 | LucVT | US-13 | Chỉ email + OTP (khớp Q-02); gửi lại OTP cách nhau ≥ 60 s, tối đa 5 lần/giờ | US-13, UC-07, NFR-08 |
| G-03 | LucVT | UC-12, US-30 | Xóa hồ sơ, hồ sơ da, hội thoại, vector bộ nhớ; ẩn danh đơn hàng; giữ log consent tối thiểu (id ẩn danh, thời điểm, loại consent) | US-30, UC-12 |
| G-04 | LucVT | UC-06 | Đơn DEMO không trừ tồn kho. S2 đặt không cần đăng nhập, từ S3 đơn gắn với khách đã đăng nhập | UC-06 |
| G-05 | LucVT | UC-09 | Thêm luồng phụ: chưa đăng nhập web thì qua UC-07 rồi quay lại; token gắn với đúng Telegram chat id đã yêu cầu | UC-09 |
| G-06 | LucVT | US-08, US-10 | Sản phẩm ẩn: `GET /api/products/{id}` trả 404; RAG chỉ gợi ý sản phẩm `active` | US-08, US-10 |
| G-07 | LucVT | UC-20 | Không xóa cứng, chỉ ẩn | UC-20 |
| G-08 | LucVT | UC-20, UC-21 | UC-20 quản lý giá niêm yết theo kênh, UC-21 quản lý khuyến mãi chồng lên; lịch sử giá tính từ S8 | UC-20, UC-21 |
| G-09 | LucVT | UC-05, US-09 | Store lọc và hiển thị theo `price_web`; so sánh giá các kênh chỉ có ở US-34 trên kênh sở hữu | UC-05 |
| G-10 | LucVT | UC-21 | "Voucher hết hạn thì tự tắt" là BR, không phải ngoại lệ; mốc giờ khuyến mãi lưu theo `Asia/Ho_Chi_Minh` | UC-21 |
| G-11 | LucVT | US-04 | AC thêm "file CSV import được, không trùng SKU" cho khớp mục 8 | US-04 |
| G-12 | ChienPX | UC-17, US-34 | Luật tất định cho "mua ngay hay đợi": chỉ khuyên đợi khi DB có khuyến mãi của đúng kênh, bắt đầu trong ≤ 7 ngày và rẻ hơn giá hiện tại ≥ 10%; LLM chỉ diễn giải | UC-17, US-34 |
| G-13 | ChienPX | UC-13, US-19 | Mọi kênh đã liên kết dùng chung hồ sơ. US-19 (S4) nghiệm thu WEB → TELEGRAM; ca WEB → SHOPEE nghiệm thu ở US-32 (S7). Hồ sơ khai qua chat chỉ lưu khi khách xác nhận | UC-13, US-32 |
| G-14 | ChienPX | UC-15, UC-16 | Sản phẩm trong routine và sản phẩm thay thế phải lấy từ catalog, đang bán (thay thế thì phải còn hàng) | UC-15, UC-16, US-31 |
| G-15 | ChienPX | US-04, UC-16, UC-17 | Kênh sở hữu (WEB, TELEGRAM, MESSENGER) dùng chung `price_web`, không thêm cột giá | US-04, UC-16, `data-model.md` |

## 10. Góp ý và thay đổi
SRS không cần ký duyệt (decisions #16). Ai thấy chỗ sai hoặc thiếu thì comment trên issue US-01 (#1) hoặc mở PR sửa thẳng. PO quyết định, ghi vào bảng phiên bản ở đầu file và đánh dấu *(sửa <ngày>)* ở chỗ đã sửa. Có vấn đề lớn thì cả team họp.

Đợt review S0 (03–05/11): LucVT góp ý phần dữ liệu và danh tính (PR #174), ChienPX góp ý phần AI (issue #1); PO đã trả lời ở mục 9.2. Phần kênh và adapter (T-01.2) không review riêng nữa vì backlog đã bỏ task review.
