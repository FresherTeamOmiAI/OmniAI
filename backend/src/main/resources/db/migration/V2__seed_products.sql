-- Dữ liệu mẫu, thương hiệu HƯ CẤU (shop demo không bán thật). LucVT thay bằng catalog thật ở sprint 0-1.
INSERT INTO product (sku, name, brand, category, skin_types, ingredients_inci, size_ml, pao_months, price_web, price_shopee, price_tiktok, stock, active) VALUES
('LUMI-SUN-01', 'Kem chống nắng kiềm dầu SPF50+ PA++++', 'Lumi Lab', 'sunscreen', 'oily,combination',
 'Aqua, Zinc Oxide, Niacinamide, Silica, Glycerin', 50, 12, 289000, 299000, 279000, 120, TRUE),
('LUMI-BHA-02', 'Dung dịch BHA 2% làm sạch lỗ chân lông', 'Lumi Lab', 'exfoliant', 'oily,combination',
 'Aqua, Salicylic Acid, Butylene Glycol, Allantoin', 100, 12, 345000, 359000, 339000, 80, TRUE),
('SEN-RET-03', 'Serum Retinol 0.3% phục hồi ban đêm', 'Sen Việt', 'serum', 'normal,combination,dry',
 'Aqua, Retinol, Squalane, Ceramide NP, Tocopherol', 30, 6, 420000, 435000, 415000, 45, TRUE),
('SEN-AHA-04', 'Toner AHA 5% làm sáng da', 'Sen Việt', 'toner', 'normal,dry',
 'Aqua, Glycolic Acid, Lactic Acid, Panthenol', 150, 12, 260000, 270000, 255000, 60, TRUE),
('HYD-CER-05', 'Kem dưỡng Ceramide cho da nhạy cảm', 'Hydra Mint', 'moisturizer', 'sensitive,dry,normal',
 'Aqua, Ceramide NP, Ceramide AP, Cholesterol, Panthenol, Centella Asiatica Extract', 50, 12, 310000, 319000, 305000, 90, TRUE),
('HYD-CLN-06', 'Sữa rửa mặt dịu nhẹ pH 5.5', 'Hydra Mint', 'cleanser', 'sensitive,oily,dry,combination,normal',
 'Aqua, Glycerin, Coco-Glucoside, Allantoin', 150, 12, 185000, 195000, 179000, 200, TRUE),
('LUMI-VTC-07', 'Serum Vitamin C 10% sáng da', 'Lumi Lab', 'serum', 'normal,combination,oily',
 'Aqua, Ascorbic Acid, Ferulic Acid, Tocopherol', 30, 6, 390000, 399000, 385000, 0, TRUE),
('SEN-BPO-08', 'Gel chấm mụn Benzoyl Peroxide 2.5%', 'Sen Việt', 'spot-treatment', 'oily',
 'Aqua, Benzoyl Peroxide, Carbomer, Glycerin', 15, 6, 150000, 159000, 149000, 70, TRUE);
