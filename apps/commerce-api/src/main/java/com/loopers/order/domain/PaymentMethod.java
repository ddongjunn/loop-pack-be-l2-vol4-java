package com.loopers.order.domain;

/**
 * 결제수단. 현재 범위에서는 사용자가 선택한 값만 기록하며, 실제 결제 처리는 결제 도메인이 추가될 때 다룬다.
 */
public enum PaymentMethod {
    CARD,
    CASH
}
