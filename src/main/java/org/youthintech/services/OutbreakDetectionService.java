package org.youthintech.services;

import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.youthintech.enums.AlertLevel;


import java.util.*;

@ApplicationScoped
public class OutbreakDetectionService {

    private static final Logger LOG = Logger.getLogger(OutbreakDetectionService.class);

    @Inject PgPool db;
    @Inject AlertService alertService;

    @ConfigProperty(name = "zamhealth.intelligence.baseline-multiplier",    defaultValue = "2.0")
    double baselineMultiplier;

    @ConfigProperty(name = "zamhealth.intelligence.r-value-threshold",      defaultValue = "1.2")
    double rValueThreshold;

    @ConfigProperty(name = "zamhealth.intelligence.r-value-consecutive-days", defaultValue = "3")
    int rValueConsecutiveDays;

    /**
     * Main analysis pipeline.
     * Called by the scheduler every hour and after each batch of reports.
     *
     * Steps:
     *   1. Pull 35 days of daily case counts per disease+district
     *   2. Compute 4-week baseline (days 8–35)
     *   3. Compute 7-day sum (days 1–7)
     *   4. Flag if 7-day sum >= 2x baseline → Watch
     *   5. Compute R-value from last 8 days
     *   6. Flag if R > 1.2 for 3+ consecutive days → Critical
     */
    public Uni<Void> runAnalysis() {
        LOG.info("Running outbreak detection analysis...");

        String sql = """
                SELECT   classified_as               AS disease,
                         district,
                         DATE(created_at)            AS report_date,
                         COUNT(*)                    AS case_count
                FROM     community_report
                WHERE    created_at >= NOW() - INTERVAL '35 days'
                AND      classified_as IS NOT NULL
                AND      classified_as != 'Unclassified'
                GROUP BY classified_as, district, DATE(created_at)
                ORDER BY classified_as, district, report_date
                """;

        return db.query(sql).execute()
                .map(rows -> {
                    // Group rows by disease+district
                    Map<String, List<long[]>> series = new LinkedHashMap<>();
                    for (var row : rows) {
                        String key = row.getString("disease") + "|" + row.getString("district");
                        long   count = row.getLong("case_count");
                        series.computeIfAbsent(key, k -> new ArrayList<>())
                              .add(new long[]{ count });
                    }
                    return series;
                })
                .chain(series -> {
                    List<Uni<Void>> checks = new ArrayList<>();
                    for (var entry : series.entrySet()) {
                        String[] parts   = entry.getKey().split("\\|");
                        String disease   = parts[0];
                        String district  = parts[1];
                        List<long[]> data = entry.getValue();

                        checks.add(evaluate(disease, district, data));
                    }
                    return checks.isEmpty()
                            ? Uni.createFrom().voidItem()
                            : Uni.join().all(checks).andFailFast().replaceWithVoid();
                });
    }

    private Uni<Void> evaluate(String disease, String district, List<long[]> dailyCounts) {
        long[] counts = dailyCounts.stream()
                .mapToLong(a -> a[0])
                .toArray();

        int n = counts.length;
        if (n < 8) return Uni.createFrom().voidItem(); // not enough data

        // 7-day sum (most recent 7 days)
        long sevenDaySum = 0;
        for (int i = Math.max(0, n - 7); i < n; i++) sevenDaySum += counts[i];

        // 4-week baseline (days 8–35, i.e. everything except last 7)
        long baselineSum = 0;
        int  baselineDays = Math.max(1, n - 7);
        for (int i = 0; i < n - 7; i++) baselineSum += counts[i];
        double baseline = (double) baselineSum / baselineDays * 7; // normalise to 7-day window

        // R-value: ratio of last 4 days to prior 4 days
        long recent = 0, prior = 0;
        for (int i = Math.max(0, n - 4); i < n; i++) recent += counts[i];
        for (int i = Math.max(0, n - 8); i < n - 4; i++) prior += counts[i];
        double rValue = prior == 0 ? (recent > 0 ? 2.0 : 1.0) : (double) recent / prior;

        LOG.debugf("disease=%s district=%s 7dSum=%d baseline=%.1f R=%.2f",
                disease, district, sevenDaySum, baseline, rValue);

        // Determine alert level
        AlertLevel level = null;
        if (rValue >= rValueThreshold) {
            level = AlertLevel.CRITICAL;
        } else if (baseline > 0 && sevenDaySum >= baselineMultiplier * baseline) {
            level = AlertLevel.WATCH;
        } else if (sevenDaySum > 0) {
            level = AlertLevel.MONITOR;
        }

        if (level == null) return Uni.createFrom().voidItem();

        final AlertLevel finalLevel = level;
        final double finalR = rValue;
        final long finalCount = sevenDaySum;

        return alertService.raiseOrUpdate(disease, district, finalLevel, finalR, (int) finalCount);
    }

    /**
     * Compute R-value time series for a specific disease+district.
     * Returns a list of {date, rValue} objects for charting.
     */
    public Uni<List<Map<String, Object>>> rValueTimeSeries(String disease, String district) {
        String sql = """
                SELECT   DATE(created_at) AS report_date,
                         COUNT(*)         AS case_count
                FROM     community_report
                WHERE    classified_as = $1
                AND      district      = $2
                AND      created_at   >= NOW() - INTERVAL '30 days'
                GROUP BY DATE(created_at)
                ORDER BY report_date
                """;

        return db.preparedQuery(sql)
                .execute(Tuple.of(disease, district))
                .map(rows -> {
                    List<Long> counts = new ArrayList<>();
                    List<String> dates = new ArrayList<>();
                    for (var row : rows) {
                        counts.add(row.getLong("case_count"));
                        dates.add(row.getLocalDate("report_date").toString());
                    }

                    List<Map<String, Object>> result = new ArrayList<>();
                    for (int i = 4; i < counts.size(); i++) {
                        long recent = counts.get(i) + counts.get(i-1);
                        long prior  = counts.get(i-2) + counts.get(i-3);
                        double r = prior == 0 ? 1.0 : (double) recent / prior;
                        result.add(Map.of("date", dates.get(i), "rValue", Math.round(r * 100.0) / 100.0));
                    }
                    return result;
                });
    }
}
