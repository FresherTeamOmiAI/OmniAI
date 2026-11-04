# docs/specs: bản đồ hệ thống cho người và agent

> Cập nhật: 2026-11-04 · commit 3922a5c

Specs là bản tóm tắt **ngắn và luôn mới** của code: hợp đồng giữa các module, quy ước, file nằm ở đâu, cái gì đã làm và cái gì chưa. Mục đích là để agent (và người mới vào) **không phải đọc lại cả repo** mỗi lần bắt đầu task.

Specs không chép lại code. Khi spec và code mâu thuẫn thì **code là chuẩn**, và phải sửa spec ngay trong thay đổi đó.

## Đọc gì theo loại task

| Task | Đọc |
|---|---|
| Bắt đầu bất kỳ task nào | [overview.md](overview.md), [status.md](status.md) |
| Phạm vi, yêu cầu, User Story / Use Case P0, NFR | [../srs/SRS.md](../srs/SRS.md) |
| Thêm/sửa endpoint, DTO, WebSocket, gọi Brain | [api-contracts.md](api-contracts.md) + spec của module đó |
| Kênh / adapter / MessageRouter (TuTM) | [backend.md](backend.md), [api-contracts.md](api-contracts.md) |
| Luật kênh, PolicyEngine (HungDQ) | [policy.md](policy.md) |
| Catalog, customer, identity, order, promotion (LucVT) | [data-model.md](data-model.md), [backend.md](backend.md) |
| AI Brain, LLM, agent, luật thành phần (ChienPX) | [ai-brain.md](ai-brain.md), [api-contracts.md](api-contracts.md) |
| Bench, dataset, metric | [bench.md](bench.md), [policy.md](policy.md) |
| Giao diện web | [web.md](web.md), [api-contracts.md](api-contracts.md) |
| Migration / bảng DB | [data-model.md](data-model.md) |
| Tại sao lại chọn X | [decisions.md](decisions.md) |
| Cách chạy, cổng, biến môi trường | [overview.md](overview.md) (mục Chạy) |

## Quy tắc cập nhật (bắt buộc với cả người và agent)

Phải sửa spec **trong cùng commit/PR** khi thay đổi một trong các thứ sau:
- API: endpoint, request/response, WebSocket, hợp đồng backend ↔ ai-brain.
- Schema DB / migration, entity.
- Định dạng YAML luật, ngữ nghĩa PolicyEngine.
- Cấu trúc thư mục / package, module mới.
- Biến môi trường, cổng, cách chạy, script trong `tools/systembat/`.
- Trạng thái tính năng: stub → đã làm (sửa [status.md](status.md)).
- Một quyết định kỹ thuật mới (thêm 1 dòng vào [decisions.md](decisions.md)).

**Không cần** sửa spec khi refactor nội bộ không đổi hành vi, sửa bug không đổi hợp đồng, hoặc chỉ thêm test.

Mỗi lần sửa thì cập nhật dòng `> Cập nhật: <ngày> · commit <sha ngắn>` ở đầu file. Nếu chưa commit, ghi sha của HEAD hiện tại.

## Cách viết

- Mỗi file không quá ~150 dòng. Dài hơn thì tách file và thêm vào bảng trên.
- Viết hợp đồng, quy ước, đường dẫn file, trạng thái. **Không** dán code, chỉ dẫn tới `path/File.java`.
- Bảng và gạch đầu dòng tốt hơn đoạn văn dài.
- File mới dùng mẫu:

```markdown
# <Tên module>

> Cập nhật: YYYY-MM-DD · commit <sha> · Owner: <tên>
> Nguồn: `path/a`, `path/b`

## Trách nhiệm
## Hợp đồng / API
## File chính
## Quy ước & bẫy
```

## Công cụ đi kèm

- MCP `codebase-memory` (nếu máy có): dùng `search_graph`, `get_code_snippet`, `trace_path` để tra code cụ thể thay vì Grep/Read cả file.
- Stop hook `tools/agent/specs_guard.py` (khai báo trong `.claude/settings.json`) nhắc agent khi code đã đổi mà specs chưa đổi.
