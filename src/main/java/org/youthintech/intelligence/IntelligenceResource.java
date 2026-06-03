package org.youthintech.intelligence;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/intelligence")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Intelligence", description = "Trend analysis and outbreak detection")
public class IntelligenceResource {

    @Inject OutbreakDetectionService detectionService;

    /**
     * Manually trigger a full analysis run.
     * Useful for demos — no need to wait for the hourly schedule.
     */
    @POST
    @Path("/run")
    @Operation(summary = "Manually trigger outbreak detection analysis")
    public Uni<Response> runNow() {
        return detectionService.runAnalysis()
                .map(v -> Response.ok(
                        java.util.Map.of("message", "Analysis complete")).build());
    }

    /**
     * R-value time series for a specific disease and district.
     * Used by the intelligence tab trend chart.
     */
    @GET
    @Path("/rvalue/{disease}/{district}")
    @Operation(summary = "R-value time series for a disease+district combination")
    public Uni<Response> rValueSeries(
            @PathParam("disease")  String disease,
            @PathParam("district") String district) {

        return detectionService.rValueTimeSeries(disease, district)
                .map(series -> Response.ok(series).build());
    }
}
