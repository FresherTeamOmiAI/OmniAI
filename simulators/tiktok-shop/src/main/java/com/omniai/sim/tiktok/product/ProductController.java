package com.omniai.sim.tiktok.product;

import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.ApiResponse;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import com.omniai.sim.tiktok.product.ProductModels.ProductIds;
import com.omniai.sim.tiktok.product.ProductModels.ProductRequest;
import com.omniai.sim.tiktok.product.ProductModels.ProductResponse;
import com.omniai.sim.tiktok.product.ProductModels.SkuUpdate;
import com.omniai.sim.tiktok.product.ProductModels.StoredProduct;
import com.omniai.sim.tiktok.product.ProductModels.UploadedImage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Products (docv2, US-50): path và version đúng theo từng trang. Đi qua GatewayFilter nên có kiểm chữ ký, token,
 * scope (product.write / product.basic) và shop_cipher như các nhóm khác.
 */
@RestController
public class ProductController {

    private final ProductCatalog catalog;

    public ProductController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @PostMapping(value = "/product/202309/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadedImage> uploadImage(@RequestPart(value = "data", required = false) MultipartFile data,
                                                  @RequestParam(value = "use_case", required = false) String useCase)
            throws IOException {
        return ApiResponse.ok(catalog.upload(data == null ? null : data.getBytes(), useCase));
    }

    @PostMapping("/product/202309/products")
    public ApiResponse<ProductResponse> create(@RequestBody(required = false) ProductRequest body) {
        return ApiResponse.ok(catalog.create(body));
    }

    @PutMapping("/product/202509/products/{productId}")
    public ApiResponse<ProductResponse> edit(@PathVariable String productId,
                                             @RequestBody(required = false) ProductRequest body) {
        return ApiResponse.ok(catalog.edit(productId, body));
    }

    @PostMapping("/product/202309/products/{productId}/prices/update")
    public ApiResponse<Map<String, Object>> updatePrice(@PathVariable String productId,
                                                        @RequestBody(required = false) SkuUpdate body) {
        catalog.updatePrice(productId, body == null ? null : body.skus());
        return ApiResponse.okEmpty();
    }

    @PostMapping("/product/202309/products/{productId}/inventory/update")
    public ApiResponse<Map<String, Object>> updateInventory(@PathVariable String productId,
                                                            @RequestBody(required = false) SkuUpdate body) {
        catalog.updateInventory(productId, body == null ? null : body.skus());
        return ApiResponse.okEmpty();
    }

    @PostMapping("/product/202309/products/activate")
    public ApiResponse<Map<String, Object>> activate(@RequestBody(required = false) ProductIds body) {
        catalog.setStatus(body == null ? null : body.productIds(), "ACTIVATE");
        return ApiResponse.okEmpty();
    }

    @PostMapping("/product/202309/products/deactivate")
    public ApiResponse<Map<String, Object>> deactivate(@RequestBody(required = false) ProductIds body) {
        catalog.setStatus(body == null ? null : body.productIds(), "SELLER_DEACTIVATED");
        return ApiResponse.okEmpty();
    }

    /** Get Product (202309), chỉ các trường simulator giữ. */
    @GetMapping("/product/202309/products/{productId}")
    public ApiResponse<StoredProduct> get(@PathVariable String productId) {
        return ApiResponse.ok(catalog.get(productId)
                .orElseThrow(() -> new ApiException(TiktokErrorCode.PRODUCT_ID_NOT_EXIST, "product_id " + productId + ".")));
    }
}
