# TikTok Shop: request/response DTO

> **File sinh tự động** bằng `tools/channel-api/extract_tiktok_dto.py`, đừng sửa tay.
> Nguồn, ngày lấy, tham số chung và envelope: xem [tiktok.md](tiktok.md).
> Không lặp lại tham số chung (`app_key`, `sign`, `timestamp`, `shop_cipher`, header `x-tts-access-token`)
> và envelope `{code, message, request_id, data}`. Cột Response chỉ liệt kê trường bên trong `data`.
> Tên trường dạng `a[].b`: `a` là mảng object, `b` là trường của mỗi phần tử.

## Authorization

### Get Authorized Shops

`GET /authorization/202309/shops` · scope: `seller.authorization.info, seller.fs.order&fufillment.management`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `shops` | []object |  | The list of shops that a seller has authorized for the app. |
| `shops[].id` | string | 7000714532876273420 | An internal identifier for the TikTok Shop. |
| `shops[].name` | string | Maomao beauty shop | The TikTok Shop name. |
| `shops[].region` | string | GB | The region of the shop. |
| `shops[].seller_type` | string | CROSS_BORDER | The type of seller. Giá trị: CROSS_BORDER, LOCAL |
| `shops[].cipher` | string | GCP_XF90igAAAABh00qsWgtvOiGFNqyubMt3 | An encrypted token used to securely identify a shop in API requests. |
| `shops[].code` | string | CNGBCBA4LLU8 | The TikTok Shop code, which is also displayed on Seller Center. |

**Mã lỗi:** `36009003` Internal error.

## Seller

### Get Active Shops

`GET /seller/202309/shops` · scope: `seller.shop.info`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `shops` | []object |  | The list of active shops that belong to the seller. |
| `shops[].id` | string | 36123502970007 | The ID of the shop. |
| `shops[].region` | string | GB | The region of the shop. |

## Event

### Get Shop Webhooks

`GET /event/202309/webhooks` · scope: `seller.authorization.info`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `webhooks` | []object |  | The list of enabled webhooks configured for the shop. |
| `webhooks[].event_type` | string | ORDER_STATUS_CHANGE | The topic of the webhook event. Giá trị: ORDER_STATUS_CHANGE, RECIPIENT_ADDRESS_UPDATE, PACKAGE_UPDATE, PRODUCT_STATUS_CHANGE, SELLER_DEAUTHORIZATION, UPCOMING_AUTHORIZATION_EXPIRATION, CANCELLATION_STATUS_CHANGE, RETURN_STATUS_CHANGE, NEW_CONVERSATION, NEW_MESSAGE, PRODUCT_INFORMATION_CHANGE, PRODUCT_CREATION … |
| `webhooks[].address` | string | https://partner.tiktokshop.com | The webhook URL used to receive the event data. |
| `webhooks[].create_time` | int | 1635338186000 | The time when the webhook was created, represented as a Unix timestamp in milliseconds. |
| `webhooks[].update_time` | int | 1635338186000 | The time when the webhook was last updated, represented as a Unix timestamp in milliseconds. |
| `total_count` | int | 1 | The total number of webhooks returned. |

### Update Shop Webhook

`PUT /event/202309/webhooks` · scope: `seller.authorization.info`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `address` | string | Y | The HTTP or HTTPS webhook URL used to receive the event data. |
| `event_type` | string | Y | The topic of the webhook event. Giá trị: ORDER_STATUS_CHANGE, RECIPIENT_ADDRESS_UPDATE, PACKAGE_UPDATE, PRODUCT_STATUS_CHANGE, SELLER_DEAUTHORIZATION, UPCOMING_AUTHORIZATION_EXPIRATION, CANCELLATION_STATUS_CHANGE, RETURN_STATUS_CHANGE, NEW_CONVERSATION, NEW_MESSAGE, PRODUCT_INFORMATION_CHANGE, PRODUCT_CREATION … |

### Delete Webhook for an Event

`DELETE /event/202309/webhooks` · scope: `seller.authorization.info`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `event_type` | string | Y | The topic of the webhook event. Giá trị: ORDER_STATUS_CHANGE, RECIPIENT_ADDRESS_UPDATE, PACKAGE_UPDATE, PRODUCT_STATUS_CHANGE, SELLER_DEAUTHORIZATION, UPCOMING_AUTHORIZATION_EXPIRATION, CANCELLATION_STATUS_CHANGE, RETURN_STATUS_CHANGE, NEW_CONVERSATION, NEW_MESSAGE, PRODUCT_INFORMATION_CHANGE, PRODUCT_CREATION … |

## Customer Service

### Get Conversations

`GET /customer_service/202309/conversations` · scope: `seller.customer_service`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `page_token` | string | N | Paging cursor, this means where this query should start. |
| `page_size` | int | Y | Number of conversations to return per page. |
| `locale` | string | N | System message's display language. |
| `need_session_id` | bool | N | Set to `true` to request `cur_session_id`. Giá trị: AUTO_REPLY, QUEUED, ASSIGNED |
| `need_session_info` | bool | N | Set to `true` to request `cur_session` and `cur_session_id`. Giá trị: AUTO_REPLY, QUEUED, ASSIGNED |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `next_page_token` | string | 1612353423 | The index indicates where we should start on the next page. |
| `conversations` | []object |  | Conversation info. |
| `conversations[].id` | string | 7494560109732334261 | Conversation ID |
| `conversations[].participant_count` | int | 3 | Number of participants in the conversation. |
| `conversations[].can_send_message` | bool | true | Whether customer service agent can send message in this conversation. |
| `conversations[].unread_count` | int | 0 | Number of messages unread by the customer service agent. |
| `conversations[].create_time` | int | 1691411573 | Unix timestamp when the conversation was created. |
| `conversations[].participants` | []object |  | Conversation participants. |
| `conversations[].participants[].im_user_id` | string | 7494560109732334261 | Participant's ID, in IM system. |
| `conversations[].participants[].avatar` | string | https://p16-oec-ttp.tiktokcdn-us.com/tos… | Participant's avatar URL. |
| `conversations[].participants[].user_id` | string | 7494560109732334262 | Participant's ID. |
| `conversations[].participants[].role` | string | BUYER | Participant's role. Giá trị: BUYER, SHOP, CUSTOMER_SERVICE |
| `conversations[].participants[].nickname` | string | Albert | Participant's nickname. |
| `conversations[].participants[].buyer_platform` | string | TIKTOK_SHOP | Which platform is the buyer from. Giá trị: BUYER |
| `conversations[].latest_message` | object |  | The latest message in conversation. |
| `conversations[].latest_message.id` | string | 7494560109732334263 | Message ID. |
| `conversations[].latest_message.type` | string | TEXT | Message type. Giá trị: TEXT, IMAGE, ALLOCATED_SERVICE, NOTIFICATION, BUYER_ENTER_FROM_TRANSFER, BUYER_ENTER_FROM_PRODUCT, BUYER_ENTER_FROM_ORDER, PRODUCT_CARD, EMOTICONS, ORDER_CARD, VIDEO, RETURN_REFUND_CARD … |
| `conversations[].latest_message.content` | string | {"content": "simple text message"} | Message content, in JSON serialized string. Giá trị: TEXT, IMAGE, PRODUCT_CARD, ORDER_CARD, RETURN_REFUND_CARD, VIDEO, ALLOCATED_SERVICE, COUPON_CARD |
| `conversations[].latest_message.create_time` | int | 1691411573 | Unix timestamp for creating the message in seconds. |
| `conversations[].latest_message.is_visible` | bool | true | Whether this message should be displayed to customer service. |
| `conversations[].latest_message.sender` | object |  | Sender of the message. |
| `conversations[].latest_message.sender.im_user_id` | string | 7494560109732334261 | Sender's IM ID. |
| `conversations[].latest_message.sender.avatar` | string | https://p16-oec-ttp.tiktokcdn-us.com/tos… | Sender's avatar URL. |
| `conversations[].latest_message.sender.role` | string | BUYER | Sender's role. Giá trị: BUYER, SHOP, CUSTOMER_SERVICE, SYSTEM, ROBOT |
| `conversations[].latest_message.sender.nickname` | string | Albert | Sender's nickname. |
| `conversations[].latest_message.index` | string | 7494560109732334274 | Message index. |
| `conversations[].cur_session_id` | string | 7494472109882334261 | Current session id. |
| `conversations[].cur_session` | object |  | Current session. |
| `conversations[].cur_session.id` | string | 7494472109882334261 | Current session id. |
| `conversations[].cur_session.phase` | string | ASSIGNED | Current phase of the session. Giá trị: AUTO_REPLY, QUEUED, ASSIGNED |

**Mã lỗi:** `45101001` Internal error.; `45101003` Record not found.; `45101004` The query quota has been reached (10000 request per day).; `36009003` Internal error.

### Get Conversation

`GET /customer_service/202601/conversations/{conversation_id}` · scope: `seller.customer_service`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `need_session_id` | bool | N | Set to `true` to request `cur_session_id`. |
| `need_session_info` | bool | N | Set to `true` to request `cur_session` and `cur_session_id`. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `conversation` | object |  | Conversation info. |
| `conversation.id` | string | 7494560109732334261 | Conversation ID |
| `conversation.participant_count` | int | 3 | Number of participants in the conversation. |
| `conversation.participants` | []object |  | Conversation participants. |
| `conversation.participants[].im_user_id` | string | 7494560109732334261 | Participant's ID, in IM system. |
| `conversation.participants[].role` | string | BUYER | Participant's role. Giá trị: BUYER, SHOP, CUSTOMER_SERVICE |
| `conversation.participants[].nickname` | string | Albert | Participant's nickname. |
| `conversation.participants[].avatar` | string | https://p16-oec-ttp.tiktokcdn-us.com/tos… | Participant's avatar URL. |
| `conversation.participants[].user_id` | string | 7494560109732334262 | Participant's ID. |
| `conversation.participants[].platform` | string | TIKTOK_SHOP | Platform the buyer is from. Giá trị: BUYER, TIKTOK_SHOP, TOKOPEDIA |
| `conversation.create_time` | int | 1691411573 | Unix timestamp when the conversation was created. |
| `conversation.unread_count` | int | 0 | Number of messages unread by the customer service agent. |
| `conversation.cur_session_id` | string | 7494472109882334261 | ID of the current open session. |
| `conversation.cur_session` | object |  | Information about the current open session. |
| `conversation.cur_session.id` | string | 7494472109882334261 | Current session id. |
| `conversation.cur_session.phase` | string | ASSIGNED | Current phase of the session. Giá trị: AUTO_REPLY, QUEUED, ASSIGNED |

**Mã lỗi:** `45101001` Internal error.; `45101003` Record not found.; `45101004` The query quota has been reached (10000 request per day).; `36009003` Internal error.

### Get Conversation Messages

`GET /customer_service/202309/conversations/{conversation_id}/messages` · scope: `seller.customer_service`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `page_token` | string | N | Paging cursor, this means where this query should start. |
| `page_size` | int | Y | Number of messages to return per page. |
| `locale` | string | N | System message's display language. |
| `sort_order` | string | N | Sort order. Giá trị: DESC, ASC |
| `sort_field` | string | N | Sort messages by one of the following properties: - `create_time` (default) - `index` |
| `need_data` | bool | N | Whether to request the `data` field for visible messages when structured data is available. |
| `need_plaintext` | bool | N | Whether to request the `plaintext` field for visible messages when a human-readable representation is available. |
| `time_zone` | string | N | Time zone used to format date and time values in supported message content, including generated plaintext. Giá trị: UTC |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `next_page_token` | string | 162312320 | The index indicates where we should start on the next page. |
| `unsupported_msg_tips` | string | Please check this message in Seller Cent… | If your app encounters a message type it doesn't support, you can display the text to guide the user to check the message in TikTok Shop. |
| `messages` | []object |  | Messages in conversation. |
| `messages[].id` | string | 7494560109732334263 | Message ID. |
| `messages[].type` | string | TEXT | Message type. Giá trị: TEXT, IMAGE, ALLOCATED_SERVICE, NOTIFICATION, BUYER_ENTER_FROM_TRANSFER, BUYER_ENTER_FROM_PRODUCT, BUYER_ENTER_FROM_ORDER, PRODUCT_CARD, EMOTICONS, ORDER_CARD, VIDEO, RETURN_REFUND_CARD … |
| `messages[].content` | string | {"content": "simple text message"} | Message content, in JSON serialized string. Giá trị: TEXT, IMAGE, PRODUCT_CARD, ORDER_CARD, LOGISTICS_CARD, RETURN_REFUND_CARD, VIDEO, ALLOCATED_SERVICE, COUPON_CARD |
| `messages[].create_time` | int | 1691411573 | Unix timestamp for creating the message in seconds. |
| `messages[].is_visible` | bool | true | Whether this message should be displayed to customer service. |
| `messages[].sender` | object |  | The message sender. |
| `messages[].sender.im_user_id` | string | 7494560109732334261 | Sender's ID. |
| `messages[].sender.role` | string | BUYER | Sender's role. Giá trị: BUYER, SHOP, CUSTOMER_SERVICE, SYSTEM, ROBOT |
| `messages[].sender.nickname` | string | Albert | Sender's nickname. |
| `messages[].sender.avatar` | string | https://p16-oec-ttp.tiktokcdn-us.com/tos… | Sender's avatar URL. |
| `messages[].index` | string | 7494560109732334274 | Message index. |
| `messages[].data` | string | {} | Structured message data encoded as a JSON string. Giá trị: LOGISTICS_CARD |
| `messages[].plaintext` | string | Product card shared:<br>- Product ID: 17… | Human-readable message content. Giá trị: PRODUCT_CARD |

**Mã lỗi:** `45101001` Internal error.; `45101003` Record not found.; `45101004` The query quota has been reached (10000 request per day).; `36009003` Internal error.

### Create Conversation

`POST /customer_service/202309/conversations` · scope: `seller.customer_service`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `buyer_user_id` | string | Y | Buyer's user ID The value is the same as `data.orders.user_id` in the response data of Get Order Detail. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `conversation_id` | string | 7106888323922608389 | Converstaion ID. |

**Mã lỗi:** `45101001` Internal error.; `36009003` Internal error.; `45109003` Unable to create a conversation as you did not meet the criteria for initiating conversations.

### Send Message

`POST /customer_service/202606/conversations/{conversation_id}/messages` · scope: `seller.customer_service`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `type` | string | Y | Message type. Giá trị: TEXT, IMAGE, VIDEO, PRODUCT_CARD, ORDER_CARD, RETURN_REFUND_CARD, COUPON_CARD, LOGISTICS_CARD |
| `content` | string | Y | Message content, in JSON serialized string. Giá trị: TEXT, VIDEO, LOGISTICS_CARD, PRODUCT_CARD, ORDER_CARD, RETURN_REFUND_CARD, IMAGE, COUPON_CARD |
| `sender_role` | string | N | Message sending role. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `message_id` | string | 7494560109732334261 | Message ID |

**Mã lỗi:** `45101001` Internal error.; `45101002` Invalid params.; `45101003` Record not found.; `45101004` The query quota has been reached (10000 request per day).; `45101006` You cannot send the message containing sensitive content.; `36009003` Internal error.; `45102007` No permission to access this conversation.; `45109001` Unable to send messages due to conversation rules.; `45111001` not support; `45111007` The buyer has already reached the maximum claim limit for this coupon.; `45111012` Failed to send the coupon card message due to exceeding the QPS limit.; `45111017` Invalid params.

### Mark Buyer Messages as Read

`POST /customer_service/202309/conversations/{conversation_id}/messages/read` · scope: `seller.customer_service`

**Mã lỗi:** `45101003` Record not found.; `45101004` The query quota has been reached (10000 request per day).; `36009003` Internal error.

### Upload Image for Buyer Messages

`POST /customer_service/202309/images/upload` · scope: `seller.customer_service`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `url` | string | https://p16-oec-va.ibyteimg.com/tos-mali… | Image URL |
| `width` | int | 1280 | Width of the uploaded image in pixels. |
| `height` | int | 720 | Height of the uploaded image in pixels. |

**Mã lỗi:** `45101001` Internal error.; `45101008` Internal error.; `36009003` Internal error.

### Get Agent Settings

`GET /customer_service/202309/agents/settings` · scope: `seller.customer_service`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `can_accept_chat` | bool | true | Whether the current agent can accept chat. |

**Mã lỗi:** `36009003` Internal error.

### Update Agent Settings

`PUT /customer_service/202309/agents/settings` · scope: `seller.customer_service`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `can_accept_chat` | bool | Y | Whether the shop owner's customer service account can accept auto-assigned chats. |

**Mã lỗi:** `45101001` Internal error.; `36009003` Internal error.

## Products

### Search Products

