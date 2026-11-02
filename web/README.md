# web

React + Vite + TypeScript. Khi chạy dev, `/api` và `/ws` được proxy sang backend ở cổng 8080 (`vite.config.ts`).

```bash
npm install
npm run dev      # http://localhost:5173   ·   http://localhost:5173/#admin
npm run build
```

| File | Owner | Nội dung |
|---|---|---|
| `src/components/DemoBanner.tsx` | chung | Banner "cửa hàng thử nghiệm", luôn hiển thị |
| `src/components/ChatWidget.tsx` | TuTM | Chat kênh WEB qua WebSocket |
| `src/pages/StorePage.tsx` | LucVT | Danh sách sản phẩm, lọc theo loại da |
| `src/pages/AdminPage.tsx` | TuTM / HungDQ | "Thử luật kênh"; sau này: inbox, duyệt danh tính, báo cáo bench |
| `src/api.ts` | chung | Kiểu dữ liệu + hàm gọi API, khớp với backend |
