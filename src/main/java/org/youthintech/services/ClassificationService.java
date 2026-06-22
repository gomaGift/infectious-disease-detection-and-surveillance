package org.youthintech.services;

import io.vertx.mutiny.pgclient.PgPool;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads classification rules from the database once at startup
 * into an in-memory map. Eliminates a DB round-trip on every
 * report submission. Refresh by restarting the app or calling reload().
 *
 * Rule priority:
 *   1. Symptom + Severity match (most specific)
 *   2. Symptom only match (general)
 *   3. "Unclassified" fallback
 */
@ApplicationScoped
public class ClassificationService {

    private static final Logger LOG = Logger.getLogger(ClassificationService.class);

    @Inject
    PgPool db;

    // key = symptom (lowercase), value = classification label
    private final Map<String, String> rules = new HashMap<>();

    // Escalation overrides — symptom+severity combos that bump to a more serious class
    private static final Map<String, String> ESCALATION = Map.of(
            "diarrhoea:severe",      "Cholera/AWD",
            "diarrhoea:emergency",   "Cholera/AWD",
            "vomiting:severe",       "Cholera/AWD",
            "vomiting:emergency",    "Cholera/AWD",
            "cough / breathing:emergency", "Respiratory/ARI - Critical",
            "fever / chills:severe", "Malaria/ILI - Severe"
    );

    @PostConstruct
    void loadRules() {
        db.query("SELECT symptom, classification FROM classification_rule")
          .execute()
          .subscribe().with(
                rows -> {
                    for (var row : rows) {
                        rules.put(
                            row.getString("symptom").toLowerCase(),
                            row.getString("classification")
                        );
                    }
                    LOG.infof("Loaded %d classification rules", rules.size());
                },
                err -> LOG.errorf("Failed to load classification rules: %s", err.getMessage())
          );
    }

    /**
     * Classify a report based on symptom, severity, and duration.
     * Returns a disease label string.
     */
    public String classify(String symptom, String severity, String duration) {
        if (symptom == null) return "Unclassified";

        String key = symptom.toLowerCase();

        // Check escalation overrides first
        String escalationKey = key + ":" + (severity != null ? severity.toLowerCase() : "");
        if (ESCALATION.containsKey(escalationKey)) {
            return ESCALATION.get(escalationKey);
        }

        // Duration-based escalation for fever
        if (key.contains("fever") && duration != null &&
                (duration.contains("4-7") || duration.contains("> 7"))) {
            return "Malaria/ILI - Prolonged";
        }

        // General rule lookup
        return rules.getOrDefault(key, "Unclassified");
    }

    /**
     * Force reload of rules — useful after updating classification_rule table.
     */
    public void reload() {
        rules.clear();
        loadRules();
        LOG.info("Classification rules reloaded");
    }
}
