package org.youthintech.ussd;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Uni;

@Path("/api/v1/ussd")
public class UssdResource {

    @Inject
    UssdSessionHandler sessionHandler;

//    @ConfigProperty(name = "zamhealth.ussd.secret")
//    String ussdSecret;

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

        // Validate shared secret from Africa's Talking
//        if (!ussdSecret.equals(secret)) {
//            Log.warnf("Invalid USSD secret from session %s", sessionId);
//            return Uni.createFrom().item("END Unauthorised request.");
//        }

        Log.debugf("USSD session=%s phone=%s text='%s'", sessionId, phoneNumber, text);

        return sessionHandler.handle(sessionId, phoneNumber, text);
    }
}
