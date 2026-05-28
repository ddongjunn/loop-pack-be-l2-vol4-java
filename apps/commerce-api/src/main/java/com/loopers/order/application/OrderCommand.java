package com.loopers.order.application;

import com.loopers.order.domain.PaymentMethod;

import java.util.List;

public class OrderCommand {

    public record Create(
        Long userId,
        List<Line> items,
        String recipientName,
        String recipientPhone,
        String zipcode,
        String address1,
        String address2,
        PaymentMethod paymentMethod
    ) {
    }

    public record Line(
        Long productId,
        int quantity
    ) {
    }
}
