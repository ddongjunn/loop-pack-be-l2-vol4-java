package com.loopers.brand.domain;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    List<Brand> findAll();
    boolean existsById(Long id);
    int softDeleteById(Long id);
}
