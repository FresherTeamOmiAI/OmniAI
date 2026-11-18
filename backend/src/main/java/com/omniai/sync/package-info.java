/**
 * Đồng bộ dữ liệu hub -> sàn, chiều đi (trụ E, US-49, T-49.1). Owner: TuTM.
 * <p>
 * Admin sửa sản phẩm → {@code catalog.ProductChanged} → {@link com.omniai.sync.ProductSyncService} ghi
 * {@code product_channel_sync} (PENDING) trong cùng transaction → {@link com.omniai.sync.ProductSyncWorker} gửi qua
 * {@link com.omniai.sync.ProductChannelConnector} của từng kênh, lỗi thì thử lại có backoff, hết lượt thì FAILED.
 * Thêm kênh = thêm 1 bean connector, không sửa khung.
 */
package com.omniai.sync;