`POST /product/202502/products/search` · scope: `seller.product.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `page_size` | int | Y | The number of results to be returned per page. |
| `page_token` | string | N | An opaque token used to retrieve the next page of a paginated result set. |

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `status` | string | N | Filter products based on the product's base version. Giá trị: ALL, DRAFT, PENDING, FAILED, ACTIVATE, SELLER_DEACTIVATED, PLATFORM_DEACTIVATED, FREEZE, DELETED |
| `seller_skus` | []string | N | Filter products by these seller SKU codes. |
| `create_time_ge` | int | N | Filter products to show only those that are created on or after the specified date and time. |
| `create_time_le` | int | N | Filter products to show only those that are created on or before the specified date and time. |
| `update_time_ge` | int | N | Filter products to show only those that are updated on or after the specified date and time. |
| `update_time_le` | int | N | Filter products to show only those that are updated on or before the specified date and time. |
| `category_version` | string | N | Filter products by the category tree version. |
| `listing_quality_tiers` | []string | N | Filter products by their listing quality tier. Giá trị: POOR, FAIR, GOOD, EXCELLENT |
| `listing_platforms` | []string | N | Filter products by the listing platforms. Giá trị: ALL |
| `audit_status` | []string | N | Filter products by their audit status for TikTok Shop. Giá trị: AUDITING, FAILED, APPROVED |
| `sku_ids` | []string | N | Filter products by SKU IDs. |
| `sns_filter` | string | N | Filter products by their Subscribe and Save (SNS) status. Giá trị: CONFIGURED, ELIGIBLE |
| `return_draft_version` | bool | N | Filter products to show only those that have a draft. Giá trị: ALL, DRAFT, ACTIVATE, SELLER_DEACTIVATED, PLATFORM_DEACTIVATED |
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `include_live_quick_products` | bool | N | Whether to include LIVE_QUICK products in the query results. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `total_count` | int | 200 | The total number of products that meet the query conditions. |
| `products` | []object |  | The list of products that meet the query conditions. |
| `products[].id` | string | 1729592969712207008 | The product ID generated by TikTok Shop. |
| `products[].title` | string | Short Boat Invisible Socks | The product title. |
| `products[].status` | string | ACTIVATE | The product status in TikTok Shop. Giá trị: TIKTOK_SHOP |
| `products[].skus` | []object |  | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `products[].skus[].id` | string | 1729592969712207012 | The SKU ID generated by TikTok Shop. |
| `products[].skus[].seller_sku` | string | Color-Red-XM01 | An internal code/name for managing SKUs, not visible to buyers. |
| `products[].skus[].price` | object |  | SKU pricing information. |
| `products[].skus[].price.currency` | string | USD | The currency. Giá trị: BRL, EUR, GBP, IDR, JPY, MXN, MYR, PHP, SGD, THB, USD, VND |
| `products[].skus[].price.tax_exclusive_price` | string | 111.01 | **Local sellers/Intra-EU sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `products[].skus[].price.sale_price` | string | 121.11 | **Global sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `products[].skus[].price.starting_bid_price` | string | 2 | starting bid price for auction product |
| `products[].skus[].inventory` | []object |  | SKU inventory information. |
| `products[].skus[].inventory[].warehouse_id` | string | 7068517275539719942 | The ID of the warehouse where the SKU is stored. |
| `products[].skus[].inventory[].quantity` | int | 999 | The total SKU quantity available in the warehouse. |
| `products[].skus[].inventory[].backorder_quantity` | int | 888 | The `backorder_quantity` will automatically be converted to `quantity` once in-stock inventory is sold out. |
| `products[].skus[].inventory[].handling_time` | int | 5 | The estimated number of working days needed for a backorder to be shipped. |
| `products[].skus[].list_price` | object |  | The SKU's list price information that has been verified to be legitimate by the audit team. |
| `products[].skus[].list_price.amount` | string | 1 | The price amount. |
| `products[].skus[].list_price.currency` | string | USD | The currency. |
| `products[].skus[].external_list_prices` | []object |  | The SKU list price (e.g. |
| `products[].skus[].external_list_prices[].source` | string | SHOPIFY_COMPARE_AT_PRICE | The external ecommerce platform from which the price is sourced. Giá trị: SHOPIFY_COMPARE_AT_PRICE |
| `products[].skus[].external_list_prices[].amount` | string | 1 | The price amount. |
| `products[].skus[].external_list_prices[].currency` | string | USD | The currency. |
| `products[].skus[].pre_sale` | object |  | SKU presale information, used to tag a product as a presale product based on its presale type. |
| `products[].skus[].pre_sale.type` | string | PRE_ORDER | The type of pre-sale. Giá trị: PRE_ORDER, MADE_TO_ORDER, CUSTOM |
| `products[].skus[].pre_sale.fulfillment_type` | object |  | Information about the type of pre-sale order fulfillment and the corresponding timeframe. |
| `products[].skus[].pre_sale.fulfillment_type.handling_duration_days` | int | 7 | The desired duration for handling a pre-sale order and handing it over to a shipping carrier. Giá trị: MADE_TO_ORDER, CUSTOM, PRE_ORDER |
| `products[].skus[].pre_sale.fulfillment_type.release_date` | int | 1619611761 | The date on which the product gets converted into a regular product and becomes available for general purchase. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `products[].skus[].status_info` | object |  | Status information of the SKU. |
| `products[].skus[].status_info.status` | string | DEACTIVATED | The SKU status in TikTok Shop. Giá trị: NORMAL, DEACTIVATED |
| `products[].skus[].status_info.deactivation_source` | string | PLATFORM | The deactivation source of the SKU with `DEACTIVATED` status. Giá trị: DEACTIVATED |
| `products[].skus[].fees` | []object |  | The fees required for this product based on TikTok Shop policies. |
| `products[].skus[].fees[].type` | string | PFAND | The type of fee. |
| `products[].skus[].fees[].amount` | string | 1.01 | The fee amount. Giá trị: PFAND |
| `products[].skus[].fees[].additional_attribute` | string | SINGLE_USE | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `products[].sales_regions` | []string | ["US"] | The regions where the product is sold. |
| `products[].create_time` | int | 1234567890 | The time when the product is created. |
| `products[].update_time` | int | 1234567800 | The time when the product is last updated. |
| `products[].product_sync_fail_reasons` | []string | ["The required qualification is missed."… | The reasons why synchronizing of global product information to local products failed. |
| `products[].is_not_for_sale` | bool | true | A flag indicating whether the product is not for sale. |
| `products[].recommended_categories` | []object |  | Recommended categories for the product based on the product title, description, and images. |
| `products[].recommended_categories[].id` | string | 853000 | The ID of the recommended category. |
| `products[].recommended_categories[].local_name` | string | Botol & Stoples Penyimpanan | The localized category name. |
| `products[].listing_quality_tier` | string | POOR | The current quality tier of this product listing. Giá trị: POOR, FAIR, GOOD, EXCELLENT |
| `products[].integrated_platform_statuses` | []object |  | The current status of the product on platforms that are natively integrated with TikTok Shop (e.g. Giá trị: TOKOPEDIA |
| `products[].integrated_platform_statuses[].platform` | string | TOKOPEDIA | The integrated platform name. Giá trị: TOKOPEDIA |
| `products[].integrated_platform_statuses[].status` | string | PLATFORM_DEACTIVATED | The product status in the integrated platform. Giá trị: DRAFT, PENDING, FAILED, ACTIVATE, SELLER_DEACTIVATED, PLATFORM_DEACTIVATED, FREEZE, DELETED |
| `products[].audit` | object |  | Product audit information for TikTok Shop. |
| `products[].audit.status` | string | AUDITING | The product audit status. Giá trị: NONE, AUDITING, FAILED, PRE_APPROVED, APPROVED |
| `products[].audit.pre_approved_reasons` | []string | ["KYC_PENDING"] | The reason why the product is pre-approved. Giá trị: KYC_PENDING, RESTRICTED_CATEGORY_PENDING |
| `products[].product_families` | []object |  | The **live** product family that this product belongs to. |
| `products[].product_families[].id` | string | 1000592969712207000 | The product family ID. |
| `products[].product_families[].products` | []object |  | A list of products that belong to the family. |
| `products[].product_families[].products[].id` | string | 1729592969712207008 | The product ID. |
| `products[].has_draft` | bool | true | A flag to indicate if the product has a draft. |
| `products[].scheduled_sale` | object |  | Scheduled listing configuration for the product, including whether scheduled listing is enabled and the scheduled listing time. |
| `products[].scheduled_sale.is_enabled_scheduled_sale` | bool | false | Whether scheduled listing is enabled for the product. |
| `products[].scheduled_sale.schedule_sale_time` | int | 1768899145000 | The scheduled listing time stored for the product, expressed as a Unix timestamp in milliseconds. |
| `products[].product_tags` | []string | ["AUCTION"] | Product tag to identity special type product Possible values: AUCTION、LIVE_QUICK |
| `products[].selling_format` | string | FIXED_PRICE | The product's sale format. |
| `next_page_token` | string | WzE3ODY0Njk5NDg1MTgsIjE3MzY5OTA2MTM0NTI5… | An opaque token used to retrieve the next page of a paginated result set. |

**Mã lỗi:** `12019027` The maximum count of “seller_skus” in the search filter is 10.; `12019087` The number of SKU IDs exceeds the limit of 10; `12019108` page number is invalid; `12019109` page size is invalid; `12019118` search create time invalid; `12019119` search update time invalid; `12052180` The total number of search results can not exceed 10000.; `12052700` The seller is inactive.; `12052704` seller id not exist; `36009003` Internal error.; `12052910` The warehouse update parameters are invalid.

### Get Product

`GET /product/202309/products/{product_id}` · scope: `seller.product.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `return_under_review_version` | bool | N | A flag to indicate what product information to retrieve if a live product (`ACTIVATE` status) is edited and resent for TikTok Shop review. Giá trị: ACTIVATE |
| `return_draft_version` | bool | N | A flag to indicate what product information to retrieve if a product has a draft in TikTok Shop. Giá trị: DRAFT, ACTIVATE, SELLER_DEACTIVATED, PLATFORM_DEACTIVATED |
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `query_options` | []string | N | Options for retrieving additional product information. Giá trị: GET_PRODUCT_DETAIL_URL |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `audit` | object |  | Product audit information. |
| `audit.pre_approved_reasons` | []string | ["KYC_PENDING"] | The reason why the product is pre-approved. Giá trị: KYC_PENDING, RESTRICTED_CATEGORY_PENDING |
| `audit.status` | string | AUDITING | The product audit status. Giá trị: NONE, AUDITING, FAILED, PRE_APPROVED, APPROVED |
| `audit.not_submitted_reasons` | []string | ["SHOP_INACTIVE"] | The reasons why the product has not been submitted for TikTok Shop audit. Giá trị: PAYMENT_ACCOUNT_NOT_LINKED, SHOP_INACTIVE, W8_NOT_CONFIGURED |
| `audit_failed_reasons` | []object |  | TikTok Shop audit failure information. |
| `audit_failed_reasons[].listing_platform` | string | TIKTOK_SHOP | The platform for listing the product. Giá trị: TIKTOK_SHOP, TOKOPEDIA |
| `audit_failed_reasons[].position` | string | product | The audit failure reason name. |
| `audit_failed_reasons[].reasons` | []string | ["violate listing rules"] | A brief reason for failing TikTok Shop audit. |
| `audit_failed_reasons[].suggestions` | []string | ["The product violates TikTok Shopping l… | A detailed explanation of the reason for failure. |
| `brand` | object |  | Product brand information. |
| `brand.id` | string | 7082427311584347905 | The ID of the brand of this product. |
| `brand.name` | string | brand xxx aaa | The brand name of this product. |
| `category_chains` | []object |  | Product category tree information. |
| `category_chains[].id` | string | 853000 | The ID of the category of this product. |
| `category_chains[].is_leaf` | bool | true | A flag to indicate if the category is a leaf category. |
| `category_chains[].local_name` | string | Botol & Stoples Penyimpanan | The category name in the requested locale. |
| `category_chains[].parent_id` | string | 851848 | The parent category ID. |
| `certifications` | []object |  | The list of certifications for your product. |
| `certifications[].expiration_date` | int | 1741235456 | The expiration date of this certification expressed in unix timestamp (seconds) UTC+0. |
| `certifications[].files` | []object |  | A list of certification related files. |
| `certifications[].files[].format` | string | PDF | The format of the certification file. |
| `certifications[].files[].id` | string | v09ea0g40000cj91373c77u3mid3g1s0 | The ID of the certification file. |
| `certifications[].files[].name` | string | CERT_X2.PDF | The name of the certification file. |
| `certifications[].files[].urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the certification files. |
| `certifications[].id` | string | 602362 | The ID to identify the type of certification required for the product. |
| `certifications[].images` | []object |  | A list of certification related images. |
| `certifications[].images[].height` | int | 600 | The image height. |
| `certifications[].images[].thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `certifications[].images[].uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `certifications[].images[].urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `certifications[].images[].width` | int | 600 | The image width. |
| `certifications[].title` | string | SNI Certificate | The title of the certification type. |
| `create_time` | int | 1234567890 | The time when the product was created, expressed as a Unix timestamp in seconds. |
| `delivery_options` | []object |  | The custom delivery options applied to this product, overriding the default warehouse delivery option. |
| `delivery_options[].id` | string | 1729592969712203232 | The delivery option ID. |
| `delivery_options[].is_available` | bool | true | A flag indicating whether the delivery option is available for this product. |
| `delivery_options[].name` | string | SEND_BY_SELLER | The delivery option name. |
| `description` | string | <p>Please compare above detailed size wi… | The product description in HTML format. |
| `external_product_id` | string | 172959296971220002 | An external identifier used in an external ecommerce platform. |
| `global_product_association` | object |  | The global product association established through global product publishing or manual binding. |
| `global_product_association.global_product_id` | string | 1729592969712207920 | The global product ID in TikTok Shop. |
| `global_product_association.sku_mappings` | []object |  | The list of sku mappings between the global and local products. |
| `global_product_association.sku_mappings[].global_sku_id` | string | 1729592969712207234 | The global SKU ID in TikTok Shop. |
| `global_product_association.sku_mappings[].local_sku_id` | string | 1729592969712207230 | The local SKU ID in TikTok Shop. |
| `global_product_association.sku_mappings[].sales_attribute_mappings` | []object |  | The list of sales attribute mappings between the global and local SKUs. |
| `global_product_association.sku_mappings[].sales_attribute_mappings[].global_attribute_id` | string | 100000 | The global sales attribute ID. |
| `global_product_association.sku_mappings[].sales_attribute_mappings[].global_value_id` | string | 1001064 | The global sales attribute value ID. |
| `global_product_association.sku_mappings[].sales_attribute_mappings[].local_attribute_id` | string | 100000 | The local sales attribute ID. |
| `global_product_association.sku_mappings[].sales_attribute_mappings[].local_value_id` | string | 1001064 | The local sales attribute value ID. |
| `has_draft` | bool | true | A flag to indicate if the product has a draft. |
| `id` | string | 1729592969712207008 | The product ID generated by TikTok Shop. |
| `integrated_platform_statuses` | []object |  | The current status of the product on platforms that are natively integrated with TikTok Shop (e.g. Giá trị: TOKOPEDIA |
| `integrated_platform_statuses[].platform` | string | TOKOPEDIA | The integrated platform name. Giá trị: TOKOPEDIA |
| `integrated_platform_statuses[].status` | string | PLATFORM_DEACTIVATED | The product status in the integrated platform. Giá trị: UNKNOWN, DRAFT, PENDING, FAILED, ACTIVATE, SELLER_DEACTIVATED, PLATFORM_DEACTIVATED, FREEZE, DELETED |
| `is_cod_allowed` | bool | true | A flag indicating whether to show the Cash On Delivery (COD) payment option during checkout. |
| `is_not_for_sale` | bool | true | A flag indicating whether the product is not for sale and only available through Gift with Purchase (GWP) promotions. |
| `is_pre_owned` | bool | false | A flag to indicate if the product is pre-owned. |
| `is_replicated` | bool | false | A flag to indicate if the product contains a replica (created through local replication) in other local markets. |
| `key_product_features` | []string | ["Breathable mesh upper", "Cushioned foa… | Key Product Features. |
| `listing_quality_tier` | string | POOR | The current quality tier of this product listing. Giá trị: POOR, FAIR, GOOD, EXCELLENT |
| `main_images` | []object |  | A list of images to display in the product image gallery. |
| `main_images[].height` | int | 600 | The image height. |
| `main_images[].thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `main_images[].uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `main_images[].urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `main_images[].width` | int | 600 | The image width. |
| `manufacturer_ids` | []string | ["172959296971220002"] | The list of manufacturer IDs. |
| `minimum_order_quantity` | int | 1 | The minimum order quantity for the product. |
| `package_dimensions` | object |  | The dimensions of the product package |
| `package_dimensions.height` | string | 10 | The package height. |
| `package_dimensions.length` | string | 10 | The package length. |
| `package_dimensions.unit` | string | CENTIMETER | The unit for the package dimensions. |
| `package_dimensions.width` | string | 10 | The package width. |
| `package_weight` | object |  | The weight of the product package. |
| `package_weight.unit` | string | KILOGRAM | The unit for the package weight. Giá trị: KILOGRAM, POUND |
| `package_weight.value` | string | 1.32 | The package weight. |
| `pod_info` | object |  | POD customization configuration for an MTO product. |
| `pod_info.pod_templates` | []object |  | A list of POD customization templates. |
| `pod_info.pod_templates[].pod_template_id` | string | 172959296971220002 | The unique identifier of the POD customization template. |
| `pod_info.pod_templates[].remark` | string | Front side customization template for ba… | Optional notes or instructions for this customization template. |
| `pod_info.pod_templates[].sku_ids` | []string | ["172959296971220002"] | A list of product SKU IDs associated with this customization template. |
| `pod_info.pod_templates[].surfaces` | []object |  | A list of customizable product surfaces under this template, such as the front side, back side, collar, or sleeve. |
| `pod_info.pod_templates[].surfaces[].background_image` | object |  | The background image of the customizable surface. |
| `pod_info.pod_templates[].surfaces[].background_image.height` | int | 600 | The height of the background image, in pixels. |
| `pod_info.pod_templates[].surfaces[].background_image.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | A list of thumbnail URLs for the background image. |
| `pod_info.pod_templates[].surfaces[].background_image.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the background image. |
| `pod_info.pod_templates[].surfaces[].background_image.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | A list of URLs for the background image. |
| `pod_info.pod_templates[].surfaces[].background_image.width` | int | 600 | The width of the background image, in pixels. |
| `pod_info.pod_templates[].surfaces[].custom_areas` | []object |  | A list of customizable areas on this surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_name` | string | logo area | The name of the customizable area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement` | object |  | The position, size, and rotation information of the customizable area on the surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.height` | string | 200 | The height of the customizable area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.rotate_degree` | string | 0 | The rotation angle of the customizable area, in degrees. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.width` | string | 200 | The width of the customizable area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.x_coordinate` | string | 0 | The X-coordinate of the customizable area's starting point on the surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.y_coordinate` | string | 0 | The Y-coordinate of the customizable area's starting point on the surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_type` | string | TEXT | The type of the customizable area. Giá trị: TEXT, IMAGE |
| `pod_info.pod_templates[].surfaces[].custom_areas[].external_area_id` | string | 172959296971220002 | The seller-defined external identifier of the customizable area, used for mapping with the seller's own system. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area` | object |  | Configuration for an image-type customization area. Giá trị: IMAGE |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image` | object |  | Optional default image displayed in this area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image.height` | int | 600 | The height of the default image, in pixels. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | A list of thumbnail URLs for the default image. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the default image. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | A list of URLs for the default image. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image.width` | int | 600 | The width of the default image, in pixels. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.min_height` | int | 300 | The minimum required height, in pixels, for an image uploaded by the consumer. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.min_width` | int | 300 | The minimum required width, in pixels, for an image uploaded by the consumer. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].is_required` | bool | false | Specifies whether the consumer must provide customization content for this area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area` | object |  | Configuration for a text-type customization area. Giá trị: TEXT |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.align_type` | string | CENTER | The text alignment type. Giá trị: LEFT, CENTER, RIGHT |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.colors` | []string | ["#FFFFFF"] | A list of text colors available for consumers to choose from, using hexadecimal color values such as `#FFFFFF`. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.default_text` | string | Hello World | Optional default text displayed in this area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.fonts` | []string | ["Roboto"] | A list of font keys available for consumers to choose from, based on the platform-supported font library. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.max_text_length` | int | 20 | The maximum number of characters allowed for consumer text input. |
| `pod_info.pod_templates[].surfaces[].external_surface_id` | string | 172959296971220002 | The seller-defined external identifier of the customizable surface, used for mapping with the seller's own system. |
| `pod_info.pod_templates[].surfaces[].mask_image` | object |  | An optional mask image placed above the customization areas to support irregular or cut-out effects. |
| `pod_info.pod_templates[].surfaces[].mask_image.height` | int | 600 | The height of the mask image, in pixels. |
| `pod_info.pod_templates[].surfaces[].mask_image.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | A list of thumbnail URLs for the mask image. |
| `pod_info.pod_templates[].surfaces[].mask_image.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the mask image. |
| `pod_info.pod_templates[].surfaces[].mask_image.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | A list of URLs for the mask image. |
| `pod_info.pod_templates[].surfaces[].mask_image.width` | int | 600 | The width of the mask image, in pixels. |
| `pod_info.pod_templates[].surfaces[].surface_name` | string | Front Side | The name of the customizable surface, for example, `Front Side` or `Back Side`. |
| `pod_info.pod_templates[].template_name` | string | T-Shirt Front Print | The name of the customization template, used by the seller for internal management. |
| `prescription_requirement` | object |  | Prescription related information for the product. |
| `prescription_requirement.needs_prescription` | bool | false | A flag to indicate whether a prescription is required to purchase this pharmacy product. |
| `primary_combined_product_id` | string | 1729592969712207008 | If the product is a virtual bundle, this value is the ID of the primary product in the bundle. |
| `product_attributes` | []object |  | A list of general attributes (e.g. |
| `product_attributes[].id` | string | 100392 | The ID of the product attribute. |
| `product_attributes[].name` | string | Occasion | The product attribute name. |
| `product_attributes[].values` | []object |  | A list of selectable values for the product attribute. |
| `product_attributes[].values[].id` | string | 1001533 | The product attribute value ID. |
| `product_attributes[].values[].name` | string | Birthday | The product attribute value name. |
| `product_attributes[].values[].metric_standard_info` | object |  | Defines the measurement template information used to describe the measurement standards and metric values configured for the product. |
| `product_attributes[].values[].metric_standard_info.id` | string | 7678592839487784712 | The unique ID of the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos` | []object |  | The list of metric value entries under the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].id` | string | 7678592839487784712 | The unique ID of a metric item within the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].value` | string | 2026 | The seller-provided value for the specified metric item in the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].unit_id` | string | 7678592839484720903 | Metric value module unit id. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].unit_name` | string | piece | Metric value module unit name. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].suffix` | string | and | Metric value suffix. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].prefix` | string | daily | Metric value prefix. |
| `product_families` | []object |  | The **live** product family that this product belongs to. |
| `product_families[].id` | string | 1000592969712207000 | The product family ID. |
| `product_families[].products` | []object |  | A list of products that belong to the family. |
| `product_families[].products[].id` | string | 1729592969712207008 | The product ID. |
| `product_status` | string | SELLER_DEACTIVATED | The product status in TikTok Shop unrelated to its audit status. Giá trị: TIKTOK_SHOP |
| `product_tags` | []string | ["AUCTION"] | Product tag to identity special type product Possible values: AUCTION |
| `product_types` | []string | ["COMBINED_PRODUCT"] | The product type. Giá trị: COMBINED_PRODUCT, IN_COMBINED_PRODUCT, GPR_TARGET_PRODUCT |
| `recommended_categories` | []object |  | Recommended categories for the product based on the product title, description, and images. |
| `recommended_categories[].id` | string | 850003 | The ID of the recommended category. |
| `recommended_categories[].local_name` | string | <br>Botol & Stoples Penyimpanan | The recommended category name in the requested locale. |
| `responsible_person_ids` | []string | ["172959296971220003"] | The list of responsible person IDs. |
| `scheduled_sale` | object |  | Scheduled listing configuration for the product, including whether scheduled listing is enabled and the scheduled listing time. |
| `scheduled_sale.is_enabled_scheduled_sale` | bool | false | Whether scheduled listing is enabled for this product. |
| `scheduled_sale.schedule_sale_time` | int | 1768899145000 | The stored scheduled listing time, expressed as a Unix timestamp in milliseconds. |
| `search_terms` | []string | ["sneakers","running shoes","athletic"] | Search terms (ST words). |
| `shipping_insurance_requirement` | string | OPTIONAL | The shipping insurance purchase requirement imposed on buyers for the product. Giá trị: REQUIRED, OPTIONAL, NOT_SUPPORTED |
| `shipping_template_id` | string | 7552764259994699538 | Identifier of the shipping template that was bound to the product |
| `size_chart` | object |  | The measurement details of the product to help buyers find the right size. |
| `size_chart.image` | object |  | An image of the size chart. |
| `size_chart.image.height` | int | 600 | The image height. |
| `size_chart.image.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `size_chart.image.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `size_chart.image.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `size_chart.image.width` | int | 600 | The image width. |
| `size_chart.template` | object |  | A TikTok Shop size chart template generated by the size chart tool in Seller Center > Manage Products > Bulk action > Batch manage size charts. |
| `size_chart.template.id` | string | 7267563252536723205 | The size chart template ID. |
| `skus` | []object |  | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].combined_skus` | []object |  | If this SKU belongs to a virtual bundle, this object contains the list of individual SKUs that form the bundle (e.g. |
| `skus[].combined_skus[].brand` | object |  | Brand information of the source product included in the virtual bundle. |
| `skus[].combined_skus[].brand.id` | string | 7082427311584347905 | The ID of the brand of the source product included in the virtual bundle. |
| `skus[].combined_skus[].brand.name` | string | brand xxx aaa | The brand name of the source product included in the virtual bundle. |
| `skus[].combined_skus[].categories` | []object |  | The category tree information of the source product included in the virtual bundle. |
| `skus[].combined_skus[].categories[].id` | string | 853000 | The ID of the category of the source product included in the virtual bundle. |
| `skus[].combined_skus[].categories[].is_leaf` | bool | true | A flag to indicate if the category is a leaf category. |
| `skus[].combined_skus[].categories[].local_name` | string | Botol & Stoples Penyimpanan | The source product category name in the requested locale. |
| `skus[].combined_skus[].categories[].parent_id` | string | 851848 | The parent category ID of the source product included in the virtual bundle. |
| `skus[].combined_skus[].combined_listing_not_live_reasons` | []string | ["SUB_PRODUCT_DELETE"] | The reasons linked to the source product included in the virtual bundle that are keeping the virtual bundle status from being live. Giá trị: SUB_PRODUCT_DEACTIVATE, SUB_PRODUCT_DELETE, SUB_PRODUCT_FROZEN, SUB_SKU_DELETE, SUB_PRODUCT_CATEGORY_FIRST_LEVEL_ERROR, SUB_PRODUCT_CATEGORY_OVER_RESTRICTION |
| `skus[].combined_skus[].inventory` | []object |  | Source SKU inventory details. |
| `skus[].combined_skus[].inventory[].quantity` | int | 999 | The total quantity available in the warehouse of the source SKU. |
| `skus[].combined_skus[].inventory[].warehouse_id` | string | 6966568648651605766 | The ID of the warehouse where the source SKU is stored. |
| `skus[].combined_skus[].price` | object |  | Source SKU pricing information. |
| `skus[].combined_skus[].price.currency` | string | USD | The currency. Giá trị: BRL, CZK, EUR, GBP, HUF, IDR, JPY, MXN, MYR, PHP, PLN, SGD … |
| `skus[].combined_skus[].price.sale_price` | string | 117.5 | All sellers The source SKU's local display price shown on the product page before any discounts. |
| `skus[].combined_skus[].price.tax_exclusive_price` | string | 110 | Global sellers The source SKU's local pre-tax price. |
| `skus[].combined_skus[].product_id` | string | 1729582718312380123 | The ID of the source product included in the virtual bundle. |
| `skus[].combined_skus[].product_main_image` | object |  | The main image of the source product included in the virtual bundle. |
| `skus[].combined_skus[].product_main_image.height` | int | 600 | The image height. |
| `skus[].combined_skus[].product_main_image.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `skus[].combined_skus[].product_main_image.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `skus[].combined_skus[].product_main_image.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `skus[].combined_skus[].product_main_image.width` | int | 600 | The image width. |
| `skus[].combined_skus[].sales_attributes` | []object |  | A list of attributes (e.g. |
| `skus[].combined_skus[].sales_attributes[].id` | string | 100000 | The sales attribute ID. |
| `skus[].combined_skus[].sales_attributes[].name` | string | Color | The sales attribute name. |
| `skus[].combined_skus[].sales_attributes[].sku_img` | object |  | The default/main image for each value (e.g. |
| `skus[].combined_skus[].sales_attributes[].sku_img.height` | int | 100 | The image height. |
| `skus[].combined_skus[].sales_attributes[].sku_img.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `skus[].combined_skus[].sales_attributes[].sku_img.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `skus[].combined_skus[].sales_attributes[].sku_img.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `skus[].combined_skus[].sales_attributes[].sku_img.width` | int | 100 | The image width. |
| `skus[].combined_skus[].sales_attributes[].supplementary_sku_images` | []object |  | A list of supplementary images for each value (e.g. |
| `skus[].combined_skus[].sales_attributes[].supplementary_sku_images[].height` | int | 100 | The image height. |
| `skus[].combined_skus[].sales_attributes[].supplementary_sku_images[].thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `skus[].combined_skus[].sales_attributes[].supplementary_sku_images[].uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `skus[].combined_skus[].sales_attributes[].supplementary_sku_images[].urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `skus[].combined_skus[].sales_attributes[].supplementary_sku_images[].width` | int | 100 | The image width. |
| `skus[].combined_skus[].sales_attributes[].value_id` | string | 100000 | The sales attribute value ID. |
| `skus[].combined_skus[].sales_attributes[].value_name` | string | Red | The sales attribute value name. |
| `skus[].combined_skus[].seller_sku` | string | sku_name | An internal code/name for managing the source SKU, not visible to buyers. |
| `skus[].combined_skus[].sku_count` | int | 1 | The quantity of the source SKU included in the virtual bundle. |
| `skus[].combined_skus[].sku_id` | string | 1729582718312380123 | The ID of the source SKU included in the virtual bundle. |
| `skus[].combined_skus[].title` | string | Short Boat Invisible Socks | The title of the source product included in the virtual bundle. |
| `skus[].external_list_prices` | []object |  | The SKU list price (e.g. |
| `skus[].external_list_prices[].amount` | string | 1 | The price amount. |
| `skus[].external_list_prices[].currency` | string | USD | The currency. |
| `skus[].external_list_prices[].source` | string | SHOPIFY_COMPARE_AT_PRICE | The external ecommerce platform from which the price is sourced. Giá trị: SHOPIFY_COMPARE_AT_PRICE |
| `skus[].external_sku_id` | string | 1729592969712207234 | An external identifier used in an external ecommerce platform. |
| `skus[].external_urls` | []string | ["https://example.com/path1", "https://e… | A list of URLs for third-party product listing pages where consumers can place orders. |
| `skus[].extra_identifier_codes` | []string | ["00012345678905","9780596520687"] | A list of up to 10 additional identifier codes if the SKU belongs to a virtual bundle (containing multiple individual SKUs). |
| `skus[].fees` | []object |  | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].additional_attribute` | string | SINGLE_USE | Possible values for Pfand: - SINGLE_USE - REUSABLE - NOT_APPLICABLE Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `skus[].fees[].amount` | string | 1.01 | The fee amount. Giá trị: PFAND |
| `skus[].fees[].type` | string | PFAND | The type of fee. |
| `skus[].global_listing_policy` | object |  | Global Product Replicate (GPR) related information if the product is a target for synchronization. |
| `skus[].global_listing_policy.inventory_type` | string | SHARED | The type of inventory to synchronize. Giá trị: SHARED, EXCLUSIVE |
| `skus[].global_listing_policy.price_sync` | bool | true | A flag indicating whether the product price is synchronized. |
| `skus[].global_listing_policy.replicate_source` | object |  | The source global listing product to synchronize from. |
| `skus[].global_listing_policy.replicate_source.product_id` | string | 1729592969712203232 | The ID of the source product. |
| `skus[].global_listing_policy.replicate_source.shop_id` | string | 7295929697122032321 | The shop ID of the source product. |
| `skus[].global_listing_policy.replicate_source.sku_id` | string | 1729592969712203232 | The SKU ID of the source product. |
| `skus[].id` | string | 10001 | The SKU ID generated by TikTok Shop. |
| `skus[].identifier_code` | object |  | A regulated identifier code assigned to a product based on international standardized regulations (e.g. |
| `skus[].identifier_code.code` | string | 10000000000010 | The identifier code. |
| `skus[].identifier_code.type` | string | GTIN | The type of identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].inventory` | []object |  | SKU inventory details. |
| `skus[].inventory[].backorder_quantity` | int | 888 | The `backorder_quantity` will automatically be converted to `quantity` once in-stock inventory is sold out. |
| `skus[].inventory[].handling_time` | int | 5 | The estimated number of working days needed for a backorder to be shipped. |
| `skus[].inventory[].quantity` | int | 999 | The total SKU quantity available in the warehouse. |
| `skus[].inventory[].warehouse_id` | string | 6966568648651605766 | The ID of the warehouse where the SKU is stored. |
| `skus[].list_price` | object |  | The SKU's list price information that has been verified to be legitimate by the audit team. |
| `skus[].list_price.amount` | string | 1 | The price amount. |
| `skus[].list_price.currency` | string | USD | The currency. |
| `skus[].pre_sale` | object |  | SKU presale information, used to tag a product as a presale product based on its presale type. |
| `skus[].pre_sale.fulfillment_type` | object |  | Information about the type of pre-sale order fulfillment and the corresponding timeframe. |
| `skus[].pre_sale.fulfillment_type.handling_duration_days` | int | 3 | The desired duration for handling a pre-sale order and handing it over to a shipping carrier. Giá trị: MADE_TO_ORDER, CUSTOM, PRE_ORDER |
| `skus[].pre_sale.fulfillment_type.release_date` | int | 1619611761 | The date on which the product gets converted into a regular product and becomes available for general purchase. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `skus[].pre_sale.type` | string | PRE_ORDER | The type of pre-sale. Giá trị: PRE_ORDER, MADE_TO_ORDER, CUSTOM |
| `skus[].price` | object |  | SKU pricing information. |
| `skus[].price.currency` | string | USD | The currency. Giá trị: BRL, CZK, EUR, GBP, HUF, IDR, JPY, MXN, MYR, PHP, PLN, SGD … |
| `skus[].price.sale_price` | string | 117.5 | **All sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.starting_bid_price` | string | 2 | starting bid price for auction product |
| `skus[].price.tax_exclusive_price` | string | 110 | **Global sellers** The SKU's **local pre-tax price**. |
| `skus[].price.unit_price` | string | 1 | The unit price of the SKU. |
| `skus[].sales_attributes` | []object |  | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | 100000 | The sales attribute ID. |
| `skus[].sales_attributes[].name` | string | Color | The sales attribute name. |
| `skus[].sales_attributes[].sku_img` | object |  | The default/main image for each value (e.g. |
| `skus[].sales_attributes[].sku_img.height` | int | 100 | The image height. |
| `skus[].sales_attributes[].sku_img.thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `skus[].sales_attributes[].sku_img.uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `skus[].sales_attributes[].sku_img.urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `skus[].sales_attributes[].sku_img.width` | int | 100 | The image width. |
| `skus[].sales_attributes[].supplementary_sku_images` | []object |  | A list of supplementary images for each value (e.g. |
| `skus[].sales_attributes[].supplementary_sku_images[].height` | int | 100 | The image height. |
| `skus[].sales_attributes[].supplementary_sku_images[].thumb_urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the image thumbnails. |
| `skus[].sales_attributes[].supplementary_sku_images[].uri` | string | tos-maliva-i-o3syd03w52-us/6c8519a3663a4… | The URI of the image. |
| `skus[].sales_attributes[].supplementary_sku_images[].urls` | []string | ["https://p16-oec-va.ibyteimg.com/tos-ma… | The URLs to view the images. |
| `skus[].sales_attributes[].supplementary_sku_images[].width` | int | 100 | The image width. |
| `skus[].sales_attributes[].value_id` | string | 100000 | The sales attribute value ID. |
| `skus[].sales_attributes[].value_name` | string | Red | The sales attribute value name. |
| `skus[].seller_sku` | string | sku name | An internal code/name for managing SKUs, not visible to buyers. |
| `skus[].sku_dimensions` | object |  | The dimensions of the sku. |
| `skus[].sku_dimensions.height` | string | 10 | The package height. |
| `skus[].sku_dimensions.length` | string | 10 | The package length. |
| `skus[].sku_dimensions.unit` | string | CENTIMETER | The unit for the SKU dimensions. Giá trị: KILOGRAM, CENTIMETER |
| `skus[].sku_dimensions.width` | string | 10 | The package width. |
| `skus[].sku_unit_count` | string | 1.00 | The total quantity/volume of the product represented by the SKU. |
| `skus[].sku_weight` | object |  | The packaged weight of the SKU. Giá trị: KILOGRAM, CENTIMETER |
| `skus[].sku_weight.unit` | string | KILOGRAM | The unit for the package weight. |
| `skus[].sku_weight.value` | string | 1.32 | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `skus[].status_info` | object |  | Status information of the SKU. |
| `skus[].status_info.deactivation_source` | string | PLATFORM | The deactivation source of the SKU with `DEACTIVATED` status. Giá trị: DEACTIVATED |
| `skus[].status_info.status` | string | DEACTIVATED | The SKU status in TikTok Shop. Giá trị: NORMAL, DEACTIVATED |
| `skus[].purchase_order_quantity_limit` | object |  | The customer purchase limits for this SKU. |
| `skus[].purchase_order_quantity_limit.maximum_order_quantity` | int | 2 | The maximum purchase quantity per order for each product variation (SKU). |
| `skus[].purchase_order_quantity_limit.cumulative_order_quantity` | int | 20 | · The total quantity a customer can buy of a product's variations (SKUs) across all orders, while the limit is active. |
| `status` | string | SELLER_DEACTIVATED | The product status in TikTok Shop. Giá trị: TIKTOK_SHOP |
| `subscribe_info` | object |  | All the Save and Subscribe promotion info associated with the given product. |
| `subscribe_info.subscribe_discount_details` | []object |  | All of the Save and Subscribe discount details. |
| `subscribe_info.subscribe_discount_details[].discount_level` | string | REGULAR | An enum that communicated the type of discount: - REGULAR - FIRST_ORDER - RETENTION Giá trị: REGULAR, FIRST_ORDER, RETENTION |
| `subscribe_info.subscribe_discount_details[].discount_value` | int | 10 | The value of the discount. |
| `subscribe_info.subscribe_promotion_config` | []object |  | A subscription configuration specific to the seller. |
| `subscribe_info.subscribe_promotion_config[].discount_level` | string | REGULAR | An enum that communicated the type of discount: - REGULAR - FIRST_ORDER - RETENTION Giá trị: REGULAR, FIRST_ORDER, RETENTION |
| `subscribe_info.subscribe_promotion_config[].discount_options` | []int | [5, 10, 15, 20] | The discount options available to the seller for the given discount level. |
| `subscribe_info.subscribe_promotion_config[].max_discount` | int | 90 | The maximum discount a seller can set for the associated discount level. |
| `subscribe_info.subscribe_promotion_config[].min_discount` | int | 5 | The minimum discount a seller can set for the associated discount level. |
| `subscribe_info.subscribe_status` | string | ENABLED | An enum outlining whether the given product has an active Subscribe and Save promotion: - ENABLED - NOT_ENABLED Giá trị: ENABLED, NOT_ENABLED |
| `subscribe_info.support_subscribe` | bool | false | A flag outlining whether the seller and given product support Subscribe and Save promotions. |
| `title` | string | Short Boat Invisible Socks | The product title. |
| `update_time` | int | 1234567899 | The time when the product was last updated, expressed as a Unix timestamp in seconds. |
| `video` | object |  | Product video information. |
| `video.cover_url` | string | https://p16-oec-va.ibyteimg.com/tos-mali… | The URL to view the video cover image. |
| `video.format` | string | MP4 | The video format. |
| `video.height` | int | 480 | The video height. |
| `video.id` | string | v09ea0g40000cj91373c77u3mid3g1s0 | The video ID. |
| `video.size` | int | 1000 | The video's original file size. |
| `video.url` | string | https://v16m-default.akamaized.net/bbae7… | The URL to view the video. |
| `video.width` | int | 1280 | The video width. |
| `selling_format` | string | FIXED_PRICE | The product's sale format. |
| `is_blind_box` | bool | false | Whether the product is a blind box of lucky bag. |
| `product_detail_url` | string | detail_url | Product detail URL supporting deep link to launch TikTok client. |

**Mã lỗi:** `12019150` The product ID does not exist.; `12052032` The product does not exist; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052260` The product ID does not exist; `12052547` There is no version of this product that is under review.; `12052704` seller id not exist; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.

### Create Product

`POST /product/202309/products` · scope: `seller.product.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `save_mode` | string | N | Indicates how the product should be saved. Giá trị: AS_DRAFT, LISTING |
| `description` | string | N | The product description in HTML format. |
| `category_id` | string | Y | The ID of the category of this product. Giá trị: INVITE_ONLY, PENDING, PRE_APPROVED |
| `brand_id` | string | N | The ID of the brand of this product. |
| `main_images` | []object | Y | A list of images to display in the product image gallery. |
| `main_images[].uri` | string | Y | The URI of the image. |
| `skus` | []object | Y | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].sales_attributes` | []object | N | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | N | The ID of a built-in sales attribute, retrieved from Get Attributes API. |
| `skus[].sales_attributes[].value_id` | string | N | The ID of a built-in sales attribute value, retrieved from the Get Attributes API. |
| `skus[].sales_attributes[].value_name` | string | N | A self-defined custom sales attribute value if the built-in values do not satisfy your needs. |
| `skus[].sales_attributes[].sku_img` | object | N | The default/main image for each value (e.g. |
| `skus[].sales_attributes[].sku_img.uri` | string | Y | The URI of the image. |
| `skus[].sales_attributes[].name` | string | N | A self-defined custom sales attribute name if the built-in attributes do not satisfy your needs. |
| `skus[].sales_attributes[].supplementary_sku_images` | []object | N | A list of supplementary images for each value (e.g. |
| `skus[].sales_attributes[].supplementary_sku_images[].uri` | string | Y | The URI of the image. |
| `skus[].inventory` | []object | Y | SKU inventory information. |
| `skus[].inventory[].warehouse_id` | string | Y | The ID of the warehouse where the SKU is stored. |
| `skus[].inventory[].quantity` | int | N | The total SKU quantity available in the warehouse. |
| `skus[].inventory[].backorder_quantity` | int | N | The `backorder_quantity` will automatically be converted to `quantity` once in-stock inventory is sold out. |
| `skus[].inventory[].handling_time` | int | N | The estimated number of working days needed for a backorder to be shipped. |
| `skus[].seller_sku` | string | N | An internal code/name for managing SKUs, not visible to buyers. |
| `skus[].price` | object | Y | SKU pricing information. |
| `skus[].price.amount` | string | N | **Local sellers/Intra-EU sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.currency` | string | Y | The currency. Giá trị: BRL, EUR, GBP, IDR, JPY, MXN, MYR, PHP, SGD, THB, USD, VND |
| `skus[].price.sale_price` | string | N | **Global sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.starting_bid_price` | string | N | starting bit price for auction product |
| `skus[].external_sku_id` | string | N | An external identifier used in an external ecommerce platform. |
| `skus[].identifier_code` | object | N | A regulated identifier code assigned to a product based on international standardized regulations (e.g. |
| `skus[].identifier_code.code` | string | N | The identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].identifier_code.type` | string | N | The type of identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].combined_skus` | []object | N | If this SKU belongs to a virtual bundle, this object contains the list of individual SKUs that form the bundle (e.g. |
| `skus[].combined_skus[].product_id` | string | Y | The ID of the source product included in the virtual bundle. |
| `skus[].combined_skus[].sku_id` | string | Y | The ID of the source SKU included in the virtual bundle. |
| `skus[].combined_skus[].sku_count` | int | Y | The quantity of the source SKU included in the virtual bundle. |
| `skus[].sku_unit_count` | string | N | The total quantity/volume of the product represented by the SKU. |
| `skus[].external_urls` | []string | N | A list of URLs for third-party product listing pages where consumers can place orders. |
| `skus[].extra_identifier_codes` | []string | N | If the SKU belongs to a virtual bundle (containing multiple individual SKUs), you can add up to 10 additional identifier codes here for the SKUs included in the… |
| `skus[].pre_sale` | object | N | SKU presale information, used to tag a product as a presale product based on its presale type. |
| `skus[].pre_sale.type` | string | N | The type of pre-sale. Giá trị: PRE_ORDER, MADE_TO_ORDER, CUSTOM |
| `skus[].pre_sale.fulfillment_type` | object | N | Information about the type of pre-sale order fulfillment and the corresponding timeframe. |
| `skus[].pre_sale.fulfillment_type.handling_duration_days` | int | N | The desired duration for handling a pre-sale order and handing it over to a shipping carrier. Giá trị: MADE_TO_ORDER, CUSTOM, PRE_ORDER |
| `skus[].pre_sale.fulfillment_type.release_date` | int | N | The date on which the product gets converted into a regular product and becomes available for general purchase. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `skus[].list_price` | object | N | The SKU's list price information. |
| `skus[].list_price.amount` | string | Y | The price amount. |
| `skus[].list_price.currency` | string | Y | The currency. |
| `skus[].external_list_prices` | []object | N | The SKU list price (e.g. |
| `skus[].external_list_prices[].source` | string | Y | The external ecommerce platform from which the price is sourced. Giá trị: SHOPIFY_COMPARE_AT_PRICE |
| `skus[].external_list_prices[].amount` | string | Y | The price amount. |
| `skus[].external_list_prices[].currency` | string | Y | The currency. |
| `skus[].fees` | []object | N | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].type` | string | N | The type of fee. |
| `skus[].fees[].amount` | string | N | The fee amount. Giá trị: PFAND |
| `skus[].fees[].additional_attribute` | string | N | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `skus[].sku_dimensions` | object | N | The dimensions of the sku. |
| `skus[].sku_dimensions.length` | string | Y | The package length. |
| `skus[].sku_dimensions.width` | string | Y | The package width. |
| `skus[].sku_dimensions.height` | string | Y | The package height. |
| `skus[].sku_dimensions.unit` | string | Y | The unit for the SKU dimensions. |
| `skus[].sku_weight` | object | N | The packaged weight of the SKU. Giá trị: KILOGRAM, CENTIMETER |
| `skus[].sku_weight.value` | string | Y | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `skus[].sku_weight.unit` | string | Y | The unit for the SKU weight. |
| `title` | string | Y | The product title. |
| `is_cod_allowed` | bool | N | A flag indicating whether to show the Cash On Delivery (COD) payment option during checkout. |
| `certifications` | []object | N | The list of certifications for your product. |
| `certifications[].id` | string | Y | The ID to identify the type of certification required for the product category. |
| `certifications[].images` | []object | N | A list of certification related images. |
| `certifications[].images[].uri` | string | Y | The URI of the image. |
| `certifications[].files` | []object | N | A list of certification related files. |
| `certifications[].files[].id` | string | Y | The ID of the certification file. |
| `certifications[].files[].name` | string | Y | The name of the certification file, including the file extension. |
| `certifications[].files[].format` | string | Y | The format of the certification file. |
| `certifications[].expiration_date` | int | N | The expiration date of this certification expressed in unix timestamp (seconds) UTC+0. |
| `package_dimensions` | object | N | The dimensions of the product package. |
| `package_dimensions.length` | string | Y | The package length. |
| `package_dimensions.width` | string | Y | The package width. |
| `package_dimensions.height` | string | Y | The package height. |
| `package_dimensions.unit` | string | Y | The unit for the package dimensions. |
| `product_attributes` | []object | N | A list of general attributes (e.g. |
| `product_attributes[].id` | string | Y | The ID of the product attribute, retrieved from the Get Attributes API. |
| `product_attributes[].values` | []object | Y | A list of selectable values for the product attribute. |
| `product_attributes[].values[].id` | string | N | The ID of a built-in product attribute value, retrieved from the Get Attributes API. |
| `product_attributes[].values[].name` | string | N | A self-defined custom product attribute value if the built-in values do not satisfy your needs. |
| `product_attributes[].values[].metric_standard_info` | object | N | Defines the measurement template information used to describe the measurement standards and metric values configured for the product. |
| `product_attributes[].values[].metric_standard_info.id` | string | N | The unique ID of the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos` | []object | N | The list of metric value entries under the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].id` | string | N | The unique ID of a metric item within the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].value` | string | N | The seller-provided value for the specified metric item in the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].unit_id` | string | N | Metric value module unit id. |
| `package_weight` | object | N | The weight of the product package. |
| `package_weight.value` | string | Y | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `package_weight.unit` | string | Y | The unit for the package weight. Giá trị: KILOGRAM, POUND, GRAM, CENTIMETER |
| `video` | object | N | A product introduction or promotion video to display for your product. |
| `video.id` | string | Y | The ID of the product video. |
| `external_product_id` | string | N | An external identifier used in an external ecommerce platform. |
| `delivery_option_ids` | []string | N | This field is supported for seller accounts in the following regions only: - ID - MX - MY - PH - SG - TH - VN For all other regions, this field is NOT used and… |
| `size_chart` | object | N | The measurement details of the product to help buyers find the right size. |
| `size_chart.image` | object | N | An image of the size chart. |
| `size_chart.image.uri` | string | Y | The URI of the size chart image. |
| `size_chart.template` | object | N | A TikTok Shop size chart template generated by the size chart tool in Seller Center > Manage Products > Bulk action > Batch manage size charts. |
| `size_chart.template.id` | string | Y | The size chart template ID. |
| `primary_combined_product_id` | string | N | If this product is associated with a virtual bundle, this value is the ID of the primary product in the bundle. |
| `is_not_for_sale` | bool | N | A flag indicating whether the product is not for sale and only available through Gift with Purchase (GWP) promotions. |
| `category_version` | string | N | The category tree version to assign this product to. |
| `manufacturer_ids` | []string | N | A list of manufacturer IDs. |
| `responsible_person_ids` | []string | N | A list of responsible person IDs. |
| `listing_platforms` | []string | N | The platforms for listing the product. Giá trị: TOKOPEDIA, TIKTOK_SHOP |
| `shipping_insurance_requirement` | string | N | The shipping insurance purchase requirement imposed on buyers for the product. Giá trị: REQUIRED, OPTIONAL, NOT_SUPPORTED |
| `minimum_order_quantity` | int | N | The minimum order quantity for the product. |
| `is_pre_owned` | bool | N | A flag to indicate if the product is pre-owned. |
| `idempotency_key` | string | N | A unique key to recognize a request and prevent duplicate processing of the same request, especially in cases of connection issues. |
| `shipping_template_id` | string | N | Identifier of the shipping template that will be bound to the product |
| `scheduled_sale` | object | N | Scheduled listing configuration for the product, including whether scheduled listing is enabled and the scheduled listing time. |
| `scheduled_sale.is_enabled_scheduled_sale` | bool | Y | Whether scheduled listing is enabled for this product. |
| `scheduled_sale.schedule_sale_time` | int | Y | Scheduled listing time, expressed as a Unix timestamp in milliseconds; must be later than the current time and within 90 days, and is required when is_enabled_s… |
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `auto_translate_enabled` | bool | N | The field works only if products need to list with more than one languages in determined country, otherwise it does not need to fill in. |
| `search_terms` | []string | N | Search terms (ST words). |
| `key_product_features` | []string | N | Key Product Features. |
| `product_tag_operations` | []object | N | product tag to identity special type product.Possible values: AUCTION |
| `product_tag_operations[].product_tag` | string | Y | Possible values: AUCTION |
| `product_tag_operations[].enable` | bool | Y | For the `AUCTION` product tag, set this to `true` to enable auction support. Giá trị: AUCTION |
| `purchase_order_quantity_limit` | object | N | Purchase quantity limits applied to each SKU of the product. |
| `purchase_order_quantity_limit.maximum_order_quantity` | int | N | The maximum purchase quantity per order for each product variation (SKU). |
| `purchase_order_quantity_limit.cumulative_order_quantity` | int | N | · The total quantity a customer can buy of a product's variations (SKUs) across all orders, while the limit is active. |
| `selling_format` | string | N | Choose either Fixed price or Fixed price and LIVE auction. |
| `is_live_quick` | bool | N | false — NORMAL products true — LIVE_QUICK products. |
| `is_blind_box` | bool | N | Whether the product is a blind box of lucky bag. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `product_id` | string | 1729592969712207008 | The product ID generated by TikTok Shop. |
| `skus` | []object |  | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].id` | string | 1729592969712207012 | The SKU ID generated by TikTok Shop. |
| `skus[].seller_sku` | string | Color-Red-XM001 | An internal code/name for managing SKUs, not visible to buyers. |
| `skus[].sales_attributes` | []object |  | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | 100000 | The sales attribute ID. |
| `skus[].sales_attributes[].value_id` | string | 1729592969712207123 | The sales attribute value ID. |
| `skus[].external_sku_id` | string | 1729592969712207234 | An external identifier used in an external ecommerce platform. |
| `skus[].fees` | []object |  | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].type` | string | PFAND | The type of fee. |
| `skus[].fees[].amount` | string | 1.01 | The fee amount. Giá trị: PFAND |
| `skus[].fees[].additional_attribute` | string | SINGLE_USE | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `warnings` | []object |  | Warning information that the API caller needs to pay special attention to. |
| `warnings[].message` | string | The [brand_id]:123 field is incorrect an… | A warning message that the API caller should review and address when applicable. |

**Mã lỗi:** `12001000` product api internal error; `12019006` product description is invalid; `12019011` product package weight is invalid; `12019012` product package size is invalid; `12019013` brand id is invalid; `12019059` seller sku is invalid; `12019061` Product weight or dimensions unit does not support the imperial units on non-US local products.; `12019095` `pre_sale.fulfillment_type` is missing or empty.; `12019096` The value for `pre_sale.type` is missing.; `12019097` `pre_sale.fulfillment_type.release_date` must be set to a non-zero value.; `12019098` `pre_sale.fulfillment_type.handling_duration_days` must be set to a non-zero value.; `12019099` The specified `pre_sale.type` is not supported in your region.; `12019100` All SKUs of a product must have the same `pre_sale.type`.; `12019215` deposit balance is not enough; `12052002` Incorrect category format; `12052003` Incorrect parcel height format; `12052004` Incorrect parcel width format; `12052005` Incorrect parcel length format; `12052006` Incorrect parcel weight format; `12052013` The product description cannot exceed maximum characters; `12052015` The product description is required; `12052023` Category does not exist; `12052024` Category is not final category; `12052025` The category is invalid; `12052026` Brand does not exist; `12052028` Main product image is required; `12052034` This product doesn't belong to this shop; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052050` A single product cannot have more than 100 different SKUs; `12052051` The product name exceed max limit characters; `12052054` The seller SKU text length cannot exceed max limit characters; `12052055` The inventory quantity for a SKU at a warehouse is outside the allowed range.; `12052056` The num of image in description cannot exceed max limit; `12052073` The product price is invalid; `12052084` The provided currency is not available for this shop/region.; `12052092` product sale price is invalid; `12052093` Exceeded the product listing limit for the seller.; `12052094` No multiple warehouse permission; `12052096` Each SKU must remain linked to at least one warehouse after the update.; `12052097` The warehouse does not exist; `12052104` property is required; `12052105` required qualification miss; `12052109` number of requests from merchants over limit; `12052115` seller has no warehouse; `12052116` product package size is invalid; `12052128` Size chart not found; `12052144` platform warehouse not support to edit; `12052151` product property value contain restricted words; `12052152` sale property name contains restricted words; `12052153` sale property value contains restricted words; `12052159` unique item total quantity must be one; `12052162` unique item sku count must be one and disable varialbes; `12052172` sale type is invalid; `12052181` The package weight of the product can not be zero.; `12052182` The '{{sub_property_name}}' field is required as you've selected '{{parent_property_value}}' for '{{parent_property_name}}'.; `12052183` The '{{sub_property_name}}' field should be left empty as you've selected '{{parent_property_value}}' for '{{parent_property_name}}'.; `12052200` brand is expired; `12052201` You are not authorized to sell this brand in this category.; `12052207` The seller's current brand authorization does not permit shipping this product from the specified warehouse.; `12052208` A brand authorization is required to publish this listing.; `12052217` All region shops must use V2 categories.; `12052219` instant product not support not for sale; `12052220` This category is prohibited or unsupported on TikTok Shop.; `12052221` Only whitelisted sellers are permitted to trade under current category; `12052222` category do not support cod; `12052223` This category is restricted.; `12052225` You are not authorized to sell in this category because it does not fall under your shop's main category.; `12052226` This category is restricted.; `12052227` the category is unauthorized or unavailable; `12052228` combo product not support not for sale; `12052229` pre order product not support not for sale; `12052230` Category version and categoryID are not matched.; `12052235` The format of url is invalid; `12052236` The protocol of the url is unsafe; `12052237` The path of url is unsafe; `12052238` The length of the url is greater than 200; `12052240` Do not support custom property.; `12052241` attribute name or attribute id is empty.; `12052242` The attribute name characters cannot exceed max_limit; `12052243` The product attribute or sale attribute name characters contain Chinese.; `12052244` The attribute name duplicate.; `12052245` The product attributes contained invalid characters.; `12052246` The {{property_type}} not support multi selected, attribute id is :{{property_id}}.; `12052247` Do not support custom product attribute.; `12052248` The {{property_type}} value name or attribute value id is empty.; `12052249` The {{property_type}} value name characters cannot exceed {{max_limit}}, attribute value name is :{{property_value_name}}.; `12052250` The {{property_type}} value name characters contain Chinese.; `12052251` Duplicated attribute values found for an attribute.; `12052253` Duplicate attribute value id; `12052254` Duplicate attribute id; `12052256` The {{property_name}} value need to be positive integers or positive decimals .; `12052260` The product ID does not exist; `12052261` product name is empty; `12052262` Chinese characters are not supported in product name; `12052263` product name prefix illegal; `12052268` seller does not support pre sale; `12052269` The selected time is outside the limit; `12052274` pre-sale and pre-order modes are mutually exclusive.; `12052275` sku's mto day is invalid; `12052278` seller does not support make to order; `12052282` Product manufacturer is required.; `12052283` The number of manufacturers cannot exceed the allowed limits.; `12052284` Manufacturer ID {{manufacturer_id}} is not found; `12052285` Manufacturer ID {{manufacturer_id}} is not associated with the seller; `12052287` Product responsible person is required; `12052288` The number of responsible person ids cannot exceed {{max_limit}}, and cannot less {{min_limit}}.; `12052289` Responsible person ID {{rp_id}} is not found; `12052290` Responsible person ID {{rp_id}} is not associated with the seller; `12052291` Extra identification code information is not supported in this region.; `12052293` When identifier code is not filled in, extra identification code is not allowed to be filled in.; `12052294` The format of the extra identification code is incorrect.; `12052295` Duplicate manufacturer IDs associated with the same product; `12052296` Duplicate responsible person IDs associated with the same product; `12052300` product main image uri illegal; `12052301` Width and length of main image must be at least {{min_limit}}, check uri {{uri}}; `12052302` The main images size exceed limit.; `12052304` Main product image format not support.; `12052305` The main images aspect ratio cannot exceed max limit.; `12052306` main product images count exceed limit; `12052309` The seller does not have backorder permission.; `12052310` The futures information filled in by the seller does not meet the requirements.; `12052311` The number of backorder product exceeds the limit max_num, or the proportion of backorder product exceeds the limit max_ratio; `12052313` This product does not allow backorder inventory.; `12052316` product second hand not applicable in this sale platform; `12052324` Product description image file format is not supported; `12052330` product is not supported to list in designated sale platform; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052340` product description image uri illegal; `12052341` The description images size cannot exceed limit.; `12052342` The description images space cannot exceed limit.; `12052343` Product description image format not support.; `12052344` The product description html syntax with error; `12052345` The product description html tag not support.; `12052346` The product description has Chinese characters; `12052348` The product description html tag required attribute is miss.; `12052349` The product description html tag not support nest.; `12052350` The product description html tag contain illegal attribute.; `12052352` The product description nest exceeding limit; `12052356` The number of 'supplementary_sku_images' for the sales attribute value exceeds the maximum limit.; `12052357` 'supplementary_sku_images' is only allowed when 'sku_img' is specified.; `12052358` Tokopedia platform doesn't support this kind of product; `12052360` The video id is not exist.; `12052361` This base unit value count is not supported.; `12052362` SKU unit count is required.; `12052364` The warehouse is unavailable because this product cannot be shipped to the target market from this warehouse.; `12052365` The manufacturer lacks language version information for this market.; `12052366` The responsible person lacks language version information for this market.; `12052369` external_id length check fail; `12052372` Generate EU energy label image failed; `12052375` product list price is invalid; `12052376` list price not support; `12052382` Can't edit Dilayani Tokopedia warehouse quantity; `12052383` product list price exceed limit; `12052385` Please enter valid certificate expiration date; `12052402` seller shipping template is empty; `12052403` The warehouse does not support fulfillment for the current shop.; `12052404` multi-warehouse not support customize logistics service; `12052405` The warehouse has not opened the subscribed logistics service; `12052419` The shop tasks necessary for posting products have not been completed.; `12052420` warehouse didn't set logistics service; `12052424` Missing 'sale price' value; `12052446` Category is not open in this market; `12052488` This region doesn't support save draft after product submitted; `12052490` The handling time must be within the allowed limits.; `12052495` Pfand packaging type is required; `12052497` Pfand field is not supported in this country; `12052498` Pfand amount should not be filled.; `12052520` product sale property image uri illegal; `12052521` The sale property images size over limit.; `12052522` Product sale property image is required; `12052523` Product sale property image format not support.; `12052524` The sale property image aspect ratio invalid; `12052525` The attribute max num cannot exceed 3.; `12052526` The attribute value max num over limit.; `12052527` The sale attribute id not exist.; `12052528` You can add images to only 1 product variation, which will serve as the primary variation for display in the product options gallery.; `12052529` The property value id not exist.; `12052530` The warehouse ' {{warehouse ID}} ' does not belong to this shop/seller.; `12052531` The specified warehouse is disabled or otherwise unavailable for this operation.; `12052532` the warehouse not delivery warehouse; `12052535` Couldn't publish this product as you haven't set the return warehouse for your shop.; `12052543` Products that support pfand are not supported in the creation of Virtual Bundles.; `12052549` The current shipping template is unavailable due to the unavailability of shipping method.; `12052550` SKU property must contain all properties; `12052554` sku name contain Chinese characters; `12052555` The warehouse id is duplicate; `12052560` The SKU contains duplicate sales attribute.; `12052570` product price exceed limit; `12052571` Missing price for SKU(s); `12052574` unit price config is invalid; `12052585` deposit balance is not enough; `12052591` Invalid number of digits of identifier code; `12052592` Identifier code already entered, cannot enter the same code twice; `12052598` Only the last digit supports input X; `12052600` The identifier code type must be selected; `12052603` Scheduled listing is not supported; `12052604` The scheduled listing time is invalid; `12052650` product qualification image uri illegal; `12052655` qualification id not exist; `12052656` qualification id duplicate; `12052657` qualification image and pdf file is out of limit.; `12052658` product qualification file uri illegal; `12052670` The size chart image URI is invalid.; `12052671` The sizechart images size over limit.; `12052673` Product sizechart image is required; `12052700` The seller is inactive.; `12052701` do not support cross-boarder seller create local product directly; `12052702` seller does not support pre-order; `12052703` The seller's tax identification number could not be validated.; `12052704` seller id not exist; `12052706` The minimum sales quantity should not be exceed limit; `12052707` Setting minimum sales quantity is not supported; `12052712` sku's pre_order is invalid; `12052722` The image URI does not exist in TikTok Shop.; `12052830` The number of sub-products contained in each combo cannot exceed {{max_limit}}.; `12052831` The number of sub-skus contained in each combo sku cannot exceed {{max_limit}}.; `12052832` The sub-SKU coefficient associated with each combo SKU cannot exceed {{max_limit}}, and cannot less {{min_limit}}.; `12052833` The number of combo associated with a product cannot exceed the max limit.; `12052834` The number of combo sku associated with a sku cannot exceed limit.; `12052835` Sub product ID not exist; `12052836` The category of the combo is inconsistent with the category of the main sub-product.; `12052837` The category of the combo is not in the set of restricted categories.; `12052838` The first-level categories of the sub-products in the combo are inconsistent; `12052840` Sub-skus in a combo sku cannot be duplicated; `12052841` The sub-sku relationship corresponding to different combo sku cannot be duplicated.; `12052842` The combo does not include the main sub-product; `12052844` The sub-sku is not in the sub-product; `12052845` Failed to create, does not support non-live status product; `12052846` combo does not support adding this product type; `12052848` Combo cannot set minimum sale quantity; `12052849` You don't have the permission to operate combo.; `12052851` The warehouse of combo sku is empty.; `12052853` The sale property of combo mismatch.; `12052854` The sale property of combo is not unique.; `12052855` The warehouse of combo mismatch.; `12052856` The combo does not support customize logistics service; `12052857` Combined products and normal products cannot be converted to each other.; `12052858` The SKU of a combined product cannot be associated with the SKU of other combined products.; `12052859` When the combined SKU is only associated with one sub-SKU, the coefficient of this sub-SKU cannot be less than 2.; `12052861` The price of combined SKU cannot exceed the sum of the prices of the sub-SKUs.; `12052862` The brand of the combined product must be within the range of sub-product brands.; `12052863` All SKU of a combined product need to be the combined SKU.; `12052864` Unable to add deactivated SKUs to the combined product.; `12052881` identity internal error; `36009003` Internal error.; `12052910` The warehouse update parameters are invalid.; `12052915` package weight unit and dimension unit miss match.; `12052923` contact info required; `12052930` Seller SKU is required; `12052931` The title must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u007F).; `12052932` The description must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \\u007F)…; `12052933` Sales attribute names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u…; `12052934` Sales attribute value names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.…; `12052935` Product attribute value names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (…; `12052936` Seller SKU format invalid.; `12052990` The product failed one or more validation checks.; `12052992` no permission to create gift product; `12052994` seller is not toko seller; `12052995` seller has not finished onboarding to tts; `12052996` external_id duplicated; `33001002` internal error; `36009002` Too many requests.; `36009004` invalid param error; `98001004` Parameter {param_name} is invalid.

### Edit Product

`PUT /product/202509/products/{product_id}` · scope: `seller.product.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `save_mode` | string | N | Indicates how the product should be saved. Giá trị: DELETED, PENDING, FREEZE |
| `description` | string | N | The product description in HTML format. |
| `category_id` | string | Y | The ID of the category of this product. Giá trị: INVITE_ONLY, PENDING, PRE_APPROVED |
| `brand_id` | string | N | The ID of the brand of this product. |
| `main_images` | []object | Y | A list of images to display in the product image gallery. |
| `main_images[].uri` | string | Y | The URI of the image. |
| `skus` | []object | Y | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].id` | string | N | The SKU ID generated by TikTok Shop. |
| `skus[].sales_attributes` | []object | N | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | N | The ID of the sales attribute. |
| `skus[].sales_attributes[].name` | string | N | A self-defined custom sales attribute name if the existing attributes do not satisfy your needs. |
| `skus[].sales_attributes[].value_id` | string | N | The ID of the sales attribute value. |
| `skus[].sales_attributes[].value_name` | string | N | A self-defined custom sales attribute value if the existing values do not satisfy your needs. |
| `skus[].sales_attributes[].sku_img` | object | N | The default/main image for each value (e.g. |
| `skus[].sales_attributes[].sku_img.uri` | string | Y | The URI of the image. |
| `skus[].sales_attributes[].supplementary_sku_images` | []object | N | A list of supplementary images for each value (e.g. |
| `skus[].sales_attributes[].supplementary_sku_images[].uri` | string | Y | The URI of the image. |
| `skus[].seller_sku` | string | N | An internal code/name for managing SKUs, not visible to buyers. |
| `skus[].price` | object | N | SKU pricing information. |
| `skus[].price.amount` | string | N | **Local sellers/Intra-EU sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.currency` | string | Y | The currency. Giá trị: BRL, EUR, GBP, IDR, JPY, MXN, MYR, PHP, SGD, THB, USD, VND |
| `skus[].price.sale_price` | string | N | **Global sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.starting_bid_price` | string | N | starting bid price for auction product |
| `skus[].external_sku_id` | string | N | An external identifier used in an external ecommerce platform. |
| `skus[].identifier_code` | object | N | A regulated identifier code assigned to a product based on international standardized regulations (e.g. |
| `skus[].identifier_code.code` | string | N | The identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].identifier_code.type` | string | N | The type of identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].inventory` | []object | N | SKU inventory information. |
| `skus[].inventory[].warehouse_id` | string | Y | The ID of the warehouse where the SKU is stored. |
| `skus[].inventory[].quantity` | int | N | The total SKU quantity available in the warehouse. |
| `skus[].inventory[].backorder_quantity` | int | N | The `backorder_quantity` is automatically converted to `quantity` once in-stock inventory is sold out. |
| `skus[].inventory[].handling_time` | int | N | The estimated number of working days needed for a backorder to be shipped. |
| `skus[].combined_skus` | []object | N | If this SKU belongs to a virtual bundle, this object contains the list of individual SKUs that form the bundle (e.g. |
| `skus[].combined_skus[].product_id` | string | Y | The ID of the source product included in the virtual bundle. |
| `skus[].combined_skus[].sku_id` | string | Y | The ID of the source SKU included in the virtual bundle. |
| `skus[].combined_skus[].sku_count` | int | Y | The quantity of the source SKU included in the virtual bundle. |
| `skus[].sku_unit_count` | string | N | The total quantity/volume of the product represented by the SKU. |
| `skus[].external_urls` | []string | N | A list of URLs for third-party product listing pages where consumers can place orders. |
| `skus[].extra_identifier_codes` | []string | N | If the SKU belongs to a virtual bundle (containing multiple individual SKUs), you can add up to 10 additional identifier codes here for the SKUs included in the… |
| `skus[].pre_sale` | object | N | SKU presale information, used to tag a product as a presale product based on its presale type. |
| `skus[].pre_sale.type` | string | N | The type of pre-sale. Giá trị: PRE_ORDER, MADE_TO_ORDER, CUSTOM, NONE |
| `skus[].pre_sale.fulfillment_type` | object | N | Information about the type of pre-sale order fulfillment and the corresponding timeframe. |
| `skus[].pre_sale.fulfillment_type.handling_duration_days` | int | N | The desired duration for handling a pre-sale order and handing it over to a shipping carrier. Giá trị: MADE_TO_ORDER, CUSTOM, PRE_ORDER |
| `skus[].pre_sale.fulfillment_type.release_date` | int | N | The date on which the product gets converted into a regular product and becomes available for general purchase. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `skus[].list_price` | object | N | The SKU's list price information. |
| `skus[].list_price.amount` | string | Y | The price amount. |
| `skus[].list_price.currency` | string | Y | The currency. |
| `skus[].external_list_prices` | []object | N | The SKU list price (e.g. |
| `skus[].external_list_prices[].source` | string | Y | The external ecommerce platform from which the price is sourced. Giá trị: SHOPIFY_COMPARE_AT_PRICE |
| `skus[].external_list_prices[].amount` | string | Y | The price amount. |
| `skus[].external_list_prices[].currency` | string | Y | The currency. |
| `skus[].fees` | []object | N | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].type` | string | N | The type of fee. |
| `skus[].fees[].amount` | string | N | The fee amount. Giá trị: PFAND |
| `skus[].fees[].additional_attribute` | string | N | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `skus[].sku_dimensions` | object | N | The dimensions of the sku. |
| `skus[].sku_dimensions.length` | string | Y | The package length. |
| `skus[].sku_dimensions.width` | string | Y | The package width. |
| `skus[].sku_dimensions.height` | string | Y | The package height. |
| `skus[].sku_dimensions.unit` | string | Y | The unit for the SKU dimensions. |
| `skus[].sku_weight` | object | N | The packaged weight of the SKU. Giá trị: KILOGRAM, CENTIMETER |
| `skus[].sku_weight.value` | string | Y | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `skus[].sku_weight.unit` | string | Y | The unit for the SKU weight. |
| `title` | string | Y | The product title. |
| `is_cod_allowed` | bool | N | A flag indicating whether to show the Cash On Delivery (COD) payment option during checkout. |
| `certifications` | []object | N | The list of certifications for your product. |
| `certifications[].id` | string | Y | The ID to identify the type of certification required for the product category. |
| `certifications[].images` | []object | N | A list of certification related images. |
| `certifications[].images[].uri` | string | Y | The URI of the image. |
| `certifications[].files` | []object | N | A list of certification related files. |
| `certifications[].files[].id` | string | Y | The ID of the certification file. |
| `certifications[].files[].name` | string | Y | The name of the certification file, including the file extension. |
| `certifications[].files[].format` | string | Y | The format of the certification file. |
| `certifications[].expiration_date` | int | N | The expiration date of this certification expressed in unix timestamp (seconds) UTC+0. |
| `package_weight` | object | N | The weight of the product package. |
| `package_weight.value` | string | Y | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `package_weight.unit` | string | Y | The unit for the package weight. Giá trị: KILOGRAM, POUND, GRAM, CENTIMETER |
| `product_attributes` | []object | N | A list of general attributes (e.g. |
| `product_attributes[].id` | string | Y | The ID of the product attribute, retrieved from the Get Attributes API. |
| `product_attributes[].values` | []object | Y | A list of selectable values for the product attribute. |
| `product_attributes[].values[].id` | string | N | The ID of the product attribute value. |
| `product_attributes[].values[].name` | string | N | A self-defined custom product attribute value if the built-in values do not satisfy your needs. |
| `product_attributes[].values[].metric_standard_info` | object | N | Defines the measurement template information used to describe the measurement standards and metric values configured for the product. |
| `product_attributes[].values[].metric_standard_info.id` | string | N | The unique ID of the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos` | []object | N | The list of metric value entries under the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].id` | string | N | The unique ID of a metric item within the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].value` | string | N | The seller-provided value for the specified metric item in the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].unit_id` | string | N | Metric value module unit id. |
| `size_chart` | object | N | The measurement details of the product to help buyers find the right size. |
| `size_chart.image` | object | N | An image of the size chart. |
| `size_chart.image.uri` | string | Y | The URI of the size chart image. |
| `size_chart.template` | object | N | A TikTok Shop size chart template generated by the size chart tool in Seller Center > Manage Products > Bulk action > Batch manage size charts. |
| `size_chart.template.id` | string | Y | The size chart template ID. |
| `package_dimensions` | object | N | The dimensions of the product package. |
| `package_dimensions.length` | string | Y | The package length. |
| `package_dimensions.width` | string | Y | The package width. |
| `package_dimensions.height` | string | Y | The package height. |
| `package_dimensions.unit` | string | Y | The unit for the package dimensions. |
| `external_product_id` | string | N | An external identifier used in an external ecommerce platform. |
| `delivery_option_ids` | []string | N | This field is supported for seller accounts in the following regions only: - ID - MX - MY - PH - SG - TH - VN For all other regions, this field is NOT used and… |
| `video` | object | N | A product introduction or promotion video to display for your product. |
| `video.id` | string | Y | The ID of the product video. |
| `category_version` | string | N | The category tree version to assign this product to. |
| `manufacturer_ids` | []string | N | A list of manufacturer IDs. |
| `responsible_person_ids` | []string | N | A list of responsible person IDs. |
| `listing_platforms` | []string | N | The platforms for listing the product. Giá trị: TOKOPEDIA, TIKTOK_SHOP |
| `shipping_insurance_requirement` | string | N | The shipping insurance purchase requirement imposed on buyers for the product. Giá trị: REQUIRED, OPTIONAL, NOT_SUPPORTED |
| `is_pre_owned` | bool | N | A flag to indicate if the product is pre-owned. |
| `minimum_order_quantity` | int | N | The minimum order quantity for the product. |
| `replicated_products` | []object | N | The list of local market replicas to which updates should be synced. |
| `replicated_products[].region` | string | Y | The market where you want to sync the changes to. |
| `replicated_products[].skus` | []object | N | The SKUs to be synced to the specified market. |
| `replicated_products[].skus[].id` | string | N | The SKU ID generated by TikTok Shop. |
| `replicated_products[].skus[].seller_sku` | string | N | An internal code/name for managing SKUs, not visible to buyers. |
| `replicated_products[].skus[].price` | object | Y | The SKU's **local display price** shown on the product page before any discounts. |
| `replicated_products[].skus[].price.currency` | string | Y | The currency. Giá trị: EUR, GBP, IDR, JPY, MYR, PHP, SGD, THB, USD, VND |
| `replicated_products[].skus[].price.sale_price` | string | Y | The SKU's local display price shown on the product page before any discounts. |
| `replicated_products[].skus[].inventory` | []object | N | SKU inventory information per warehouse. |
| `replicated_products[].skus[].inventory[].warehouse_id` | string | N | The warehouse ID. |
| `replicated_products[].skus[].inventory[].quantity` | int | N | The SKU quantity available in the warehouse. |
| `replicated_products[].inventory_mode` | string | N | Defines the inventory mode for the duplicated target product. Giá trị: SHARED, EXCLUSIVE |
| `subscribe_info_edit` | object | N | All the editable Save and Subscribe promotion info associated with the given product. |
| `subscribe_info_edit.subscribe_status` | string | N | An enum outlining whether the given product has an active Subscribe and Save promotion: - ENABLED - NOT_ENABLED This field is required if the subscription is be… Giá trị: ENABLED, NOT_ENABLED |
| `subscribe_info_edit.discount_details` | []object | N | All of the Save and Subscribe discount details. |
| `subscribe_info_edit.discount_details[].discount_level` | string | N | An enum that communicated the type of discount: - REGULAR - FIRST_ORDER - RETENTION Giá trị: REGULAR, FIRST_ORDER, RETENTION |
| `subscribe_info_edit.discount_details[].discount_value` | int | N | The value of the discount. |
| `shipping_template_id` | string | N | Identifier of the shipping template that will be bound to the product |
| `scheduled_sale` | object | N | Scheduled listing configuration for the product, including whether scheduled listing is enabled and the scheduled listing time. |
| `scheduled_sale.is_enabled_scheduled_sale` | bool | Y | Whether scheduled listing is enabled for this product. |
| `scheduled_sale.schedule_sale_time` | int | Y | Scheduled listing time, expressed as a Unix timestamp in milliseconds; must be later than the current time and within 90 days, and is required when is_enabled_s… |
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `auto_translate_enabled` | bool | N | Whether to automatically translate the product content when listing the product in a market where multiple languages are needed. |
| `search_terms` | []string | N | Search terms (ST words). |
| `key_product_features` | []string | N | Key Product Features. |
| `inventory_mode` | string | N | Defines the inventory mode for the duplicated source product. Giá trị: SHARED, EXCLUSIVE |
| `product_tag_operations` | []object | N | Legacy operations for enabling or disabling the `AUCTION` product tag. Giá trị: AUCTION |
| `product_tag_operations[].product_tag` | string | Y | Possible values: AUCTION |
| `product_tag_operations[].enable` | bool | Y | Whether to enable the specified product tag. Giá trị: AUCTION |
| `purchase_order_quantity_limit` | object | N | Purchase quantity limits applied to each SKU of the product, including the maximum quantity per order and the cumulative quantity across orders. |
| `purchase_order_quantity_limit.maximum_order_quantity` | int | N | The maximum purchase quantity per order for each product variation (SKU). |
| `purchase_order_quantity_limit.cumulative_order_quantity` | int | N | · The total quantity a customer can buy of a product's variations (SKUs) across all orders, while the limit is active. |
| `selling_format` | string | N | Choose either Fixed price or Fixed price and LIVE auction. |
| `is_live_quick` | bool | N | false — NORMAL products true — LIVE_QUICK products. |
| `is_blind_box` | bool | N | Whether the product is a blind box of lucky bag. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `product_id` | string | 1729592969712207008 | The product ID generated by TikTok Shop. |
| `skus` | []object |  | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].id` | string | 1729592969712207012 | The SKU ID generated by TikTok Shop. |
| `skus[].seller_sku` | string | Color-Red-XM001 | An internal code/name for managing SKUs, not visible to buyers. |
| `skus[].sales_attributes` | []object |  | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | 100000 | The sales attribute ID. |
| `skus[].sales_attributes[].value_id` | string | 1729592969712207123 | The sales attribute value ID. |
| `skus[].external_sku_id` | string | 1729592969712207234 | An external identifier used in an external ecommerce platform. |
| `skus[].fees` | []object |  | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].type` | string | PFAND | The type of fee. |
| `skus[].fees[].amount` | string | 1.01 | The fee amount. Giá trị: PFAND |
| `skus[].fees[].additional_attribute` | string | SINGLE_USE | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `warnings` | []object |  | Warning information that the API caller needs to pay special attention to. |
| `warnings[].message` | string | The [brand_id]:123 field is incorrect an… | A warning message that the API caller should review and address when applicable. |
| `audit` | object |  | Product audit information. |
| `audit.status` | string | AUDITING | The product audit status. Giá trị: NONE, AUDITING, APPROVED |

