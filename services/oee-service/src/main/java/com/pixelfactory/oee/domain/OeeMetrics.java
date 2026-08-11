package com.pixelfactory.oee.domain;

/** OEE 결과 지표. 모두 0.0 ~ 1.0 비율. */
public record OeeMetrics(double availability, double performance, double quality, double oee) {
}
