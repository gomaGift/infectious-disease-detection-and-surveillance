package org.youthintech.services;

import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.youthintech.enums.AlertLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AlertService {

    private static final Logger LOG = Logger.getLogger(AlertService.class);

    @Inject PgPool db;

    @ConfigProperty(name = "zamhealth.intelligence.resolution-days", defaultValue = "5")
    int resolutionDays;

    /**
     * Upsert an alert for a disease+district combination.
     * If an active alert already exists, update its level and R-value.
     * If none exists, insert a new one.
     */
    public Uni<Void> raiseOrUpdate(String disease, String district,
                                   AlertLevel level, double rValue, int caseCount) {

        // Check for existing active alert
        String findSql = """
                SELECT id FROM alert
                WHERE  disease = $1
                AND    district = $2
                AND    resolved_at IS NULL
                LIMIT  1
                """;

        return db.preparedQuery(findSql)
                .execute(Tuple.of(disease, district))
                .chain(rows -> {
                    if (rows.size() > 0) {
                        // Update existing
                        String id = rows.iterator().next().getUUID("id").toString();
                        return updateAlert(id, level, rValue, caseCount);
                    } else {
                        // Insert new
                        return insertAlert(disease, district, level, rValue, caseCount);
                    }
                });
    }

    private Uni<Void> insertAlert(String disease, String district,
                                   AlertLevel level, double rValue, int caseCount) {
        String sql = """
                INSERT INTO alert (disease, district, level, r_value, case_count)
                VALUES ($1, $2, $3, $4, $5)
                """;
        return db.preparedQuery(sql)
                .execute(Tuple.of(disease, district, level.toDbValue(), rValue, caseCount))
                .replaceWithVoid()
                .invoke(() -> LOG.infof("Alert raised: %s %s level=%s R=%.2f",
                        disease, district, level, rValue));
    }

    private Uni<Void> updateAlert(String id, AlertLevel level, double rValue, int caseCount) {
        String sql = """
                UPDATE alert
                SET    level = $1, r_value = $2, case_count = $3
                WHERE  id = $4::uuid
                """;
        return db.preparedQuery(sql)
                .execute(Tuple.of(level.toDbValue(), rValue, caseCount, id))
                .replaceWithVoid()
                .invoke(() -> LOG.debugf("Alert updated id=%s level=%s R=%.2f", id, level, rValue));
    }

    /**
     * Auto-resolve alerts where R-value has dropped <= 1.0
     * for the configured number of consecutive days.
     * Called by the scheduler alongside detection.
     */
    public Uni<Void> resolveStaleAlerts() {
        String sql = """
                UPDATE alert
                SET    resolved_at = NOW()
                WHERE  resolved_at IS NULL
                AND    triggered_at <= NOW() - ($1 || ' days')::INTERVAL
                AND    r_value <= 1.0
                """;
        return db.preparedQuery(sql)
                .execute(Tuple.of(String.valueOf(resolutionDays)))
                .replaceWithVoid()
                .invoke(() -> LOG.debug("Stale alert resolution check complete"));
    }

    /**
     * Fetch all active alerts for the dashboard.
     */
    public Uni<List<Map<String, Object>>> activeAlerts() {
        String sql = """
                SELECT id, disease, district, level, r_value,
                       case_count, triggered_at
                FROM   alert
                WHERE  resolved_at IS NULL
                ORDER  BY
                    CASE level WHEN 'critical' THEN 1
                               WHEN 'watch'    THEN 2
                               ELSE                  3 END,
                    triggered_at DESC
                """;
        return db.query(sql).execute()
                .map(rows -> {
                    var list = new ArrayList<Map<String, Object>>();
                    for (var row : rows) {
                        list.add(Map.of(
                                "id",          row.getUUID("id").toString(),
                                "disease",     row.getString("disease"),
                                "district",    row.getString("district"),
                                "level",       row.getString("level"),
                                "rValue",      row.getDouble("r_value"),
                                "caseCount",   row.getInteger("case_count"),
                                "triggeredAt", row.getOffsetDateTime("triggered_at").toString()
                        ));
                    }
                    return list;
                });
    }
}
