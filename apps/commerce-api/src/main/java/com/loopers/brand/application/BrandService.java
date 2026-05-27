package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandReader brandReader;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional
    public Brand create(BrandCommand.Create command) {
        Brand brand = Brand.create(command.name(), command.description());
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand update(BrandCommand.Update command) {
        Brand brand = brandReader.get(command.brandId());
        brand.update(command.name(), command.description());
        return brand;
    }

    @Transactional
    public void delete(Long brandId) {
        brandReader.get(brandId);
        productStockRepository.softDeleteByBrandId(brandId);
        productRepository.softDeleteByBrandId(brandId);
        brandRepository.softDeleteById(brandId);
    }

    @Transactional(readOnly = true)
    public Brand get(Long brandId) {
        return brandReader.get(brandId);
    }

    @Transactional(readOnly = true)
    public List<Brand> getAll() {
        return brandRepository.findAll();
    }
}
