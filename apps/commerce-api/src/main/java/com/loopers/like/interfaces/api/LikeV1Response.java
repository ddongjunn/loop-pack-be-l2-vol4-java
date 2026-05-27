package com.loopers.like.interfaces.api;

import com.loopers.like.application.LikeResult;

public class LikeV1Response {

    public record LikedProduct(
        Long productId,
        Long brandId,
        String name,
        String description,
        long price
    ) {
        public static LikedProduct from(LikeResult.LikedProduct result) {
            return new LikedProduct(
                result.productId(),
                result.brandId(),
                result.name(),
                result.description(),
                result.price()
            );
        }
    }
}
