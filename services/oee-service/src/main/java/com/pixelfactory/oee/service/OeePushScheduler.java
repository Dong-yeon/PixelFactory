package com.pixelfactory.oee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 현재 시프트 OEE 요약을 주기적으로 /topic/oee로 push한다. */
@Component
public class OeePushScheduler {

    private static final Logger log = LoggerFactory.getLogger(OeePushScheduler.class);

    private final OeeService oeeService;
    private final SimpMessagingTemplate messagingTemplate;

    public OeePushScheduler(OeeService oeeService, SimpMessagingTemplate messagingTemplate) {
        this.oeeService = oeeService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${oee.push-interval-ms:5000}")
    public void pushOeeSummary() {
        try {
            messagingTemplate.convertAndSend("/topic/oee", oeeService.getSummary(null, null));
        } catch (Exception e) {
            log.warn("Failed to push OEE summary.", e);
        }
    }
}
