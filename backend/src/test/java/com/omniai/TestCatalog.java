package com.omniai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc shared/catalog/products.csv (nguồn của migration catalog, T-47.1) để test tính số SKU mong đợi thay vì ghi cứng.
 * Thêm sản phẩm vào catalog thì test không phải sửa. Đường dẫn tính từ thư mục backend/, giống omniai.policy-dir.
 */
public final class TestCatalog {

    public static final Path CSV = Path.of("..", "shared", "catalog", "products.csv");

    /** {@code prices}: mã kênh -> giá, từ 3 cột price_web/price_shopee/price_tiktok của CSV (DB lưu ở product_channel, V13). */
    public record Row(String sku, String brand, String category, int stock, boolean active, String barcode,
                      Map<String, Long> prices) {
    }

    /** Cột giá trong CSV ứng với mã kênh trong sales_channel. */
    public static final Map<String, String> PRICE_COLUMNS = Map.of(
            "WEB", "price_web", "SHOPEE", "price_shopee", "TIKTOK", "price_tiktok");

    private static List<Row> rows;

    private TestCatalog() {
    }

    public static synchronized List<Row> rows() {
        if (rows == null) {
            rows = load();
        }
        return rows;
    }

    public static int size() {
        return rows().size();
    }

    public static long activeCount() {
        return rows().stream().filter(Row::active).count();
    }

    public static List<String> hiddenSkus() {
        return rows().stream().filter(r -> !r.active()).map(Row::sku).toList();
    }

    public static long outOfStockCount() {
        return rows().stream().filter(r -> r.stock() == 0).count();
    }

    public static long brandCount() {
        return rows().stream().map(Row::brand).distinct().count();
    }

    private static List<Row> load() {
        try {
            List<String> lines = Files.readAllLines(CSV, StandardCharsets.UTF_8);
            List<String> header = parse(lines.getFirst());
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                col.put(header.get(i), i);
            }
            List<Row> result = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> f = parse(line);
                result.add(new Row(f.get(col.get("sku")), f.get(col.get("brand")), f.get(col.get("category")),
                        Integer.parseInt(f.get(col.get("stock"))), Boolean.parseBoolean(f.get(col.get("active"))),
                        col.containsKey("barcode") ? f.get(col.get("barcode")) : "", prices(f, col)));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + CSV.toAbsolutePath(), e);
        }
    }

    private static Map<String, Long> prices(List<String> f, Map<String, Integer> col) {
        Map<String, Long> prices = new HashMap<>();
        PRICE_COLUMNS.forEach((channel, column) -> prices.put(channel, Long.parseLong(f.get(col.get(column)))));
        return Map.copyOf(prices);
    }

    /** Một dòng CSV (RFC 4180, không có xuống dòng trong ô): ô có dấu phẩy nằm trong "...", "" là dấu nháy. */
    static List<String> parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
