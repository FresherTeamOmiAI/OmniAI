package com.omniai.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductSkuLookupTest {

    @Test
    void activeSkuReturnsChannelPriceAndStock() throws Exception {
        Product product = mock(Product.class);
        when(product.getSku()).thenReturn("CERA-CLE-01");
        when(product.getBrand()).thenReturn("CeraVe");
        when(product.getName()).thenReturn("Sữa rửa mặt");
        when(product.getCategory()).thenReturn("cleanser");
        when(product.getPrices()).thenReturn(Map.of("WEB", 186_000L, "SHOPEE", 190_000L));
        when(product.getStock()).thenReturn(0);
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findBySkuAndActiveTrue("CERA-CLE-01")).thenReturn(Optional.of(product));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(repository, mock(ProductService.class))).build();

        mvc.perform(get("/api/products/sku/CERA-CLE-01").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("CERA-CLE-01"))
                .andExpect(jsonPath("$.prices.WEB").value(186000))
                .andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    void hiddenSkuIsNotFound() throws Exception {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findBySkuAndActiveTrue("GONE-SUN-01")).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(repository, mock(ProductService.class))).build();

        mvc.perform(get("/api/products/sku/GONE-SUN-01"))
                .andExpect(status().isNotFound());
    }
}
