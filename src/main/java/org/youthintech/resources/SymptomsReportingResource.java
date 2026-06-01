package org.youthintech.resources;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@Path("symptoms")
public class SymptomsReportingResource {


    @POST
    @Path("/ussd")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<String> handleUssd(
            @FormParam("sessionId") String sessionId,
            @FormParam("phoneNumber") String phone,
            @FormParam("text") String text,
            @FormParam("serviceCode") String code) {

        String response = processSession(sessionId, text);
        return Uni.createFrom().item(response);
    }

    private String processSession(String sessionId, String text) {
        return String.format("sessionId: %s, reported symptoms: %s", sessionId, text);
    }
}
