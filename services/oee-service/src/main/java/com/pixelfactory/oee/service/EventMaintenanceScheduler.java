package com.pixelfactory.oee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventMaintenanceScheduler.class);

    private final EventMaintenanceService maintenanceService;
    private final boolean enabled;

    public EventMaintenanceScheduler(
            EventMaintenanceService maintenanceService,
            @Value("${oee.maintenance.enabled:true}") boolean enabled
    ) {
        this.maintenanceService = maintenanceService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${oee.maintenance.cron:0 10 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            maintenanceService.runMaintenance();
        } catch (Exception e) {
            log.warn("Event maintenance failed. Will retry on next schedule.", e);
        }
    }
}
