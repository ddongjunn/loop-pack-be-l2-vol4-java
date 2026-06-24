package com.loopers.order.application;

public record OrderInfo(Long orderId, boolean payable, long finalAmount) {
}
