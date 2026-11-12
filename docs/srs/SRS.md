# Đặc tả yêu cầu phần mềm (SRS): OmniAI

> Phiên bản **1.1** · 2026-11-11 · Tác giả: HungDQ (PO) · Task T-01.1, T-01.5 (US-01), T-48.1 (US-48)
> Góp ý: comment trên issue US-01 (#1) hoặc mở PR sửa thẳng. PO quyết định, không cần ký duyệt. Xem [mục 10](#10-góp-ý-và-thay-đổi).

| Phiên bản | Ngày | Người sửa | Nội dung |
|---|---|---|---|
| 0.1 | 2026-11-04 | HungDQ | Bản nháp đầu: chốt 34 US P0 và 21 UC P0 để cả team review |
| 0.1.1 | 2026-11-05 | LucVT | Review T-01.3: chốt Q-01, Q-02 (mục 9), điền ký duyệt (mục 10) |
| 1.0 | 2026-11-06 | HungDQ | T-01.5: trả lời 15 góp ý (mục 9.2), chốt Q-03 đến Q-08, bỏ ký duyệt (decisions #16). Bản chính thức |
| 1.0.1 | 2026-11-06 | HungDQ | Q-08: thầy chấp nhận simulator theo schema thật (decisions #17) |
| 1.1 | 2026-11-11 | HungDQ | T-48.1, sau buổi họp với thầy: kênh sàn 100% simulator, bỏ Telegram và Messenger (decisions #22); hướng hub omnichannel với dữ liệu 2 chiều, thêm trụ E và US-49 đến US-55 (decisions #23); S2 kéo về 10–16/11 (decisions #24); dữ liệu sản phẩm thật kèm ảnh (US-47) |

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này mô tả OmniAI cần làm gì và đạt tới mức nào để được coi là xong. Đây là căn cứ chung để cả team hiểu cùng một phạm vi, để PO nghiệm thu từng User Story cuối sprint, và để giảng viên đánh giá. Phần "làm như thế nào" nằm trong [docs/specs/](../specs/README.md).

### 1.2 Phạm vi
OmniAI là **hub omnichannel cho shop mỹ phẩm**, kèm trợ lý tư vấn theo nguyên tắc "một bộ não, nhiều luật kênh". Hub giữ dữ liệu gốc (sản phẩm, giá, tồn kho, khuyến mãi, khách, hội thoại, đơn) và **đẩy ra** các kênh; dữ liệu phân tán ở các kênh (hội thoại chăm sóc khách hàng, đơn hàng, trạng thái duyệt sản phẩm, đánh giá) được **gom về** hub (mục 2.2). Một AI Brain dùng chung trả lời người mua trên web của shop, Shopee và TikTok Shop. Mỗi kênh có bộ luật riêng, áp cho câu trả lời của bot và cho nội dung đẩy ra kênh. Khi khách đồng ý liên kết tài khoản, bot nhớ khách xuyên kênh. Chất lượng được đo bằng **bench**, và đây là đóng góp chính của dự án. *(sửa 11/11, decisions #22, #23)*

**Trong phạm vi:** 5 trụ (epic) A–E ở mục 2.3; shop demo, dữ liệu sản phẩm thật kèm ảnh (US-47); Shopee và TikTok Shop chạy bằng simulator theo schema thật; trang admin cho sản phẩm, khuyến mãi, hội thoại, đơn sàn, danh tính và báo cáo bench.

**Ngoài phạm vi:** bán hàng thật (thanh toán, giao hàng, hóa đơn); khách hàng thật; nối API thật của sàn; trả hàng/hoàn tiền, vận chuyển, đối soát tài chính, quảng cáo trên sàn; kênh Telegram và Messenger; app di động; ngôn ngữ khác tiếng Việt; tự huấn luyện mô hình ML. *(sửa 11/11: đồng bộ tồn kho với sàn chuyển vào phạm vi, làm qua simulator)*

### 1.3 Thuật ngữ
| Thuật ngữ | Nghĩa |
|---|---|
| Kênh sở hữu (owned) | Kênh shop tự quản: WEB. Được gửi link, được so sánh giá giữa các kênh *(sửa 11/11)* |
| Kênh sàn | SHOPEE, TIKTOK. Cấm điều hướng giao dịch ra ngoài sàn: không link, không SĐT, không nhắc kênh khác |
| Adapter | Thành phần chuyển tin của một kênh về định dạng chung `CanonicalMessage`, và gửi câu trả lời ngược lại kênh đó |
| Luật kênh, policy | Luật trong `shared/policies/*.yaml`, áp cho câu trả lời theo kênh: `block` (thay cả câu bằng câu mẫu), `redact` (ẩn đoạn vi phạm), `replace` (thay bằng cụm khác) |
| rawText / finalText | Câu AI Brain sinh ra / câu khách thực sự nhận sau khi áp policy |
| Handoff | Chuyển hội thoại cho nhân viên khi bot lỗi hoặc khách yêu cầu |
| Simulator theo schema thật | Giả lập sàn bằng đúng định dạng JSON trong tài liệu của sàn (webhook, API, chữ ký). Từ 11/11 đây là cách duy nhất dự án nối với Shopee, TikTok Shop (decisions #12, #22) |
| Hub | Backend OmniAI: nơi giữ dữ liệu gốc và là điểm trung chuyển giữa các kênh |
| Dữ liệu đi / về | Đi: hub đẩy ra kênh (sản phẩm, giá, tồn kho, khuyến mãi, trạng thái xử lý đơn). Về: kênh gửi về hub (hội thoại CSKH, đơn hàng, trạng thái duyệt sản phẩm, đánh giá) |
| Bench | Bộ ca kiểm thử tự động đo chất lượng bot theo kênh (`bench/`) |
| Customer / danh tính kênh | Khách hợp nhất / một tài khoản của khách trên một kênh (`channel_identity`) |
| INCI, PAO | Tên thành phần theo chuẩn quốc tế / số tháng dùng được sau khi mở nắp |
| P0 / P1 / P2 | Bắt buộc / nên có / cắt được. MoSCoW: Must / Should / Could have |

### 1.4 Tài liệu tham chiếu
- [docs/de-xuat-OmniAI.md](../de-xuat-OmniAI.md): đề xuất, phản biện, kiến trúc, cách bench.
- [README.md](../../README.md): bài toán, cách chạy. [docs/specs/](../specs/README.md): hợp đồng API, dữ liệu, luật kênh, trạng thái.
- `tools/tracking/backlog.py` và `docs/tracking/OmniAI_Project_Tracking.xlsx`: backlog, sprint, rủi ro. GitHub Issues: mỗi US là một issue, mỗi task là một sub-issue.
- [docs/specs/decisions.md](../specs/decisions.md): #12 (kênh sàn dùng simulator), #13 (chỉ dùng LLM miễn phí), #22 đến #24 (các quyết định ngày 11/11).
- [docs/bench/huong-dan-benchmark.md](../bench/huong-dan-benchmark.md): phương pháp benchmark và tài liệu tham khảo.
- Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 (hiệu lực 01/01/2026), Nghị định 356/2025.

## 2. Mô tả tổng quan

### 2.1 Bài toán
Shop mỹ phẩm bán cùng lúc trên Shopee, TikTok Shop và web riêng, nhưng mỗi kênh chỉ nhớ phần của mình. Khách phải khai lại loại da và dị ứng ở từng kênh. Không ai cảnh báo khi đồ mua ở các kênh khác nhau xung đột hoạt chất, và tư vấn giữa các kênh không nhất quán. Ghép một bộ não chung thì AI lại không biết luật riêng của từng kênh: trên Shopee, nhắc tới web hay gửi số điện thoại có thể khiến shop bị khóa. Nhận ra 3 tài khoản là cùng một người thì cần sự đồng ý của khách (Luật 91/2025). Hiện cũng hầu như không ai đo chất lượng chatbot theo từng kênh. Sản phẩm, giá, tồn kho, khuyến mãi phải nhập lại ở từng sàn; hội thoại, đơn hàng, đánh giá thì nằm rải rác mỗi sàn một nơi. *(thêm 11/11)*

### 2.2 Góc nhìn sản phẩm
```
Kênh (web WS, Shopee/TikTok Shop qua simulator theo schema thật, simulator chung)
  -> Adapter -> CanonicalMessage -> MessageRouter (chống trùng tin)
  -> AI Brain (FastAPI: agent, RAG, luật thành phần, tool gọi backend)
  -> PolicyEngine (luật của kênh) -> BotReply {rawText, finalText, violations, blocked, handoff} -> kênh
Bench gửi ca kiểm thử qua simulator hoặc WebSocket, rồi chấm lại finalText bằng cùng bộ YAML.

Dữ liệu 2 chiều giữa hub và kênh sàn (trụ E, từ S3):
  ĐI  hub -> Shopee, TikTok Shop : sản phẩm (tên, mô tả, ảnh, thành phần), giá theo kênh, tồn kho,
                                   khuyến mãi, trạng thái xử lý đơn (xác nhận, hủy)
  VỀ  Shopee, TikTok Shop -> hub : hội thoại chăm sóc khách hàng, đơn hàng,
                                   trạng thái duyệt/vi phạm sản phẩm, đánh giá sản phẩm
```
Backend Spring Boot (modular monolith), AI Brain Python, web React, PostgreSQL + pgvector. Chi tiết ở [overview.md](../specs/overview.md).

Danh sách dữ liệu đi/về do team chốt ngày 11/11 (decisions #23) và báo cáo thầy ở buổi họp sau. Hub là nguồn dữ liệu gốc. Đơn từ sàn về thì hub trừ tồn rồi đẩy tồn mới ra các kênh. Nội dung đẩy ra sàn cũng phải qua luật của kênh (US-54).

### 2.3 Chức năng chính (5 trụ)
| Trụ | Nội dung | US P0 |
|---|---|---|
| COMMON: Nền tảng & vận hành | Môi trường, chat web, store demo, lưu và xem hội thoại, handoff, kiểm thử E2E, demo, chuyển hướng 11/11 | 11 |
| A: Adapter & luật kênh + Bench | Simulator Shopee/TikTok Shop theo schema thật, policy engine, độ bền adapter, bench và metric | 9 |
| B: Chuyên gia mỹ phẩm | Catalog có INCI, dữ liệu sản phẩm thật kèm ảnh, RAG gợi ý, giá/tồn kho từ DB, luật thành phần, routine an toàn | 7 |
| C: Danh tính & bộ nhớ xuyên kênh | OTP, mã OMNI, hồ sơ da, lịch sử đơn, quyền dữ liệu cá nhân | 5 |
| D: Săn sale & chọn kênh | Khuyến mãi theo kênh, cố vấn mua ngay hay đợi | 2 |
| E: Đồng bộ dữ liệu hub ↔ kênh *(thêm 11/11)* | Đẩy sản phẩm, giá, tồn kho, khuyến mãi, trạng thái đơn ra sàn; nhận đơn hàng, trạng thái duyệt sản phẩm, đánh giá về hub | 4 |

### 2.4 Nhóm người dùng
| Actor | Mô tả | Dùng qua |
|---|---|---|
| Người mua | Hỏi tư vấn, xem sản phẩm, đặt hàng demo, liên kết tài khoản, quản lý dữ liệu của mình | Web, Shopee, TikTok Shop |
| Admin shop | Quản lý sản phẩm, khuyến mãi; theo dõi đồng bộ ra sàn; xác nhận, hủy đơn sàn; xem hội thoại; trả lời khi handoff; duyệt gộp/tách danh tính | Trang admin trên web |
| Lead / PO | Thử luật kênh, chạy bench, xem báo cáo, nghiệm thu | `/admin/policy`, `bench/`, trang báo cáo bench |
| Hệ thống | Tác vụ định kỳ (nhắc mua lại, P2) | |
| Kênh ngoài | Simulator Shopee và TikTok Shop theo schema của Shopee Open Platform, TikTok Shop API (gửi webhook về hub, nhận dữ liệu từ hub) | |

### 2.5 Môi trường vận hành
Máy dev Windows chạy bằng `tools/systembat/*.bat`. Cần JDK 21, Maven 3.9+, Python 3.11+, Node 20.19+, Docker Desktop. Hạ tầng (Postgres 16 + pgvector, Redis, RabbitMQ) chạy bằng Docker Compose. Cổng: web 5173, backend 8080, AI Brain 8000.

### 2.6 Ràng buộc
| # | Ràng buộc | Nguồn |
|---|---|---|
| C-01 | Dữ liệu cá nhân: phải có consent trước khi liên kết; khách được xem, xuất, xóa dữ liệu; không bao giờ tự gộp danh tính | Luật 91/2025/QH15, decisions #9 |
| C-02 | Trên kênh sàn: không link ngoài, không SĐT, không nhắc kênh khác. YAML luật sàn hiện là bản nháp, đối chiếu quy định ở US-20 | Quy định người bán Shopee/TikTok |
| C-03 | Không quảng cáo mỹ phẩm như thuốc (điều trị, chữa, đặc trị); không chẩn đoán bệnh | Luật quảng cáo mỹ phẩm, `common.yaml` |
| C-04 | Shop là bản demo: không thanh toán, không giao hàng; banner demo luôn hiển thị. Dữ liệu sản phẩm chuyển sang dữ liệu thật kèm ảnh, có ghi nguồn và điều kiện sử dụng (US-47) *(sửa 11/11: trước là thương hiệu hư cấu)* | Đề xuất, mục 1; decisions #22 |
| C-05 | Chỉ dùng LLM miễn phí (free tier hoặc model mở chạy local); chỉ gửi dữ liệu tổng hợp | decisions #13 |
| C-06 | Shopee và TikTok Shop chỉ chạy bằng simulator theo schema thật, không nối API thật của sàn *(sửa 11/11)* | decisions #12, #22 |
| C-07 | Mọi con số (giá, tồn kho, khuyến mãi) phải lấy từ DB qua tool, LLM không tự nói số | Đề xuất, mục 1 |
| C-08 | Luật an toàn mỹ phẩm (xung đột, mang thai, dị ứng) là code tất định có test; LLM chỉ diễn giải | decisions, `ai-brain.md` |
| C-09 | 4 người, 13 sprint (03/11/2026 – 25/01/2027) và 1 tuần đệm tới 01/02/2027; từ S2 mỗi sprint kéo lên 1 tuần *(sửa 11/11)* | `backlog.py`, decisions #24 |
| C-10 | Hub là nguồn dữ liệu gốc của sản phẩm, giá, tồn kho, khuyến mãi; sàn chỉ nhận. Mọi webhook từ sàn phải kiểm chữ ký và chống trùng *(thêm 11/11)* | decisions #23 |

### 2.7 Giả định và phụ thuộc
- A-01: Demo kênh sàn là giả lập theo schema thật; báo cáo cuối phải nêu rõ giới hạn này (decisions #22). *(sửa 11/11)*
- A-02: Có ít nhất một model miễn phí đạt tiêu chí trong `docs/llm-eval/README.md` (US-05).
- A-03: Không có khách thật. Dùng persona tổng hợp và team đóng vai; dataset bench tự xây. Báo cáo cuối phải nêu rõ giới hạn này.
- A-04: (bỏ 11/11 cùng kênh Telegram.)
- A-05: Catalog 80 SKU đợt 1 dùng thương hiệu hư cấu (tham khảo Open Beauty Facts). US-47 tìm dữ liệu sản phẩm thật kèm ảnh để thay; ảnh và nhãn hiệu của bên thứ ba chỉ dùng cho mục đích học tập. *(sửa 11/11)*
- A-06: Luật thành phần cần người có chuyên môn (dược sĩ hoặc bác sĩ da liễu) review trước khi demo.
- A-07: Schema simulator lấy từ tài liệu công khai của Shopee Open Platform và TikTok Shop Partner Center tại thời điểm đọc (T-21.4, T-25.3); sàn đổi API sau đó thì simulator không tự cập nhật. *(thêm 11/11)*

## 3. Yêu cầu chức năng (User Story)
Mỗi yêu cầu là một User Story. Mục này **chốt 38 US mức P0** (ngày 11/11 hủy US-17, US-18; thêm US-47 đến US-52): Acceptance Criteria (AC) là căn cứ PO nghiệm thu cuối sprint. Dòng đầu mỗi US ghi: ưu tiên · sprint · owner · kênh · Use Case liên quan. Những chỗ đã sửa được đánh dấu *(sửa 04/11)* *(sửa 06/11, G-xx)* với G-xx là góp ý ở mục 9.2, hoặc *(sửa 11/11)* theo decisions #22 đến #24. `backlog.py` đã được cập nhật theo.

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

**US-11 · Lưu hội thoại mọi kênh**: P0 · S2 · TuTM · All · UC-01, UC-03, UC-04, UC-22, UC-27
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
- Given kịch bản 7 bước (web → Shopee → TikTok Shop → cảnh báo retinol → bench → fault → xóa dữ liệu) · When chạy thử · Then 7/7 bước pass; 0 bug Critical/Major còn mở. *(sửa 11/11)*

**US-46 · Tài liệu & demo cuối**: P0 · S12 · HungDQ · All
Là giảng viên/hội đồng, tôi muốn xem demo và tài liệu đầy đủ của dự án.
- Given báo cáo cuối, user guide, slide · When demo · Then chạy kịch bản trong ≤ 15 phút, trả lời được câu hỏi về bench.

**US-48 · Chuyển hướng: hub omnichannel, kênh sàn 100% simulator**: P0 · S2 · HungDQ · All *(thêm 11/11)*
Là PO, tôi muốn tài liệu, backlog và code phản ánh hướng đã chốt sau buổi họp với thầy để cả team làm cùng một phạm vi.
- Given các quyết định ngày 11/11 (decisions #22, #23, #24) · When đọc SRS, backlog, specs và code · Then không còn Telegram, Messenger; kênh sàn chỉ còn simulator; có danh sách dữ liệu đi và về kèm thiết kế luồng.

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

**US-17 · Kênh Telegram**: **hủy 11/11** (decisions #22, bỏ Telegram khỏi dự án).

**US-20 · Luật đủ 3 kênh**: P0 · S4 · HungDQ · All · UC-24
Là chủ shop, tôi muốn mỗi kênh có bộ luật riêng đã đối chiếu với quy định hiện hành.
- Given 3 file YAML WEB, SHOPEE, TIKTOK + common · When chạy test · Then mỗi luật có ≥ 2 test (khớp và không khớp nhầm). *(sửa 11/11)*

**US-21 · Kênh Shopee (simulator theo schema thật)**: P0 · S2 · ChienPX · SHOPEE · UC-03
Là người mua trên Shopee, tôi muốn được tư vấn ngay trong chat Shopee.
- Given simulator Shopee theo schema thật (webhook và API gửi tin chép từ tài liệu Shopee, có kiểm chữ ký) · When buyer gửi tin · Then nhận phản hồi đã qua luật Shopee, không vượt độ dài tối đa. *(sửa 11/11: kéo từ S5 về S2, giao ChienPX, decisions #22)*

**US-24 · Bẫy policy + LLM-as-judge**: P0 · S5 · HungDQ · SHOPEE, TIKTOK · UC-25
Là Lead, tôi muốn đo được bot có bị dụ vi phạm luật kênh không.
- Given 20 ca bẫy (hỏi web rẻ hơn, xin Zalo, injection) · When chạy bench · Then 0 ca lọt tới khách; có điểm judge theo rubric.

**US-25 · Kênh TikTok Shop (simulator theo schema thật)**: P0 · S2 · TuTM · TIKTOK · UC-04
Là người mua trên TikTok Shop, tôi muốn được tư vấn ngay trong chat TikTok Shop.
- Given simulator TikTok Shop theo schema thật (webhook và API gửi tin chép từ tài liệu TikTok Shop, có kiểm chữ ký) · When buyer gửi tin · Then nhận phản hồi đã qua luật TikTok. *(sửa 11/11: kéo từ S6 về S2, decisions #22)*

**US-29 · Độ bền adapter (fault injection)**: P0 · S7 · TuTM · All · UC-03, UC-04
Là chủ shop, tôi muốn bot không trả lời trùng hay mất tin khi kênh gửi webhook lỗi.
- Given bật fault injection (trùng, trễ, sai thứ tự) · When gửi 1 tin 3 lần · Then khách nhận đúng 1 phản hồi; tin trễ vẫn được xử lý.

**US-32 · Metric bộ nhớ & xung đột**: P0 · S7 · HungDQ · All · UC-25
Là Lead, tôi muốn đo được bot nhớ đúng khi đổi kênh và bắt đúng xung đột hoạt chất.
- Given dataset nhiều lượt, đổi kênh giữa chừng (có ca khai hồ sơ trên WEB rồi hỏi trên SHOPEE đã liên kết) · When chạy bench · Then báo cáo memory recall và conflict recall. *(sửa 06/11, G-13)*

### 3.3 Trụ B: Chuyên gia mỹ phẩm

**US-04 · Catalog đợt 1 (80 SKU)**: P0 · S0 · LucVT · All · UC-05, UC-20
Là người mua, tôi muốn shop có danh mục sản phẩm đủ thông tin thành phần để được tư vấn đúng.
- Given file catalog · When kiểm tra · Then có ≥ 80 SKU với tên, giá theo 3 kênh, INCI, loại da, dung tích, PAO; thương hiệu hư cấu; file CSV import được, không trùng SKU. Kênh sở hữu (WEB) dùng giá web. *(sửa 06/11, G-11, G-15; sửa 11/11)*

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

**US-47 · Dữ liệu sản phẩm thật kèm ảnh**: P0 · S2 · LucVT · All · UC-05, UC-20 *(thêm 11/11)*
Là người mua, tôi muốn thấy sản phẩm thật có ảnh để việc tư vấn và demo giống thực tế.
- Given nguồn dữ liệu sản phẩm thật · When kiểm tra bộ dữ liệu tìm được · Then mỗi sản phẩm có tên, thương hiệu, giá, thành phần và ít nhất 1 ảnh mở được; ghi rõ nguồn và điều kiện sử dụng; nơi lưu ảnh (Supabase Storage hoặc local) được PO chốt và ghi vào decisions (Q-10).

### 3.4 Trụ C: Danh tính & bộ nhớ xuyên kênh

**US-13 · Tài khoản web + OTP**: P0 · S3 · LucVT · WEB · UC-07
Là người mua, tôi muốn đăng ký/đăng nhập web bằng email + OTP để hệ thống nhớ tôi.
- Given nhập email · When nhập OTP 6 số trong 5 phút · Then đăng nhập thành công; sai 5 lần khóa 15 phút; gửi lại OTP cách nhau ≥ 60 s, tối đa 5 lần/giờ. *(sửa 06/11, G-02; OTP gửi qua email theo Q-02)*

**US-18 · Liên kết Telegram bằng deep-link**: **hủy 11/11** (decisions #22). Trang xác nhận liên kết và consent chuyển sang US-22 (T-22.4).

**US-19 · Bộ nhớ hồ sơ da**: P0 · S4 · ChienPX · WEB, SHOPEE · UC-13
Là người mua, tôi muốn bot nhớ loại da và dị ứng tôi đã khai, kể cả khi tôi đổi kênh.
- Given khai "da dầu, dị ứng hương liệu" trên web · When hỏi trên Shopee (simulator) đã liên kết bằng mã OMNI · Then bot dùng đúng hồ sơ; 10 ca bench memory recall ≥ 90%. *(sửa 11/11)*

**US-22 · Mã liên kết OMNI-xxxx**: P0 · S4 · LucVT · SHOPEE, TIKTOK · UC-08
Là người mua, tôi muốn liên kết tài khoản Shopee với tài khoản web mà không cần bot gửi link.
- Given lấy mã `OMNI-XXXXXX` (6 ký tự) trên web · When gõ mã vào chat Shopee trong 15 phút, rồi xác nhận và tích consent trên web · Then 2 danh tính được liên kết; mã chỉ dùng 1 lần; một danh tính kênh gõ sai 5 lần trong 1 giờ thì bị khóa nhập mã. *(sửa 06/11, G-01; sửa 11/11: kéo từ S5 về S4 vì US-19 cần liên kết WEB ↔ Shopee, nhận thêm trang xác nhận và consent từ US-18)*

**US-23 · Lịch sử đơn mọi kênh**: P0 · S5 · ChienPX · All · UC-01, UC-14
Là người mua, tôi muốn bot biết tôi đã mua gì ở mọi kênh đã liên kết để tư vấn phù hợp.
- Given khách có đơn ở web và Shopee đã liên kết · When hỏi "mình đã mua gì" · Then bot liệt kê đúng; không lộ đơn của kênh chưa liên kết.

**US-30 · Quyền dữ liệu cá nhân**: P0 · S7 · LucVT · WEB · UC-12
Là người mua, tôi muốn xem, tải về, xóa dữ liệu và tách liên kết kênh của mình.
- Given đã đăng nhập · When yêu cầu xóa · Then hồ sơ, hồ sơ da, hội thoại và vector bộ nhớ bị xóa; đơn hàng được ẩn danh; liên kết kênh bị gỡ; log consent chỉ giữ id ẩn danh, thời điểm, loại consent. *(sửa 06/11, G-03)*

### 3.5 Trụ D: Săn sale & chọn kênh

**US-33 · Khuyến mãi theo kênh**: P0 · S5 · LucVT · All · UC-21
Là admin shop, tôi muốn nhập voucher, flash sale, giá theo từng kênh và xem lịch sử giá.
- Given nhập voucher Shopee 12.12 · When gọi API khuyến mãi của kênh SHOPEE · Then trả đúng voucher đó, hết hạn thì không trả; lịch sử giá lưu mỗi lần đổi. *(sửa 11/11: kéo từ S8 về S5 vì US-52 cần dữ liệu khuyến mãi; phần "bot dùng đúng voucher" nghiệm thu ở US-34)*

**US-34 · Cố vấn săn sale**: P0 · S8 · ChienPX · All · UC-17
Là người mua, tôi muốn biết nên mua ngay hay đợi sale và (trên kênh của shop) kênh nào lợi nhất.
- Given dữ liệu khuyến mãi · When hỏi trên WEB · Then so sánh đúng giá các kênh; hỏi trên SHOPEE thì chỉ nói voucher/giờ sale Shopee; số liệu khớp DB 100%; chỉ khuyên "đợi" khi có khuyến mãi của đúng kênh bắt đầu trong ≤ 7 ngày và rẻ hơn ≥ 10% (luật ở UC-17). *(sửa 06/11, G-12)*

### 3.6 Trụ E: Đồng bộ dữ liệu hub ↔ kênh *(thêm 11/11, decisions #23)*

**US-49 · Đẩy sản phẩm từ hub ra Shopee**: P0 · S3 · TuTM · SHOPEE · UC-26
Là admin shop, tôi muốn sửa sản phẩm một lần trên hub và thấy nó cập nhật trên Shopee để không phải nhập lại ở từng kênh.
- Given sản phẩm đang bán trên hub · When admin tạo hoặc sửa tên, mô tả, ảnh, thành phần · Then simulator Shopee nhận đúng payload theo schema thật trong ≤ 10 s; hub lưu trạng thái đồng bộ theo từng kênh; gửi lỗi thì thử lại, quá 3 lần thì hiện lỗi ở admin.

**US-50 · Đẩy sản phẩm ra TikTok Shop; giá và tồn kho ra cả hai sàn**: P0 · S4 · TuTM · SHOPEE, TIKTOK · UC-26
Là admin shop, tôi muốn giá và tồn kho trên Shopee, TikTok Shop luôn khớp với hub để không bán sai giá hay bán quá số hàng có.
- Given sản phẩm đã đồng bộ ra sàn · When admin đổi giá theo kênh hoặc tồn kho trên hub · Then simulator Shopee và TikTok Shop nhận giá của đúng kênh và tồn kho mới trong ≤ 10 s; sản phẩm cũng xuất hiện trên simulator TikTok Shop.

**US-51 · Đơn hàng từ sàn về hub, trừ tồn kho**: P0 · S5 · TuTM · SHOPEE, TIKTOK · UC-27
Là chủ shop, tôi muốn đơn trên Shopee, TikTok Shop tự về hub và tồn kho được trừ ở mọi kênh để không phải đối chiếu tay.
- Given simulator phát 1 đơn mới · When hub nhận webhook · Then đơn được lưu kèm kênh và mã đơn sàn; gửi lại cùng đơn không tạo đơn trùng; tồn kho hub giảm đúng số lượng và tồn mới được đẩy ra các kênh.

**US-52 · Đẩy khuyến mãi ra kênh sàn**: P0 · S6 · TuTM · SHOPEE, TIKTOK · UC-26
Là admin shop, tôi muốn nhập voucher, flash sale một lần trên hub và thấy chúng xuất hiện trên đúng sàn.
- Given voucher hoặc flash sale của kênh SHOPEE trên hub · When admin tạo, sửa hoặc khuyến mãi hết hạn · Then simulator Shopee nhận đúng theo schema thật; simulator TikTok Shop không nhận khuyến mãi của Shopee; hết hạn thì trên sàn cũng tắt.

### 3.7 Ngoài P0 (chưa chốt trong đợt này)
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
| US-53 | Cập nhật trạng thái xử lý đơn ra sàn *(thêm 11/11)* | P1 | S6 | LucVT |
| US-54 | Trạng thái duyệt, vi phạm sản phẩm từ sàn về hub; kiểm luật kênh trước khi đẩy *(thêm 11/11)* | P1 | S7 | HungDQ |
| US-55 | Đánh giá sản phẩm từ sàn về hub *(thêm 11/11)* | P2 | S8 | LucVT |

## 4. Use Case

### 4.1 Danh sách
| UC | Tên | Actor | US | Ưu tiên |
|---|---|---|---|---|
| UC-01 | Chat tư vấn trên web | Người mua | US-06, 10, 11, 23 | P0 |
| UC-02 | Chat tư vấn trên Telegram (hủy 11/11) | | | |
| UC-03 | Chat tư vấn trên Shopee | Người mua | US-21, 29 | P0 |
| UC-04 | Chat tư vấn trên TikTok Shop | Người mua | US-25, 29 | P0 |
| UC-05 | Xem & lọc sản phẩm | Người mua | US-09, 04 | P0 |
| UC-06 | Đặt hàng ảo | Người mua | US-09 | P0 |
| UC-07 | Đăng ký/đăng nhập OTP | Người mua | US-13 | P0 |
| UC-08 | Liên kết kênh sàn bằng mã OMNI | Người mua | US-22 | P0 |
| UC-09 | Liên kết Telegram bằng deep-link (hủy 11/11) | | | |
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
| UC-26 | Đồng bộ dữ liệu từ hub ra kênh sàn *(thêm 11/11)* | Admin, Hệ thống | US-49, 50, 52, 53 | P0 |
| UC-27 | Nhận dữ liệu từ kênh sàn về hub *(thêm 11/11)* | Hệ thống | US-11, 51, 54, 55 | P0 |

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

**UC-02 · Chat tư vấn trên Telegram**: hủy 11/11 (decisions #22).

**UC-03 · Chat tư vấn trên Shopee** (Người mua). Tiền điều kiện: simulator Shopee theo schema thật đang chạy (decisions #22) → Hậu điều kiện: hội thoại được lưu. *(sửa 11/11)*
1. Buyer nhắn trong chat Shopee.
2. Webhook từ simulator tới adapter Shopee; adapter kiểm chữ ký, chuyển tin thành `CanonicalMessage`.
3. AI Brain trả lời với system prompt của kênh sàn.
4. Policy áp luật SHOPEE: ẩn link và SĐT, chặn câu nhắc kênh khác, cắt theo độ dài tối đa.
5. Adapter gửi finalText qua API gửi tin của simulator Shopee.
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
- BR: không gọi cổng thanh toán, không giao hàng thật (C-04); đơn DEMO trên web không trừ tồn kho, còn đơn từ sàn về thì có trừ (UC-27). *(sửa 06/11, G-04; sửa 11/11)*

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

**UC-09 · Liên kết Telegram bằng deep-link**: hủy 11/11 (decisions #22). Trang xác nhận và consent dùng lại cho UC-08.

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
- BR: thông tin sức khỏe là dữ liệu nhạy cảm, cần consent. Hồ sơ khai qua chat chỉ được lưu sau khi khách xác nhận ở bước 2; chưa xác nhận thì chỉ dùng trong phiên đó. Kênh sàn đã liên kết cũng dùng chung hồ sơ, nghiệm thu ở US-19 (WEB → SHOPEE) và US-32. *(sửa 06/11, G-13; sửa 11/11)*

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
- BR: số liệu chỉ lấy từ DB, giá theo đúng kênh (C-07); kênh sở hữu (WEB) dùng giá web; sản phẩm thay thế phải có trong catalog, đang bán và còn hàng. *(sửa 06/11, G-14, G-15)*

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
- BR: lưu lịch sử giá mỗi lần đổi, tính từ S5 (US-33); voucher hết hạn thì tự tắt; mọi mốc giờ khuyến mãi lưu theo `Asia/Ho_Chi_Minh`. *(sửa 06/11, G-08, G-10)*

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

**UC-26 · Đồng bộ dữ liệu từ hub ra kênh sàn** (Admin, Hệ thống). Tiền điều kiện: simulator sàn đang chạy → Hậu điều kiện: dữ liệu trên sàn khớp hub, trạng thái đồng bộ theo kênh được lưu. *(thêm 11/11)*
1. Admin tạo hoặc sửa sản phẩm, giá theo kênh, tồn kho, khuyến mãi; hoặc xác nhận, hủy một đơn sàn.
2. Hub ghi thay đổi vào hàng đợi đồng bộ.
3. Connector của từng kênh chuyển dữ liệu sang payload theo schema của sàn, ký và gửi tới simulator.
4. Simulator kiểm chữ ký, lưu và trả kết quả; hub cập nhật trạng thái đồng bộ theo kênh.
5. Admin thấy trạng thái Đã đồng bộ / Đang chờ / Lỗi trên màn admin.
- Luồng phụ: giá theo kênh chỉ gửi sang đúng kênh đó; khuyến mãi của Shopee không gửi sang TikTok Shop.
- Ngoại lệ: gửi lỗi thì thử lại, quá 3 lần thì hiện lỗi ở admin. Nội dung vi phạm luật kênh thì hub cảnh báo trước khi đẩy (US-54).
- BR: hub là nguồn dữ liệu gốc (C-10); một thay đổi không được gửi 2 lần.

**UC-27 · Nhận dữ liệu từ kênh sàn về hub** (Hệ thống). Tiền điều kiện: simulator sàn đang chạy → Hậu điều kiện: dữ liệu được lưu kèm kênh nguồn. *(thêm 11/11)*
1. Simulator phát webhook: tin nhắn của buyer, đơn mới, kết quả duyệt sản phẩm hoặc đánh giá.
2. Hub kiểm chữ ký và chống trùng.
3. Hub lưu dữ liệu kèm kênh nguồn: tin nhắn vào hội thoại (UC-03, UC-04), đơn vào đơn hàng, kết quả duyệt vào sản phẩm, đánh giá theo sản phẩm.
4. Với đơn mới: hub trừ tồn kho rồi đẩy tồn mới ra các kênh (UC-26).
- Ngoại lệ: sai chữ ký thì từ chối; gửi trùng thì không lưu lần 2.
- BR: mọi webhook đều kiểm chữ ký và chống trùng (C-10); đơn của kênh chưa liên kết không lộ cho bot (US-23).

## 5. Yêu cầu giao diện ngoài
- **Người dùng:** store `/` (danh sách, lọc, chi tiết, đặt hàng demo, banner demo); chat widget trên mọi trang của store; trang admin (`/admin/policy`, `/admin/products` đã có; khuyến mãi, inbox, đơn sàn, trạng thái đồng bộ, danh tính, báo cáo bench sẽ thêm theo sprint).
- **Kênh:** WEB qua WebSocket `/ws/chat`; SHOPEE, TIKTOK qua simulator theo schema thật (S2), gồm webhook về hub và API nhận dữ liệu từ hub (trụ E); simulator chung `POST /api/simulator/{channel}/messages` cho bench và trang thử luật.
- **Phần mềm:** backend gọi AI Brain qua `POST /chat` (JSON snake_case, timeout 10 s, lỗi thì handoff); AI Brain gọi LLM miễn phí qua interface `LLMProvider`; PostgreSQL 16 + pgvector; Redis và RabbitMQ dùng từ S7.
- Hợp đồng chi tiết: [api-contracts.md](../specs/api-contracts.md).

## 6. Yêu cầu phi chức năng
| ID | Nhóm | Yêu cầu | Đo bằng | US |
|---|---|---|---|---|
| NFR-01 | Hiệu năng | Bot trả lời một tin với p95 < 5 s | Latency p95 trong báo cáo bench | US-06 |
| NFR-02 | Hiệu năng dữ liệu | API p95 < 300 ms với 300 SKU và 10.000 tin | Load test | US-43 (P1) |
| NFR-03 | Tuân thủ kênh | 0 ca vi phạm luật lọt tới khách trên kênh sàn | Bench: `leaked = 0` | US-07, 20, 24 |
| NFR-04 | Đúng số liệu | Tỉ lệ bịa giá, tồn kho = 0 | 20 ca bench giá/tồn kho | US-15 |
| NFR-05 | Độ tin cậy kênh | Không trả lời trùng khi kênh gửi lại; ≥ 99% thành công khi bật fault injection | Bench adapter | US-21, 25, 29 |
| NFR-06 | Khả dụng | AI Brain lỗi hoặc quá 10 s thì trả câu chờ và handoff; tin vẫn được lưu | Test router | US-11, 35 |
| NFR-07 | Dữ liệu cá nhân | Consent trước khi liên kết; xem, xuất, xóa được; không tự gộp; hồ sơ sức khỏe là dữ liệu nhạy cảm | Test API, review | US-22, 30 |
| NFR-08 | Xác thực | OTP 6 số hết hạn sau 5 phút, sai 5 lần khóa 15 phút, gửi lại cách nhau ≥ 60 s và ≤ 5 lần/giờ; mã OMNI 6 ký tự, 1 lần, 15 phút, sai 5 lần/giờ thì khóa *(sửa 06/11, G-01, G-02; sửa 11/11: bỏ deep-link)* | Test API | US-13, 22 |
| NFR-09 | An toàn nội dung | Không chẩn đoán, không claim y khoa; bắt được 100% xung đột và chống chỉ định trong bench | Bench xung đột, routine | US-27, 31 |
| NFR-10 | Bí mật | Không commit `.env`, API key; key đọc từ biến môi trường | Review PR | All |
| NFR-11 | Kiểm thử được | Mỗi luật kênh có ≥ 2 test (khớp đúng và không khớp nhầm), chạy giống nhau ở Java và Python | `PolicyEngineTest`, `test_policy_check.py` | US-07, 20 |
| NFR-12 | Bảo trì | `docs/specs` cập nhật trong cùng PR khi đổi hợp đồng; CI chạy build, test, bench | Review PR, CI | US-16 (P1) |
| NFR-13 | Triển khai | Chạy được trên Windows bằng `tools/systembat`; hạ tầng bằng Docker Compose | Setup trên 4 máy | US-02 |
| NFR-14 | Chi phí | Chi phí LLM 0đ | Báo cáo US-05 | US-05 |
| NFR-15 | Đồng bộ dữ liệu *(thêm 11/11)* | Thay đổi trên hub tới simulator sàn trong ≤ 10 s; không gửi trùng; webhook về hub sai chữ ký bị từ chối, gửi trùng không lưu 2 lần | Test đồng bộ, bench nội dung | US-49–52, 54 |

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
| Đồng bộ 2 chiều *(thêm 11/11)* | Tỉ lệ bản ghi trên sàn khớp hub; số webhook trùng bị lưu 2 lần; nội dung vi phạm lọt ra sàn | 100%; 0; 0 trên 10 ca nội dung | US-49–52 S3–S6, US-54 S7 (P1) |

Cách chạy và định dạng dataset: [bench.md](../specs/bench.md). Phương pháp và tài liệu tham khảo: [huong-dan-benchmark.md](../bench/huong-dan-benchmark.md).

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
| US-11 | S2 | TuTM | UC-01, 03, 04, 22, 27 | Test lưu tin khi AI Brain lỗi |
| US-12 | S2 | HungDQ | UC-25 | 50 ca, ≥ 5 nhóm, báo cáo theo kênh |
| US-21 | S2 | ChienPX | UC-03 | 5 ca bench kênh Shopee; payload đúng mẫu trong tài liệu Shopee |
| US-25 | S2 | TuTM | UC-04 | 5 ca bench kênh TikTok; payload đúng mẫu trong tài liệu TikTok Shop |
| US-47 | S2 | LucVT | UC-05, 20 | File dữ liệu thật kèm ảnh, có ghi nguồn; decisions ghi nơi lưu ảnh |
| US-48 | S2 | HungDQ | | SRS 1.1; không còn Telegram/Messenger ngoài regex luật sàn; có thiết kế luồng đi/về |
| US-13 | S3 | LucVT | UC-07 | Test OTP hết hạn, sai OTP, khóa, giới hạn gửi lại |
| US-14 | S3 | TuTM | UC-22 | Test 3 bộ lọc |
| US-15 | S3 | ChienPX | UC-16 | 20 ca bench, bịa số = 0 |
| US-49 | S3 | TuTM | UC-26 | Sửa sản phẩm thì simulator Shopee nhận đúng payload; test thử lại và chống gửi trùng |
| US-19 | S4 | ChienPX | UC-13 | 10 ca bench, memory recall ≥ 90% |
| US-20 | S4 | HungDQ | UC-24 | Mỗi luật ≥ 2 test, có ghi nguồn quy định |
| US-22 | S4 | LucVT | UC-08 | Test mã hết hạn, dùng lại, sai mã, khóa sau 5 lần sai; chưa xác nhận trên web thì chưa gắn |
| US-50 | S4 | TuTM | UC-26 | Đổi giá, tồn kho thì số trên 2 simulator khớp hub |
| US-23 | S5 | ChienPX | UC-01, 14 | 5 ca bench |
| US-24 | S5 | HungDQ | UC-25 | 20 ca bẫy, 0 lọt; judge ≥ 80% |
| US-33 | S5 | LucVT | UC-21 | Test voucher hết hạn; API trả đúng voucher theo kênh |
| US-51 | S5 | TuTM | UC-27 | Gửi 1 đơn 3 lần thì chỉ có 1 đơn; tồn giảm đúng trên hub và 2 simulator |
| US-27 | S6 | ChienPX | UC-14 | 30 ca bench, recall 100% |
| US-52 | S6 | TuTM | UC-26 | Voucher Shopee chỉ tới simulator Shopee; hết hạn thì tắt |
| US-29 | S7 | TuTM | UC-03, 04 | Bench adapter dưới lỗi ≥ 99% |
| US-30 | S7 | LucVT | UC-12 | Test từng quyền; xóa xác nhận 2 bước |
| US-31 | S7 | ChienPX | UC-15 | 10 hồ sơ mẫu; 100% ca chống chỉ định bị loại |
| US-32 | S7 | HungDQ | UC-25 | ≥ 15 ca nhiều lượt; báo cáo có 2 metric mới |
| US-34 | S8 | ChienPX | UC-17 | 10 ca bench sale theo kênh |
| US-35 | S8 | TuTM | UC-19 | Test khi AI Brain lỗi; tin admin vẫn qua policy |
| US-45 | S11 | HungDQ | mọi UC P0 | Kịch bản E2E 7/7 bước |
| US-46 | S12 | HungDQ | | Diễn tập demo ≤ 15 phút |

US-17 và US-18 đã hủy ngày 11/11 (decisions #22) nên không còn trong bảng.

## 9. Câu hỏi mở và góp ý review
Mọi câu hỏi và góp ý của đợt review S0 đã được PO chốt ngày 06/11/2026 (T-01.5). Câu hỏi mới thì thêm dòng từ Q-09 và ghi người chốt.

### 9.1 Câu hỏi mở
| # | Câu hỏi | Kết luận |
|---|---|---|
| Q-01 | US-08 (S1) cần "đăng nhập admin" nhưng chưa có US nào cho đăng nhập admin | **Chốt 05/11 (LucVT).** S1–S2 dùng một tài khoản admin cấu hình qua biến môi trường (Spring Security in-memory), làm đầy đủ ở S3 cùng US-13. API quản trị nằm dưới `/api/admin/**`, xác thực HTTP Basic bằng `ADMIN_USERNAME`/`ADMIN_PASSWORD` (thêm vào `.env.example` ở T-08.1). API đọc của store vẫn mở |
| Q-02 | OTP (US-13) gửi qua đâu? SMS thường tốn phí | **Chốt 05/11 (LucVT).** Chỉ gửi qua email (SMTP miễn phí), khi dev in mã ra log. SĐT chỉ là trường hồ sơ, không dùng để đăng nhập trong P0 |
| Q-03 | Messenger đã có trong `ChannelType` và YAML nhưng không có US | **Chốt 06/11 (PO).** Ngoài phạm vi P0; giữ enum và YAML cho simulator và bench; chỉ làm nếu Telegram bị chặn. **11/11: bỏ hẳn Messenger**, xóa enum và YAML (decisions #22) |
| Q-04 | Telegram webhook cần HTTPS công khai | **Chốt 06/11 (PO).** Khi dev dùng long polling hoặc tunnel (ngrok/cloudflared); demo dùng tunnel. **11/11: không còn áp dụng**, bỏ Telegram (decisions #22) |
| Q-05 | Model miễn phí có thể không đạt p95 < 5 s (US-06, US-17) | **Chốt 06/11 (PO, T-05.3).** Tạm dùng `gemini-3.6-flash`, duyệt có điều kiện (decisions #14). Giữ NFR-01. T-06.4 đo lại p95 trước Sprint Review S1 (16/11); vẫn > 5 s thì PO chọn nới NFR-01 hoặc đổi model chính sang model dự phòng |
| Q-06 | LLM-as-judge (US-24) cũng phải là model miễn phí | **Chốt 06/11 (PO).** Judge là model trong shortlist US-05 nhưng khác model chính đang dùng; hiện là `qwen/qwen3.8-27b` (Groq) |
| Q-07 | BR của UC-25 cần CI (US-16, P1, S3). Trước S3 thì sao? | **Chốt 06/11 (PO).** Người mở PR dán kết quả bench/test vào mô tả PR (đã ghi trong `docs/TEAM_GIT_WORKFLOW.md`) |
| Q-08 | Kênh sàn chạy bằng "simulator theo schema thật" có được thầy chấp nhận không | **Chốt 06/11: thầy chấp nhận.** Shopee và TikTok Shop dùng simulator theo schema thật (decisions #12, #17); adapter thật vẫn bật nếu sàn duyệt app kịp. **11/11: chốt 100% simulator**, không làm adapter thật (decisions #22) |
| Q-09 | Dữ liệu nào đi từ hub ra kênh, dữ liệu nào từ kênh về hub? | **Chốt 11/11 (PO; team tự quyết rồi báo cáo thầy).** Đi: sản phẩm, giá theo kênh, tồn kho, khuyến mãi, trạng thái xử lý đơn. Về: hội thoại CSKH, đơn hàng, trạng thái duyệt/vi phạm sản phẩm, đánh giá. Không làm: trả hàng/hoàn tiền, vận chuyển, đối soát tài chính, quảng cáo (decisions #23) |
| Q-10 | Ảnh sản phẩm lưu ở đâu: Supabase Storage hay local? | **Mở, đã có đề xuất.** LucVT so sánh ở T-47.2 ([docs/catalog/luu-anh-san-pham.md](../catalog/luu-anh-san-pham.md)), đề xuất local. PO chốt trong S2 |

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
| G-13 | ChienPX | UC-13, US-19 | Mọi kênh đã liên kết dùng chung hồ sơ. US-19 (S4) nghiệm thu WEB → TELEGRAM; ca WEB → SHOPEE nghiệm thu ở US-32 (S7). Hồ sơ khai qua chat chỉ lưu khi khách xác nhận. *(11/11: bỏ Telegram nên US-19 nghiệm thu WEB → SHOPEE)* | UC-13, US-32 |
| G-14 | ChienPX | UC-15, UC-16 | Sản phẩm trong routine và sản phẩm thay thế phải lấy từ catalog, đang bán (thay thế thì phải còn hàng) | UC-15, UC-16, US-31 |
| G-15 | ChienPX | US-04, UC-16, UC-17 | Kênh sở hữu (WEB, TELEGRAM, MESSENGER) dùng chung `price_web`, không thêm cột giá. *(11/11: kênh sở hữu chỉ còn WEB)* | US-04, UC-16, `data-model.md` |

## 10. Góp ý và thay đổi
SRS không cần ký duyệt (decisions #16). Ai thấy chỗ sai hoặc thiếu thì comment trên issue US-01 (#1) hoặc mở PR sửa thẳng. PO quyết định, ghi vào bảng phiên bản ở đầu file và đánh dấu *(sửa <ngày>)* ở chỗ đã sửa. Có vấn đề lớn thì cả team họp.

Đợt review S0 (03–05/11): LucVT góp ý phần dữ liệu và danh tính (PR #174), ChienPX góp ý phần AI (issue #1); PO đã trả lời ở mục 9.2. Phần kênh và adapter (T-01.2) không review riêng nữa vì backlog đã bỏ task review.

Thay đổi ngày 11/11 (sau buổi họp với thầy): xem dòng phiên bản 1.1 và decisions #22 đến #24. Thầy không chốt hộ các quyết định: team tự quyết, làm rồi báo cáo để thầy góp ý.
