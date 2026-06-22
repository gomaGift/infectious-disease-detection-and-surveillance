package org.youthintech.resources;

import io.quarkus.logging.Log;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Uni;
import org.youthintech.services.UssdService;

@Path("/api/v1/ussd")
public class UssdResource {

     private  final UssdService ussdService;

    public UssdResource(UssdService ussdService) {
        this.ussdService = ussdService;
    }

    /**
     * Africa's Talking posts to this endpoint on every keypress.
     * Body params: sessionId, phoneNumber, text (accumulated input), serviceCode
     * Response must be plain text starting with CON (continue) or END (close session).
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> handleUssd(
            @FormParam("sessionId")   String sessionId,
            @FormParam("phoneNumber") String phoneNumber,
            @FormParam("text")        String text,
            @FormParam("serviceCode") String serviceCode) {


        Log.debugf("USSD session=%s phone=%s text='%s' serviceCode=%s", sessionId, phoneNumber, text, serviceCode);

        return ussdService.handle(sessionId, phoneNumber, text);
    }
}
