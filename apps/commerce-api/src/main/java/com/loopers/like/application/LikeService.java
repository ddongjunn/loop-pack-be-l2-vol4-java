package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.application.ProductReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductReader productReader;

    @Transactional
    public void register(Long userId, Long productId) {
        productReader.ensureActiveExists(productId);
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresentOrElse(
                        Like::restore,
                        () -> likeRepository.save(Like.create(userId, productId))
                );
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(Like::delete);
    }
}
