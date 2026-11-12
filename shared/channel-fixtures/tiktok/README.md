# Fixture hợp đồng TikTok Shop

Hub (`backend/`) và simulator (`simulators/tiktok-shop/`) cùng test với các file JSON này: hub parse được và simulator sinh đúng dạng.
Nguồn và cách đối chiếu: [docs/channel-api/tiktok.md](../../../docs/channel-api/tiktok.md).

| File | Nguồn |
|---|---|
| `send-message.request.json` | Body trong curl mẫu của trang Send Message (docv2, lấy 11/11/2026) |
| `send-message.response.json`, `get-conversation-messages.response.json`, `get-conversations.response.json`, `create-conversation.response.json`, `get-authorized-shops.response.json`, `get-shop-webhooks.response.json` | Mục "Example" của trang API tương ứng (docv2, lấy 11/11/2026), chép nguyên văn |
| `webhook-new-message.json`, `token-get.response.json` | **Chưa đối chiếu**: thư mục docs không có trang Webhooks/NEW_MESSAGE và Get Access Token, dựng theo hiểu biết chung |
