package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public BrandResult.Detail getBrand(Long brandId) {
        return BrandResult.Detail.from(get(brandId));
    }

    @Transactional(readOnly = true)
    public List<BrandResult.Detail> getBrands() {
        return brandRepository.findAll().stream()
                .map(BrandResult.Detail::from)
                .toList();
    }

    private Brand get(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }
}
