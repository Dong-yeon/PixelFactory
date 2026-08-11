package com.pixelfactory.oee.dto;

import java.util.List;

/** 전체 라인 OEE 스냅샷 — 대시보드 초기 로딩과 WebSocket 주기 push에 공용. */
public record OeeSummaryResponse(OeeWindowResponse window, List<LineOeeResponse> lines) {
}
