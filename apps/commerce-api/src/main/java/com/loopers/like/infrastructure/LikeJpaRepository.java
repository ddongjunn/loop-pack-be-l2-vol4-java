package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    List<Like> findAllByUserIdAndDeletedAtIsNull(Long userId);
}
