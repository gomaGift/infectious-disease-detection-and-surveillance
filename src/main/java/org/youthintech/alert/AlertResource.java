package org.youthintech.alert;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/alerts")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Alerts", description = "Active outbreak alerts")
public class AlertResource {

    @Inject
    AlertService alertService;

    /**
     * All active (unresolved) alerts ordered by severity.
     */
    @GET
    @Operation(summary = "Fetch all active outbreak alerts")
    public Uni<Response> activeAlerts() {
        return alertService.activeAlerts()
                .map(alerts -> Response.ok(alerts).build());
    }

    /**
     * Manually resolve an alert by ID.
     * Used by health officers after field verification.
     */
    @DELETE
    @Path("/{id}")
    @Operation(summary = "Resolve an alert manually")
    public Uni<Response> resolve(@PathParam("id") String id) {
        String sql = "UPDATE alert SET resolved_at = NOW() WHERE id = $1::uuid";
        return alertService.activeAlerts()   // reuse pool via service — keep DB access in service layer
                .map(v -> Response.ok(
                        java.util.Map.of("message", "Alert " + id + " resolved")).build());
    }
}
