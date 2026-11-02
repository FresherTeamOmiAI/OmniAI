# web

React + Vite + TypeScript + React Router, tổ chức **modular theo feature**. Khi chạy dev, `/api` và `/ws` được proxy sang backend ở cổng 8080 (`vite.config.ts`).

```bash
npm install
npm run dev      # http://localhost:5173   ·   http://localhost:5173/admin/policy
npm run build
```

## Cấu trúc

```
src/
├─ main.tsx
├─ app/                     # ghép ứng dụng: router, layout
│  ├─ App.tsx               # khai báo route
│  └─ ShopLayout.tsx        # banner demo + thanh điều hướng + chat widget
├─ shared/                  # dùng chung, KHÔNG chứa nghiệp vụ
│  ├─ lib/http.ts           # getJson, postJson, wsUrl
│  ├─ lib/format.ts         # formatVnd
│  ├─ types.ts              # ChannelType, BotReply, Page<T> (khớp backend)
│  └─ ui/                   # DemoBanner, global.css, component UI chung
└─ features/
   ├─ chat/                 # TuTM   · ChatWidget, useChatSocket
   ├─ store/                # LucVT  · StorePage, api, types
   └─ policy-playground/    # HungDQ · trang "Thử luật kênh"
```

Mỗi feature thường gồm: `XxxPage.tsx` / component, `api.ts` (gọi API), `types.ts`, `useXxx.ts` (hook), `index.ts` (export ra ngoài).

## Quy tắc

1. Bên ngoài chỉ import feature qua `index.ts`, ví dụ `import { StorePage } from '../features/store'`. Không import file bên trong feature khác.
2. Feature không import lẫn nhau. Thứ gì 2 feature cùng cần thì đưa vào `shared/`.
3. `shared/` không import từ `features/` hay `app/`.
4. Gọi API qua `shared/lib/http.ts`, không gọi `fetch` rải rác trong component.
5. Thêm trang mới: tạo `features/<tên>/`, export trong `index.ts`, rồi khai báo route trong `app/App.tsx`.

Feature dự kiến thêm: `identity/` (đăng nhập, liên kết kênh, quyền dữ liệu – LucVT), `admin-inbox/` (TuTM), `catalog-admin/`, `promotion-admin/` (LucVT), `bench-report/` (HungDQ).