**Mã lỗi:** `12019006` product description is invalid; `12019011` product package weight is invalid; `12019012` product package size is invalid; `12019013` brand id is invalid; `12019019` sale property is invalid; `12019022` SKU must contain a valid warehouse.; `12019056` sku not allow add; `12019057` sku not allow delete; `12019061` Product weight or dimensions unit does not support the imperial units on non-US local products.; `12019095` `pre_sale.fulfillment_type` is missing or empty.; `12019096` The value for `pre_sale.type` is missing.; `12019097` `pre_sale.fulfillment_type.release_date` must be set to a non-zero value.; `12019098` `pre_sale.fulfillment_type.handling_duration_days` must be set to a non-zero value.; `12019100` All SKUs of a product must have the same `pre_sale.type`.; `12019215` deposit balance is not enough; `12052002` Incorrect category format; `12052003` Incorrect parcel height format; `12052004` Incorrect parcel width format; `12052005` Incorrect parcel length format; `12052013` The product description cannot exceed maximum characters; `12052015` The product description is required; `12052023` Category does not exist; `12052024` Category is not final category; `12052025` The category is invalid; `12052026` Brand does not exist; `12052028` Main product image is required; `12052032` The product does not exist; `12052034` This product doesn't belong to this shop; `12052037` The inventory update or warehouse unlink operation failed for this SKU and warehouse.; `12052038` Product price locked due to ongoing promotion.; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052050` A single product cannot have more than 100 different SKUs; `12052051` The product name exceed max limit characters; `12052054` The seller SKU text length cannot exceed max limit characters; `12052055` The inventory quantity for a SKU at a warehouse is outside the allowed range.; `12052056` The num of image in description cannot exceed max limit; `12052073` The product price is invalid; `12052084` The provided currency is not available for this shop/region.; `12052092` product sale price is invalid; `12052094` No multiple warehouse permission; `12052095` Warehouse information parsing error; `12052096` Each SKU must remain linked to at least one warehouse after the update.; `12052097` The warehouse does not exist; `12052104` property is required; `12052105` required qualification miss; `12052115` seller has no warehouse; `12052116` product package size is invalid; `12052128` Size chart not found; `12052141` Inventory for this SKU must be managed at the global product level.; `12052144` platform warehouse not support to edit; `12052151` product property value contain restricted words; `12052152` sale property name contains restricted words; `12052153` sale property value contains restricted words; `12052158` category unable to change; `12052159` unique item total quantity must be one; `12052160` sku count must be one and disable variables; `12052162` unique item sku count must be one and disable varialbes; `12052163` The gift product are not allowed to be edited; `12052181` The package weight of the product can not be zero.; `12052182` The '{{sub_property_name}}' field is required as you've selected '{{parent_property_value}}' for '{{parent_property_name}}'.; `12052183` The '{{sub_property_name}}' field should be left empty as you've selected '{{parent_property_value}}' for '{{parent_property_name}}'.; `12052200` brand is expired; `12052201` You are not authorized to sell this brand in this category.; `12052207` The seller's current brand authorization does not permit shipping this product from the specified warehouse.; `12052208` A brand authorization is required to publish this listing.; `12052217` All region shops must use V2 categories.; `12052220` This category is prohibited or unsupported on TikTok Shop.; `12052221` Only whitelisted sellers are permitted to trade under current category; `12052222` category do not support cod; `12052223` This category is restricted.; `12052225` You are not authorized to sell in this category because it does not fall under your shop's main category.; `12052226` This category is restricted.; `12052227` the category is unauthorized or unavailable; `12052230` Category version and categoryID are not matched.; `12052235` The format of url is invalid; `12052238` The length of the url is greater than 200; `12052240` Do not support custom property.; `12052241` attribute name or attribute id is empty.; `12052242` The attribute name characters cannot exceed max_limit; `12052243` The product attribute or sale attribute name characters contain Chinese.; `12052244` The attribute name duplicate.; `12052245` The product attributes contained invalid characters.; `12052246` The {{property_type}} not support multi selected, attribute id is :{{property_id}}.; `12052247` Do not support custom product attribute.; `12052248` The {{property_type}} value name or attribute value id is empty.; `12052249` The {{property_type}} value name characters cannot exceed {{max_limit}}, attribute value name is :{{property_value_name}}.; `12052250` The {{property_type}} value name characters contain Chinese.; `12052251` Duplicated attribute values found for an attribute.; `12052253` Duplicate attribute value id; `12052254` Duplicate attribute id; `12052256` The {{property_name}} value need to be positive integers or positive decimals .; `12052260` The product ID does not exist; `12052261` product name is empty; `12052262` Chinese characters are not supported in product name; `12052263` product name prefix illegal; `12052273` After the pre-sale product has been onlive product, the pre-sale info cannot be modified.; `12052275` sku's mto day is invalid; `12052278` seller does not support make to order; `12052282` Product manufacturer is required.; `12052284` Manufacturer ID {{manufacturer_id}} is not found; `12052285` Manufacturer ID {{manufacturer_id}} is not associated with the seller; `12052287` Product responsible person is required; `12052289` Responsible person ID {{rp_id}} is not found; `12052294` The format of the extra identification code is incorrect.; `12052300` product main image uri illegal; `12052301` Width and length of main image must be at least {{min_limit}}, check uri {{uri}}; `12052302` The main images size exceed limit.; `12052304` Main product image format not support.; `12052305` The main images aspect ratio cannot exceed max limit.; `12052306` main product images count exceed limit; `12052307` skus in the lottery cannot be deleted; `12052308` There's an ongoing appeal.; `12052310` The futures information filled in by the seller does not meet the requirements.; `12052311` The number of backorder product exceeds the limit max_num, or the proportion of backorder product exceeds the limit max_ratio; `12052314` Standard product cannot be converted to MTO product; `12052315` Standard product cannot be converted to preorder product; `12052316` product second hand not applicable in this sale platform; `12052317` purchase order quantity not support; `12052319` maximum order quantity smaller than minimum order quantity; `12052324` Product description image file format is not supported; `12052329` A pre-order product can't be changed to a regular product; `12052330` product is not supported to list in designated sale platform; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052340` product description image uri illegal; `12052341` The description images size cannot exceed limit.; `12052342` The description images space cannot exceed limit.; `12052343` Product description image format not support.; `12052344` The product description html syntax with error; `12052345` The product description html tag not support.; `12052346` The product description has Chinese characters; `12052348` The product description html tag required attribute is miss.; `12052349` The product description html tag not support nest.; `12052350` The product description html tag contain illegal attribute.; `12052352` The product description nest exceeding limit; `12052357` 'supplementary_sku_images' is only allowed when 'sku_img' is specified.; `12052359` save subscribe not support; `12052360` The video id is not exist.; `12052361` This base unit value count is not supported.; `12052362` SKU unit count is required.; `12052364` The warehouse is unavailable because this product cannot be shipped to the target market from this warehouse.; `12052365` The manufacturer lacks language version information for this market.; `12052371` Auction product is bidding when it is not supported to change product info; `12052372` Generate EU energy label image failed; `12052375` product list price is invalid; `12052376` list price not support; `12052382` Can't edit Dilayani Tokopedia warehouse quantity; `12052383` product list price exceed limit; `12052385` Please enter valid certificate expiration date; `12052393` Product locked for Promotion and stock can't be updated below current stock; `12052402` seller shipping template is empty; `12052403` The warehouse does not support fulfillment for the current shop.; `12052404` multi-warehouse not support customize logistics service; `12052405` The warehouse has not opened the subscribed logistics service; `12052420` warehouse didn't set logistics service; `12052424` Missing 'sale price' value; `12052446` Category is not open in this market; `12052465` warehouse is unavailable for eu gpr product; `12052490` The handling time must be within the allowed limits.; `12052503` If the inventory allocation mode is SHARED, the inventory quantity must be the same across all SKUs.; `12052508` The SKU ID in `replicated_products` is invalid and cannot be found.; `12052509` Missing seller_sku in `replicated_products`.; `12052510` No matching seller_sku found in the source product.; `12052511` Missing SKU details in `replicated_products`.; `12052512` No replicas exist for this product.; `12052513` No replicas exist for this product in the stated region.; `12052514` Missing regions in `replicated_products`.; `12052515` Missing regions in `replicated_products`.; `12052516` Missing inventory details.; `12052520` product sale property image uri illegal; `12052521` The sale property images size over limit.; `12052522` Product sale property image is required; `12052523` Product sale property image format not support.; `12052524` The sale property image aspect ratio invalid; `12052525` The attribute max num cannot exceed 3.; `12052526` The attribute value max num over limit.; `12052527` The sale attribute id not exist.; `12052528` You can add images to only 1 product variation, which will serve as the primary variation for display in the product options gallery.; `12052529` The property value id not exist.; `12052530` The warehouse ' {{warehouse ID}} ' does not belong to this shop/seller.; `12052531` The specified warehouse is disabled or otherwise unavailable for this operation.; `12052532` the warehouse not delivery warehouse; `12052533` Removal, addition, and change of warehouses are not permitted.; `12052534` The warehouse cannot be unlinked because it has inventory reserved for unfulfilled orders, its inventory configuration does not support unlinking, or its invent…; `12052535` Couldn't publish this product as you haven't set the return warehouse for your shop.; `12052550` SKU property must contain all properties; `12052553` Sku id duplicate; `12052554` sku name contain Chinese characters; `12052557` The SKU ID does not belong to the product.; `12052558` Number of product skus exceed limit.; `12052559` The product with duplicate default sku.; `12052560` The SKU contains duplicate sales attribute.; `12052570` product price exceed limit; `12052571` Missing price for SKU(s); `12052572` the sale price is invalid; `12052574` unit price config is invalid; `12052592` Identifier code already entered, cannot enter the same code twice; `12052593` The identifier code cannot be changed once be submitted; `12052650` product qualification image uri illegal; `12052651` The qualification images size over limit.; `12052655` qualification id not exist; `12052656` qualification id duplicate; `12052657` qualification image and pdf file is out of limit.; `12052658` product qualification file uri illegal; `12052670` The size chart image URI is invalid.; `12052671` The sizechart images size over limit.; `12052673` Product sizechart image is required; `12052700` The seller is inactive.; `12052701` do not support cross-boarder seller create local product directly; `12052702` seller does not support pre-order; `12052703` The seller's tax identification number could not be validated.; `12052704` seller id not exist; `12052706` The minimum sales quantity should not be exceed limit; `12052707` Setting minimum sales quantity is not supported; `12052712` sku's pre_order is invalid; `12052805` Bundle does not support adding this product type; `12052815` product in bundle deal cannot set minimum sale quantity; `12052832` The sub-SKU coefficient associated with each combo SKU cannot exceed {{max_limit}}, and cannot less {{min_limit}}.; `12052834` The number of combo sku associated with a sku cannot exceed limit.; `12052835` Sub product ID not exist; `12052836` The category of the combo is inconsistent with the category of the main sub-product.; `12052838` The first-level categories of the sub-products in the combo are inconsistent; `12052841` The sub-sku relationship corresponding to different combo sku cannot be duplicated.; `12052843` The main sub-product in the combo cannot be changed; `12052844` The sub-sku is not in the sub-product; `12052846` combo does not support adding this product type; `12052847` The combo sku relationship cannot be changed; `12052850` The stock of combo does not support being edited.; `12052853` The sale property of combo mismatch.; `12052857` Combined products and normal products cannot be converted to each other.; `12052859` When the combined SKU is only associated with one sub-SKU, the coefficient of this sub-SKU cannot be less than 2.; `12052863` All SKU of a combined product need to be the combined SKU.; `12052864` Unable to add deactivated SKUs to the combined product.; `12052881` identity internal error; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.; `12052902` The SKU list is empty.; `12052910` The warehouse update parameters are invalid.; `12052915` package weight unit and dimension unit miss match.; `12052921` Couldn't edit the inventory quantity because this product shares the inventory with the source shop; `12052922` edit fields limit; `12052930` Seller SKU is required; `12052931` The title must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u007F).; `12052932` The description must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \\u007F)…; `12052933` Sales attribute names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u…; `12052934` Sales attribute value names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.…; `12052935` Product attribute value names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (…; `12052936` Seller SKU format invalid.; `12052994` seller is not toko seller

### Partial Edit Product

`POST /product/202509/products/{product_id}/partial_edit` · scope: `seller.product.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `auto_translate_enabled` | bool | N | Whether to automatically translate the product content when listing the product in a market where multiple languages are needed. |
| `brand_id` | string | N | The ID of the brand of this product. |
| `category_version` | string | N | Filter categories by the category tree version.Possible values based on region: - US, EU and SEA: v2, represents the 7-level category tree. |
| `certifications` | []object | N | The list of certifications for your product. |
| `certifications[].expiration_date` | int | N | The expiration date of this certification expressed in unix timestamp (seconds) UTC+0. |
| `certifications[].files` | []object | N | A list of certification related files. |
| `certifications[].files[].format` | string | Y | The format of the certification file. |
| `certifications[].files[].id` | string | Y | The ID of the certification file. |
| `certifications[].files[].name` | string | Y | The name of the certification file, including the file extension. |
| `certifications[].id` | string | Y | The ID to identify the type of certification required for the product category. |
| `certifications[].images` | []object | N | A list of certification related images. |
| `certifications[].images[].uri` | string | Y | The URI of the image. |
| `description` | string | N | The product description in HTML format. |
| `external_product_id` | string | N | An external identifier used in an external ecommerce platform. |
| `inventory_mode` | string | N | Defines the inventory mode for the duplicated source product. Giá trị: SHARED, EXCLUSIVE |
| `is_cod_allowed` | bool | N | A flag indicating whether to show the Cash On Delivery (COD) payment option during checkout. |
| `key_product_features` | []string | N | Key Product Features. |
| `listing_platforms` | []string | N | The platforms for listing the product. Giá trị: TOKOPEDIA, TIKTOK_SHOP |
| `locale` | string | N | The BCP-47 locale code used when retrieving the existing product information for this edit. |
| `main_images` | []object | N | A list of images to display in the product image gallery. |
| `main_images[].uri` | string | Y | The URI of the image. |
| `manufacturer_ids` | []string | N | A list of manufacturer IDs. |
| `package_dimensions` | object | N | The dimensions of the product package. |
| `package_dimensions.height` | string | Y | The package height. |
| `package_dimensions.length` | string | Y | The package length. |
| `package_dimensions.unit` | string | Y | The unit for the package dimensions. |
| `package_dimensions.width` | string | Y | The package width. |
| `package_weight` | object | N | The weight of the product package. |
| `package_weight.unit` | string | Y | The unit for the package weight. Giá trị: KILOGRAM, POUND, GRAM, CENTIMETER |
| `package_weight.value` | string | Y | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `pod_info` | object | N | POD customization configuration for an MTO product. |
| `pod_info.pod_templates` | []object | N | A list of POD customization templates. |
| `pod_info.pod_templates[].pod_template_id` | string | N | The unique identifier of the POD customization template. |
| `pod_info.pod_templates[].remark` | string | N | Optional notes or instructions for this customization template. |
| `pod_info.pod_templates[].sku_ids` | []string | N | A list of product SKU IDs associated with this customization template. |
| `pod_info.pod_templates[].surfaces` | []object | N | A list of customizable product surfaces under this template, such as the front side, back side, collar, or sleeve. |
| `pod_info.pod_templates[].surfaces[].background_image` | object | N | The background image of the customizable surface. |
| `pod_info.pod_templates[].surfaces[].background_image.uri` | string | N | The URI of the background image. |
| `pod_info.pod_templates[].surfaces[].custom_areas` | []object | N | A list of customizable areas on this surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_name` | string | N | The name of the customizable area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement` | object | N | The position, size, and rotation information of the customizable area on the surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.height` | string | N | The height of the customizable area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.rotate_degree` | string | N | The rotation angle of the customizable area, in degrees. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.width` | string | N | The width of the customizable area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.x_coordinate` | string | N | The X-coordinate of the customizable area's starting point on the surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_placement.y_coordinate` | string | N | The Y-coordinate of the customizable area's starting point on the surface. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].area_type` | string | N | The type of the customizable area. Giá trị: TEXT, IMAGE |
| `pod_info.pod_templates[].surfaces[].custom_areas[].external_area_id` | string | N | The seller-defined external identifier of the customizable area, used for mapping with the seller's own system. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area` | object | N | Configuration for an image-type customization area. Giá trị: IMAGE |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image` | object | N | Optional default image displayed in this area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.default_image.uri` | string | N | The URI of the default image. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.min_height` | int | N | The minimum required height, in pixels, for an image uploaded by the consumer. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].image_area.min_width` | int | N | The minimum required width, in pixels, for an image uploaded by the consumer. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].is_required` | bool | N | Specifies whether the consumer must provide customization content for this area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area` | object | N | Configuration for a text-type customization area. Giá trị: TEXT |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.align_type` | string | N | The text alignment type. Giá trị: LEFT, CENTER, RIGHT |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.colors` | []string | N | A list of text colors available for consumers to choose from, using hexadecimal color values such as `#FFFFFF`. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.default_text` | string | N | Optional default text displayed in this area. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.fonts` | []string | N | A list of font keys available for consumers to choose from, based on the platform-supported font library. |
| `pod_info.pod_templates[].surfaces[].custom_areas[].text_area.max_text_length` | int | N | The maximum number of characters allowed for consumer text input. |
| `pod_info.pod_templates[].surfaces[].external_surface_id` | string | N | The seller-defined external identifier of the customizable surface, used for mapping with the seller's own system. |
| `pod_info.pod_templates[].surfaces[].mask_image` | object | N | An optional mask image placed above the customization areas to support irregular or cut-out effects. |
| `pod_info.pod_templates[].surfaces[].mask_image.uri` | string | N | The URI of the mask image. |
| `pod_info.pod_templates[].surfaces[].surface_name` | string | N | The name of the customizable surface, for example, `Front Side` or `Back Side`. |
| `pod_info.pod_templates[].template_name` | string | N | The name of the customization template, used by the seller for internal management. |
| `product_attributes` | []object | N | A list of general attributes (e.g. |
| `product_attributes[].id` | string | Y | The ID of the product attribute, retrieved from the Get Attributes API. |
| `product_attributes[].values` | []object | Y | A list of selectable values for the product attribute. |
| `product_attributes[].values[].id` | string | N | The ID of the product attribute value. |
| `product_attributes[].values[].name` | string | N | A self-defined custom product attribute value if the existing values do not satisfy your needs. |
| `product_attributes[].values[].metric_standard_info` | object | N | Defines the measurement template information used to describe the measurement standards and metric values configured for the product. |
| `product_attributes[].values[].metric_standard_info.id` | string | N | The unique ID of the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos` | []object | N | The list of metric value entries under the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].id` | string | N | The unique ID of a metric item within the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].value` | string | N | The seller-provided value for the specified metric item in the measurement template. |
| `product_attributes[].values[].metric_standard_info.metric_value_module_infos[].unit_id` | string | N | Metric value module unit id. |
| `replicated_products` | []object | N | The list of local market replicas to which updates should be synced. |
| `replicated_products[].inventory_mode` | string | N | Defines the inventory mode for the duplicated target product. Giá trị: SHARED, EXCLUSIVE |
| `replicated_products[].region` | string | Y | The local market replica region to which this product's updates should be synchronized. |
| `replicated_products[].skus` | []object | N | The SKUs to be synced to the specified market. |
| `replicated_products[].skus[].external_sku_id` | string | N | An external identifier used in an external ecommerce platform. |
| `replicated_products[].skus[].id` | string | N | The SKU ID generated by TikTok Shop. |
| `replicated_products[].skus[].inventory` | []object | N | SKU inventory information per warehouse. |
| `replicated_products[].skus[].inventory[].quantity` | int | N | The SKU quantity available in the warehouse. |
| `replicated_products[].skus[].inventory[].warehouse_id` | string | N | The warehouse ID. |
| `replicated_products[].skus[].price` | object | Y | The SKU's **local display price** shown on the product page before any discounts |
| `replicated_products[].skus[].price.currency` | string | Y | The currency. Giá trị: BRL, CZK, EUR, GBP, HUF, IDR, JPY, MXN, MYR, PHP, PLN, SGD … |
| `replicated_products[].skus[].price.sale_price` | string | Y | The SKU's local display price shown on the product page before any discounts. |
| `replicated_products[].skus[].seller_sku` | string | N | An internal code/name for managing SKUs, not visible to buyers. |
| `responsible_person_ids` | []string | N | A list of responsible person IDs. |
| `save_mode` | string | N | Indicates how the product should be saved. Giá trị: DELETED, PENDING, FREEZE |
| `scheduled_sale` | object | N | Scheduled listing configuration for the product, including whether scheduled listing is enabled and the scheduled listing time. |
| `scheduled_sale.is_enabled_scheduled_sale` | bool | Y | Whether scheduled listing is enabled for this product. |
| `scheduled_sale.schedule_sale_time` | int | Y | Scheduled listing time, expressed as a Unix timestamp in milliseconds; must be later than the current time and within 90 days, and is required when is_enabled_s… |
| `search_terms` | []string | N | Search terms (ST words). |
| `size_chart` | object | N | The measurement details of the product to help buyers find the right size. |
| `size_chart.image` | object | N | An image of the size chart. |
| `size_chart.image.uri` | string | Y | The URI of the size chart image. |
| `size_chart.template` | object | N | A TikTok Shop size chart template generated by the size chart tool in Seller Center > Manage Products > Bulk action > Batch manage size charts. |
| `size_chart.template.id` | string | Y | The size chart template ID. |
| `skus` | []object | N | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].external_list_prices` | []object | N | The SKU list price (e.g. |
| `skus[].external_list_prices[].amount` | string | Y | The price amount. |
| `skus[].external_list_prices[].currency` | string | Y | The currency. |
| `skus[].external_list_prices[].source` | string | Y | The external ecommerce platform from which the price is sourced. Giá trị: SHOPIFY_COMPARE_AT_PRICE |
| `skus[].external_sku_id` | string | N | An external identifier used in an external ecommerce platform. |
| `skus[].external_urls` | []string | N | A list of URLs for third-party product listing pages where consumers can place orders. |
| `skus[].extra_identifier_codes` | []string | N | If the SKU belongs to a virtual bundle (containing multiple individual SKUs), you can add up to 10 additional identifier codes here for the SKUs included in the… |
| `skus[].fees` | []object | N | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].additional_attribute` | string | N | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `skus[].fees[].amount` | string | N | The fee amount. Giá trị: PFAND |
| `skus[].fees[].type` | string | N | The type of fee. |
| `skus[].id` | string | N | The SKU ID generated by TikTok Shop. |
| `skus[].identifier_code` | object | N | A regulated identifier code assigned to a product based on international standardized regulations (e.g. |
| `skus[].identifier_code.code` | string | N | The identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].identifier_code.type` | string | N | The type of identifier code. Giá trị: GTIN, EAN, UPC, ISBN, JAN |
| `skus[].inventory` | []object | N | SKU inventory information. |
| `skus[].inventory[].backorder_quantity` | int | N | The `backorder_quantity` is automatically converted to `quantity` once in-stock inventory is sold out. |
| `skus[].inventory[].handling_time` | int | N | The estimated number of working days needed for a backorder to be shipped. |
| `skus[].inventory[].quantity` | int | Y | The total SKU quantity available in the warehouse. |
| `skus[].inventory[].warehouse_id` | string | Y | The ID of the warehouse where the SKU is stored. |
| `skus[].list_price` | object | N | The SKU's list price information. |
| `skus[].list_price.amount` | string | Y | The price amount. |
| `skus[].list_price.currency` | string | Y | The currency. |
| `skus[].pre_sale` | object | N | SKU presale information, used to tag a product as a presale product based on its presale type. |
| `skus[].pre_sale.fulfillment_type` | object | N | Information about the type of pre-sale order fulfillment and the corresponding timeframe. |
| `skus[].pre_sale.fulfillment_type.handling_duration_days` | int | N | The desired duration for handling a pre-sale order and handing it over to a shipping carrier. Giá trị: MADE_TO_ORDER, CUSTOM, PRE_ORDER |
| `skus[].pre_sale.fulfillment_type.release_date` | int | N | The date on which the product gets converted into a regular product and becomes available for general purchase. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `skus[].pre_sale.type` | string | N | The type of pre-sale. Giá trị: PRE_ORDER, MADE_TO_ORDER, CUSTOM |
| `skus[].price` | object | N | SKU pricing information. |
| `skus[].price.amount` | string | N | **Local sellers/Intra-EU sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.currency` | string | Y | The currency. Giá trị: BRL, CZK, EUR, GBP, HUF, IDR, JPY, MXN, MYR, PHP, PLN, SGD … |
| `skus[].price.sale_price` | string | N | **Global sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].sales_attributes` | []object | N | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | N | The ID of the sales attribute. |
| `skus[].sales_attributes[].name` | string | N | A self-defined custom sales attribute name if the existing attributes do not satisfy your needs. |
| `skus[].sales_attributes[].sku_img` | object | N | The default/main image for each value (e.g. |
| `skus[].sales_attributes[].sku_img.uri` | string | Y | The URI of the image. |
| `skus[].sales_attributes[].supplementary_sku_images` | []object | N | A list of supplementary images for each value (e.g. |
| `skus[].sales_attributes[].supplementary_sku_images[].uri` | string | Y | The URI of the image. |
| `skus[].sales_attributes[].value_id` | string | N | The ID of the sales attribute value. |
| `skus[].sales_attributes[].value_name` | string | N | A self-defined custom sales attribute value if the existing values do not satisfy your needs. |
| `skus[].seller_sku` | string | N | An internal code/name for managing SKUs, not visible to buyers. |
| `skus[].sku_dimensions` | object | N | The dimensions of the sku. |
| `skus[].sku_dimensions.height` | string | Y | The package height. |
| `skus[].sku_dimensions.length` | string | Y | The package length. |
| `skus[].sku_dimensions.unit` | string | Y | The unit for the SKU dimensions. Giá trị: KILOGRAM, CENTIMETER |
| `skus[].sku_dimensions.width` | string | Y | The package width. |
| `skus[].sku_unit_count` | string | N | The total quantity/volume of the product represented by the SKU. |
| `skus[].sku_weight` | object | N | The packaged weight of the SKU. |
| `skus[].sku_weight.unit` | string | Y | The unit for the SKU weight. Giá trị: KILOGRAM, CENTIMETER |
| `skus[].sku_weight.value` | string | Y | The package weight, which must be a positive number. Giá trị: GRAM, KILOGRAM, POUND |
| `subscribe_info_edit` | object | N | All of the Save and Subscribe discount details. |
| `subscribe_info_edit.discount_details` | []object | N | All of the Save and Subscribe discount details. |
| `subscribe_info_edit.discount_details[].discount_level` | string | N | An enum that communicated the type of discount: - REGULAR - FIRST_ORDER - RETENTION Giá trị: REGULAR, FIRST_ORDER, RETENTION |
| `subscribe_info_edit.discount_details[].discount_value` | int | N | The value of the discount. |
| `subscribe_info_edit.subscribe_status` | string | N | An enum outlining whether the given product has an active Subscribe and Save promotion: - ENABLED - NOT_ENABLED This field is required if the subscription is be… Giá trị: ENABLED, NOT_ENABLED |
| `title` | string | N | The product title. |
| `video` | object | N | A product introduction or promotion video to display for your product. |
| `video.id` | string | Y | The ID of the product video. |
| `is_blind_box` | bool | N | Whether the product is a blind box of lucky bag. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `audit` | object |  | Product audit information. |
| `audit.status` | string | AUDITING | The product audit status. Giá trị: NONE, AUDITING, APPROVED |
| `product_id` | string | 1729592969712207008 | The product ID generated by TikTok Shop. |
| `skus` | []object |  | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].external_sku_id` | string | 1729592969712207012 | An external identifier used in an external ecommerce platform. |
| `skus[].fees` | []object |  | The fees required for this product based on TikTok Shop policies. |
| `skus[].fees[].additional_attribute` | string | SINGLE_USE | An optional attribute that provides additional context for the fee. Giá trị: SINGLE_USE, REUSABLE, NOT_APPLICABLE |
| `skus[].fees[].amount` | string | 1.01 | The fee amount. Giá trị: PFAND |
| `skus[].fees[].type` | string | PFAND | The type of fee. |
| `skus[].id` | string | 1729592969712207012 | The SKU ID generated by TikTok Shop. |
| `skus[].sales_attributes` | []object |  | A list of attributes (e.g. |
| `skus[].sales_attributes[].id` | string | 100000 | The sales attribute ID. |
| `skus[].sales_attributes[].value_id` | string | 1729592969712207123 | The sales attribute value ID. |
| `skus[].seller_sku` | string | seller sku xxaa | An internal code/name for managing SKUs, not visible to buyers. |

**Mã lỗi:** `12019011` product package weight is invalid; `12019022` SKU must contain a valid warehouse.; `12019060` package_dimension_unit field is for America market(US) only.; `12019094` Conflicting presale fields. Giá trị: NONE; `12019096` The value for `pre_sale.type` is missing.; `12019097` `pre_sale.fulfillment_type.release_date` must be set to a non-zero value.; `12019098` `pre_sale.fulfillment_type.handling_duration_days` must be set to a non-zero value.; `12019099` The specified `pre_sale.type` is not supported in your region.; `12019100` All SKUs of a product must have the same `pre_sale.type`.; `12052003` Incorrect parcel height format; `12052004` Incorrect parcel width format; `12052005` Incorrect parcel length format; `12052013` The product description cannot exceed maximum characters; `12052015` The product description is required; `12052023` Category does not exist; `12052024` Category is not final category; `12052025` The category is invalid; `12052026` Brand does not exist; `12052028` Main product image is required; `12052031` Invalid product ID(s) found; `12052032` The product does not exist; `12052038` Product price locked due to ongoing promotion.; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052050` A single product cannot have more than 100 different SKUs; `12052051` The product name exceed max limit characters; `12052054` The seller SKU text length cannot exceed max limit characters; `12052055` The inventory quantity for a SKU at a warehouse is outside the allowed range.; `12052056` The num of image in description cannot exceed max limit; `12052073` The product price is invalid; `12052084` The provided currency is not available for this shop/region.; `12052092` product sale price is invalid; `12052094` No multiple warehouse permission; `12052096` Each SKU must remain linked to at least one warehouse after the update.; `12052104` property is required; `12052105` required qualification miss; `12052115` seller has no warehouse; `12052116` product package size is invalid; `12052128` Size chart not found; `12052144` platform warehouse not support to edit; `12052151` product property value contain restricted words; `12052152` sale property name contains restricted words; `12052153` sale property value contains restricted words; `12052157` One or more of the product attributes cannot be edited by cross-border sellers; `12052159` unique item total quantity must be one; `12052160` sku count must be one and disable variables; `12052163` The gift product are not allowed to be edited; `12052181` The package weight of the product can not be zero.; `12052182` The '{{sub_property_name}}' field is required as you've selected '{{parent_property_value}}' for '{{parent_property_name}}'.; `12052183` The '{{sub_property_name}}' field should be left empty as you've selected '{{parent_property_value}}' for '{{parent_property_name}}'.; `12052200` brand is expired; `12052201` You are not authorized to sell this brand in this category.; `12052208` A brand authorization is required to publish this listing.; `12052216` manufacturer info is invalid; `12052220` This category is prohibited or unsupported on TikTok Shop.; `12052221` Only whitelisted sellers are permitted to trade under current category; `12052222` category do not support cod; `12052223` This category is restricted.; `12052225` You are not authorized to sell in this category because it does not fall under your shop's main category.; `12052226` This category is restricted.; `12052227` the category is unauthorized or unavailable; `12052240` Do not support custom property.; `12052241` attribute name or attribute id is empty.; `12052242` The attribute name characters cannot exceed max_limit; `12052243` The product attribute or sale attribute name characters contain Chinese.; `12052244` The attribute name duplicate.; `12052245` The product attributes contained invalid characters.; `12052246` The {{property_type}} not support multi selected, attribute id is :{{property_id}}.; `12052247` Do not support custom product attribute.; `12052248` The {{property_type}} value name or attribute value id is empty.; `12052249` The {{property_type}} value name characters cannot exceed {{max_limit}}, attribute value name is :{{property_value_name}}.; `12052250` The {{property_type}} value name characters contain Chinese.; `12052251` Duplicated attribute values found for an attribute.; `12052253` Duplicate attribute value id; `12052254` Duplicate attribute id; `12052256` The {{property_name}} value need to be positive integers or positive decimals .; `12052260` The product ID does not exist; `12052261` product name is empty; `12052262` Chinese characters are not supported in product name; `12052263` product name prefix illegal; `12052269` The selected time is outside the limit; `12052273` After the pre-sale product has been onlive product, the pre-sale info cannot be modified.; `12052282` Product manufacturer is required.; `12052283` The number of manufacturers cannot exceed the allowed limits.; `12052284` Manufacturer ID {{manufacturer_id}} is not found; `12052285` Manufacturer ID {{manufacturer_id}} is not associated with the seller; `12052287` Product responsible person is required; `12052288` The number of responsible person ids cannot exceed {{max_limit}}, and cannot less {{min_limit}}.; `12052289` Responsible person ID {{rp_id}} is not found; `12052290` Responsible person ID {{rp_id}} is not associated with the seller; `12052292` The number of extra identification codes cannot exceed the allowed limits.; `12052294` The format of the extra identification code is incorrect.; `12052300` product main image uri illegal; `12052301` Width and length of main image must be at least {{min_limit}}, check uri {{uri}}; `12052302` The main images size exceed limit.; `12052304` Main product image format not support.; `12052305` The main images aspect ratio cannot exceed max limit.; `12052306` main product images count exceed limit; `12052307` skus in the lottery cannot be deleted; `12052308` There's an ongoing appeal.; `12052314` Standard product cannot be converted to MTO product; `12052315` Standard product cannot be converted to preorder product; `12052316` product second hand not applicable in this sale platform; `12052324` Product description image file format is not supported; `12052329` A pre-order product can't be changed to a regular product; `12052330` product is not supported to list in designated sale platform; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052340` product description image uri illegal; `12052341` The description images size cannot exceed limit.; `12052342` The description images space cannot exceed limit.; `12052343` Product description image format not support.; `12052344` The product description html syntax with error; `12052345` The product description html tag not support.; `12052346` The product description has Chinese characters; `12052348` The product description html tag required attribute is miss.; `12052349` The product description html tag not support nest.; `12052350` The product description html tag contain illegal attribute.; `12052352` The product description nest exceeding limit; `12052356` The number of 'supplementary_sku_images' for the sales attribute value exceeds the maximum limit.; `12052357` 'supplementary_sku_images' is only allowed when 'sku_img' is specified.; `12052359` save subscribe not support; `12052360` The video id is not exist.; `12052361` This base unit value count is not supported.; `12052362` SKU unit count is required.; `12052365` The manufacturer lacks language version information for this market.; `12052366` The responsible person lacks language version information for this market.; `12052371` Auction product is bidding when it is not supported to change product info; `12052375` product list price is invalid; `12052376` list price not support; `12052382` Can't edit Dilayani Tokopedia warehouse quantity; `12052383` product list price exceed limit; `12052385` Please enter valid certificate expiration date; `12052393` Product locked for Promotion and stock can't be updated below current stock; `12052402` seller shipping template is empty; `12052403` The warehouse does not support fulfillment for the current shop.; `12052520` product sale property image uri illegal; `12052521` The sale property images size over limit.; `12052522` Product sale property image is required; `12052523` Product sale property image format not support.; `12052524` The sale property image aspect ratio invalid; `12052525` The attribute max num cannot exceed 3.; `12052526` The attribute value max num over limit.; `12052527` The sale attribute id not exist.; `12052528` You can add images to only 1 product variation, which will serve as the primary variation for display in the product options gallery.; `12052529` The property value id not exist.; `12052530` The warehouse ' {{warehouse ID}} ' does not belong to this shop/seller.; `12052531` The specified warehouse is disabled or otherwise unavailable for this operation.; `12052532` the warehouse not delivery warehouse; `12052533` Removal, addition, and change of warehouses are not permitted.; `12052534` The warehouse cannot be unlinked because it has inventory reserved for unfulfilled orders, its inventory configuration does not support unlinking, or its invent…; `12052535` Couldn't publish this product as you haven't set the return warehouse for your shop.; `12052550` SKU property must contain all properties; `12052554` sku name contain Chinese characters; `12052557` The SKU ID does not belong to the product.; `12052558` Number of product skus exceed limit.; `12052559` The product with duplicate default sku.; `12052560` The SKU contains duplicate sales attribute.; `12052570` product price exceed limit; `12052571` Missing price for SKU(s); `12052572` the sale price is invalid; `12052574` unit price config is invalid; `12052591` Invalid number of digits of identifier code; `12052592` Identifier code already entered, cannot enter the same code twice; `12052593` The identifier code cannot be changed once be submitted; `12052598` Only the last digit supports input X; `12052600` The identifier code type must be selected; `12052650` product qualification image uri illegal; `12052655` qualification id not exist; `12052656` qualification id duplicate; `12052657` qualification image and pdf file is out of limit.; `12052670` The size chart image URI is invalid.; `12052673` Product sizechart image is required; `12052700` The seller is inactive.; `12052702` seller does not support pre-order; `12052703` The seller's tax identification number could not be validated.; `12052704` seller id not exist; `12052706` The minimum sales quantity should not be exceed limit; `12052712` sku's pre_order is invalid; `12052722` The image URI does not exist in TikTok Shop.; `12052805` Bundle does not support adding this product type; `12052842` The combo does not include the main sub-product; `12052846` combo does not support adding this product type; `12052847` The combo sku relationship cannot be changed; `12052849` You don't have the permission to operate combo.; `12052863` All SKU of a combined product need to be the combined SKU.; `12052864` Unable to add deactivated SKUs to the combined product.; `12052881` identity internal error; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.; `12052902` The SKU list is empty.; `12052910` The warehouse update parameters are invalid.; `12052915` package weight unit and dimension unit miss match.; `12052916` The package unit is not supported in the current region.; `12052921` Couldn't edit the inventory quantity because this product shares the inventory with the source shop; `12052922` edit fields limit; `12052930` Seller SKU is required; `12052931` The title must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u007F).; `12052932` The description must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \\u007F)…; `12052933` Sales attribute names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u…; `12052934` Sales attribute value names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.…; `12052935` Product attribute value names must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (…; `12052936` Seller SKU format invalid.; `12052994` seller is not toko seller; `12052995` seller has not finished onboarding to tts

### Update Inventory

`POST /product/202309/products/{product_id}/inventory/update` · scope: `seller.product.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `skus` | []object | Y | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].id` | string | Y | The SKU ID generated by TikTok Shop. Giá trị: FREEZE, DELETED |
| `skus[].inventory` | []object | N | SKU inventory information, including warehouse IDs and their corresponding quantities. |
| `skus[].inventory[].warehouse_id` | string | N | The ID of the warehouse where the SKU is stored. |
| `skus[].inventory[].quantity` | int | Y | The total SKU quantity available in the warehouse. |
| `skus[].inventory[].backorder_quantity` | int | N | The `backorder_quantity` is automatically converted to `quantity` when in-stock inventory is sold out. |
| `skus[].inventory[].handling_time` | int | N | The estimated number of working days needed for a backorder to be shipped. |
| `skus[].unlink_warehouse_ids` | []string | N | The IDs of the warehouses to unlink from the SKU. |
| `sku_warehouse_stock_update_mode` | string | N | The mode used to update warehouse inventory for a SKU. Giá trị: INCREMENTAL, FULL |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `errors` | []object |  | Inventory update errors for individual SKU and warehouse combinations. |
| `errors[].code` | int | 12052990 | The main error code. |
| `errors[].message` | string | Batch update inventory error. | The main error message. |
| `errors[].detail` | object |  | The details of the main error. |
| `errors[].detail.sku_id` | string | 1729592969712207013 | The ID of the SKU where the error occurred. |
| `errors[].detail.extra_errors` | []object |  | A list of secondary errors or issues related to the main error. |
| `errors[].detail.extra_errors[].warehouse_id` | string | 7068517275539719942 | The ID of the warehouse where the error occurred. |
| `errors[].detail.extra_errors[].code` | int | 12052097 | The secondary error code. |
| `errors[].detail.extra_errors[].message` | string | The warehouse does not exist | The secondary error message. |

**Mã lỗi:** `12019022` SKU must contain a valid warehouse.; `12019024` The inventory quantity is invalid.; `12019028` Multiple warehouses detected.; `12052031` Invalid product ID(s) found; `12052032` The product does not exist; `12052036` Provide a valid SKU ID for every SKU in the request.; `12052037` The inventory update or warehouse unlink operation failed for this SKU and warehouse.; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052050` A single product cannot have more than 100 different SKUs; `12052055` The inventory quantity for a SKU at a warehouse is outside the allowed range.; `12052094` No multiple warehouse permission; `12052096` Each SKU must remain linked to at least one warehouse after the update.; `12052097` The warehouse does not exist; `12052115` seller has no warehouse; `12052141` Inventory for this SKU must be managed at the global product level.; `12052160` sku count must be one and disable variables; `12052165` The requested update would leave insufficient inventory to cover campaign reservations or would violate the permitted available-stock limit.Adjust the inventory…; `12052207` The seller's current brand authorization does not permit shipping this product from the specified warehouse.; `12052260` The product ID does not exist; `12052308` There's an ongoing appeal.; `12052364` The warehouse is unavailable because this product cannot be shipped to the target market from this warehouse.; `12052371` Auction product is bidding when it is not supported to change product info; `12052393` Product locked for Promotion and stock can't be updated below current stock; `12052403` The warehouse does not support fulfillment for the current shop.; `12052465` warehouse is unavailable for eu gpr product; `12052486` In FULL mode, provide a non-empty inventory list for every SKU.; `12052530` The warehouse ' {{warehouse ID}} ' does not belong to this shop/seller.; `12052531` The specified warehouse is disabled or otherwise unavailable for this operation.; `12052532` the warehouse not delivery warehouse; `12052533` Removal, addition, and change of warehouses are not permitted.; `12052534` The warehouse cannot be unlinked because it has inventory reserved for unfulfilled orders, its inventory configuration does not support unlinking, or its invent…; `12052535` Couldn't publish this product as you haven't set the return warehouse for your shop.; `12052553` Sku id duplicate; `12052556` The SKU id not exist.; `12052561` The requested inventory change could not be completed because insufficient stock was available.; `12052700` The seller is inactive.; `12052703` The seller's tax identification number could not be validated.; `12052704` seller id not exist; `12052849` You don't have the permission to operate combo.; `12052850` The stock of combo does not support being edited.; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.; `12052902` The SKU list is empty.; `12052910` The warehouse update parameters are invalid.; `12052921` Couldn't edit the inventory quantity because this product shares the inventory with the source shop; `12052990` The product failed one or more validation checks.

### Update Price

`POST /product/202309/products/{product_id}/prices/update` · scope: `seller.product.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `skus` | []object | Y | A list of Stock Keeping Units (SKUs) used to identify distinct variants of the product. |
| `skus[].id` | string | Y | The SKU ID generated by TikTok Shop. Giá trị: ACTIVATE |
| `skus[].price` | object | Y | SKU pricing information. |
| `skus[].price.amount` | string | N | **Local sellers/Intra-EU sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].price.currency` | string | Y | The currency. Giá trị: BRL, CZK, EUR, GBP, HUF, IDR, JPY, MXN, MYR, PHP, PLN, SGD … |
| `skus[].price.sale_price` | string | N | **Global sellers** The SKU's **local display price** shown on the product page before any discounts. |
| `skus[].list_price` | object | N | The SKU's list price information. |
| `skus[].list_price.amount` | string | Y | The price amount. |
| `skus[].list_price.currency` | string | Y | The currency. |
| `skus[].external_list_prices` | []object | N | The SKU list price (e.g. |
| `skus[].external_list_prices[].source` | string | Y | The external ecommerce platform from which the price is sourced. Giá trị: SHOPIFY_COMPARE_AT_PRICE |
| `skus[].external_list_prices[].amount` | string | Y | The price amount. |
| `skus[].external_list_prices[].currency` | string | Y | The currency. |

**Mã lỗi:** `12019022` SKU must contain a valid warehouse.; `12019045` price is locked; `12019059` seller sku is invalid; `12019113` product status is invalid; `12052012` Incorrect price format; `12052023` Category does not exist; `12052032` The product does not exist; `12052038` Product price locked due to ongoing promotion.; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052073` The product price is invalid; `12052084` The provided currency is not available for this shop/region.; `12052092` product sale price is invalid; `12052096` Each SKU must remain linked to at least one warehouse after the update.; `12052097` The warehouse does not exist; `12052163` The gift product are not allowed to be edited; `12052260` The product ID does not exist; `12052308` There's an ongoing appeal.; `12052371` Auction product is bidding when it is not supported to change product info; `12052376` list price not support; `12052383` product list price exceed limit; `12052424` Missing 'sale price' value; `12052531` The specified warehouse is disabled or otherwise unavailable for this operation.; `12052553` Sku id duplicate; `12052557` The SKU ID does not belong to the product.; `12052570` product price exceed limit; `12052572` the sale price is invalid; `12052700` The seller is inactive.; `12052703` The seller's tax identification number could not be validated.; `12052704` seller id not exist; `12052861` The price of combined SKU cannot exceed the sum of the prices of the sub-SKUs.; `12052881` identity internal error; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.; `12052902` The SKU list is empty.; `12052910` The warehouse update parameters are invalid.

