package org.youthintech.health.report;


import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.youthintech.health.intelligence.ClassificationService;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class ReportService {

    private static final Logger LOG = Logger.getLogger(ReportService.class);

    @Inject
    PgPool db;

    @Inject
    ClassificationService classifier;

    /**
     * Save a community report and return the reference number.
     * Classification runs inline before insert so the record is
     * stored with its disease label from the start.
     */
    public Uni<String> save(ReportRequest req) {
        String ref           = generateRef();
        String classification = classifier.classify(req.symptom, req.severity, req.duration);

        String sql = """
                INSERT INTO community_report
                    (ref, channel, phone_hash, symptom, duration, severity,
                     age_group, district, lat, lng, classified_as)
                VALUES
                    ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
                """;


        var params = Tuple.from(List.of(ref, req.channel, req.phoneHash, req.symptom,  req.duration, req.severity,  req.ageGroup, req.district, req.lat, req.lng, classification));

        return db.preparedQuery(sql)
                .execute((io.vertx.mutiny.sqlclient.Tuple) params)
                .map(rows -> {
                    LOG.infof("Saved report ref=%s channel=%s district=%s classified_as=%s",
                            ref, req.channel, req.district, classification);
                    return ref;
                })
                .onFailure().invoke(e ->
                        LOG.errorf("Failed to save report ref=%s: %s", ref, e.getMessage()));
    }

    /**
     * Fetch latest reports for the live dashboard feed.
     */
    public Uni<java.util.List<ReportSummary>> latestReports(int limit) {
        String sql = """
                SELECT ref, channel, symptom, severity, district,
                       classified_as, created_at
                FROM   community_report
                ORDER  BY created_at DESC
                LIMIT  $1
                """;

        return db.preparedQuery(sql)
                .execute(Tuple.of(limit))
                .map(rows -> {
                    var list = new java.util.ArrayList<ReportSummary>();
                    for (var row : rows) {
                        var r = new ReportSummary();
                        r.ref           = row.getString("ref");
                        r.channel       = row.getString("channel");
                        r.symptom       = row.getString("symptom");
                        r.severity      = row.getString("severity");
                        r.district      = row.getString("district");
                        r.classifiedAs  = row.getString("classified_as");
                        r.createdAt     = row.getOffsetDateTime("created_at").toString();
                        list.add(r);
                    }
                    return list;
                });
    }

    /**
     * Aggregate counts by disease and district for the intelligence engine.
     * Returns rows for the past N days.
     */
    public Uni<java.util.List<io.vertx.mutiny.sqlclient.Row>> aggregateByDiseaseDistrict(int days) {
        String sql = """
                SELECT   classified_as AS disease,
                         district,
                         DATE(created_at) AS report_date,
                         COUNT(*)         AS case_count
                FROM     community_report
                WHERE    created_at >= NOW() - ($1 || ' days')::INTERVAL
                GROUP BY classified_as, district, DATE(created_at)
                ORDER BY report_date DESC
                """;

        return db.preparedQuery(sql)
                .execute(Tuple.of(String.valueOf(days)))
                .map(rows -> {
                    var list = new java.util.ArrayList<io.vertx.mutiny.sqlclient.Row>();
                    for (var row : rows) list.add(row);
                    return list;
                });
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Generate a human-readable reference like ZH-48291.
     * Collision probability is negligible at competition scale.
     */
    private String generateRef() {
        int number = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "ZH-" + number;
    }
}
