package com.loopers.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findActiveByOrderId(Long orderId);
}
