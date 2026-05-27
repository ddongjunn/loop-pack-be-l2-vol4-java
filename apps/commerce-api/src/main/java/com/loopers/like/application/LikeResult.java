package com.loopers.like.application;

import com.loopers.product.domain.Product;

public class LikeResult {

    public record LikedProduct(
        Long productId,
        Long brandId,
        String name,
        String description,
        long price
    ) {
        public static LikedProduct from(Product product) {
            return new LikedProduct(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
            );
        }
    }
}