### Inventory Search

`POST /product/202309/inventory/search` · scope: `seller.product.basic`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `product_ids` | []string | N | A list of product IDs used to search for inventory information. |
| `sku_ids` | []string | N | A list of SKU IDs used to search for inventory information. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `inventory` | []object |  | Inventory information for the requested products or SKUs. |
| `inventory[].product_id` | string | 1729592969712207008 | The ID of the requested product. |
| `inventory[].skus` | []object |  | The list of requested SKUs. |
| `inventory[].skus[].id` | string | 1729388324987897824 | The SKU ID generated by TikTok Shop. |
| `inventory[].skus[].seller_sku` | string | Color-Red-XM01 | An internal code/name for managing SKUs, not visible to buyers. |
| `inventory[].skus[].total_available_quantity` | int | 100 | The total number of units available for ordering across all warehouses. |
| `inventory[].skus[].total_committed_quantity` | int | 10 | The total number of units reserved by existing customer orders across all warehouses (and therefore not available for ordering). |
| `inventory[].skus[].warehouse_inventory` | []object |  | SKU warehouse inventory information. |
| `inventory[].skus[].warehouse_inventory[].warehouse_id` | string | 7054915283043174149 | The ID of the warehouse where the SKU is stored. |
| `inventory[].skus[].warehouse_inventory[].available_quantity` | int | 100 | The total number of units available for ordering in this warehouse. |
| `inventory[].skus[].warehouse_inventory[].committed_quantity` | int | 10 | The total number of units reserved by existing customer orders in this warehouse (and therefore not available for ordering). |
| `inventory[].skus[].total_available_inventory_distribution` | object |  | The distribution of the total available inventory for the SKU across different channels. |
| `inventory[].skus[].total_available_inventory_distribution.campaign_inventory` | []object |  | Inventory that is allocated to a specific campaign. |
| `inventory[].skus[].total_available_inventory_distribution.campaign_inventory[].quantity` | int | 30 | The number of units allocated. |
| `inventory[].skus[].total_available_inventory_distribution.campaign_inventory[].campaign_name` | string | Big Sale | The name of the associated campaign. |
| `inventory[].skus[].total_available_inventory_distribution.creator_inventory` | []object |  | Inventory that is allocated to TikTok creators. |
| `inventory[].skus[].total_available_inventory_distribution.creator_inventory[].quantity` | int | 20 | The number of units allocated. |
| `inventory[].skus[].total_available_inventory_distribution.creator_inventory[].creator_name` | string | py001 | The name of the associated TikTok creator. |
| `inventory[].skus[].total_available_inventory_distribution.in_shop_inventory` | object |  | The remaining inventory available for the shop after deducting those allocated for campaigns and creators. |
| `inventory[].skus[].total_available_inventory_distribution.in_shop_inventory.quantity` | int | 50 | The number of units allocated. |

**Mã lỗi:** `36009003` Internal error.; `12019008` Product id is invalid; `12019015` The number of SKU exceed the limit; `12019022` SKU must contain a valid warehouse.; `12019112` seller id is invalid; `12019114` Seller has no permission; `12019120` The number of provided product IDs exceeds the limit.

### Activate Products

`POST /product/202309/products/activate` · scope: `seller.product.basic`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `product_ids` | []string | Y | The product IDs to activate. |
| `listing_platforms` | []string | N | The listing platforms where the product will be activated. Giá trị: TOKOPEDIA, TIKTOK_SHOP |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `errors` | []object |  | The errors for individual products that could not be activated. |
| `errors[].code` | int | 12052990 | The main error code. |
| `errors[].message` | string | Batch activate business error. | The main error message. |
| `errors[].detail` | object |  | The details of the main error. |
| `errors[].detail.product_id` | string | 1729382588639839583 | The ID of the product that could not be activated. |
| `errors[].detail.extra_errors` | []object |  | A list of further nested errors or issues related to the main error. |
| `errors[].detail.extra_errors[].code` | int | 12052048 | The secondary error code. |
| `errors[].detail.extra_errors[].message` | string | You can't edit other sellers' products | The secondary error message. |

**Mã lỗi:** `12019022` SKU must contain a valid warehouse.; `12019120` The number of provided product IDs exceeds the limit.; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052093` Exceeded the product listing limit for the seller.; `12052330` product is not supported to list in designated sale platform; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052700` The seller is inactive.; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.; `12052910` The warehouse update parameters are invalid.; `12052990` The product failed one or more validation checks.

### Deactivate Products

`POST /product/202309/products/deactivate` · scope: `seller.product.basic`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `product_ids` | []string | Y | The product IDs to deactivate. |
| `listing_platforms` | []string | N | The listing platforms where the product will be deactivated. Giá trị: TOKOPEDIA, TIKTOK_SHOP |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `errors` | []object |  | The errors for individual products that could not be deactivated. |
| `errors[].code` | int | 12052048 | The main error code. |
| `errors[].message` | string | You can't edit other sellers' products | The main error message. |
| `errors[].detail` | object |  | The details of the main error. |
| `errors[].detail.product_id` | string | 1729382588639839583 | The ID of the product that could not be deactivated. |

**Mã lỗi:** `12019120` The number of provided product IDs exceeds the limit.; `12052032` The product does not exist; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052330` product is not supported to list in designated sale platform; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052371` Auction product is bidding when it is not supported to change product info; `12052700` The seller is inactive.; `12052703` The seller's tax identification number could not be validated.; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.; `12052910` The warehouse update parameters are invalid.

