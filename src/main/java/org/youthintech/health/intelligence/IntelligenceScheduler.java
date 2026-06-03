package org.youthintech.health.intelligence;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IntelligenceScheduler {

    private static final Logger LOG = Logger.getLogger(IntelligenceScheduler.class);

    @Inject
    OutbreakDetectionService detectionService;

    /**
     * Run full outbreak detection every hour.
     * Covers all disease+district combinations with enough data.
     */
    @Scheduled(every = "1h", identity = "outbreak-detection")
    void runOutbreakDetection() {
        LOG.info("Scheduled outbreak detection starting...");
//        detectionService.runAnalysis()
//                .subscribe().with(
//                        v    -> LOG.info("Outbreak detection completed"),
//                        err  -> LOG.errorf("Outbreak detection failed: %s", err.getMessage())
//                );
    }

    /**
     * Reload classification rules from DB every 6 hours.
     * Picks up any updates made to the classification_rule table
     * without requiring a restart.
     */
    @Scheduled(every = "6h", identity = "rules-reload")
    void reloadClassificationRules() {
        LOG.info("Reloading classification rules...");
    }
}
