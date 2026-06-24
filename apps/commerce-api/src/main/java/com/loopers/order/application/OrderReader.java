package com.loopers.order.application;

import com.loopers.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderReader {

    private final OrderRepository orderRepository;

    public Optional<OrderInfo> findForPayment(Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> new OrderInfo(order.getId(), order.isPayable(), order.getFinalAmount().value()));
    }
}