### Delete Products

`DELETE /product/202309/products` · scope: `seller.product.delete`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `product_ids` | []string | Y | The product IDs to delete. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `errors` | []object |  | The errors for individual products that could not be deleted. |
| `errors[].code` | int | 12052048 | The error code. |
| `errors[].message` | string | You can't edit other sellers' products. | The error message. |
| `errors[].detail` | object |  | The details of the error. |
| `errors[].detail.product_id` | string | 1729382588639839583 | The ID of the product that could not be deleted. |

**Mã lỗi:** `12019120` The number of provided product IDs exceeds the limit.; `12052032` The product does not exist; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052371` Auction product is bidding when it is not supported to change product info; `12052377` The product has dt stocks, not allow to delete.; `12052700` The seller is inactive.; `12052703` The seller's tax identification number could not be validated.; `12052705` seller can not manage local product.; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.

### Recover Products

`POST /product/202309/products/recover` · scope: `seller.product.delete`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `product_ids` | []string | Y | The product IDs to recover. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `errors` | []object |  | The errors for individual products that could not be recovered. |
| `errors[].code` | int | 12052048 | The main error code. |
| `errors[].message` | string | You can't edit other sellers' products | The main error message. |
| `errors[].detail` | object |  | The details of the main error. |
| `errors[].detail.product_id` | string | 1729382588639839583 | The ID of the product that could not be recovered. |

**Mã lỗi:** `12019120` The number of provided product IDs exceeds the limit.; `12052032` The product does not exist; `12052048` You cannot edit the specified product(s) as it isn't listed in your account or shop.; `12052332` The seller center is locked during shop integration, only editing stock and price is allowed; `12052700` The seller is inactive.; `12052703` The seller's tax identification number could not be validated.; `12052705` seller can not manage local product.; `36009003` Internal error.; `12052901` The product in its current status is not available for this operation.

### Get Categories

`GET /product/202309/categories` · scope: `seller.product.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `keyword` | string | N | Filter categories by this keyword in `local_name`. |
| `category_version` | string | N | Filter categories by the category tree version.Possible values based on region: - US, EU and SEA: v2, represents the 7-level category tree. |
| `listing_platform` | string | N | Filter categories by the specified platform. Giá trị: TIKTOK_SHOP, TOKOPEDIA |
| `include_prohibited_categories` | bool | N | A flag to indicate whether to include categories that are prohibited on TikTok Shop. |
| `is_live_quick` | bool | N | false — NORMAL products true — LIVE_QUICK products. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `categories` | []object |  | The list of categories that meet the query conditions. |
| `categories[].id` | string | 600002 | The category ID. |
| `categories[].parent_id` | string | 600001 | The parent category ID. |
| `categories[].local_name` | string | Home Supplies | The category name in the requested locale. |
| `categories[].is_leaf` | bool | false | A flag to indicate if the category is a leaf category. |
| `categories[].permission_statuses` | []string | ["INVITE_ONLY", "NON_MAIN_CATEGORY"] | The shop's permission statuses for this category. Giá trị: AVAILABLE, INVITE_ONLY, NON_MAIN_CATEGORY, PROHIBITED |

**Mã lỗi:** `12019210` publish param invalid; `12052023` Category does not exist; `12052217` All region shops must use V2 categories.; `12052230` Category version and categoryID are not matched.; `12052700` The seller is inactive.; `12052704` seller id not exist; `36009003` Internal error.

### Get Category Attributes

