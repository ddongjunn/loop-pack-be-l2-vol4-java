package com.loopers.order.application;

import com.loopers.common.domain.Money;
import com.loopers.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PlaceOrderFacade {

    private final PlaceOrderService placeOrderService;
    private final PaymentService paymentService;
    private final OrderCompensationService orderCompensationService;

    public OrderResult.Detail place(OrderCommand.Create command) {
        OrderResult.Detail order = placeOrderService.createPendingOrder(command);
        try {
            paymentService.pay(order.orderId(), Money.of(order.finalAmount()));
        } catch (RuntimeException e) {
            orderCompensationService.compensate(order.orderId());
            throw e;
        }
        return order;
    }
}
