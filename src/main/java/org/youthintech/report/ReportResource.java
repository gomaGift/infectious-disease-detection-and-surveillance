package org.youthintech.report;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/v1/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Community symptom report endpoints")
public class ReportResource {

    @Inject
    ReportService reportService;

    /**
     * Web form report submission.
     * USSD reports go through UssdResource — this is the web channel only.
     */
    @POST
    @Operation(summary = "Submit a symptom report via web form")
    public Uni<Response> submit(@Valid ReportRequest req) {
        req.channel = "web";
        return reportService.save(req)
                .map(ref -> Response
                        .status(Response.Status.CREATED)
                        .entity(Map.of(
                                "ref",     ref,
                                "message", "Report received. Reference: " + ref))
                        .build());
    }

    /**
     * Latest reports for the live dashboard feed.
     * Default limit 50, max 200.
     */
    @GET
    @Path("/feed")
    @Operation(summary = "Latest community reports for the dashboard feed")
    public Uni<Response> feed(
            @QueryParam("limit") @DefaultValue("50") int limit) {

        int safeLimit = Math.min(limit, 200);
        return reportService.latestReports(safeLimit)
                .map(reports -> Response.ok(reports).build());
    }

    /**
     * Aggregate summary — counts by disease, district, channel.
     * Used by the dashboard overview tab.
     */
    @GET
    @Path("/summary")
    @Operation(summary = "Aggregated report counts for the dashboard")
    public Uni<Response> summary(
            @QueryParam("days") @DefaultValue("7") int days) {

        return reportService.aggregateByDiseaseDistrict(days)
                .map(rows -> {
                    var result = new java.util.ArrayList<Map<String, Object>>();
                    for (var row : rows) {
                        result.add(Map.of(
                                "disease",    row.getString("disease"),
                                "district",   row.getString("district"),
                                "reportDate", row.getLocalDate("report_date").toString(),
                                "caseCount",  row.getLong("case_count")
                        ));
                    }
                    return Response.ok(result).build();
                });
    }
}