`GET /product/202309/categories/{category_id}/attributes` · scope: `seller.product.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `category_version` | string | N | Filter categories by the category tree version.Possible values based on region: - US, EU and SEA: v2, represents the 7-level category tree. |
| `is_live_quick` | bool | N | false — NORMAL products true — LIVE_QUICK products. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `attributes` | []object |  | The list of standard built-in product and sales attributes that are bound to the specified category, based on your shop's location. |
| `attributes[].id` | string | 100392 | The ID of the built-in attribute. |
| `attributes[].name` | string | Occasion | The name of the built-in attribute. |
| `attributes[].type` | string | PRODUCT_PROPERTY | The attribute type. Giá trị: SALES_PROPERTY, PRODUCT_PROPERTY |
| `attributes[].is_requried` | bool | false | A flag to indicate if the product attribute is always required when creating or editing a product. |
| `attributes[].values` | []object |  | A list of selectable values for the attribute. |
| `attributes[].values[].id` | string | 1001533 | The ID of the built-in attribute value. |
| `attributes[].values[].name` | string | Birthday | The name of the built-in attribute value. |
| `attributes[].values[].icon_url` | string | https://p16-oec-sg.ibyteimg.com/tos-alis… | The icon url value of the built-in attribute value. |
| `attributes[].value_data_format` | string | POSITIVE_INT_OR_DECIMAL | The data format for a free-form attribute value. Giá trị: POSITIVE_INT_OR_DECIMAL |
| `attributes[].is_customizable` | bool | true | A flag indicating whether sellers can enter a custom value for the attribute when creating or editing a product. |
| `attributes[].requirement_conditions` | []object |  | A list of conditions that determine if the product attribute is required based on the seller's inputs for other attributes. Giá trị: CAT |
| `attributes[].requirement_conditions[].condition_type` | string | VALUE_ID_MATCH | The type of condition. Giá trị: VALUE_ID_MATCH |
| `attributes[].requirement_conditions[].attribute_id` | string | 101610 | The ID of the product attribute that is being evaluated by the condition. |
| `attributes[].requirement_conditions[].attribute_value_id` | string | 1024358 | The ID of the product attribute value that must match the seller's input for the condition to be true. |
| `attributes[].is_multiple_selection` | bool | true | A flag indicating whether sellers can select multiple values for the attribute when creating or editing a product. |
| `attributes[].metric_standards` | []object |  | Defines the measurement template information used to describe the measurement standards and metric values configured for the product. |
| `attributes[].metric_standards[].id` | string | 7678592839487784712 | The unique ID of the measurement template. |
| `attributes[].metric_standards[].name` | string | prefix+num+units+suffix | The name of the measurement template. |
| `attributes[].metric_standards[].metric_value_modules` | []object |  | Metric value modules. |
| `attributes[].metric_standards[].metric_value_modules[].id` | string | 7678592839487735560 | The unique ID of a metric item within the measurement template. |
| `attributes[].metric_standards[].metric_value_modules[].name` | string | prefix+num | The seller-provided value for the specified metric item in the measurement template. |
| `attributes[].metric_standards[].metric_value_modules[].input_type` | string | CUSTOMIZED | Metric value modules input type. |
| `attributes[].metric_standards[].metric_value_modules[].data_types` | []string | POSITIVE_INTEGER | Metric value modules data type. |
| `attributes[].metric_standards[].metric_value_modules[].default_values` | []object |  | Metric value modules default values. |
| `attributes[].metric_standards[].metric_value_modules[].default_values[].id` | string | 7678592839487735560 | Metric value modules default value id. |
| `attributes[].metric_standards[].metric_value_modules[].default_values[].name` | string | 2026 | Metric value modules default value name. |
| `attributes[].metric_standards[].metric_value_modules[].units` | []object |  | Metric value modules units. |
| `attributes[].metric_standards[].metric_value_modules[].units[].id` | string | 7678592839484720903 | Unit id. |
| `attributes[].metric_standards[].metric_value_modules[].units[].name` | string | piece | Unit name. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit` | object |  | Metric value value limit. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit.num_limit` | object |  | Metric value num limit. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit.num_limit.min_value` | int | 1000 | Metric value num limit min value. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit.num_limit.max_value` | int | 1 | Metric value num limit max value. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit.length_limit` | object |  | Metric value length limit. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit.length_limit.min_length` | int | 10 | Metric value min length. |
| `attributes[].metric_standards[].metric_value_modules[].value_limit.length_limit.max_length` | int | 1 | Metric value max length. |
| `attributes[].metric_standards[].metric_value_modules[].prefix` | string | daily | Metric value prefix. |
| `attributes[].metric_standards[].metric_value_modules[].suffix` | string | and | Metric value suffix. |
| `attributes[].metric_standards[].metric_value_modules[].shading_word` | string | number2 | Metric value shading word. |

**Mã lỗi:** `12052023` Category does not exist; `12052024` Category is not final category; `12052025` The category is invalid; `12052217` All region shops must use V2 categories.; `12052230` Category version and categoryID are not matched.; `12052704` seller id not exist; `36009003` Internal error.

### Get Category Rules

`GET /product/202309/categories/{category_id}/rules` · scope: `seller.product.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `category_version` | string | N | Filter categories by the category tree version.Possible values based on region: - US, EU and SEA: v2, represents the 7-level category tree. |
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `product_certifications` | []object |  | Certification related rules. |
| `product_certifications[].id` | string | 602362 | The ID to identify the type of certification required. |
| `product_certifications[].name` | string | SNI Certificate | The name of the certification type. |
| `product_certifications[].is_required` | bool | true | A flag to indicate if the certification is always required when creating or editing a product. |
| `product_certifications[].document_details` | string | Upload a user manual or instructions in … | Details about the certification documents that you must submit, including the content, format, and any other guidelines. |
| `product_certifications[].sample_image_url` | string | https://p-boei18n.byted.org/tos-boei18n-… | The URL to view an image of the sample certification document. |
| `product_certifications[].requirement_conditions` | []object |  | A list of conditions that determine if the certification is required based on the seller's inputs for a product attribute. Giá trị: CAT |
| `product_certifications[].requirement_conditions[].condition_type` | string | VALUE_ID_MATCH | The type of condition. Giá trị: VALUE_ID_MATCH |
| `product_certifications[].requirement_conditions[].attribute_id` | string | 101610 | The ID of the product attribute that is being evaluated by the condition. |
| `product_certifications[].requirement_conditions[].attribute_value_id` | string | 1024358 | The ID of the product attribute value that must match the seller's input for the condition to be true. |
| `product_certifications[].expiration_date` | object |  | Certification expiration date related rules. |
| `product_certifications[].expiration_date.is_required` | bool | true | A flag to indicate whether an expiration date is required for this certification. |
| `size_chart` | object |  | Size chart related rules. |
| `size_chart.is_supported` | bool | true | A flag to indicate whether size charts are supported. |
| `size_chart.is_required` | bool | true | A flag to indicate whether a size chart is required. |
| `size_chart.measurement_and_options` | []object |  | Measurement rules grouped by measurement basis. |
| `size_chart.measurement_and_options[].size_systems` | []string | ["size_chart_size_system_us","size_chart… | Size system IDs supported by the current shop, determined by the shop region and seller type. |
| `size_chart.measurement_and_options[].measurement_type` | string | BODY_MEASUREMENTS | Possible values: GENERIC_MEASUREMENTS: generic measurements for categories that do not distinguish body and product measurements; BODY_MEASUREMENTS: shopper bod… |
| `size_chart.measurement_and_options[].options` | []object |  | Online measurement options for this group. |
| `size_chart.measurement_and_options[].options[].name` | string | Bust | Localized measurement name resolved using the request locale. |
| `size_chart.measurement_and_options[].options[].id` | string | size_chart_option_bust | Platform-defined standard measurement ID, for example size_chart_option_bust. |
| `size_chart.measurement_and_options[].options[].role` | string | KEY | Possible values: KEY: a key category measurement; RECOMMEND: a useful recommended measurement. |
| `size_chart.measurement_and_options[].options[].unit` | string | LENGTH | Possible values: NONE: no unit conversion; LENGTH: length values such as cm or in; WEIGHT: weight values such as kg or lb; HEIGHT: person-height values such as… |
| `size_chart.measurement_and_options[].options[].metric_value_range` | []string | ["70","160"] | Allowed metric range in [minimum, maximum] order. |
| `size_chart.measurement_and_options[].options[].imperial_value_range` | []string | ["27.5","63"] | Allowed imperial range in [minimum, maximum] order. |
| `size_chart.size_types` | []string | ["size_chart_size_type_regular","size_ch… | Available size types configured for this category. |
| `size_chart.size_systems` | []string | ["size_chart_size_system_us","size_chart… | Size system IDs supported by the current shop, determined by the shop region and seller type. |
| `cod` | object |  | Cash on Delivery (COD) related rules. |
| `cod.is_supported` | bool | true | A flag to indicate whether COD is supported. |
| `package_dimension` | object |  | Package dimension related rules. |
| `package_dimension.is_required` | bool | true | A flag to indicate whether package dimensions are required. |
| `epr` | object |  | Extended Producer Responsibility (EPR) related rules. |
| `epr.is_required` | bool | false | A flag to indicate whether EPR is required. |
| `responsible_person` | object |  | Responsible person (RP) related rules. |
| `responsible_person.is_required` | bool | false | A flag to indicate whether the responsible person information is required. |
| `manufacturer` | object |  | Manufacturer related rules. |
| `manufacturer.is_required` | bool | false | A flag to indicate whether the manufacturer information is required. |
| `allowed_special_product_types` | []string | ["PRE_ORDER"] | The list of special product types, excluding regular products, that your shop is allowed to list in this category. Giá trị: PRE_ORDER, MADE_TO_ORDER, CUSTOM |
| `fees` | []object |  | Product fees related rules. |
| `fees[].type` | string | PFAND | The type of fee. |
| `fees[].is_required` | bool | true | A flag to indicate whether information about the fee is required. |
| `seller_sku` | object |  | Seller SKU related rules. |
| `seller_sku.is_required` | bool | true | A flag to indicate whether a Seller SKU is required when creating or editing a product in this category. |

**Mã lỗi:** `12052023` Category does not exist; `12052024` Category is not final category; `12052217` All region shops must use V2 categories.; `12052220` This category is prohibited or unsupported on TikTok Shop.; `12052223` This category is restricted.; `12052226` This category is restricted.; `12052230` Category version and categoryID are not matched.; `12052446` Category is not open in this market; `36009003` Internal error.

### Get Brands

`GET /product/202309/brands` · scope: `seller.product.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `category_id` | string | N | Specify a category ID to show the availability of **authorized brands** in the category. |
| `is_authorized` | bool | N | Filter results by the brand authorization status. |
| `brand_name` | string | N | Filter results to include brand names that begin with the specified value. |
| `page_size` | int | Y | The number of results to be returned per page. |
| `page_token` | string | N | An opaque token used to retrieve the next page of a paginated result set. |
| `category_version` | string | N | Filter categories by the category tree version.Possible values based on region: - US, EU and SEA: v2, represents the 7-level category tree. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `brands` | []object |  | The list of brands that meet the query conditions. |
| `brands[].id` | string | 7082427311584347905 | The brand ID. |
| `brands[].name` | string | Teas | The brand name. |
| `brands[].authorized_status` | string | AUTHORIZED | A status to indicate whether the seller has obtained prior authorization to sell goods bearing the brand's trademark, name, or logo. Giá trị: UNAUTHORIEZD, AUTHORIZED |
| `brands[].is_t1_brand` | bool | true | A flag to indicate whether the brand is a T1 brand, which refers to internationally renowned brands that may have compliance risks and require sellers to obtain… |
| `brands[].brand_status` | string | AVAILABLE | The availability of an **authorized brand** in the requested category. Giá trị: AVAILABLE, UNAVAILABLE |
| `total_count` | int | 10000 | The total count of brands that meet the query conditions. |
| `next_page_token` | string | cGFnZV9udW1iZXI9Mg== | An opaque token used to retrieve the next page of a paginated result set. |

**Mã lỗi:** `12019108` page number is invalid; `12019109` page size is invalid; `12019123` product of pageSize and pageNumber exceeds the maximum limit; `12019124` pageSize and pageNumber need to be used together; `12052023` Category does not exist; `12052217` All region shops must use V2 categories.; `12052230` Category version and categoryID are not matched.; `12052700` The seller is inactive.; `12052704` seller id not exist; `36009003` Internal error.

### Recommend Category

`POST /product/202309/categories/recommend` · scope: `seller.product.basic`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `category_version` | string | N | Filter categories by the category tree version.Possible values based on region: - US, EU and SEA: v2, represents the 7-level category tree. |
| `description` | string | N | The product description in HTML format. |
| `images` | []object | N | Product images, including gallery images, images that appear in the description, product variant images. |
| `images[].uri` | string | N | The URI of the image. |
| `include_prohibited_categories` | bool | N | A flag to indicate whether to include matching categories that are prohibited on TikTok Shop. |
| `listing_platform` | string | N | Recommend categories that belong to the specified platform. Giá trị: TIKTOK_SHOP, TOKOPEDIA |
| `locale` | string | N | The BCP-47 locale codes for displaying category information. |
| `origin_platform_product_type` | string | N | The product type defined on the source platform. |
| `origin_platform_vendor` | string | N | The product vendor or brand name on the source platform. |
| `origin_platform_tags` | []string | N | Tags associated with the product on the source platform. |
| `origin_platform_category` | object | N | Category information assigned to the product on the source platform. |
| `origin_platform_category.id` | string | N | The category ID on the source platform. |
| `origin_platform_category.name` | string | N | The category name on the source platform. |
| `origin_platform_category.full_name` | string | N | The full category path or full category name on the source platform. |
| `origin_platform_category.is_leaf` | bool | N | Whether the category is a leaf category. |
| `origin_platform_category.is_root` | bool | N | Whether the category is a root category. |
| `origin_platform_product_meta_data` | object | N | Additional product metadata provided by the source platform. |
| `origin_platform_product_meta_data.attribute_value_list` | []object | N | A list of product metadata attributes and their values. |
| `origin_platform_product_meta_data.attribute_value_list[].name` | string | N | The metadata attribute name. |
| `origin_platform_product_meta_data.attribute_value_list[].values` | []string | N | The values associated with the metadata attribute. |
| `product_title` | string | Y | The product title. |
| `is_live_quick` | bool | N | false — NORMAL products true — LIVE_QUICK products. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `categories` | []object |  | The category hierarchy for the recommended leaf category, ordered from the top-level category to the leaf category. |
| `categories[].id` | string | 605254 | The category ID. |
| `categories[].is_leaf` | bool | true | A flag to indicate if the category is a leaf category. |
| `categories[].level` | int | 1 | The category level. |
| `categories[].name` | string | Teas | The category name. |
| `categories[].permission_statuses` | []string | ["INVITE_ONLY"] | The shop's permission status for this category. Giá trị: INVITE_ONLY |
| `leaf_category_id` | string | 605254 | The recommended category ID. |

**Mã lỗi:** `12019006` product description is invalid; `12019009` product image list is invalid; `12019064` No matching categories.; `12052013` The product description cannot exceed maximum characters; `12052051` The product name exceed max limit characters; `12052056` The num of image in description cannot exceed max limit; `12052217` All region shops must use V2 categories.; `12052230` Category version and categoryID are not matched.; `12052261` product name is empty; `12052262` Chinese characters are not supported in product name; `12052266` The product name contains non-English characters.; `12052300` product main image uri illegal; `12052301` Width and length of main image must be at least {{min_limit}}, check uri {{uri}}; `12052305` The main images aspect ratio cannot exceed max limit.; `12052306` main product images count exceed limit; `12052340` product description image uri illegal; `12052341` The description images size cannot exceed limit.; `12052343` Product description image format not support.; `12052345` The product description html tag not support.; `12052346` The product description has Chinese characters; `12052348` The product description html tag required attribute is miss.; `12052349` The product description html tag not support nest.; `12052350` The product description html tag contain illegal attribute.; `12052351` The product description contains non-English characters.; `12052352` The product description nest exceeding limit; `12052700` The seller is inactive.; `12052704` seller id not exist; `12052722` The image URI does not exist in TikTok Shop.; `36009003` Internal error.; `12052912` No matching category found within your account's main category.; `12052931` The title must follow these formatting rules: it cannot contain HTML escape characters (e.g., &nbsp;), emojis, or ASCII control characters (e.g., \u007F).

### Upload Product Image

`POST /product/202309/images/upload` · scope: `seller.product.basic`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `use_case` | string | N | The usage scenario of the image. Giá trị: MAIN_IMAGE, ATTRIBUTE_IMAGE |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `uri` | string | tos-maliva-i-o3syd03w52-us/c668cdf70b7f4… | The URI to identify the image in API requests and responses. |
| `url` | string | https://p-oec-va.ibyteimg.com/tos-maliva… | The URL to access and view the image. |
| `height` | int | 720 | The height of the image after aspect ratio adjustment. |
| `width` | int | 720 | The width of the image after aspect ratio adjustment. |
| `use_case` | string | MAIN_IMAGE | The usage scenario specified during upload. Giá trị: MAIN_IMAGE, ATTRIBUTE_IMAGE, DESCRIPTION_IMAGE, CERTIFICATION_IMAGE, SIZE_CHART_IMAGE, CUSTOMIZATION_IMAGE |

**Mã lỗi:** `11050001` invalid param; `12019116` The image dimensions exceed the allowed limits.; `36009003` Internal error.; `12038002` Invalid upload file.; `12038004` Failed to process the image.; `12038005` The file size exceeds the maximum allowed size.; `12038014` The image dimensions exceed the allowed limits.; `12038015` Failed to process the image.

## Orders

### Get Order List

`POST /order/202309/orders/search` · scope: `seller.order.info, seller.fs.order&fufillment.management`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `page_size` | int | Y | The number of results to be returned per page. |
| `sort_order` | string | N | The sort order for the `sort_field` parameter. Giá trị: ASC, DESC |
| `page_token` | string | N | An opaque token used to retrieve the next page of a paginated result set. |
| `sort_field` | string | N | The returned results will be sorted by the specified field. |

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `order_status` | string | N | Specific order status. Giá trị: UNPAID, ON_HOLD, AWAITING_SHIPMENT, PARTIALLY_SHIPPING, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `create_time_ge` | int | N | Filter orders to show only those that are created on or after the specified date and time. |
| `create_time_lt` | int | N | Filter orders to show only those that are created before the specified date and time. |
| `update_time_ge` | int | N | Filter orders to show only those that are updated on or after the specified date and time. |
| `update_time_lt` | int | N | Filter orders to show only those that are updated before the specified date and time. |
| `shipping_type` | string | N | The delivery method. Giá trị: TIKTOK, SELLER, TIKTOK_DIGITAL |
| `buyer_user_id` | string | N | Buyer user ID. |
| `is_buyer_request_cancel` | bool | N | Whether the buyer has initiated an order cancellation request. |
| `warehouse_ids` | []string | N | Filter orders by pickup/sales warehouse IDs. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `next_page_token` | string | 6AsPQsUMvH3RkchNUPPh22NROHkE0D8pmq/N5M1k… | An opaque token used to retrieve the next page of a paginated result set. |
| `total_count` | int | 22113 | Total number of orders in the search result. |
| `orders` | []object |  | Order information. |
| `orders[].id` | string | 576461413038785752 | TikTok Shop order ID. |
| `orders[].buyer_message` | string | Please ship asap! | The note from the buyer. |
| `orders[].cancellation_initiator` | string | SELLER | Cancellation request initiator. Giá trị: SELLER, BUYER, SYSTEM |
| `orders[].shipping_provider_id` | string | 6617675021119438849 | The ID of the current shipping provider. |
| `orders[].create_time` | int | 1619611561 | The date and time that the order was created. |
| `orders[].shipping_provider` | string | TT Virtual express | The name of the current shipping provider. |
| `orders[].packages` | []object |  | List of packages included in this order. |
| `orders[].packages[].id` | string | 1152321127278713123 | Package ID. |
| `orders[].payment` | object |  | Payment info about this order. |
| `orders[].payment.currency` | string | IDR | Currency for payment. |
| `orders[].payment.sub_total` | string | 5000 | Buyer paid sub-total of all the SKUs in the order. |
| `orders[].payment.shipping_fee` | string | 5000 | Buyer paid shipping fee. |
| `orders[].payment.seller_discount` | string | 5000 | Product discount by seller. |
| `orders[].payment.platform_discount` | string | 5000 | Product discount by platform. |
| `orders[].payment.payment_platform_discount` | string | 10 | Discount applied to the order funded by a payment platform |
| `orders[].payment.payment_discount_service_fee` | string | 10 | Service fee as paid by the buyer to apply payment platform discount |
| `orders[].payment.total_amount` | string | 5000 | Buyer paid total payment. |
| `orders[].payment.original_total_product_price` | string | 5000 | Total original price of the products (VAT included for cross-border shops). |
| `orders[].payment.original_shipping_fee` | string | 5000 | Shipping fee before discount. |
| `orders[].payment.shipping_fee_seller_discount` | string | 5000 | Shipping fee discount provided by seller for an order that will not qualify for co-funded reimbursement. |
| `orders[].payment.shipping_fee_platform_discount` | string | 5000 | Shipping fee discount provided by platform. |
| `orders[].payment.shipping_fee_cofunded_discount` | string | 5000 | Shipping fee discount provided by seller, eligible for co-funded reimbursement upon order delivery, based on Co-Funded Free Shipping program terms. |
| `orders[].payment.tax` | string | 5000 | Buyer paid total taxes for the order. |
| `orders[].payment.small_order_fee` | string | 3000 | Small order fee for TH (**Thailand market only**). |
| `orders[].payment.shipping_fee_tax` | string | 11 | The tax on the shipping price. |
| `orders[].payment.product_tax` | string | 21.3 | The tax on the total item price. |
| `orders[].payment.retail_delivery_fee` | string | 1.28 | Retail delivery fee (RDF). |
| `orders[].payment.buyer_service_fee` | string | 1000 | A service fee is charged on every transaction made. |
| `orders[].payment.handling_fee` | string | 1000 | A fee charged to the buyer to cover the additional processing, handling, and/or installment costs associated with the chosen payment method. |
| `orders[].payment.shipping_insurance_fee` | string | 1000 | The cost incurred by the buyer for coverage against loss or damage to goods during transit. |
| `orders[].payment.item_insurance_fee` | string | 1000 | The cost incurred by the buyers for coverage against defects or damage to the product after purchase. |
| `orders[].payment.item_insurance_tax` | string | 10 | The tax paid on the insurance purchased by buyers. |
| `orders[].payment.distance_shipping_fee` | string | 1000 | Distance shipping fee is fee that charged charged by our logistics partner and covers the separate distance-based cost for deliveries outside Java island as a p… |
| `orders[].payment.distance_fee` | string | 2000 | Total distance fee for Horizon+ Program. |
| `orders[].recipient_address` | object |  | `recipient_address` is not available under `UNPAID` and `ON_HOLD` statuses. Giá trị: UNPAID, ON_HOLD |
| `orders[].recipient_address.full_address` | string | 1199 Coleman Ave San Jose, CA 95110 | Complete recipient address information. |
| `orders[].recipient_address.phone_number` | string | (+1)213-***-1234 | Recipient telephone number. |
| `orders[].recipient_address.name` | string | David Kong | Recipient name. |
| `orders[].recipient_address.first_name` | string | David | Recipient first name. |
| `orders[].recipient_address.last_name` | string | Kong | Recipient last name. |
| `orders[].recipient_address.first_name_local_script` | string | ジョン | Recipient first name in katakana. |
| `orders[].recipient_address.last_name_local_script` | string | ドゥ | Recipient last name in katakana. |
| `orders[].recipient_address.address_detail` | string | Unit one building 8 | Full recipient detailed address. |
| `orders[].recipient_address.address_line1` | string | TikTok 5800 bristol Pkwy | The first line of the street address. |
| `orders[].recipient_address.address_line2` | string | Suite 100 | The second line of the street address. |
| `orders[].recipient_address.address_line3` | string |  | The third line of the street address. |
| `orders[].recipient_address.address_line4` | string |  | The fourth line of the street address. |
| `orders[].recipient_address.district_info` | []object |  | `district_info` is unavailable under `UNPAID` and `ON_HOLD` statuses. Giá trị: UNPAID, ON_HOLD |
| `orders[].recipient_address.district_info[].address_level_name` | string | Country | The name of administrative division that can be used by the seller for shipping. |
| `orders[].recipient_address.district_info[].address_name` | string | United Kingdom | Administrative area name. |
| `orders[].recipient_address.district_info[].address_level` | string | L0 | Administrative district level code. |
| `orders[].recipient_address.district_info[].iso_code` | string | VAN | ISO code of the administrative district level |
| `orders[].recipient_address.delivery_preferences` | object |  | Contains all of the delivery instructions provided by the recipient for the shipping address. |
| `orders[].recipient_address.delivery_preferences.drop_off_location` | string | Front Door | Drop-off location selected by the recipient. |
| `orders[].recipient_address.postal_code` | string | 95110 | The postal code that can be used by seller for shipping. |
| `orders[].recipient_address.region_code` | string | US | Region code. |
| `orders[].recipient_address.post_town` | string | Ribbleton | Post town of the address Note: Available only in UK market |
| `orders[].status` | string | UNPAID | Specific order status. Giá trị: UNPAID, ON_HOLD, AWAITING_SHIPMENT, PARTIALLY_SHIPPING, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `orders[].fulfillment_type` | string | FULFILLMENT_BY_SELLER | Fulfillment type. Giá trị: FULFILLMENT_BY_SELLER, FULFILLMENT_BY_TIKTOK, FULFILLMENT_BY_DILAYANI_TOKOPEDIA |
| `orders[].delivery_type` | string | HOME_DELIVERY | Indicates whether it is a Pick-Up Drop-Off (PUDO) location. Giá trị: HOME_DELIVERY, COLLECTION_POINT |
| `orders[].paid_time` | int | 1619611563 | The date and time that the order was paid. |
| `orders[].rts_sla_time` | int | 1619611688 | The latest shipping time specified by the platform. |
| `orders[].tts_sla_time` | int | 1619611761 | The latest collection time specified by the platform. |
| `orders[].cancel_reason` | string | Pricing error | The cancellation reason. |
| `orders[].update_time` | int | 1619621355 | Time of order status change. |
| `orders[].payment_method_name` | string | CCDC | Payment method name, for display purposes. |
| `orders[].rts_time` | int | 1619611563 | The time sellers shipped the order (called Ship Package API successfully). |
| `orders[].tracking_number` | string | JX12345 | Tracking number. |
| `orders[].split_or_combine_tag` | string | COMBINED | Indicates whether the order is combined or split: - `COMBINED` - `SPLIT` This field will be used in future fulfillment APIs. Giá trị: COMBINED, SPLIT |
| `orders[].has_updated_recipient_address` | bool | false | Whether the recipient address has been updated or changed. |
| `orders[].cancel_order_sla_time` | int | 1619621355 | The automatic cancellation time for orders specified by the platform. |
| `orders[].warehouse_id` | string | 6955005333819123123 | Seller warehouse ID. |
| `orders[].request_cancel_time` | int | 1678389618 | Buyer request cancellation time. |
| `orders[].shipping_type` | string | TIKTOK | Delivery method. Giá trị: TIKTOK, SELLER, TIKTOK_DIGITAL |
| `orders[].user_id` | string | 7021436810468230477 | Buyer user ID. |
| `orders[].seller_note` | string | seller note | The seller note from TikTok Shop Seller Center. |
| `orders[].delivery_sla_time` | int | 1678389618 | Order should arrive by this date to be considered on-time and to avoid late delivery penalties. |
| `orders[].is_cod` | bool | false | This option is for sellers that accept cash payment on delivery (COD). |
| `orders[].delivery_option_id` | string | 7091146663229654785 | Delivery option ID is mapped to seller configured logistics templates ID. |
| `orders[].cancel_time` | int | 1678389618 | The time an order's status was updated to `CANCELLED`. Giá trị: CANCELLED |
| `orders[].need_upload_invoice` | string | NEED_INVOICE | Whether an invoice needs to be uploaded and uploaded status (only for Brazil market). Giá trị: UNKNOWN, NEED_INVOICE, NO_NEED, INVOICE_UPLOADED, INVOICE_PROCESSING |
| `orders[].delivery_option_name` | string | Standard Shipping | Delivery option name. |
| `orders[].cpf` | string | 3213-31231412 | CPF (invoice number), used to issue an invoice. |
| `orders[].line_items` | []object |  | Line item info list. |
| `orders[].line_items[].id` | string | 577086512123755123 | Line item ID. |
| `orders[].line_items[].sku_id` | string | 2729382476852921560 | SKU ID. |
| `orders[].line_items[].combined_listing_skus` | []object |  | For a virtual bundle SKU, returns an array of related product SKUs that compose the virtual bundle. |
| `orders[].line_items[].combined_listing_skus[].sku_id` | string | 2729382476852921123 | The original SKU ID related to the virtual bundle SKU. |
| `orders[].line_items[].combined_listing_skus[].sku_count` | int | 1 | The quantity of original SKUs that compose the virtual bundle. |
| `orders[].line_items[].combined_listing_skus[].product_id` | string | 1729582718312380456 | The original product ID related to the virtual bundle SKU. |
| `orders[].line_items[].combined_listing_skus[].seller_sku` | string | yellow-24-XL | The original seller SKU (which is defined by the seller) related to the virtual bundle SKU. |
| `orders[].line_items[].display_status` | string | UNPAID | Available values: - `UNPAID`: The order has been placed, but payment has not yet completed. Giá trị: UNPAID, AWAITING_SHIPMENT, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `orders[].line_items[].product_name` | string | Women's Winter Crochet Clothes | Product name. |
| `orders[].line_items[].seller_sku` | string | red_iphone_256 | The seller stock keeping unit (SKU) of the item. |
| `orders[].line_items[].sku_image` | string | https://p16-oec-va.itexeitg.com/tos-mali… | SKU image. |
| `orders[].line_items[].sku_name` | string | Iphone | The name of the SKU, combined by product SKU attribute like size or color. |
| `orders[].line_items[].product_id` | string | 1729582718312380123 | Product ID. |
| `orders[].line_items[].sale_price` | string | 0.01 | Item sale price. |
| `orders[].line_items[].pfand_fee` | string | 1.50 | Deposit fee that is applied to certain products, typically beverage containers such as bottles or cans. |
| `orders[].line_items[].platform_discount` | string | 0 | Platform discount amount. |
| `orders[].line_items[].seller_discount` | string | 0 | Seller discount amount. |
| `orders[].line_items[].sku_type` | string | PRE_ORDER | [**Deprecated**: This field is deprecated and will be removed in a future API version. Giá trị: NORMAL, ZERO_LOTTERY, SHOP_PARTNER, PRE_ORDER, MADE_TO_ORDER |
| `orders[].line_items[].cancel_reason` | string | Discount not as expected | The cancellation reason. |
| `orders[].line_items[].original_price` | string | 0.01 | Item original price. |
| `orders[].line_items[].rts_time` | int | 1678389618 | The time sellers shipped the order (called Ship Package API successfully). |
| `orders[].line_items[].package_status` | string | TO_FULFILL | The package status of the item. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |
| `orders[].line_items[].currency` | string | IDR | Currency for payment. |
| `orders[].line_items[].shipping_provider_name` | string | TT Virtual express | The shipping provider name. |
| `orders[].line_items[].cancel_user` | string | BUYER | Cancellation request initiator. Giá trị: BUYER, SELLER, OPERATOR, SYSTEM |
| `orders[].line_items[].shipping_provider_id` | string | 6617675021119438849 | The shipping provider ID of the item. |
| `orders[].line_items[].is_gift` | bool | false | Indicates whether the current order line item is a gift. |
| `orders[].line_items[].item_tax` | []object |  | Item tax detail. |
| `orders[].line_items[].item_tax[].tax_type` | string | SALES_TAX | Tax type. Giá trị: SALES_TAX |
| `orders[].line_items[].item_tax[].tax_amount` | string | 21.2 | Tax amount. |
| `orders[].line_items[].item_tax[].tax_rate` | string | 0.35 | Tax rate. |
| `orders[].line_items[].tracking_number` | string | JX12345 | Tracking number. |
| `orders[].line_items[].package_id` | string | 1153132168123859123 | An order can contain one or more packages based on how the seller chooses to ship. |
| `orders[].line_items[].retail_delivery_fee` | string | 1.28 | Retail delivery fee (RDF). |
| `orders[].line_items[].buyer_service_fee` | string | 1000 | A service fee is charged on every transaction made. |
| `orders[].line_items[].small_order_fee` | string | 5000 | Small order fee for Thailand (TH). |
| `orders[].line_items[].handling_duration_days` | string | 7 | [**Deprecated**: This field is deprecated and will be removed in a future API version. Giá trị: MADE_TO_ORDER |
| `orders[].line_items[].is_dangerous_good` | bool | false | Whether the SKU is a hazmat item. |
| `orders[].line_items[].needs_prescription` | bool | false | A flag to indicate whether the included product requires a prescription. |
| `orders[].line_items[].gift_retail_price` | string | 20 | Retail price of the free item in orders Live giveaway, free sample and gift with purchase. |
| `orders[].line_items[].is_unboxing_item` | bool | false | If `true`, indicates the sku is a blind box item that will need be unboxed to reveal the actual product to the buyer. |
| `orders[].line_items[].unboxing_sku_code` | string | 14531134 | A seller's third party sku code for the actual unboxed item, which warehouse fulfillment operations can reference to identify the accurate product they are ship… |
| `orders[].line_items[].unboxing_case_list` | []object |  | A seller's third party sku code for the case unboxed item, which warehouse fulfillment operations can reference to identify the accurate product they are shippi… |
| `orders[].line_items[].unboxing_case_list[].unbox_sku` | string | 14531134 | Unboxed sku code associated with the actual unboxed item. |
| `orders[].line_items[].unboxing_case_list[].unbox_sku_type` | string | GIFT | Indicates if the unboxed sku is a gift item Enumerations: NORMAL or GIFT |
| `orders[].line_items[].room_id` | string | 7652884452049160991 | The unique ID of the LIVE session where the order line item was created. |
| `orders[].line_items[].sub_item_info` | []object |  | Sub items represent skus that belong to a "virtual bundle" that has been defined by a seller. |
| `orders[].line_items[].sub_item_info[].id` | string | 577086512123755123 | Line item id of the sub_item. |
| `orders[].line_items[].sub_item_info[].sku_id` | string | 2729382476852921560 | SKU ID. |
| `orders[].line_items[].sub_item_info[].display_status` | string | UNPAID | Available values: - `UNPAID`: The order has been placed, but payment has not yet completed. Giá trị: UNPAID, AWAITING_SHIPMENT, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `orders[].line_items[].sub_item_info[].product_name` | string | Women's Winter Crochet Clothes | Product name. |
| `orders[].line_items[].sub_item_info[].seller_sku` | string | red_iphone_256 | The seller stock keeping unit (SKU) of the item. |
| `orders[].line_items[].sub_item_info[].sku_image` | string | https://p16-oec-va.itexeitg.com/tos-mali… | SKU image. |
| `orders[].line_items[].sub_item_info[].sku_name` | string | Iphone | The name of the SKU, combined by product SKU attribute like size or color. |
| `orders[].line_items[].sub_item_info[].product_id` | string | 1729582718312380123 | Product ID. |
| `orders[].line_items[].sub_item_info[].sale_price` | string | 0.01 | Item sale price. |
| `orders[].line_items[].sub_item_info[].platform_discount` | string | 0 | Platform discount amount. |
| `orders[].line_items[].sub_item_info[].seller_discount` | string | 0 | Seller discount amount. |
| `orders[].line_items[].sub_item_info[].cancel_reason` | string | Discount not as expected | The cancellation reason. |
| `orders[].line_items[].sub_item_info[].original_price` | string | 0.01 | Item original price. |
| `orders[].line_items[].sub_item_info[].rts_time` | int | 1678389618 | The time sellers shipped the order (called Ship Package API successfully). |
| `orders[].line_items[].sub_item_info[].package_status` | string | TO_FULFILL | The package status of the item. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |
| `orders[].line_items[].sub_item_info[].currency` | string | USD | Currency for payment. |
| `orders[].line_items[].sub_item_info[].shipping_provider_name` | string | TT Virtual express | The shipping provider name. |
| `orders[].line_items[].sub_item_info[].cancel_user` | string | BUYER | Cancellation request initiator. Giá trị: BUYER, SELLER, OPERATOR, SYSTEM |
| `orders[].line_items[].sub_item_info[].shipping_provider_id` | string | 6617675021119438849 | The shipping provider ID of the item. |
| `orders[].line_items[].sub_item_info[].item_tax` | []object |  | Item tax detail. |
| `orders[].line_items[].sub_item_info[].item_tax[].tax_type` | string | SALES_TAX | Tax type. Giá trị: SALES_TAX |
| `orders[].line_items[].sub_item_info[].item_tax[].tax_amount` | string | 21.2 | Tax amount. |
| `orders[].line_items[].sub_item_info[].item_tax[].tax_rate` | string | 0.35 | Tax rate. |
| `orders[].line_items[].sub_item_info[].tracking_number` | string | JX12345 | Tracking number. |
| `orders[].line_items[].sub_item_info[].package_id` | string | 1153132168123859123 | An order can contain one or more packages based on how the seller chooses to ship. |
| `orders[].line_items[].sub_item_info[].retail_delivery_fee` | string | 1.28 | Retail delivery fee (RDF). |
| `orders[].line_items[].sub_item_info[].is_dangerous_good` | bool | false | Whether the SKU is a hazmat item. |
| `orders[].line_items[].sub_item_info[].warehouse_id` | string | 6955005333819123123 | Seller warehouse ID. |
| `orders[].line_items[].distance_shipping_fee` | string | 1000 | Distance shipping fee is fee that charged charged by our logistics partner and covers the separate distance-based cost for deliveries outside Java island as a p… |
| `orders[].line_items[].distance_fee` | string | 2000 | Total distance fee for Horizon+ Program. |
| `orders[].line_items[].shipping_vat_amount` | string | 3.5 | Shipping vat amount Available in EU market |
| `orders[].line_items[].shipping_vat_rate` | string | 0.25 | Shipping vat rate Available in EU market |
| `orders[].line_items[].is_pod_customized` | bool | false | Identifies if this SKU contains print on demand customization |
| `orders[].line_items[].pod_info_id` | string | 789821378123 | ID of the print on demand customization details. |
| `orders[].line_items[].product_listing_type` | string | TEMPORARY_LISTING | This describes the product listing type of the SKU. |
| `orders[].buyer_email` | string | v2b2V5@chat.seller.tiktok.com | The anonymized email address of the buyer. |
| `orders[].delivery_due_time` | int | 1678389618 | If the order hasn't updated its status to `DELIVERED` before this time, the order will be automatically canceled by TikTok Shop. Giá trị: DELIVERED |
| `orders[].is_sample_order` | bool | false | Use this field to determine whether the order is a sample order. |
| `orders[].shipping_due_time` | int | 1678389618 | If the order hasn't updated its status to `AWAITING_COLLECTION` before this time, the order will be automatically canceled by TikTok Shop. Giá trị: AWAITING_COLLECTION |
| `orders[].collection_due_time` | int | 1678389618 | If the order hasn't updated its status to `IN_TRANSIT` before this time, the order will be canceled by TikTok Shop. Giá trị: IN_TRANSIT |
| `orders[].delivery_option_required_delivery_time` | int | 1678389618 | Order should be delivered before this time. |
| `orders[].is_on_hold_order` | bool | false | Indicates whether the order has been changed to or will be updated to `ON_HOLD` status. Giá trị: ON_HOLD |
| `orders[].delivery_time` | int | 1678389618 | The time an order's status changed to `DELIVERED`. Giá trị: DELIVERED |
| `orders[].is_replacement_order` | bool | false | Whether this is a replacement order. |
| `orders[].is_subscription_order` | bool | true | Subscription orders are recurring orders set up by customers. |
| `orders[].is_refundable_sample` | bool | false | This indicates if an order is a refundable sample. |
| `orders[].authentication_required` | bool | false | If true, order requires authentication. |
| `orders[].collection_time` | int | 1678389618 | The time an order's status has been updated to `IN_TRANSIT`. Giá trị: IN_TRANSIT |
| `orders[].replaced_order_id` | string | 576461416728782174 | The order Id for the order that is being replaced. |
| `orders[].is_buyer_request_cancel` | bool | false | Whether the buyer has a pending cancellation request. |
| `orders[].pick_up_cut_off_time` | int | 1678389618 | To avoid LDR, you must ensure the package is picked up by this time. |
| `orders[].fast_dispatch_sla_time` | int | 1678389618 | The latest collection time to gain incentives of NDD (Next Day Delivery) project. |
| `orders[].commerce_platform` | string | TIKTOK_SHOP | The platform where the order was placed. Giá trị: TIKTOK_SHOP, TOKOPEDIA, TIKTOK_SHOP_APP |
| `orders[].order_type` | string | ZERO_LOTTERY | The order type. Giá trị: NORMAL, ZERO_LOTTERY, AUCTION, PRE_ORDER, MADE_TO_ORDER, BACK_ORDER, VIRTUAL_ORDER |
| `orders[].release_date` | int | 1619611762 | The date on which order handling starts and the status of the order changes to `AWAITING_SHIPMENT`. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `orders[].handling_duration` | object |  | The duration for the seller to process the order and hand it over to a shipping carrier after the order is placed. Giá trị: MADE_TO_ORDER, BACK_ORDER |
| `orders[].handling_duration.days` | string | 7 | The number of days. |
| `orders[].handling_duration.type` | string | BUSINESS_DAY | Indicates if the duration is calculated in calendar days or business days. Giá trị: CALENDAR_DAY, BUSINESS_DAY |
| `orders[].auto_combine_group_id` | string | 12345677 | An identifier assigned to orders from the same customer during a LIVE session to facilitate combined order shipping when "auto-combination" is activated in Sell… |
| `orders[].cpf_name` | string | John Smith | Name belonging to the CPF number for the Brazil market. |
| `orders[].is_exchange_order` | bool | false | When TRUE, this is an exchange order. |
| `orders[].exchange_source_order_id` | string | 576461413038785752 | If the order is an exchange order, this field returns the original order's order ID, from which the exchange order was generated. |
| `orders[].consultation_id` | string | 123456 | An ID to identify the corresponding ePharmacy consultation. |
| `orders[].fast_delivery_program` | string | 3_DAY_DELIVERY | A badge presented on the merchandise to tell the buyer that the seller participates in the fast delivery program, such that the order should arrive in a promise… |
| `orders[].fulfillment_priority_level` | int | 100 | Fulfillment priority value that can be used to prioritize shipping (only available in SEA) 100 = Instant 200 = Sameday 8 Hours 300 = Sameday 400 = Next Day Deli… |
| `orders[].recommended_shipping_time` | int | 1619611561 | Recommended time to ship based on the each LSP service type (only available in SEA) |
| `orders[].buyer_nickname` | string | user213123 | The nickname of the TikTok buyer, which is defined by the TikTok user. |
| `orders[].buyer_avatar` | string | https://p16-oec-va.itexeitg.com/tos-mali… | The avatar of the TikTok buyer, which is defined by the TikTok user. |
| `orders[].order_rights` | []int | [1] | Order tag identifier if has certain rights within the order based on the program subscribed by sellers. |
| `orders[].instore_pickup_sla_time` | int | 1784090976 | The latest buyer pickup time for instore pickup method specified by the platform. |
| `orders[].fast_rts_sla_time` | int | 1784090976 | Indicate fast fulfillment SLA for shipping action. |
| `orders[].fast_collection_sla_time` | int | 1784090976 | Indicate fast fulfillment SLA for collection action. |

**Mã lỗi:** `36009003` Internal error.

### Get Order Detail

`GET /order/202507/orders` · scope: `seller.order.info, seller.fs.order&fufillment.management`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `ids` | []string | Y | A list of TikTok Shop order ID values. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `orders` | []object |  | Order information. |
| `orders[].id` | string | 576461413038785752 | A unique identifier for a TikTok Shop order. |
| `orders[].cancellation_initiator` | string | SELLER | The initiator of a cancellation request. Giá trị: SELLER, BUYER, SYSTEM |
| `orders[].shipping_provider` | string | TT Virtual express | The name of the current shipping provider. |
| `orders[].shipping_provider_id` | string | 6617675021119438849 | The ID of the current shipping provider. |
| `orders[].user_id` | string | 7021436810468230477 | The buyer's user ID. |
| `orders[].status` | string | UNPAID | The order status. Giá trị: UNPAID, ON_HOLD, AWAITING_SHIPMENT, PARTIALLY_SHIPPING, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `orders[].rts_time` | int | 1619611563 | The time seller shipped the order (call Ship Order endpoint successfully). |
| `orders[].payment` | object |  | Payment info about a TikTok Shop order. |
| `orders[].payment.currency` | string | IDR | Currency for payment. |
| `orders[].payment.sub_total` | string | 5000 | Buyer paid subtotal of all the SKUs in the order. |
| `orders[].payment.shipping_fee` | string | 5000 | Buyer paid shipping fee. |
| `orders[].payment.seller_discount` | string | 5000 | Product discount by seller. |
| `orders[].payment.platform_discount` | string | 5000 | Product discount by platform. |
| `orders[].payment.payment_platform_discount` | string | 10 | Discount applied to the order funded by a payment platform |
| `orders[].payment.payment_discount_service_fee` | string | 10 | Service fee as paid by the buyer to apply payment platform discount |
| `orders[].payment.total_amount` | string | 5000 | Buyer paid total amount. |
| `orders[].payment.original_total_product_price` | string | 5000 | Total original price of products (VAT included for crossborder shop). |
| `orders[].payment.original_shipping_fee` | string | 5000 | Shipping fee before discount |
| `orders[].payment.shipping_fee_seller_discount` | string | 5000 | Shipping fee discount provided by seller for an order that will not qualify for co-funded reimbursement. |
| `orders[].payment.shipping_fee_platform_discount` | string | 5000 | Shipping fee discount by platform. |
| `orders[].payment.shipping_fee_cofunded_discount` | string | 7 | Shipping fee discount provided by seller, eligible for co-funded reimbursement upon order delivery, based on Co-Funded Free Shipping program terms. |
| `orders[].payment.tax` | string | 5000 | Buyer paid total taxes of the order. |
| `orders[].payment.small_order_fee` | string | 3000 | Small order fee for TH. |
| `orders[].payment.shipping_fee_tax` | string | 11 | The tax on the shipping price. |
| `orders[].payment.product_tax` | string | 21.3 | The tax on the total item price. |
| `orders[].payment.retail_delivery_fee` | string | 1.28 | RDF (retail delivery fee). |
| `orders[].payment.buyer_service_fee` | string | 1000 | A service fee is charged on every transaction made. |
| `orders[].payment.handling_fee` | string | 1000 | A fee charged to buyers to cover the additional processing, handling, and/or installment costs associated with the chosen payment method. |
| `orders[].payment.shipping_insurance_fee` | string | 1000 | The cost incurred by the buyers for coverage against loss or damage to goods during transit. |
| `orders[].payment.item_insurance_fee` | string | 1000 | The cost incurred by the buyers for coverage against defects or damage to the product after purchase. |
| `orders[].payment.item_insurance_tax` | string | 10 | The tax paid on the insurance purchased by buyers. |
| `orders[].payment.distance_shipping_fee` | string | 1000 | Distance shipping fee is fee that charged charged by our logistics partner and covers the separate distance-based cost for deliveries outside Java island as a p… |
| `orders[].payment.distance_fee` | string | 2000 | Total distance fee for Horizon+ Program. |
| `orders[].recipient_address` | object |  | `recipient_address` is not available under `UNPAID` and `ON_HOLD` status. Giá trị: UNPAID, ON_HOLD |
| `orders[].recipient_address.full_address` | string | 1199 Coleman Ave San Jose, CA 95110 | The complete recipient addresses information. |
| `orders[].recipient_address.phone_number` | string | (+1)213-***-1234 | The telephone number of the buyer. |
| `orders[].recipient_address.name` | string | David Kong | The name of the recipient. |
| `orders[].recipient_address.first_name` | string | David | The first name of the recipient. |
| `orders[].recipient_address.last_name` | string | Kong | The last name of the recipient. |
| `orders[].recipient_address.first_name_local_script` | string | ジョン | Recipient first name in katakana. |
| `orders[].recipient_address.last_name_local_script` | string | ドゥ | Recipient last name in katakana. |
| `orders[].recipient_address.region_code` | string | US | Region code. |
| `orders[].recipient_address.postal_code` | string | 95110 | The postal code that can be used by seller for shipping (in the U.S, this is the ZIP code). |
| `orders[].recipient_address.post_town` | string | Ribbleton | Post town of the address Note: Available only in UK market |
| `orders[].recipient_address.address_line1` | string | TikTok 5800 bristol Pkwy | The first line of the street address. |
| `orders[].recipient_address.address_line2` | string | Suite 100 | The second line of the street address |
| `orders[].recipient_address.address_line3` | string | Vila Olímpia | The third line of the street address. |
| `orders[].recipient_address.address_line4` | string | Avenida Presidente Juscelino Kubitschek | The fourth line of the street address. |
| `orders[].recipient_address.district_info` | []object |  | District information list. |
| `orders[].recipient_address.district_info[].address_level_name` | string | Country | The name of administrative division that can be used by seller for ship. |
| `orders[].recipient_address.district_info[].address_name` | string | United Kingdom | Administrative area name. |
| `orders[].recipient_address.district_info[].address_level` | string | L0 | Administrative district level code. |
| `orders[].recipient_address.district_info[].iso_code` | string | VAN | ISO code of the administrative district level |
| `orders[].recipient_address.delivery_preferences` | object |  | Contains all of the delivery instructions provided by the buyer for the shipping address. |
| `orders[].recipient_address.delivery_preferences.drop_off_location` | string | Front Door | Drop-off location selected by the buyer. |
| `orders[].recipient_address.address_detail` | string | Unit one building 8 | Full buyer detail address. |
| `orders[].buyer_message` | string | Please ship asap! | The note from buyer. |
| `orders[].create_time` | int | 1619611561 | The date and time that the order was created. |
| `orders[].tracking_number` | string | JX12345 | Tracking number. |
| `orders[].cancel_reason` | string | Pricing error | The order level cancellation reason. |
| `orders[].rts_sla_time` | int | 1619611688 | The latest shipping time specified by the platform. |
| `orders[].paid_time` | int | 1619611563 | The date and time that the order was paid. |
| `orders[].fulfillment_type` | string | FULFILLMENT_BY_SELLER | Fulfillment type. Giá trị: FULFILLMENT_BY_SELLER, FULFILLMENT_BY_TIKTOK, FULFILLMENT_BY_DILAYANI_TOKOPEDIA |
| `orders[].seller_note` | string | seller note | This field return the note, which seller notes in TikTok Seller Center. |
| `orders[].tts_sla_time` | int | 1619611761 | The latest collection time specified by the platform. |
| `orders[].cancel_order_sla_time` | int | 1619621355 | The automatic cancellation time for orders specified by the platform. |
| `orders[].update_time` | int | 1619621355 | Time of order status changes. |
| `orders[].packages` | []object |  | List of packages included in this order |
| `orders[].packages[].id` | string | 1152321127278713123 | Package ID |
| `orders[].delivery_type` | string | HOME_DELIVERY | Indicates whether it is a PickUp DropOff (PUDO) location. Giá trị: HOME_DELIVERY, COLLECTION_POINT |
| `orders[].is_sample_order` | bool | false | Use this field to determine whether the order is a sample order. |
| `orders[].warehouse_id` | string | 6955005333819123123 | seller warehouse ID. |
| `orders[].split_or_combine_tag` | string | COMBINED | Indicate whether the order is combined or split. Giá trị: COMBINED, SPLIT |
| `orders[].has_updated_recipient_address` | bool | false | Whether the recipient address has been updated. |
| `orders[].cpf` | string | 3213-31231412 | CPF (invoice number), used to issue an invoice. |
| `orders[].delivery_option_id` | string | 7091146663229654785 | Order delivery option ID. |
| `orders[].delivery_sla_time` | int | 1678389618 | Order should arrive by this date to be considered on-time and to avoid late delivery penalties. |
| `orders[].payment_method_name` | string | CCDC | Payment method name, only for display |
| `orders[].payment_card_type` | string | Debit<br> | Code to distinguish different payment method. |
| `orders[].channel_entity_national_registry_id` | string | 26.545.167/0001-21 | The national channel entity registration ID of payment institution/creditor. |
| `orders[].payment_method_code` | string | pm_pi_ccdc_visa_c_d | Payment method code identifying current transaction. |
| `orders[].payment_auth_code` | string | AUTH123457 | Authorization code of current transaction (only for the Brazilian market). |
| `orders[].shipping_due_time` | int | 1678389618 | If the order hasn't updated its status to `AWAITING_COLLECTION` before this time, the order will be automatically canceled by TikTok Shop. Giá trị: AWAITING_COLLECTION |
| `orders[].line_items` | []object |  | Line item info list. |
| `orders[].line_items[].id` | string | 577086512123755123 | Line item ID. |
| `orders[].line_items[].sku_id` | string | 2729382476852921560 | SKU ID. |
| `orders[].line_items[].combined_listing_skus` | []object |  | For a virtual bundle SKU, returns an array of related product SKUs that compose the virtual bundle SKU. |
| `orders[].line_items[].combined_listing_skus[].sku_id` | string | 2729382476852921123 | The original `sku_id` related to the virtual bundle SKU. |
| `orders[].line_items[].combined_listing_skus[].sku_count` | int | 1 | The quantity of original SKU that compose the virtual bundle SKU. |
| `orders[].line_items[].combined_listing_skus[].product_id` | string | 1729582718312380456 | The original `product_id` related to the virtual bundle SKU. |
| `orders[].line_items[].combined_listing_skus[].seller_sku` | string | yellow-24-XL | The original `seller_sku` (which is defined by sellers) related to the virtual bundle SKU. |
| `orders[].line_items[].product_id` | string | 1729582718312380123 | Product ID. |
| `orders[].line_items[].product_name` | string | Women's Winter Crochet Clothes | Product name. |
| `orders[].line_items[].sku_name` | string | Iphone | The name of the SKU, combined by product SKU attribute like size or color. |
| `orders[].line_items[].sku_image` | string | https://p16-oec-va.itexeitg.com/tos-mali… | SKU image. |
| `orders[].line_items[].original_price` | string | 0.01 | Item original price, please refer to the currency of `payment_info`. |
| `orders[].line_items[].sale_price` | string | 0.01 | Item sale price, please refer to the currency of `payment_info`. |
| `orders[].line_items[].pfand_fee` | string | 1.5 | Deposit fee that is applied to certain products, typically beverage containers such as bottles or cans. |
| `orders[].line_items[].platform_discount` | string | 0 | Platform discount amount, please refer to the currency of `payment_info`. |
| `orders[].line_items[].display_status` | string | TO_SHIP | - `UNPAID`: The order is placed, but payment is not yet completed. Giá trị: UNPAID, AWAITING_SHIPMENT, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `orders[].line_items[].cancel_user` | string | BUYER | The initiator of a cancellation request: - `BUYER` - `SELLER` - `OPERATOR` - `SYSTEM` Giá trị: BUYER, SELLER, OPERATOR, SYSTEM |
| `orders[].line_items[].sku_type` | string | PRE_ORDER | [**Deprecated**: This field is deprecated and will be removed in a future API version. Giá trị: NORMAL, SHOP_PARTNER, PRE_ORDER, MADE_TO_ORDER |
| `orders[].line_items[].seller_sku` | string | red_iphone_256 | The seller stock keeping unit (SKU) of the item. |
| `orders[].line_items[].shipping_provider_id` | string | 6617675021119438849 | The shipping provider ID of the item. |
| `orders[].line_items[].seller_discount` | string | 0 | Seller discount amount. |
| `orders[].line_items[].currency` | string | IDR | Currency for payment. |
| `orders[].line_items[].package_id` | string | 1153132168123859123 | An order can contain one or more packages based on how the seller chooses to ship. |
| `orders[].line_items[].rts_time` | int | 1678389618 | The time seller shipped line order (call Ship Order endpoint successfully). |
| `orders[].line_items[].item_tax` | []object |  | Item tax detail. |
| `orders[].line_items[].item_tax[].tax_type` | string | SALES_TAX | Tax type. Giá trị: SALES_TAX |
| `orders[].line_items[].item_tax[].tax_amount` | string | 21.2 | Tax amount. |
| `orders[].line_items[].item_tax[].tax_rate` | string | 0.35 | Tax rate. |
| `orders[].line_items[].package_status` | string | TO_FULFILL | The package status of the item: - `TO_FULFILL`: package waiting seller to arrange shipment. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |
| `orders[].line_items[].shipping_provider_name` | string | TT Virtual express | The shipping provider name of the item. |
| `orders[].line_items[].is_gift` | bool | false | Indicates whether the current order line is a gift. |
| `orders[].line_items[].cancel_reason` | string | Discount not as expected | Item cancellation reason. |
| `orders[].line_items[].small_order_fee` | string | 5000 | Small order fee for TH. |
| `orders[].line_items[].retail_delivery_fee` | string | 1.28 | RDF (retail delivery fee). |
| `orders[].line_items[].tracking_number` | string | JX12345 | Tracking number. |
| `orders[].line_items[].buyer_service_fee` | string | 1000 | A service fee is charged on every transaction made. |
| `orders[].line_items[].handling_duration_days` | string | 7 | [**Deprecated**: This field is deprecated and will be removed in a future API version. Giá trị: MADE_TO_ORDER |
| `orders[].line_items[].is_dangerous_good` | bool | true | Whether the SKU is a hazmat item. |
| `orders[].line_items[].needs_prescription` | bool | true | A flag to indicate whether the included product requires a prescription. |
| `orders[].line_items[].gift_retail_price` | string | 20 | Retail price of the free item in orders Live giveaway, free sample and gift with purchase. |
| `orders[].line_items[].is_unboxing_item` | bool | false | If `true`, indicates the sku is a blind box item that will need be unboxed to reveal the actual product to the buyer. |
| `orders[].line_items[].unboxing_sku_code` | string | 14531134 | A seller's third party sku code for the actual unboxed item, which warehouse fulfillment operations can reference to identify the accurate product they are ship… |
| `orders[].line_items[].unboxing_case_list` | []object |  | A seller's third party sku code for the case unboxed item, which warehouse fulfillment operations can reference to identify the accurate product they are shippi… |
| `orders[].line_items[].unboxing_case_list[].unbox_sku` | string | 14531134 | Unboxed sku code associated with the actual unboxed item. |
| `orders[].line_items[].unboxing_case_list[].unbox_sku_type` | string | GIFT | Indicates if the unboxed sku is a gift item Enumerations: NORMAL or GIFT |
| `orders[].line_items[].sub_item_info` | []object |  | Sub items represent skus that belong to a "virtual bundle" that has been defined by a seller. |
| `orders[].line_items[].sub_item_info[].id` | string | 577086512123755123 | Line item id of the sub_item. |
| `orders[].line_items[].sub_item_info[].sku_id` | string | 2729382476852921560 | SKU ID. |
| `orders[].line_items[].sub_item_info[].display_status` | string | UNPAID | Available values: - `UNPAID`: The order has been placed, but payment has not yet completed. Giá trị: UNPAID, AWAITING_SHIPMENT, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED |
| `orders[].line_items[].sub_item_info[].product_name` | string | Women's Winter Crochet Clothes | Product name. |
| `orders[].line_items[].sub_item_info[].seller_sku` | string | red_iphone_256 | The seller stock keeping unit (SKU) of the item. |
| `orders[].line_items[].sub_item_info[].sku_image` | string | https://p16-oec-va.itexeitg.com/tos-mali… | SKU image. |
| `orders[].line_items[].sub_item_info[].sku_name` | string | Iphone | The name of the SKU, combined by product SKU attribute like size or color. |
| `orders[].line_items[].sub_item_info[].product_id` | string | 1729582718312380123 | Product ID. |
| `orders[].line_items[].sub_item_info[].sale_price` | string | 0.01 | Item sale price. |
| `orders[].line_items[].sub_item_info[].platform_discount` | string | 0 | Platform discount amount. |
| `orders[].line_items[].sub_item_info[].seller_discount` | string | 0 | Seller discount amount. |
| `orders[].line_items[].sub_item_info[].cancel_reason` | string | Discount not as expected | The cancellation reason. |
| `orders[].line_items[].sub_item_info[].original_price` | string | 0.01 | Item original price. |
| `orders[].line_items[].sub_item_info[].rts_time` | int | 1678389618 | The time sellers shipped the order (called Ship Package API successfully). |
| `orders[].line_items[].sub_item_info[].package_status` | string | TO_FULFILL | The package status of the item. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |
| `orders[].line_items[].sub_item_info[].currency` | string | USD | Currency for payment. |
| `orders[].line_items[].sub_item_info[].shipping_provider_name` | string | TT Virtual express | The shipping provider name. |
| `orders[].line_items[].sub_item_info[].cancel_user` | string | BUYER | Cancellation request initiator. Giá trị: BUYER, SELLER, OPERATOR, SYSTEM |
| `orders[].line_items[].sub_item_info[].shipping_provider_id` | string | 6617675021119438849 | The shipping provider ID of the item. |
| `orders[].line_items[].sub_item_info[].item_tax` | []object |  | Item tax detail. |
| `orders[].line_items[].sub_item_info[].item_tax[].tax_type` | string | SALES_TAX | Tax type. Giá trị: SALES_TAX |
| `orders[].line_items[].sub_item_info[].item_tax[].tax_amount` | string | 21.2 | Tax amount. |
| `orders[].line_items[].sub_item_info[].item_tax[].tax_rate` | string | 0.35 | Tax rate. |
| `orders[].line_items[].sub_item_info[].tracking_number` | string | JX12345 | Tracking number. |
| `orders[].line_items[].sub_item_info[].package_id` | string | 1153132168123859123 | An order can contain one or more packages based on how the seller chooses to ship. |
| `orders[].line_items[].sub_item_info[].retail_delivery_fee` | string | 1.28 | Retail delivery fee (RDF). |
| `orders[].line_items[].sub_item_info[].is_dangerous_good` | bool | false | Whether the SKU is a hazmat item. |
| `orders[].line_items[].sub_item_info[].warehouse_id` | string | 6955005333819123123 | Seller warehouse ID. |
| `orders[].line_items[].distance_shipping_fee` | string | 1000 | Distance shipping fee is fee that charged charged by our logistics partner and covers the separate distance-based cost for deliveries outside Java island as a p… |
| `orders[].line_items[].distance_fee` | string | 2000 | Total distance fee for Horizon+ Program. |
| `orders[].line_items[].shipping_vat_amount` | string | 3.5 | Shipping vat amount Available in EU market |
| `orders[].line_items[].shipping_vat_rate` | string | 0.25 | Shipping vat rate Available in EU market |
| `orders[].line_items[].is_pod_customized` | bool | false | Identifies if this SKU contains print on demand customization |
| `orders[].line_items[].pod_info_id` | string | 789123812334 | ID of the print on demand customization details. |
| `orders[].line_items[].room_id` | string | 7652884452049160991 | The unique ID of the LIVE session where the order line item was created. |
| `orders[].line_items[].product_listing_type` | string | TEMPORARY_LISTING | This describes the product listing type of the SKU. Giá trị: TEMPORARY_LISTING |
| `orders[].shipping_type` | string | TIKTOK | The method of delivery. Giá trị: TIKTOK, SELLER, TIKTOK_DIGITAL |
| `orders[].buyer_email` | string | v2b2V5@chat.seller.tiktok.com | The anonymized email address of the buyer. |
| `orders[].delivery_time` | int | 1678389618 | The timestamp of the order's status update to `DELIVERED`. Giá trị: DELIVERED |
| `orders[].need_upload_invoice` | string | NEED_INVOICE | Whether an invoice needs to be uploaded and uploaded status (only for Brazil market). Giá trị: UNKNOWN, NEED_INVOICE, NO_NEED, INVOICE_UPLOADED, INVOICE_PROCESSING |
| `orders[].is_cod` | bool | false | This option is for sellers that accept cash payment on delivery which is rare. Giá trị: FALSE |
| `orders[].request_cancel_time` | int | 1678389618 | Buyer request cancel time. |
| `orders[].delivery_option_required_delivery_time` | int | 1678389618 | Order should be delivered before this time. |
| `orders[].delivery_option_name` | string | Express shipping | Delivery option name. |
| `orders[].is_buyer_request_cancel` | bool | false | True when the buyer has a pending cancellation request |
| `orders[].delivery_due_time` | int | 1678389618 | If the order hasn't updated its status to `DELIVERED` before this time, the order will be automatically canceled by TikTok Shop. Giá trị: DELIVERED |
| `orders[].collection_time` | int | 1678389618 | The timestamp of the order's status update to `IN_TRANSIT`. Giá trị: IN_TRANSIT |
| `orders[].is_on_hold_order` | bool | false | Indicates whether the order experienced or will be experienced `ON_HOLD` status. Giá trị: ON_HOLD |
| `orders[].cancel_time` | int | 1678389618 | The timestamp of the order's status update to `CANCELLED`. Giá trị: CANCELLED |
| `orders[].is_replacement_order` | bool | false | When true, this is a replacement order. |
| `orders[].replaced_order_id` | string | 576461416728782174 | The order ID for the order that is being replaced. |
| `orders[].collection_due_time` | int | 1678389618 | If the order hasn't updated its status to `IN_TRANSIT` before this time, the order will be automatically canceled by TikTok Shop Giá trị: IN_TRANSIT |
| `orders[].pick_up_cut_off_time` | int | 1678389618 | To avoid LDR, you must ensure the package is picked up by this time. |
| `orders[].fast_dispatch_sla_time` | int | 1678389618 | The latest collection time to gain incentives of NDD (Next Day Delivery) project. |
| `orders[].commerce_platform` | string | TIKTOK_SHOP | The platform where the order was placed. Giá trị: TIKTOK_SHOP, TOKOPEDIA, TIKTOK_SHOP_APP |
| `orders[].order_type` | string | ZERO_LOTTERY | The order type. Giá trị: NORMAL, ZERO_LOTTERY, AUCTION, PRE_ORDER, MADE_TO_ORDER, BACK_ORDER, VIRTUAL_ORDER |
| `orders[].release_date` | int | 1619611762 | The date on which order handling starts and the status of the order changes to `AWAITING_SHIPMENT`. Giá trị: AWAITING_SHIPMENT, PRE_ORDER |
| `orders[].handling_duration` | object |  | The duration for the seller to process the order and hand it over to a shipping carrier after the order is placed. Giá trị: MADE_TO_ORDER, BACK_ORDER |
| `orders[].handling_duration.days` | string | 7 | The number of days. |
| `orders[].handling_duration.type` | string | BUSINESS_DAY | Indicates if the duration is calculated in calendar days or business days. Giá trị: CALENDAR_DAY, BUSINESS_DAY |
| `orders[].auto_combine_group_id` | string | 12345677 | An identifier assigned to orders from the same customer during a LIVE session to facilitate combined order shipping when "auto-combination" is activated in Sell… |
| `orders[].cpf_name` | string | John Smith | Name belonging to the CPF number for the Brazil market. |
| `orders[].is_exchange_order` | bool | false | When TRUE, this is an exchange order. |
| `orders[].exchange_source_order_id` | string | 576461413038785752 | If the order is an exchange order, this field returns the original order's order ID, from which the exchange order was generated. |
| `orders[].is_subscription_order` | bool | true | Subscription orders are recurring orders set up by customers. |
| `orders[].is_refundable_sample` | bool | false | This indicates if an order is a refundable sample. |
| `orders[].authentication_required` | bool | false | If true, order requires authentication. |
| `orders[].consultation_id` | string | 123241 | An ID to identify the corresponding ePharmacy consultation. |
| `orders[].fast_delivery_program` | string | 3_DAY_DELIVERY | A badge presented on the merchandise to tell the buyer that the seller participates in the fast delivery program, such that the order should arrive in a promise… |
| `orders[].buyer_nickname` | string | user213123 | The nickname of the TikTok buyer, which is defined by the TikTok user. |
| `orders[].buyer_avatar` | string | https://p16-oec-va.itexeitg.com/tos-mali… | The avatar of the TikTok buyer, which is defined by the TikTok user. |
| `orders[].order_rights` | []int | [1] | Order tag identifier if has certain rights within the order based on the program subscribed by sellers. |
| `orders[].fulfillment_priority_level` | int | 100 | Fulfillment priority value that can be used to prioritize shipping (only available in SEA) 100 = Instant 200 = Sameday 8 Hours 300 = Sameday 400 = Next Day Deli… |
| `orders[].recommended_shipping_time` | int | 1619611561 | Recommended time to ship based on the each LSP service type (only available in SEA) |
| `orders[].instore_pickup_sla_time` | int | 1784090976 | The latest buyer pickup time for instore pickup method specified by the platform. |
| `orders[].fast_rts_sla_time` | int | 1784090976 | Indicate fast fulfillment SLA for shipping action. |
| `orders[].fast_collection_sla_time` | int | 1784090976 | Indicate fast fulfillment SLA for collection action. |

**Mã lỗi:** `10002014` Failed to get orders, If multiple retry have failed, please contact the platform for assistance; `10002015` Failed to get orders, If multiple retry have failed, please contact the platform for assistance; `10006402` Internal error.; `36009003` Internal error.; `10037002` Failed to get orders, If multiple retry have failed, please contact the platform for assistance; `10037003` Failed to get orders, If multiple retry have failed, please contact the platform for assistance; `10037004` Failed to get orders, If multiple retry have failed, please contact the platform for assistance; `21008111` The order or package does not belong to the current seller.

### Get Order Price Details

`GET /order/202407/orders/{order_id}/price_detail` · scope: `seller.order.info`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `currency` | string | USD | Currency Type. |
| `total` | string | 122.00 | Total original price of the order. |
| `payment` | string | 101.00 | Amount paid by the buyer for the order. |
| `sku_list_price` | string | 97.00 | Total MSRP price of the products. |
| `sku_sale_price` | string | 96.00 | Total promotional sale price of the products. |
| `subtotal` | string | 98.00 | Total promotional sale price of the products in the order, including tax. |
| `subtotal_deduction_seller` | string | 0.50 | Seller provided price discount on the product |
| `subtotal_deduction_platform` | string | 0.50 | Platform provided price discount on the product |
| `subtotal_tax_amount` | string | 2.00 | Total tax amount on the product |
| `voucher_deduction_platform` | string | 1010000 | Total product-price deduction from platform-funded vouchers for the order. |
| `voucher_deduction_seller` | string | 1020000 | Total product-price deduction from seller-funded vouchers for the order. |
| `shipping_list_price` | string | 25.00 | Original shipping price |
| `shipping_sale_price` | string | 3.00 | Promotional shipping price. |
| `shipping_fee_deduction_seller` | string | 10.00 | Shipping discount covered by the seller. |
| `shipping_fee_deduction_platform` | string | 12.00 | Shipping discount covered by the platform |
| `shipping_fee_deduction_platform_voucher` | string | 1010000 | Shipping-fee deduction from platform-funded vouchers for the order. |
| `tax_amount` | string | 2.00 | Total tax amount for the order. |
| `tax_rate` | string | 0.021 | Tax rate |
| `net_price_amount` | string | 97.00 | Order price excluding tax. |
| `cod_fee` | string | 0.00 | COD fee charged by shipping aggregators. |
| `cod_fee_net_amount` | string | 0.00 | COD fee charged by shipping aggregators including tax. |
| `sku_gift_original_price` | string | 2.00 | Original sku list price of the gift product from the seller. |
| `sku_gift_net_price` | string | 2.02 | Original sku list price of the gift product from the seller including tax. |
| `distance_shipping_fee` | string | 1.00 | Distance shipping fee is fee that charged charged by our logistics partner and covers the separate distance-based cost for deliveries outside Java island as a p… |
| `distance_fee` | string | 2.00 | Total distance fee for Horizon+ Program. |
| `line_items` | []object |  | Price details for each line item in the order. |
| `line_items[].id` | string | 576461413032342720 | corresponding line_item id |
| `line_items[].currency` | string | USD | Currency Type. |
| `line_items[].total` | string | 122.00 | Total original price of the line item. |
| `line_items[].payment` | string | 101.00 | Amount paid by the buyer for the line item. |
| `line_items[].sku_list_price` | string | 97.00 | Total MSRP price of the products. |
| `line_items[].sku_sale_price` | string | 96.00 | Total promotional sale price of the products. |
| `line_items[].subtotal` | string | 98.00 | Total promotional sale price for the line item, including tax. |
| `line_items[].subtotal_deduction_seller` | string | 0.50 | Seller provided price discount on the product |
| `line_items[].subtotal_deduction_platform` | string | 0.50 | Platform provided price discount on the product |
| `line_items[].subtotal_tax_amount` | string | 2.00 | Total tax amount on the product |
| `line_items[].voucher_deduction_platform` | string | 1010000 | Product-price deduction from platform-funded vouchers for the line item. |
| `line_items[].voucher_deduction_seller` | string | 1020000 | Product-price deduction from seller-funded vouchers for the line item. |
| `line_items[].shipping_list_price` | string | 25.00 | Original shipping price |
| `line_items[].shipping_sale_price` | string | 3.00 | Promotional shipping price. |
| `line_items[].shipping_fee_deduction_seller` | string | 10.00 | Shipping discount covered by the seller. |
| `line_items[].shipping_fee_deduction_platform` | string | 12.00 | Shipping discount covered by the platform |
| `line_items[].shipping_fee_deduction_platform_voucher` | string | 1010000 | Shipping-fee deduction from platform-funded vouchers for the line item. |
| `line_items[].tax_amount` | string | 2.00 | Total tax amount for the line item. |
| `line_items[].tax_rate` | string | 0.021 | Tax rate |
| `line_items[].net_price_amount` | string | 97.00 | Line item price excluding tax. |
| `line_items[].cod_fee` | string | 0.00 | COD fee charged by shipping aggregators. |
| `line_items[].cod_fee_amount` | string | 0.00 | COD fee charged by shipping aggregators including tax. |
| `line_items[].sku_gift_original_price` | string | 2.00 | Original sku list price of the gift product from the seller. |
| `line_items[].sku_gift_net_price` | string | 2.02 | Original sku list price of the gift product from the seller including tax. |
| `line_items[].distance_shipping_fee` | string | 1.00 | Distance shipping fee is fee that charged charged by our logistics partner and covers the separate distance-based cost for deliveries outside Java island as a p… |
| `line_items[].distance_fee` | string | 2.00 | Total distance fee for Horizon+ Program. |
| `line_items[].seller_funded_product_promotions` | []object |  | Seller-funded product promotions associated with the line item. |
| `line_items[].seller_funded_product_promotions[].activity_id` | string | 7136104329798256386 | A unique ID that identifies different promotion activities. |
| `line_items[].seller_funded_product_promotions[].discount_amount` | string | 2.25 | Discount amount of this activity |
| `line_items[].seller_funded_product_promotions[].discount_type` | string | COUPON | Discount type of this activity. Giá trị: COUPON, ACTIVITY |
| `line_items[].seller_funded_shipping_promotions` | []object |  | Seller-funded shipping promotions associated with the line item. |
| `line_items[].seller_funded_shipping_promotions[].activity_id` | string | 7136104329798256386 | A unique ID that identifies different promotion activities. |
| `line_items[].seller_funded_shipping_promotions[].discount_amount` | string | 2.25 | Discount amount of this activity |
| `line_items[].seller_funded_shipping_promotions[].discount_type` | string | COUPON<br> | Discount type of this activity. Giá trị: COUPON, ACTIVITY |

**Mã lỗi:** `21001001` Invalid parameters.; `21008111` The order or package does not belong to the current seller.; `36009003` Internal error.

## Promotion

### Create Promotion Activity

`POST /promotion/202309/activities` · scope: `seller.promotion.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `title` | string | Y | Unique name across all your activities. Giá trị: FLASHSALE, SHIPPING_DISCOUNT, FIXED_PRICE, DIRECT_DISCOUNT, BUY_MORE_SAVE_MORE, BUY_X_GET_Y, GIFT_WITH_PURCHASE |
| `activity_type` | string | Y | The type of promotion activity. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, GIFT_WITH_PURCHASE, BUY_X_GET_Y |
| `product_level` | string | Y | The product scope of the activity. Giá trị: PRODUCT, VARIATION, SHOP, FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, BUY_X_GET_Y, GIFT_WITH_PURCHASE |
| `duration_type` | string | N | The effective time type of the current activity - NORMAL: `begin_time` and `end_time` must be filled - INDEFINITE: `begin_time` and `end_time` aren't required,… Giá trị: INDEFINITE, SHIPPING_DISCOUNT |
| `begin_time` | int | Y | Activity start time. |
| `end_time` | int | Y | Activity end time as a Unix timestamp in seconds. Giá trị: NORMAL, FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, BUY_MORE_SAVE_MORE, BUY_X_GET_Y, GIFT_WITH_PURCHASE |
| `participation_limit` | []object | N | Buyer participation-limit settings. Giá trị: BUY_MORE_SAVE_MORE, BUY_X_GET_Y, GIFT_WITH_PURCHASE |
| `participation_limit[].type` | string | Y | Determines whether each buyer can receive the activity benefit more than once. Giá trị: BUYER_NO_LIMIT, BUYER_LIMIT_ONLY_ONE |
| `discount` | object | N | Discount configuration. Giá trị: SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, GIFT_WITH_PURCHASE, BUY_X_GET_Y, FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE |
| `discount.shipping_discount` | object | N | Configurations for the shipping discount, including purchase criteria, discount value, shipping method, inventory type, and area scope. |
| `discount.shipping_discount.threshold_type` | string | Y | The type of purchase criteria. Giá trị: NO_THRESHOLD, MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.shipping_discount.threshold_value` | string | N | The purchase threshold of the discount, based on `threshold_type`, for example, `3`. |
| `discount.shipping_discount.type` | string | Y | The shipping benefit type. Giá trị: FREE_SHIPPING, DISCOUNT_SHIPPING_FEE |
| `discount.shipping_discount.value` | string | N | The shipping discount value determined by `type`. |
| `discount.shipping_discount.shipping_method` | string | N | The shipping methods to which this promotion applies. Giá trị: STANDARD_SHIPPING, EXPRESS_SHIPPING, ALL_SHIPPING_METHOD |
| `discount.shipping_discount.inventory_type` | string | N | The inventory types to which the promotion activity applies: - `SELF_FULFILLED`: Only applies to inventory fulfilled from seller warehouses - `FULFILLED_BY_TIKT… Giá trị: SELF_FULFILLED, FULFILLED_BY_TIKTOK, ALL, SHOP, NO_THRESHOLD |
| `discount.shipping_discount.area_scope` | object | Y | The (buyer's) geographical areas to which the promotion activity applies. |
| `discount.shipping_discount.area_scope.type` | string | Y | The (buyer's) geographical area to which the promotion activity applies. Giá trị: WHOLE, SPECIFIC_AREAS |
| `discount.shipping_discount.area_scope.specific_areas` | []string | N | The list of areas for which the promotion activity applies. |
| `discount.bmsm_discount` | object | N | Configurations related to Buy More Save More (BMSM) promotions |
| `discount.bmsm_discount.details` | []object | N | Configurations for Buy More Save More tiers. |
| `discount.bmsm_discount.details[].tier` | int | Y | `tier` uses 1-based indexing: the first and second tiers are `1` and `2`, not `0` and `1`. |
| `discount.bmsm_discount.details[].threshold_type` | string | Y | The type of purchase criteria. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bmsm_discount.details[].threshold_value` | string | Y | The purchase threshold for this tier. |
| `discount.bmsm_discount.details[].type` | string | Y | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `discount.bmsm_discount.details[].value` | string | Y | The discount value. |
| `discount.gift_discount` | object | N | Configurations related to Gift With Purchase (GWP) promotions. |
| `discount.gift_discount.gift_receiving_type` | string | Y | The receiving type of gift. Giá trị: ALL_RECEIVE |
| `discount.gift_discount.gift_infos` | []object | Y | Gift With Purchase tier configurations. |
| `discount.gift_discount.gift_infos[].tier` | int | Y | `tier` uses 1-based indexing, i.e. |
| `discount.gift_discount.gift_infos[].threshold_type` | string | Y | The purchase threshold type of discount. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.gift_discount.gift_infos[].threshold_value` | string | Y | The purchase threshold for the gift, determined by `threshold_type`. |
| `discount.gift_discount.gift_infos[].gift_details` | []object | Y | The gift SKUs for this tier. |
| `discount.gift_discount.gift_infos[].gift_details[].product_id` | string | Y | TikTok Shop product id of gift. |
| `discount.gift_discount.gift_infos[].gift_details[].sku_id` | string | Y | TikTok Shop sku id of gift. |
| `discount.gift_discount.gift_infos[].gift_details[].total_claim_limit` | string | Y | The total claim limit for the gift SKU in this activity. |
| `discount.bxgy_discount` | object | N | Configurations related to Buy X Get Y (BXGY) promotions. |
| `discount.bxgy_discount.threshold_type` | string | Y | The purchase criterion. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bxgy_discount.threshold_value` | string | Y | The purchase threshold determined by `threshold_type`. |
| `discount.bxgy_discount.discount_type` | string | Y | The type of benefit. Giá trị: PERCENTAGE_OFF, FREE, AMOUNT_OFF_EACH |
| `discount.bxgy_discount.discount_value` | string | Y | Value of the discount. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF_EACH |
| `discount.bxgy_discount.benefit_max_applicable_quantity` | string | Y | The quantity of item Y awarded each time the Buy X Get Y benefit applies. |
| `discount.bxgy_discount.benefit_max_applicable_times` | string | N | Optional. |
| `target_user_info` | object | N | Target-audience configuration for `BUY_MORE_SAVE_MORE`, `BUY_X_GET_Y`, and `GIFT_WITH_PURCHASE` activities. Giá trị: BUY_MORE_SAVE_MORE, BUY_X_GET_Y, GIFT_WITH_PURCHASE, ALL_USER |
| `target_user_info.user_type` | string | N | Possible enumerations are: `ALL_USER` `SHOP_NEW_CUSTOMER` `SHOP_EXISTING_CUSTOMER` `SHOP_REPEAT_CUSTOMER` Giá trị: ALL_USER, SHOP_NEW_CUSTOMER, SHOP_EXISTING_CUSTOMER, SHOP_REPEAT_CUSTOMER |
| `auction_support_type` | string | N | For `SHIPPING_DISCOUNT` activities, determines whether auction products are supported. Giá trị: SHIPPING_DISCOUNT, ALL, EXCLUDE_AUCTION |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | On success, the ID of the created activity. |
| `create_time` | int | 1661756811 | The time when the activity was created. |
| `update_time` | int | 1661756811 | The time when the activity was last updated. |
| `status` | string | ONGOING | Possible values: - DRAFT: Promotion activities with this status are not available to TikTok users. Giá trị: DRAFT, NOT_START, ONGOING, EXPIRED, DEACTIVATED, NOT_EFFECTIVE |

**Mã lỗi:** `17003203` A storewide free shipping promotion activity is already active.; `17029001` Invalid parameters; `17029002` Title Length Too Long; `17029004` Duplicate activity title.; `17029005` Begin Time Earlier Than Now; `17029006` The activity period is too short.; `17029007` The activity period is too long.; `17029008` FlashSale Period Too Long; `17029026` Invalid SellerID; `17029027` Empty RequestSerialNo; `17029035` ShopRisk Check Failed; `17029036` ActivityType is not supported.; `17029049` ActivityVersion is not supported.; `17029052` ProductType is not supported; `17029068` Selected `inventory_type` is not supported in your region.; `17029070` Empty gift sku info.; `17029071` Gift sku id is invalid, not an integer.; `17029072` Gift sku repeated; `17029073` Price of the gift exceeds the upper limit; `17029074` Eligible products and the gift products must not be the same product.; `17029075` Number of gifts per tier does not meet the limit; `17029076` Gift sku is out of stock.; `17029077` Gifts across different tiers are repeated; `17029078` Number of gift tiers does not meet the requirement; `17029082` Specified time is outside of the defined range for this activity.; `36009003` Internal error.

### Update Promotion Activity

`PUT /promotion/202309/activities/{activity_id}` · scope: `seller.promotion.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `title` | string | Y | Activity name. |
| `duration_type` | string | N | The activity schedule mode. Giá trị: NORMAL, INDEFINITE |
| `begin_time` | int | Y | Activity start time as a Unix timestamp in seconds. |
| `end_time` | int | Y | Activity end time as a Unix timestamp in seconds. |
| `product_level` | string | N | The promotion's product scope. Giá trị: PRODUCT, VARIATION, SHOP |
| `participation_limit` | []object | N | The buyer participation limit. |
| `participation_limit[].type` | string | Y | Determines whether the promotion limits how many orders each buyer can receive the benefit from. Giá trị: BUYER_NO_LIMIT, BUYER_LIMIT_ONLY_ONE |
| `discount` | object | N | Discount information. |
| `discount.shipping_discount` | object | N | Configurations for the shipping discount, including purchase criteria, discount value, shipping method, inventory type, and area scope. |
| `discount.shipping_discount.threshold_type` | string | Y | The type of purchase criteria. Giá trị: NO_THRESHOLD, MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.shipping_discount.threshold_value` | string | N | The purchase threshold for the shipping discount, interpreted according to `threshold_type`. Giá trị: NO_THRESHOLD, MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.shipping_discount.type` | string | Y | The shipping benefit type. Giá trị: FREE_SHIPPING, DISCOUNT_SHIPPING_FEE |
| `discount.shipping_discount.value` | string | N | The shipping discount amount. Giá trị: FREE_SHIPPING, DISCOUNT_SHIPPING_FEE |
| `discount.shipping_discount.shipping_method` | string | N | The shipping methods to which this promotion applies. Giá trị: STANDARD_SHIPPING, EXPRESS_SHIPPING, ALL_SHIPPING_METHOD |
| `discount.shipping_discount.inventory_type` | string | N | The inventory types to which the promotion activity applies: - `SELF_FULFILLED`: Only applies to inventory fulfilled from seller warehouses - `FULFILLED_BY_TIKT… Giá trị: SELF_FULFILLED, FULFILLED_BY_TIKTOK, ALL, SHOP, NO_THRESHOLD |
| `discount.shipping_discount.area_scope` | object | Y | The (buyer's) geographical areas to which the promotion activity applies |
| `discount.shipping_discount.area_scope.type` | string | Y | The buyer's geographical area to which the promotion activity applies. Giá trị: WHOLE, SPECIFIC_AREAS |
| `discount.shipping_discount.area_scope.specific_areas` | []string | N | The list of areas for which the promotion activity applies. |
| `discount.bmsm_discount` | object | N | Configurations related to Buy More Save More (BMSM) promotions |
| `discount.bmsm_discount.details` | []object | N | Configurations for BMSM promotions. |
| `discount.bmsm_discount.details[].tier` | int | Y | The 1-based tier number. |
| `discount.bmsm_discount.details[].threshold_type` | string | Y | The type of purchase criteria. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bmsm_discount.details[].threshold_value` | string | Y | The purchase threshold for the discount, based on `threshold_type`. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bmsm_discount.details[].type` | string | Y | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `discount.bmsm_discount.details[].value` | string | Y | The positive discount value, interpreted according to `type`. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `discount.gift_discount` | object | N | Configurations related to Gift With Purchase (GWP) promotions. |
| `discount.gift_discount.gift_receiving_type` | string | Y | The receiving type of gift. Giá trị: ALL_RECEIVE |
| `discount.gift_discount.gift_infos` | []object | Y | Gift With Purchase (GWP) tier configurations. |
| `discount.gift_discount.gift_infos[].tier` | int | Y | `tier` uses 1-based indexing, i.e. |
| `discount.gift_discount.gift_infos[].threshold_type` | string | Y | The purchase threshold type of discount. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.gift_discount.gift_infos[].threshold_value` | string | Y | The positive purchase threshold for receiving the gift, interpreted according to `threshold_type`. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.gift_discount.gift_infos[].gift_details` | []object | Y | The gift SKUs for this tier. |
| `discount.gift_discount.gift_infos[].gift_details[].product_id` | string | Y | TikTok Shop product id of gift. |
| `discount.gift_discount.gift_infos[].gift_details[].sku_id` | string | Y | TikTok Shop sku id of gift. |
| `discount.gift_discount.gift_infos[].gift_details[].total_claim_limit` | string | Y | The quantity limit of the gift sku which is involved in the activity. |
| `discount.bxgy_discount` | object | N | Configurations for Buy X Get Y (BXGY) promotions. |
| `discount.bxgy_discount.threshold_type` | string | Y | The purchase threshold type. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bxgy_discount.threshold_value` | string | Y | The purchase threshold for the discount, interpreted according to `threshold_type`. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bxgy_discount.discount_type` | string | Y | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, FREE, AMOUNT_OFF_EACH |
| `discount.bxgy_discount.discount_value` | string | Y | Value of the discount. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF_EACH |
| `discount.bxgy_discount.benefit_max_applicable_quantity` | string | Y | The maximum quantity of item Y that can receive the BXGY benefit per application. |
| `discount.bxgy_discount.benefit_max_applicable_times` | string | N | Optional. |
| `target_user_info` | object | N | The promotion's target-user configuration. |
| `target_user_info.user_type` | string | N | Possible enumerations are: `ALL_USER` `SHOP_NEW_CUSTOMER` `SHOP_EXISTING_CUSTOMER` `SHOP_REPEAT_CUSTOMER` Giá trị: ALL_USER, SHOP_NEW_CUSTOMER, SHOP_EXISTING_CUSTOMER, SHOP_REPEAT_CUSTOMER |
| `auction_support_type` | string | N | Determines whether a shipping-discount activity supports auction products. Giá trị: ALL, EXCLUDE_AUCTION |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | A unique ID that identifies different activities. |
| `title` | string | BlackFridayDiscount | Activity name set by the seller. |
| `update_time` | int | 1661756811 | Last update time as a Unix timestamp in seconds. |

**Mã lỗi:** `17003203` A storewide free shipping promotion activity is already active.; `17029001` Invalid parameters; `17029002` Title Length Too Long; `17029003` The activity title is empty.; `17029004` Duplicate activity title.; `17029005` Begin Time Earlier Than Now; `17029006` The activity period is too short.; `17029007` The activity period is too long.; `17029008` FlashSale Period Too Long; `17029009` Activity does not exist; `17029010` Activity is deactivated; `17029011` Not allowed to update the beginning time of activity; `17029012` Not allowed to update expired activities; `17029022` The product or SKU cannot be in two `ONGOING` or `NOT_START` activities at the same time. Giá trị: ONGOING, NOT_START; `17029026` Invalid SellerID; `17029028` You are specifying activities not in your shop.; `17029052` ProductType is not supported; `17029070` Empty gift sku info.; `17029071` Gift sku id is invalid, not an integer.; `17029072` Gift sku repeated; `17029073` Price of the gift exceeds the upper limit; `17029074` Eligible products and the gift products must not be the same product.; `17029075` Number of gifts per tier does not meet the limit; `17029076` Gift sku is out of stock.; `17029077` Gifts across different tiers are repeated; `17029078` Number of gift tiers does not meet the requirement; `17029082` Specified time is outside of the defined range for this activity.; `17029083` The claim limit quantity of gift can not be decreased; `17029084` The threshold type of gift can not be modified; `17029085` The number of gift tiers can not be changed; `17029102` Product level and shop level are not interchangeable; `17029103` Partial of products have already been applied to another conflict promotion during the selected period.; `17029104` The product(s) selected have already been applied to another buy more save more promotion during the selected period.; `17029105` The threshold type and discount type can not be modified.; `17029106` Participation Limit info cannot change or modify.; `17029107` Due to overlapping with other activities, sellers cannot create shop-scope BMSM activity.; `17029109` The promotion exceeds the threshold.; `17029110` The discount value is below the minimum threshold.; `17029111` The threshold value is invalid.; `17029112` The discount value is invalid.; `17029113` The number of products will exceed the maximum number of the promotion.; `17029114` The promotion type is not supported.; `36009003` Internal error.

### Get Activity

`GET /promotion/202309/activities/{activity_id}` · scope: `seller.promotion.info`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | A unique ID that identifies different activities. |
| `title` | string | FlashSale 20230707 | Activity name (50 characters max.) The name must be unique. |
| `activity_type` | string | FIXED_PRICE | The activity type. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, GIFT_WITH_PURCHASE, BUY_X_GET_Y |
| `duration_type` | string | NORMAL | The effective time type of the current activity - NORMAL: `begin_time` and `end_time` must be filled - INDEFINITE: `begin_time` and `end_time` aren't required,… Giá trị: INDEFINITE, SHIPPING_DISCOUNT |
| `begin_time` | int | 1661756811 | Activity start time as a UNIX timestamp in seconds. |
| `end_time` | int | 1661856811 | Activity end time as a UNIX timestamp in seconds. |
| `participation_limit` | []object |  | The number of times a buyer can participate in the promotion. |
| `participation_limit[].type` | string | BUYER_NO_LIMIT | Determines whether the promotion activity limits the number of times each buyer can receive its benefit. Giá trị: BUYER_NO_LIMIT, BUYER_LIMIT_ONLY_ONE |
| `products` | []object |  | Product List. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE |
| `products[].id` | string | 7136011254174631686 | TikTok Shop Product ID. |
| `products[].activity_price` | object |  | Activity price. |
| `products[].activity_price.amount` | string | 70500 | Activity price amount. |
| `products[].activity_price.currency` | string | IDR | Currency unit of an activity price. |
| `products[].quantity_limit` | int | -1 | The quantity limit of the products involved in the activity. |
| `products[].quantity_per_user` | int | -1 | Limit of product purchase per buyer. |
| `products[].discount` | string | 10 | Discount value. |
| `products[].skus` | []object |  | Available only if `product_level==VARIATION`. |
| `products[].skus[].id` | string | 7136382541418366725 | TikTok Shop SKU ID. |
| `products[].skus[].discount` | string | 10 | Discount value. |
| `products[].skus[].quantity_limit` | int | -1 | The quantity limit of the SKU involved in the activity. |
| `products[].skus[].quantity_per_user` | int | -1 | Limit of SKU purchase per buyer. |
| `products[].skus[].activity_price` | object |  | Activity price. |
| `products[].skus[].activity_price.amount` | string | 70500 | Activity price amount. |
| `products[].skus[].activity_price.currency` | string | IDR | Currency unit of an activity price. |
| `products[].skus[].used_quantity` | int | 3 | The quantity of SKU that have already taken effect in the activity. |
| `products[].used_quantity` | int | 3 | The quantity of products that have already taken effect in the activity. |
| `status` | string | ONGOING | Activity status. Giá trị: DRAFT, NOT_START, ONGOING, EXPIRED, DEACTIVATED, NOT_EFFECTIVE |
| `create_time` | int | 1789355787347 | Activity creation time as a UNIX timestamp in milliseconds. |
| `update_time` | int | 1789355865063 | Activity update time as a UNIX timestamp in milliseconds. |
| `product_level` | string | PRODUCT | Activity product dimension, values are: - PRODUCT: Product level promotion activity. Giá trị: PRODUCT, VARIATION, SHOP |
| `activity_commands` | []string | ["IMMUTABLE"] | Applicable commands to the activity: - IMMUTABLE: The activity cann't be editable or deactivated Giá trị: IMMUTABLE |
| `discount` | object |  | Type-specific discount information. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE |
| `discount.shipping_discount` | object |  | Shipping discount |
| `discount.shipping_discount.threshold_type` | string | MINIMAL_ITEM_QUANTITY | The type of purchase criteria. Giá trị: NO_THRESHOLD, MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.shipping_discount.threshold_value` | string | 3 | The purchase threshold returned for the shipping discount, based on `threshold_type`. |
| `discount.shipping_discount.type` | string | DISCOUNT_SHIPPING_FEE | The type of shipping discount returned for the promotion activity. Giá trị: FREE_SHIPPING, DISCOUNT_SHIPPING_FEE |
| `discount.shipping_discount.value` | string | 10.5 | Value of the discount. |
| `discount.shipping_discount.shipping_method` | string | STANDARD_SHIPPING | The shipping methods to which this promotion applies. Giá trị: STANDARD_SHIPPING, EXPRESS_SHIPPING, ALL_SHIPPING_METHOD |
| `discount.shipping_discount.inventory_type` | string | SELF_FULFILLED | The inventory types to which the promotion activity applies: - `SELF_FULFILLED`: Only applies to Inventory fulfilled from seller warehouses - `FULFILLED_BY_TIKT… Giá trị: SELF_FULFILLED, FULFILLED_BY_TIKTOK, ALL, SHOP |
| `discount.shipping_discount.area_scope` | object |  | The (buyer's) geographical areas to which the promotion activity applies. |
| `discount.shipping_discount.area_scope.type` | string | WHOLE | The (buyer's) geographical area to which the promotion activity applies. Giá trị: WHOLE, SPECIFIC_AREAS |
| `discount.shipping_discount.area_scope.specific_areas` | []string | ["Washington","New York"] | The list of areas for which the promotion activity applies. |
| `discount.bmsm_discount` | object |  | Configurations related to Buy More Save More (BMSM) promotions. |
| `discount.bmsm_discount.details` | []object |  | Configurations for BMSM promotions. |
| `discount.bmsm_discount.details[].tier` | int | 1 | `tier` uses 1-based indexing, i.e. |
| `discount.bmsm_discount.details[].threshold_type` | string | MINIMAL_ITEM_QUANTITY | The type of purchase criteria. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bmsm_discount.details[].threshold_value` | string | 5 | The purchase threshold of the discount, based on the `threshold_type`, e.g., "3". |
| `discount.bmsm_discount.details[].type` | string | PERCENTAGE_OFF | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `discount.bmsm_discount.details[].value` | string | 10 | Value of the discount. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `discount.gift_discount` | object |  | Configurations related to Gift With Purchase (GWP) promotions. |
| `discount.gift_discount.gift_receiving_type` | string | ALL_RECEIVE | The receiving type of gift. Giá trị: ALL_RECEIVE |
| `discount.gift_discount.gift_infos` | []object |  | Configurations regarding Gift With Purchase (GWP) promotions. |
| `discount.gift_discount.gift_infos[].tier` | int | 1 | `tier` uses 1-based indexing, i.e. |
| `discount.gift_discount.gift_infos[].threshold_type` | string | MINIMAL_ITEM_QUANTITY | purchase threshold type of discount. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.gift_discount.gift_infos[].threshold_value` | string | 3 | The purchase threshold value of the gift, based on `threshold_type`. |
| `discount.gift_discount.gift_infos[].gift_details` | []object |  | Gift configurations related to Gift With Purchase (GWP) promotions. |
| `discount.gift_discount.gift_infos[].gift_details[].product_id` | string | 1732239744891062847 | TikTok Shop product id of gift. |
| `discount.gift_discount.gift_infos[].gift_details[].sku_id` | string | 1732239787859544639 | TikTok Shop sku ID of gift. |
| `discount.gift_discount.gift_infos[].gift_details[].total_claim_limit` | string | 100 | The quantity limit of the gift sku which is involved in the activity. |
| `discount.bxgy_discount` | object |  | Configurations related to Buy X Get Y (BXGY) promotions. |
| `discount.bxgy_discount.threshold_type` | string | MINIMAL_ITEM_QUANTITY | The purchase criterion returned for the activity. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `discount.bxgy_discount.threshold_value` | string | 5 | The purchase threshold returned for the activity, based on `threshold_type`. |
| `discount.bxgy_discount.discount_type` | string | AMOUNT_OFF_EACH | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, FREE, AMOUNT_OFF_EACH |
| `discount.bxgy_discount.discount_value` | string | 10 | Value of the discount. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF_EACH |
| `discount.bxgy_discount.benefit_max_applicable_quantity` | string | 5 | The quantity of item Y in a BXGY promotion. |
| `discount.bxgy_discount.benefit_max_applicable_times` | string | 5 | Optional. |
| `target_user_info` | object |  | The targeted user type of the promotion. |
| `target_user_info.user_type` | string | ALL_USER | The targeted user type. Giá trị: ALL_USER, SHOP_NEW_CUSTOMER, SHOP_EXISTING_CUSTOMER, SHOP_REPEAT_CUSTOMER, SHOP_ABANDONED_CHECKOUTS_CUSTOMER |
| `benefit_products` | []object |  | Benefit products in BXGY promotions |
| `benefit_products[].id` | string | 1732239744891062847 | TikTokShop product ID |
| `auction_support_type` | string | ALL | Whether a `SHIPPING_DISCOUNT` activity supports auction products. Giá trị: SHIPPING_DISCOUNT, ALL, EXCLUDE_AUCTION |

**Mã lỗi:** `17029001` Invalid parameters; `17029009` Activity does not exist; `17029028` You are specifying activities not in your shop.; `17029036` ActivityType is not supported.; `17029052` ProductType is not supported; `17029055` Request Timeout; `36009003` Internal error.

### Search Promotion Activities

`POST /promotion/202309/activities/search` · scope: `seller.promotion.info`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `status` | string | N | Filter activities by status. Giá trị: NOT_START, ONGOING, EXPIRED, DEACTIVATED |
| `activity_title` | string | N | Activity title key words. |
| `page_size` | int | N | Number of activities to return per page. |
| `page_token` | string | N | Specify the value for the page. |
| `activity_type` | string | N | The type of promotion activity to return. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, GIFT_WITH_PURCHASE, BUY_X_GET_Y |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `total_count` | int | 120 | Total number of activities matching the supplied filters. |
| `next_page_token` | string | 1661756811000 | Page cursor for the next request. |
| `activities` | []object |  | Activities list information. |
| `activities[].id` | string | 7136104329798256386 | A unique ID that identifies different activities. |
| `activities[].title` | string | FlashSale 20230707 | Activity name (50 characters max). |
| `activities[].activity_type` | string | FIXED_PRICE | The type of promotion activity. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, GIFT_WITH_PURCHASE, BUY_X_GET_Y |
| `activities[].duration_type` | string | INDEFINITE | The effective time type of the current activity - NORMAL: `begin_time` and `end_time` must be filled - INDEFINITE: `begin_time` and `end_time` aren't required,… Giá trị: INDEFINITE, SHIPPING_DISCOUNT |
| `activities[].begin_time` | int | 1661756811 | Activity start time, as a Unix timestamp in seconds. |
| `activities[].end_time` | int | 1661756811 | Activity end time, as a Unix timestamp in seconds. |
| `activities[].status` | string | ONGOING | Activity status. Giá trị: DRAFT, NOT_START, ONGOING, EXPIRED, DEACTIVATED, NOT_EFFECTIVE |
| `activities[].create_time` | int | 1788301318971 | The time when the activity was created, as a Unix timestamp in milliseconds. |
| `activities[].update_time` | int | 1788301318971 | The time when the activity was last updated, as a Unix timestamp in milliseconds. |
| `activities[].product_level` | string | PRODUCT | Activity product dimension, values are: ​- PRODUCT: Promotion activity applies at the product (SPU) level. Giá trị: PRODUCT, VARIATION, SHOP |
| `activities[].activity_commands` | []string | ["IMMUTABLE"] | Applicable commands to the activity: - IMMUTABLE: The activity cannot be edited or deactivated. Giá trị: IMMUTABLE |
| `activities[].participation_limit` | []object |  | The number of times a buyer can participate in the promotion. |
| `activities[].participation_limit[].type` | string | BUYER_NO_LIMIT | Determines whether the promotion activity limits the number of times each buyer can receive its benefit. Giá trị: BUYER_NO_LIMIT, BUYER_LIMIT_ONLY_ONE |
| `activities[].discount` | object |  | Discount information. Giá trị: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE |
| `activities[].discount.shipping_discount` | object |  | Configurations for the shipping discount, including purchase criteria, discount value, shipping method, inventory type, and area scope. |
| `activities[].discount.shipping_discount.threshold_type` | string | MINIMAL_ITEM_QUANTITY | The type of purchase criteria. Giá trị: NO_THRESHOLD, MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `activities[].discount.shipping_discount.threshold_value` | string | 3 | The purchase threshold of the shipping discount, based on the `threshold_type`, e.g., "3". |
| `activities[].discount.shipping_discount.type` | string | DISCOUNT_SHIPPING_FEE | The type of shipping discount returned for the promotion activity. Giá trị: FREE_SHIPPING, DISCOUNT_SHIPPING_FEE |
| `activities[].discount.shipping_discount.value` | string | 10.5 | Value of the shipping discount. |
| `activities[].discount.shipping_discount.shipping_method` | string | STANDARD_SHIPPING | The shipping methods to which this promotion applies. Giá trị: STANDARD_SHIPPING, EXPRESS_SHIPPING, ALL_SHIPPING_METHOD |
| `activities[].discount.shipping_discount.inventory_type` | string | SELF_FULFILLED | The inventory types to which the promotion activity applies: - `SELF_FULFILLED`: Only applies to Inventory fulfilled from seller warehouses - `FULFILLED_BY_TIKT… Giá trị: SELF_FULFILLED, FULFILLED_BY_TIKTOK, ALL, SHOP |
| `activities[].discount.shipping_discount.area_scope` | object |  | The (buyer's) geographical areas to which the promotion activity applies |
| `activities[].discount.shipping_discount.area_scope.type` | string | WHOLE | The buyer's geographical area to which the promotion activity applies. Giá trị: WHOLE, SPECIFIC_AREAS |
| `activities[].discount.shipping_discount.area_scope.specific_areas` | []string | ["Washington","New York"] | The list of areas for which the promotion activity applies. |
| `activities[].discount.bmsm_discount` | object |  | Configurations related to Buy More Save More (BMSM) promotions |
| `activities[].discount.bmsm_discount.details` | []object |  | Configurations for Buy More Save More (BMSM) promotions. |
| `activities[].discount.bmsm_discount.details[].tier` | int | 1 | `tier` uses 1-based indexing, i.e. |
| `activities[].discount.bmsm_discount.details[].threshold_type` | string | MINIMAL_ITEM_QUANTITY | The type of purchase criteria. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `activities[].discount.bmsm_discount.details[].threshold_value` | string | 5 | The purchase threshold of the discount, based on the `threshold_type`, e.g., "3". |
| `activities[].discount.bmsm_discount.details[].type` | string | PERCENTAGE_OFF | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `activities[].discount.bmsm_discount.details[].value` | string | 10 | Value of the discount. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF |
| `activities[].discount.gift_discount` | object |  | Configurations related to Gift With Purchase (GWP) promotions. |
| `activities[].discount.gift_discount.gift_receiving_type` | string | ALL_RECEIVE | The receiving type of gift. Giá trị: ALL_RECEIVE |
| `activities[].discount.gift_discount.gift_infos` | []object |  | Configurations regarding Gift With Purchase (GWP) promotions. |
| `activities[].discount.gift_discount.gift_infos[].tier` | int | 1 | `tier` uses 1-based indexing, i.e. |
| `activities[].discount.gift_discount.gift_infos[].threshold_type` | string | MINIMAL_ITEM_QUANTITY | Purchase threshold type of discount. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `activities[].discount.gift_discount.gift_infos[].threshold_value` | string | 3 | The purchase threshold value for the gift, based on the `threshold_type`. |
| `activities[].discount.gift_discount.gift_infos[].gift_details` | []object |  | Gift configurations related to Gift With Purchase (GWP) promotions. |
| `activities[].discount.gift_discount.gift_infos[].gift_details[].product_id` | string | 1732239744891062847 | TikTok Shop product id of gift. |
| `activities[].discount.gift_discount.gift_infos[].gift_details[].sku_id` | string | 1732239787859544639 | TikTok Shop sku id of gift. |
| `activities[].discount.gift_discount.gift_infos[].gift_details[].total_claim_limit` | string | 100 | The quantity limit of the gift sku which is involved in the activity. |
| `activities[].discount.bxgy_discount` | object |  | Configurations related to Buy X Get Y (BXGY) promotions. |
| `activities[].discount.bxgy_discount.threshold_type` | string | MINIMAL_ITEM_QUANTITY | The purchase criterion returned for the activity. Giá trị: MINIMAL_ITEM_QUANTITY, MINIMAL_ORDER_AMOUNT |
| `activities[].discount.bxgy_discount.threshold_value` | string | 5 | The purchase threshold returned for the activity, based on `threshold_type`. |
| `activities[].discount.bxgy_discount.discount_type` | string | AMOUNT_OFF_EACH | The type of discount awarded as a benefit. Giá trị: PERCENTAGE_OFF, FREE, AMOUNT_OFF_EACH |
| `activities[].discount.bxgy_discount.discount_value` | string | 5 | Value of the discount. Giá trị: PERCENTAGE_OFF, AMOUNT_OFF_EACH |
| `activities[].discount.bxgy_discount.benefit_max_applicable_quantity` | string | 5 | The quantity of item Y in a BXGY promotion. |
| `activities[].discount.bxgy_discount.benefit_max_applicable_times` | string | 5 | Optional. |
| `activities[].auction_support_type` | string | ALL | Whether a `SHIPPING_DISCOUNT` activity supports auction products. Giá trị: SHIPPING_DISCOUNT, ALL, EXCLUDE_AUCTION |

**Mã lỗi:** `17029001` Invalid parameters; `17029055` Request Timeout; `17029059` `page_token + page_size` must be less than `10000`.; `36009003` Internal error.

### Update Promotion Activity Products

`PUT /promotion/202309/activities/{activity_id}/products` · scope: `seller.promotion.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `products` | []object | N | The items to add to the list or the existing items in the list of promotion activity to edit. |
| `products[].id` | string | Y | TikTok Shop product ID |
| `products[].activity_price_amount` | string | N | Deal price in the product's currency. Giá trị: FIXED_PRICE, FLASHSALE |
| `products[].discount` | string | N | Discount value. |
| `products[].quantity_limit` | int | Y | The quantity limit of the products involved in the activity. |
| `products[].quantity_per_user` | int | Y | Limit of product purchase per buyer. |
| `products[].skus` | []object | N | The SKUs to add to the list or to edit. |
| `products[].skus[].id` | string | Y | SKU ID |
| `products[].skus[].activity_price_amount` | string | N | Deal price in the SKU's currency. Giá trị: FIXED_PRICE, FLASHSALE |
| `products[].skus[].discount` | string | N | Discount value. Giá trị: FIXED_PRICE, FLASHSALE |
| `products[].skus[].quantity_limit` | int | Y | The quantity limit of the SKU involved in the activity. |
| `products[].skus[].quantity_per_user` | int | Y | Limit of SKU purchase per buyer. |
| `activity_id` | string | Y | A unique ID that identifies activities. Giá trị: DEACTIVATED, EXPIRED |
| `benefit_product_ids` | []string | N | IDs of benefit products to add to or update in a Buy More Save More (BMSM) or Buy X Get Y (BXGY) activity. |
| `exclude_product_ids` | []string | N | For a SHOP-level Buy More Save More (BMSM) or Buy X Get Y (BXGY) activity, these are the IDs of products to add to or update in the exclusion list. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | A unique ID that identifies different activities. |
| `title` | string | DiscountPromotion | Activity name set by the merchant. |
| `update_time` | int | 1661756811 | The time when the response was generated, as a Unix timestamp in seconds (UTC). |
| `status` | string | DRAFT | Activity status. Giá trị: DRAFT, NOT_START, ONGOING, EXPIRED, DEACTIVATED, NOT_EFFECTIVE |
| `total_count` | int | 1 | The number of items in this request. |

**Mã lỗi:** `17011102` Promotion title cannot be a duplicate.; `17027107` Internal system error, please try again later.; `17029001` Invalid parameters; `17029009` Activity does not exist; `17029010` Activity is deactivated; `17029012` Not allowed to update expired activities; `17029013` `sku` must be `[]` for activities of which `product_level==PRODUCT`.; `17029014` The value of `quantity_per_user` is out of range.; `17029015` When `product_level==VARIATION`, the value of `quantity_limit` must be `-1`.; `17029016` Invalid SKU ID.; `17029017` You are specifying products not in your shop.; `17029018` The provided SKU quantity is greater than the available stock.; `17029019` The provided product quantity is greater than the available stock.; `17029020` Product discount exceeds limit.; `17029021` SKU discount exceeds limit.; `17029022` The product or SKU cannot be in two `ONGOING` or `NOT_START` activities at the same time. Giá trị: ONGOING, NOT_START; `17029025` The quantity of the products or the SKUs included in the activity exceeds `10000`.; `17029026` Invalid SellerID; `17029028` You are specifying activities not in your shop.; `17029029` You are incorrectly specifying `activity_price_amount` instead of `discount` when `activity_type==DIRECT_DISCOUNT` and `product_level==PRODUCT`.; `17029030` You are incorrectly specifying `discount` instead of `activity_price_amount` when `activity_type==FIXED_PRICE / FLASHSALE` and `product_level==PRODUCT`.; `17029031` You are incorrectly specifying `activity_price_amount` instead of `discount` when `activity_type==DIRECT_DISCOUNT` and `product_level==VARIATION`.; `17029032` You are incorrectly specifying `discount` instead of `activity_price_amount` when `activity_type==FIXED_PRICE / FLASHSALE` and `product_level==VARIATION`.; `17029033` `products` is empty.; `17029034` `activity_price_amount` is invalid.; `17029037` `skus` is empty when `product_level==VARIATION`.; `17029038` When `product_level==VARIATION`, you must not specify `product.activity_price_amount` and `product.discount`.; `17029039` Duplicate product ID.; `17029040` Duplicate SKU ID.; `17029041` You must specify `product.discount` when `activity_type==DIRECT_DISCOUNT` and `product_level==PRODUCT`.; `17029042` You must specify `product.activity_price_amount` when `activity_type==FIXED_PRICE / FLASHSALE` and `product_level==PRODUCT`.; `17029043` You must specify `sku.discount` when `activity_type==DIRECT_DISCOUNT` and `product_level==VARIATION`.; `17029044` You must specify `sku.activity_price_amount` when `activity_type==FIXED_PRICE / FLASHSALE` and `product_level==VARIATION`.; `17029045` Duplicated RequestSerialNo.; `17029046` Number of items per request exceeds the limit.; `17029047` An `ONGOING` flash sale product cannot be updated. Giá trị: ONGOING; `17029048` An `ONGOING` flash sale SKU cannot be updated. Giá trị: ONGOING; `17029049` ActivityVersion is not supported.; `17029050` The value of `quantity_limit` and `quantity_per_user` must be `-1` or in the range of `[1, 99]`.; `17029051` Product ID not found.; `17029053` The SKU ID does not match the product ID.; `17029054` Please limit the request frequency.; `17029055` Request Timeout; `17029056` Invalid product status.; `17029058` Unable to decrease buyer/num PurchaseLimit.; `17029060` The Categories of products is invalid; `17029062` Discount is below the limit, please confirm.; `17029063` SKU discount is below the limit, please confirm.; `17029064` Product is non-sellable.; `17029065` Target quota is less than the initial quota.; `17029066` Limit info cannot be changed or modified.; `17029067` Auction products are not eligible for promotions.; `17029069` Access denied.; `17029074` Eligible products and the gift products must not be the same product.; `17029079` Product is already assigned in another conflicting promotion.; `17029103` Partial of products have already been applied to another conflict promotion during the selected period.; `17029104` The product(s) selected have already been applied to another buy more save more promotion during the selected period.; `17029113` The number of products will exceed the maximum number of the promotion.; `36009003` Internal error.

### Remove Promotion Activity Products

`DELETE /promotion/202309/activities/{activity_id}/products` · scope: `seller.promotion.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `product_ids` | []string | N | IDs of the products to remove. |
| `sku_ids` | []string | N | IDs of the SKUs to remove. |
| `benefit_product_ids` | []string | N | IDs of benefit-product relations to remove from a `BUY_MORE_SAVE_MORE` or `BUY_X_GET_Y` activity. Giá trị: BUY_MORE_SAVE_MORE, BUY_X_GET_Y |
| `exclude_product_ids` | []string | N | IDs of excluded-product relations to remove from a SHOP-scoped `BUY_MORE_SAVE_MORE`, `BUY_X_GET_Y`, or `SHIPPING_DISCOUNT` activity. Giá trị: BUY_MORE_SAVE_MORE, BUY_X_GET_Y, SHIPPING_DISCOUNT |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | A unique ID that identifies activities. |
| `status` | string | ONGOING | Activity status. Giá trị: DRAFT, NOT_START, ONGOING, EXPIRED, DEACTIVATED, NOT_EFFECTIVE |
| `update_time` | int | 1661765135 | The time when the response was generated, as a Unix timestamp in seconds (UTC). |

**Mã lỗi:** `17027802` Internal error, promotion purchase limit deletion failed, please try again; `17029001` Invalid parameters; `17029009` Activity does not exist; `17029010` Activity is deactivated; `17029011` Not allowed to update the beginning time of activity; `17029012` Not allowed to update expired activities; `17029016` Invalid SKU ID.; `17029023` Products Not In Promotion; `17029024` SKU(s) not found in this promotion.; `17029028` You are specifying activities not in your shop.; `17029036` ActivityType is not supported.; `17029046` Number of items per request exceeds the limit.; `17029047` An `ONGOING` flash sale product cannot be updated. Giá trị: ONGOING; `17029048` An `ONGOING` flash sale SKU cannot be updated. Giá trị: ONGOING; `17029052` ProductType is not supported; `17029055` Request Timeout; `17029069` Access denied.; `17029074` Eligible products and the gift products must not be the same product.; `17029079` Product is already assigned in another conflicting promotion.; `17029102` Product level and shop level are not interchangeable; `36009003` Internal error.

### Deactivate Activity

`POST /promotion/202309/activities/{activity_id}/deactivate` · scope: `seller.promotion.write`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | A unique ID that identifies different activities. |
| `title` | string | FlashSale 20230707 | Activity name (50 characters max.) The name must be unique. |
| `status` | string | DEACTIVATED | Activity status after successful deactivation. Giá trị: DEACTIVATED |
| `update_time` | int | 1661756811 | The time when the deactivation response was created, as a Unix timestamp in seconds. |

**Mã lỗi:** `17029001` Invalid parameters; `17029009` Activity does not exist; `17029010` Activity is deactivated; `17029011` Not allowed to update the beginning time of activity; `17029012` Not allowed to update expired activities; `17029028` You are specifying activities not in your shop.; `17029036` ActivityType is not supported.; `17029052` ProductType is not supported; `17029055` Request Timeout; `36009003` Internal error.

### Republish Activity

`POST /promotion/202607/activities/{activity_id}/republish` · scope: `seller.promotion.write`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `end_time` | int | N | Activity end time as a Unix timestamp in seconds. |
| `begin_time` | int | N | Activity start time as a Unix timestamp in seconds. |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `activity_id` | string | 7136104329798256386 | On success, the ID of the republished activity. |

**Mã lỗi:** `17003243` This activity don't allow republishing

### Get Coupon

`GET /promotion/202406/coupons/{coupon_id}` · scope: `seller.promotion.info, seller.customer_service`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `coupon` | object |  | The coupon detail object, including identity, display settings, status, claim and redemption durations, discount, threshold, product scope, usage limits, usage… |
| `coupon.id` | string | 7342461823095965461 | A unique ID that identifies different coupons. |
| `coupon.title` | string | Coupon202407 | Seller-specified title of the coupon. |
| `coupon.display_type` | string | CHAT<br><br><br> | The display type of coupons. Giá trị: REGULAR, LIVE, CREATOR_EXCLUSIVE, CHAT, PROMO_CODE |
| `coupon.status` | string | NOT_START<br><br><br><br> | Coupon's promotion status. Giá trị: NOT_START, ONGOING, EXPIRED, DEACTIVATED |
| `coupon.create_time` | int | 1661756811000 | The UNIX timestamp, in milliseconds, when the coupon was created. |
| `coupon.update_time` | int | 1661756811000 | The UNIX timestamp, in milliseconds, when the coupon was last updated. |
| `coupon.claim_duration` | object |  | Claimable period of the coupon. |
| `coupon.claim_duration.start_time` | int | 1709568000 | The UNIX timestamp, in seconds, from which a shopper can claim the coupon. |
| `coupon.claim_duration.end_time` | int | 1709654340 | The UNIX timestamp, in seconds, after which a shopper can no longer claim the coupon. |
| `coupon.redemption_duration` | object |  | Period during which the coupon can be redeemed (i.e. |
| `coupon.redemption_duration.type` | string | ABSOLUTE | Values: - `ABSOLUTE`: the redemption period starts from `start_time` and ends at `end_time`. Giá trị: ABSOLUTE, RELATIVE |
| `coupon.redemption_duration.start_time` | int | 1709754340 | The UNIX timestamp, in seconds, at which the redemption period starts, if `type == 'ABSOLUTE'`. |
| `coupon.redemption_duration.end_time` | int | 1709768000 | The UNIX timestamp, in seconds, at which the redemption period ends, if `type == 'ABSOLUTE'`. |
| `coupon.redemption_duration.relative_time` | int | 4 | The number of days after coupon claim time for which a coupon is valid to be redeemed, `type = 'RELATIVE'`. |
| `coupon.display_channels` | []string | ["SHOPPING_CENTER"]<br><br> | The channels in which the coupons can be displayed or distributed: - `ALL`: All channels. Giá trị: ALL, SHOPPING_CENTER, CHAT, LIVE, PRIVATE, PRIVATE_WITH_ALLOW, VIDEO, SHOWCASE |
| `coupon.promo_code` | string | TTS12345 | Promotion code. |
| `coupon.target_buyer_segment` | string | NEW<br> | The target buyer segment of the coupon. Giá trị: ALL, NEW, REPEAT_CUSTOMERS, RECENT_CUSTOMERS, FREQUENT_CUSTOMERS, LAPSED_CUSTOMERS, NEW_FOLLOWERS, EXISTING_ACTIVE_FOLLOWERS |
| `coupon.usage_limits` | object |  | Limitations for the use of the coupons. |
| `coupon.usage_limits.single_buyer_claim_limit` | int | 30 | The number of times a single buyer can claim the coupon. |
| `coupon.usage_limits.total_claim_limit` | int | 1000 | The total number of claims allowed for the coupon across all buyers. |
| `coupon.usage_limits.redemption_limit` | int | 1000 | The number of times that a claimed coupon can be redeemed across all buyers. |
| `coupon.usage_stats` | object |  | Usage statistics |
| `coupon.usage_stats.claimed_count` | int | 5 | The number of times that the coupon has been claimed. |
| `coupon.usage_stats.redeemed_count` | int | 0 | The number of times that a coupon has been redeemed (i.e. |
| `coupon.discount` | object |  | The coupon discount configuration, including type, reduction_amount, percentage, and max_discount. |
| `coupon.discount.type` | string | AMOUNT_OFF | The type of discount offered by the coupon. Giá trị: AMOUNT_OFF, PERCENT_OFF |
| `coupon.discount.reduction_amount` | object |  | Will appear when `type == 'AMOUNT_OFF'`. |
| `coupon.discount.reduction_amount.amount` | string | 30.5 | Monetary value of an `AMOUNT_OFF` discount. Giá trị: AMOUNT_OFF |
| `coupon.discount.reduction_amount.currency` | string | USD | Currency of the discount amount. |
| `coupon.discount.percentage` | string | 30 | The discount offered by the coupon, in percentage points. |
| `coupon.discount.max_discount` | object |  | Optional monetary upper limit of the total discount amount when `type == "PERCENT_OFF"`. |
| `coupon.discount.max_discount.amount` | string | 40 | Monetary value of the `max_discount`. |
| `coupon.discount.max_discount.currency` | string | USD | Currency of the `max_discount`. |
| `coupon.threshold` | object |  | Optional minimum purchase criteria that must be met in order to receive the coupon's discount. |
| `coupon.threshold.type` | string | MIN_SPEND | The type of purchase criteria. Giá trị: NONE, MIN_SPEND |
| `coupon.threshold.min_spend` | object | MIN_SPEND | When `threshold.type == 'MIN_SPEND'`, a customer must purchase products from the range defined by `product_scope` with a monetary amount greater than or equal t… |
| `coupon.threshold.min_spend.amount` | string | 30 | Monetary value of the `min_spend`. |
| `coupon.threshold.min_spend.currency` | string | USD | Currency of the `min_spend` threshold. |
| `coupon.product_scope` | string | FULL_SHOP | The range of the products which the coupon applies to. Giá trị: FULL_SHOP, SPECIFIC_PRODUCTS |
| `coupon.product_ids` | []string | ["1729581162206365900"] | The IDs of the eligible products when `product_scope == 'SPECIFIC_PRODUCTS'`. |
| `coupon.seller_tnc` | string | terms & conditions: xxx | Custom terms & conditions optionally uploaded by the seller when configuring the coupon. |
| `coupon.creation_source` | string | SELLER_CENTER | The system where the coupon is created: - `SELLER_CENTER`: Created via the Promotions section of TikTok Seller Center. Giá trị: SELLER_CENTER, SELLER_APP, TTS_CRM |
| `coupon.live_tasks` | []object |  | The tasks which the shopper must fulfill before claiming the coupon distributed in TikTok live. |
| `coupon.live_tasks[].type` | string | FOLLOW_HOST | Possible enumerations: - `NONE`: Any shopper in the LIVE room may claim the coupon. Giá trị: NONE, FOLLOW_HOST, WATCH_FOR_MIN_TIME |
| `coupon.live_tasks[].min_watch_time` | string | 60 | Number of seconds for which the shopper must watch the LIVE in order to claim the coupon. |

**Mã lỗi:** `36009003` Internal error.

### Search Coupon List

`POST /promotion/202406/coupons/search` · scope: `seller.promotion.info, seller.customer_service`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `page_token` | string | N | Page cursor. |
| `page_size` | int | N | The number of coupons returned in one page of results. |

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `status` | []string | N | Coupon's promotion status. Giá trị: NOT_START, ONGOING, EXPIRED, DEACTIVATED |
| `title_keyword` | string | N | Keywords in coupon titles to search for. |
| `display_type` | []string | N | The display type of coupons. Giá trị: REGULAR, LIVE, CREATOR_EXCLUSIVE, CHAT, PROMO_CODE |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `total_count` | int | 20 | Total number of coupon records matching the supplied filters. |
| `next_page_token` | string | 1661756811000 | Cursor for the next page. |
| `coupons` | []object |  | The list of coupons matching the search criteria. |
| `coupons[].id` | string | 7342461823095965461 | A unique ID that identifies different coupons. |
| `coupons[].title` | string | Coupon202407 | Seller-specified title of the coupon. |
| `coupons[].display_type` | string | CHAT<br><br><br> | The display type of coupons. Giá trị: REGULAR, LIVE, CREATOR_EXCLUSIVE, CHAT, PROMO_CODE |
| `coupons[].status` | string | NOT_START<br><br><br><br> | Coupon's promotion status. Giá trị: NOT_START, ONGOING, EXPIRED, DEACTIVATED |
| `coupons[].create_time` | int | 1661756811000 | The Unix timestamp, in milliseconds, when the coupon was created. |
| `coupons[].update_time` | int | 1661756811000 | The Unix timestamp, in milliseconds, when the coupon was last updated. |
| `coupons[].claim_duration` | object |  | Claimable period of the coupon |
| `coupons[].claim_duration.start_time` | int | 1709568000 | The Unix timestamp, in seconds, from which a shopper can claim the coupon. |
| `coupons[].claim_duration.end_time` | int | 1709654340 | The Unix timestamp, in seconds, after which a shopper can no longer claim the coupon. |
| `coupons[].redemption_duration` | object |  | Period during which the coupon can be redeemed (i.e. |
| `coupons[].redemption_duration.type` | string | ABSOLUTE | Values: - `ABSOLUTE`: the redemption period starts from `start_time` and ends at `end_time`. Giá trị: ABSOLUTE, RELATIVE |
| `coupons[].redemption_duration.start_time` | int | 1709654340 | The Unix timestamp, in seconds, at which the redemption period starts when `type == 'ABSOLUTE'`. |
| `coupons[].redemption_duration.end_time` | int | 1709754340 | The Unix timestamp, in seconds, at which the redemption period ends when `type == 'ABSOLUTE'`. |
| `coupons[].redemption_duration.relative_time` | int | 4 | The number of days after coupon claim time for which a coupon is valid to be redeemed, `type = 'RELATIVE'`. |
| `coupons[].promo_code` | string | TTS12345 | Promotion code. |
| `coupons[].target_buyer_segment` | string | NEW<br> | The target buyer segment of the coupon. Giá trị: ALL, NEW, REPEAT_CUSTOMERS, RECENT_CUSTOMERS, FREQUENT_CUSTOMERS, LAPSED_CUSTOMERS, NEW_FOLLOWERS, EXISTING_ACTIVE_FOLLOWERS |
| `coupons[].usage_limits` | object |  | Limitations for the use of the coupons. |
| `coupons[].usage_limits.single_buyer_claim_limit` | int | 30 | The number of times a single buyer can claim the coupon. |
| `coupons[].usage_limits.total_claim_limit` | int | 1000 | The total number of claims allowed for the coupon across all buyers. |
| `coupons[].usage_limits.redemption_limit` | int | 1000 | The number of times that a claimed coupon can be redeemed across all buyers. |
| `coupons[].discount` | object |  | The coupon discount configuration, including type, reduction_amount, percentage, and max_discount. |
| `coupons[].discount.type` | string | AMOUNT_OFF | The type of discount offered by the coupon. Giá trị: AMOUNT_OFF, PERCENT_OFF |
| `coupons[].discount.reduction_amount` | object |  | Will appear when `type == 'AMOUNT_OFF'`. |
| `coupons[].discount.reduction_amount.amount` | string | 30.5 | Monetary value of an `AMOUNT_OFF` discount. Giá trị: AMOUNT_OFF |
| `coupons[].discount.reduction_amount.currency` | string | USD | Currency of the discount amount. |
| `coupons[].discount.percentage` | string | 30 | The discount offered by the coupon, in percentage points. |
| `coupons[].discount.max_discount` | object |  | Optional monetary upper limit of the total discount amount when `type == "PERCENT_OFF"`. |
| `coupons[].discount.max_discount.amount` | string | 40 | Monetary value of the `max_discount`. |
| `coupons[].discount.max_discount.currency` | string | USD | Currency of the `max_discount`. |
| `coupons[].threshold` | object |  | Optional minimum purchase criteria that must be met in order to receive the coupon's discount. |
| `coupons[].threshold.type` | string | MIN_SPEND | The type of purchase criteria. Giá trị: NONE, MIN_SPEND |
| `coupons[].threshold.min_spend` | object |  | When `threshold.type == 'MIN_SPEND'`, a customer must purchase products from the range defined by `product_scope` with a monetary amount greater than or equal t… |
| `coupons[].threshold.min_spend.amount` | string | 30 | Monetary value of the `min_spend`. |
| `coupons[].threshold.min_spend.currency` | string | USD | Currency of the `min_spend` threshold. |
| `coupons[].product_scope` | string | FULL_SHOP | The range of the products which the coupon applies to. Giá trị: FULL_SHOP, SPECIFIC_PRODUCTS |
| `coupons[].creation_source` | string | SELLER_CENTER | The system where the coupon is created: - `SELLER_CENTER`: Created via the Promotions section of TikTok Seller Center. Giá trị: SELLER_CENTER, SELLER_APP, TTS_CRM |

**Mã lỗi:** `36009003` Internal error.

## Fullfillment

### Search Package

`POST /fulfillment/202309/packages/search` · scope: `seller.fulfillment.basic`

**Query**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `page_size` | int | Y | The number of results to be returned per page. |
| `sort_field` | string | N | The returned results will be sorted by the specified field. |
| `sort_order` | string | N | The sort order for the sort_field parameter. Giá trị: DESC, ASC |
| `page_token` | string | N | An opaque token used to retrieve the next page of a paginated result set. |

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `create_time_ge` | int | N | Filter packages to those created at or after the specified time. |
| `create_time_lt` | int | N | Filter packages to those created before the specified time. |
| `update_time_ge` | int | N | Filter packages to those updated at or after the specified time. |
| `update_time_lt` | int | N | Filter packages to those updated before the specified time. |
| `package_status` | string | N | Possible values: - `TO_FULFILL` - `PROCESSING`: Package has been arranged by seller. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `next_page_token` | string | 6AsPQsUMvH3RkchNUPPh22NROHkE0D8pmq/N5M1k… | An opaque token used to retrieve the next page of a paginated result set. |
| `total_count` | int | 221 | The number of packages that meet the query conditions. |
| `packages` | []object |  | The response list of packages. |
| `packages[].id` | string | 577828281214600000 | Package ID. |
| `packages[].orders` | []object |  | The response list of TikTok Shop orders. |
| `packages[].orders[].id` | string | 577828281214600000 | TikTok Shop order ID. |
| `packages[].orders[].skus` | []object |  | [Deprecated]The response list of SKUs. |
| `packages[].orders[].skus[].id` | string | 577828281214883345 | [Deprecated]The SKU ID. |
| `packages[].orders[].skus[].name` | string | white,128g | [Deprecated]The SKU name. |
| `packages[].orders[].skus[].image_url` | string | https://p19-oec-sg.ibyteimg.com/tos-mali… | [Deprecated]The SKU image in order snapshot. |
| `packages[].orders[].skus[].quantity` | int | 5 | [Deprecated]The SKU quantity. |
| `packages[].create_time` | int | 1635338186 | Package creation time. |
| `packages[].update_time` | int | 1635338186 | The time when the package was last updated. |
| `packages[].status` | string | PROCESSING | Possible values: - `TO_FULFILL` - `PROCESSING`: Package has been arranged by seller. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |
| `packages[].tracking_number` | string | 6617675021119438849 | Package tracking number. |
| `packages[].shipping_provider_name` | string | TT Virtual express | Package shipping provider. |
| `packages[].shipping_provider_id` | string | 6617675021119438849 | Package shipping provider ID. |
| `packages[].order_line_item_ids` | []string | ["1729382476852921560"] | The order line item ID contained in the package. |

**Mã lỗi:** `36009003` Internal error.

### Get Package Detail

`GET /fulfillment/202309/packages/{package_id}` · scope: `seller.fulfillment.basic, seller.fs.order&fufillment.management`

**Response `data`**

| Trường | Kiểu | Ví dụ | Mô tả |
|---|---|---|---|
| `package_id` | string | 5433567853345 | TikTok Shop package ID. |
| `orders` | []object |  | The response list of TikTok Shop orders. |
| `orders[].id` | string | 54335448533296 | TikTok Shop order ID. |
| `orders[].skus` | []object |  | [Deprecated]SKU information. |
| `orders[].skus[].id` | string | 2729382476852921560 | [Deprecated]SKU ID. |
| `orders[].skus[].name` | string | white,128g | [Deprecated]SKU name. |
| `orders[].skus[].image_url` | string | https://p19-oec-sg.ibyteimg.com/tos-mali… | [Deprecated]SKU image in order snapshot. |
| `orders[].skus[].quantity` | int | 5 | [Deprecated]SKU quantity. |
| `package_status` | string | PROCESSING | Possible values: - `TO_FULFILL`：Waiting for the merchant or warehouse to ship the package. Giá trị: TO_FULFILL, PROCESSING, FULFILLING, COMPLETED, CANCELLED |
| `package_sub_status` | string | CREATED | Package sub-status. Giá trị: CREATED, ISSUE, STOCKING, RTSING, HBW, HBWING, TSP_PENDING, RTS, TTS, DELIVERED, SHIP_EXCEPTION, PART_DELIVERED_AND_EXCEPTION … |
| `ship_exception_reason` | string | PACKAGE_LOST | -PACKAGE_DAMAGAED -PACKAGE_LOST -RETURNED_TO_SHIPPER -PACKAGE_SCRAP |
| `split_and_combine_tag` | string | SPLIT | Possible values: - `DEFAULT`: The package has not undergone any combine or split operation. Giá trị: DEFAULT, COMBINE, SPLIT |
| `has_multi_skus` | bool | true | Whether there are multiple SKU IDs in a package. |
| `note_tag` | string | BUYER_UNNOTED | Possible values: - `BUYER_UNNOTED`: The order has not been noted by buyer. Giá trị: BUYER_UNNOTED, BUYER_NOTED |
| `shipping_provider_name` | string | TT Virtual express | Package shipping provider name. |
| `shipping_provider_id` | string | 6617675021119438849 | Package shipping provider ID. |
| `shipping_type` | string | TIKTOK | Indicates who provides the shipping service. Giá trị: TIKTOK, SELLER |
| `delivery_option_name` | string | Shipped from seller<br> | Delivery option name. |
| `delivery_option_id` | string | 7091146663229654785 | Order delivery option ID. |
| `tracking_number` | string | 6617675021119438849 | Package tracking number. |
| `last_mile_tracking_number` | string | 6617675021119438849 | For cross-border order only. |
| `pickup_slot` | object |  | The carrier pickup time slot scheduled for the package. |
| `pickup_slot.start_time` | int | 1635338186 | Start of the time slot when the carrier is scheduled to pick up the package, as a Unix timestamp in seconds. |
| `pickup_slot.end_time` | int | 1635338186 | End of the time slot when the carrier is scheduled to pick up the package, as a Unix timestamp in seconds. |
| `create_time` | int | 1635338186 | Package creation time. |
| `handover_method` | string | PICKUP | Whether the package is delivered by pick up or drop off. Giá trị: PICKUP, DROP_OFF |
| `order_line_item_ids` | []string | ["1729382476852921560"] | The order line item ID contained in the package. |
| `recipient_address` | object |  | Recipient address and contact information. |
| `recipient_address.full_address` | string | 1*** Coleman Ave San Jose, CA 95110<br> | The recipient's complete address, including geographic and detailed street-address information. |
| `recipient_address.phone_number` | string | (+1)213-***-1234<br> | The telephone number of the buyer. |
| `recipient_address.name` | string | Zay | The name of the recipient. |
| `recipient_address.postal_code` | string | 95110<br> | The postal code that can be used by seller for shipping (in the U.S, this refers to the ZIP code). |
| `recipient_address.address_detail` | string | Unit one building 8<br> | The detailed street-address portion of the recipient's address. |
| `recipient_address.region_code` | string | US | The country or region code of the recipient address. |
| `recipient_address.address_line1` | string | TikTok 5800 bristol Pkwy<br> | The first line of the street address |
| `recipient_address.address_line2` | string | Suite 100<br> | The second line of the street address. |
| `recipient_address.address_line3` | string | Suite 100<br> | The third line of the street address. |
| `recipient_address.address_line4` | string | Suite 100<br> | The fourth line of the street address. |
| `sender_address` | object |  | The sender's address and contact information. |
| `sender_address.full_address` | string | 1*** Coleman Ave San Jose, CA 95110 | The complete sender address, including geographic address components and address details. |
| `sender_address.phone_number` | string | (+1)213-***-1234<br> | The telephone number of the sender. |
| `sender_address.name` | string | Zay | The name of the sender. |
| `sender_address.postal_code` | string | 95110 | The postal code of the sender. |
| `sender_address.address_detail` | string | Unit one building 8 | The detailed street-address portion of the sender's address. |
| `sender_address.region_code` | string | US | Region code of the sender. |
| `sender_address.address_line1` | string | TikTok 5800 bristol Pkwy | The first line of the sender's street address. |
| `sender_address.address_line2` | string | Suite 100 | The second line of the sender's street address. |
| `sender_address.address_line3` | string | Suite 100 | The third line of the sender's street address. |
| `sender_address.address_line4` | string | Suite 100 | The fourth line of the sender's street address. |
| `weight` | object |  | The weight of the scheduled package. |
| `weight.value` | string | 1.2 | The value of the weight of the scheduled package. |
| `weight.unit` | string | GRAM | The unit of measurement used to measure the weight. Giá trị: GRAM, POUND |
| `dimension` | object |  | The dimensions of the scheduled package. |
| `dimension.length` | string | 1.2 | The length of the scheduled package. |
| `dimension.width` | string | 0.2 | The width of the scheduled package. |
| `dimension.height` | string | 0.03 | The height of the scheduled package. |
| `dimension.unit` | string | CM | The unit of measurement for the package dimensions. Giá trị: INCH |
| `update_time` | int | 1635338186 | The time when the package was last updated, as a Unix timestamp in seconds. |
| `insurance` | object |  | US only. |
| `insurance.is_purchased` | bool | true | Whether insurance has been purchased for the package. |
| `insurance.coverage_amount` | string | 200 | The insurance coverage amount for the package. |
| `insurance.is_claim_eligible` | bool | true | Whether the package is eligible for an insurance claim, based on eligible refund reasons. |
| `insurance.claim_status` | string | CLAIM_PENDING | The insurance claim status. Giá trị: NOT_STARTED, CLAIM_PENDING, APPROVED, DECLINED |
| `biz_extension_tags` | []string | ["NON_MCF"] | Special tags for current package if any. Giá trị: NON_MCF |
| `warehouse_id` | string | 7317467398322308906 | Seller Warehouse id for this package |

**Mã lỗi:** `10006402` Internal error.; `36009003` Internal error.

### Ship Package

`POST /fulfillment/202309/packages/{package_id}/ship` · scope: `seller.fulfillment.basic, seller.fs.order&fufillment.management`

**Body**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `handover_method` | string | N | Possible values: - `PICKUP`: A logistics carrier will pick up the package(s) from the seller's pickup address. Giá trị: PICKUP, DROP_OFF |
| `pickup_slot` | object | N | Pickup time slot. |
| `pickup_slot.start_time` | int | N | The start date and time of the package pickup time slot, as a Unix timestamp in seconds. |
| `pickup_slot.end_time` | int | N | The end date and time of the package pickup time slot, as a Unix timestamp in seconds. |
| `self_shipment` | object | N | Only needed for merchant self-shipping packages. |
| `self_shipment.tracking_number` | string | Y | For package with `SEND_BY_SELLER` as `delivery_option` (merchant self-shipping mode), you must input a `tracking_number` to call this API. Giá trị: SEND_BY_SELLER |
| `self_shipment.shipping_provider_id` | string | Y | For package with `SEND_BY_SELLER` as `delivery_option` (merchant self-shipping mode), you must input a `shipping_provider_id` to call this API. Giá trị: SEND_BY_SELLER |

**Mã lỗi:** `36009003` Internal error.; `11001002` invalid param.; `11001031` System process error, please try again later.; `11006013` Failed to ship package.; `11006014` Failed to ship package.; `11006021` Package has been shipped.; `11021009` No available shipping services for this request.; `11021010` The shipment failed due to an invalid postal code.; `11021011` Fail to ship package because the product name is invalid.; `11021012` illegal address; `11021013` The shipment failed because the delivery address is too long.; `11021014` product oversize; `11021015` Fail to ship package because the package is overweight.; `11021016` Fail to ship package because the weight of the SKU exceeds the allowed limit.; `11021017` Fail to ship package because the SKU dimensions exceed the allowed limits.; `11021018` Fail to ship package because the number of items in the package exceeds the allowed limit.; `11021019` Fail to ship package because the package dimensions exceed the allowed limits.; `11021020` Fail to ship package because the package's circumference exceeds the allowed limit.; `11021021` Fail to ship package because the SKU name is invalid.; `11021022` Fail to ship package because the longest dimension of the package exceeds the allowed limit.; `11021023` Fail to ship package because the buyer and/or seller addresses are invalid.; `11021024` Fail to ship package because the buyer recipient address is invalid.; `11021025` Fail to ship package because the seller shipping address is invalid.; `11028006` This tracking number cannot be matched to any carrier, please check the tracking number and upload again.; `11050001` invalid param; `21001001` Invalid parameters.; `21001003` System timeout.; `21001008` not found error; `21001011` System process error, please try again later.; `21001028` The order is being processed.; `21004006` Failed to ship.; `21004017` ON DEMAND RTS NOT IN PICKUP TIME.; `21004019` Need invoice uploaded for all order before rts.; `21004020` Fulfillment Invoice status is not success, can not rts pkg.; `21004024` ship order failed due to platform limitations.; `21008025` Seller cannot operate orders which are fulfilled by platform; `21008044` SellerFulfillUnitInReverseCannotOperate; `21008099` Package has been shipped.; `21011001` Package not found.; `21011003` fulfill_unit action not allowed; `21011007` Package item has after-sale request, please process the after-sale request first.; `21011008` Package is not complete.; `21011020` These orders are already shipped.; `21011022` Invalid tracking number or provider ID.; `21011025` Duplicate tracking number.; `21011026` fulfillment tracking no used times over limit; `21011027` Package has already been delivered.; `21011028` The package does not belong to the current shop.; `21011030` fulfillment not allow update tracking no; `21011040` Package has already been shipped.; `21011041` Cannot complete the operation when order is cancelled.; `21011050` The tracking number can only be updated within a certain number of hours.; `21011051` The tracking number can only be updated once.; `21011053` Fail to ship package because the shipment time is outside of the pick-up time slot.; `21011054` No available shipping services for this request due to missing pick-up time slot.; `21014009` Failed to ship.; `21021010` Failed to ship.; `21022029` System process error, please try again later.; `21022032` match provider failed.; `21034012` tracking no has been used; `21042001` System process error, please try again later.; `21042067` The logistics service provider couldn't process your order.; `21042076` System process error, please try again later.; `21042227` For pop 4pl（cross-border），the return warehouse for the shipping location has not been configured.; `21042251` Abnormal order status, item cannot be shipped.
