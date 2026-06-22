package org.youthintech.services;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.youthintech.models.CommunityReport;
import org.youthintech.repositories.CommunityReportRepository;

@ApplicationScoped
public class UssdService {

    private final CommunityReportRepository communityReportRepository;
    private final ClassificationService classificationService;

    public UssdService(CommunityReportRepository communityReportRepository, ClassificationService classificationService) {
        this.communityReportRepository = communityReportRepository;
        this.classificationService = classificationService;
    }


    public Uni<String> handle(String sessionId, String phoneNumber, String text) {
        String[] steps = text == null || text.isBlank() ? new String[0] : text.split("\\*", -1);

        return switch (steps.length) {
            case 0 -> Uni.createFrom().item(welcomeScreen());
            case 1 -> handleWelcome(steps[0]);
            case 2 -> handleSymptom(steps[0], steps[1]);
            case 3 -> handleDuration(steps[0], steps[1], steps[2]);
            case 4 -> handleSeverity(steps[0], steps[1], steps[2], steps[3]);
            case 5 -> handleAgeGroup(steps[0], steps[1], steps[2], steps[3], steps[4]);
            case 6 -> handleDistrict(steps[0], steps[1], steps[2], steps[3], steps[4], steps[5]);
            case 7 -> handleConfirm(phoneNumber, steps);
            default -> Uni.createFrom().item("END Invalid session. Please dial again.");
        };
    }

    // ── Step 0: Welcome ──────────────────────────────────────

    private String welcomeScreen() {
        return """
                CON Welcome to ZamHealth
                Disease Surveillance

                1. Report symptoms
                2. Health alerts
                3. Nearest clinic
                4. Exit""";
    }

    // ── Step 1: Route from welcome ───────────────────────────

    private Uni<String> handleWelcome(String choice) {
        return Uni.createFrom().item(switch (choice) {
            case "1" -> """
                    CON Report symptoms

                    Select main symptom:
                    1. Fever / chills
                    2. Diarrhoea
                    3. Vomiting
                    4. Cough / breathing
                    5. Rash
                    6. Other""";
            case "2" -> """
                    END Health alerts:

                    ! Cholera - Lusaka Central
                      38 cases active

                    ~ Malaria - Kafue
                      Above seasonal baseline

                    Dial again for updates.""";
            case "3" -> """
                    END Nearest clinics:

                    1. Chilenje Clinic 0.8km
                    2. Matero Ref. Hospital 2.1km
                    3. Lusaka Trust Hospital 3.4km

                    Open now. Stay safe.""";
            case "4" -> "END Thank you for using ZamHealth. Stay safe.";
            default  -> "CON Invalid option.\n\n" + welcomeScreen().substring(4);
        });
    }

    // ── Step 2: Symptom selected ─────────────────────────────

    private Uni<String> handleSymptom(String welcome, String symptom) {
        if (!"1".equals(welcome)) {
            return Uni.createFrom().item("END Session expired. Please dial again.");
        }
        if (!symptom.matches("[1-6]")) {
            return Uni.createFrom().item("""
                    CON Invalid option.

                    Select main symptom:
                    1. Fever / chills
                    2. Diarrhoea
                    3. Vomiting
                    4. Cough / breathing
                    5. Rash
                    6. Other""");
        }
        return Uni.createFrom().item("""
                CON How long have you had
                this symptom?

                1. Less than 1 day
                2. 1 to 3 days
                3. 4 to 7 days
                4. More than 7 days""");
    }

    // ── Step 3: Duration selected ────────────────────────────

    private Uni<String> handleDuration(String w, String symptom, String duration) {
        if (!duration.matches("[1-4]")) {
            return Uni.createFrom().item("""
                    CON Invalid option.

                    How long have you had this?
                    1. Less than 1 day
                    2. 1 to 3 days
                    3. 4 to 7 days
                    4. More than 7 days""");
        }
        return Uni.createFrom().item("""
                CON How severe is it?

                1. Mild - able to do daily tasks
                2. Moderate - limited activity
                3. Severe - cannot get up
                4. Emergency - need help now""");
    }

    // ── Step 4: Severity selected ────────────────────────────

    private Uni<String> handleSeverity(String w, String symptom, String duration, String severity) {
        if (!severity.matches("[1-4]")) {
            return Uni.createFrom().item("""
                    CON Invalid option.

                    How severe is it?
                    1. Mild
                    2. Moderate
                    3. Severe
                    4. Emergency""");
        }
        return Uni.createFrom().item("""
                CON Patient age group:

                1. Under 5
                2. 5 to 17
                3. 18 to 59
                4. 60 and above""");
    }

    // ── Step 5: Age group selected ───────────────────────────

    private Uni<String> handleAgeGroup(String w, String symptom, String duration,
                                       String severity, String age) {
        if (!age.matches("[1-4]")) {
            return Uni.createFrom().item("""
                    CON Invalid option.

                    Patient age group:
                    1. Under 5
                    2. 5 to 17
                    3. 18 to 59
                    4. 60 and above""");
        }
        return Uni.createFrom().item("""
                CON Your district:

                1. Lusaka Central
                2. Kafue
                3. Chongwe
                4. Chilanga
                5. Luangwa
                6. Other""");
    }

    // ── Step 6: District selected ────────────────────────────

    private Uni<String> handleDistrict(String w, String symptom, String duration,
                                       String severity, String age, String district) {
        if (!district.matches("[1-6]")) {
            return Uni.createFrom().item("""
                    CON Invalid option.

                    Your district:
                    1. Lusaka Central
                    2. Kafue
                    3. Chongwe
                    4. Chilanga
                    5. Luangwa
                    6. Other""");
        }

        String symptomLabel  = resolveSymptom(symptom);
        String durationLabel = resolveDuration(duration);
        String severityLabel = resolveSeverity(severity);
        String ageLabel      = resolveAge(age);
        String districtLabel = resolveDistrict(district);

        return Uni.createFrom().item(String.format("""
                CON Confirm your report:

                Symptom:  %s
                Duration: %s
                Severity: %s
                Age:      %s
                District: %s

                1. SUBMIT
                2. CANCEL""",
                symptomLabel, durationLabel, severityLabel, ageLabel, districtLabel));
    }

    // ── Step 7: Confirm ──────────────────────────────────────

    private Uni<String> handleConfirm(String phoneNumber, String[] steps) {

        String confirm = steps[6];

        if ("2".equals(confirm)) {
            return Uni.createFrom().item("END Report cancelled. Stay safe.");
        }
        if (!"1".equals(confirm)) {
            return Uni.createFrom().item("END Invalid option. Please dial again.");
        }

        CommunityReport communityReport = new CommunityReport();

        communityReport.setChannel("ussd");
        communityReport.setPhoneHash(hashPhone(phoneNumber));
        communityReport.setSymptom(resolveSymptom(steps[1]));
        communityReport.setDuration(resolveDuration(steps[2]));
        communityReport.setSeverity(resolveSeverity(steps[3]));
        communityReport.setAgeGroup(resolveAge(steps[4]));
        communityReport.setDistrict(resolveDistrict(steps[5]));

        String classification = classificationService.classify(communityReport.getSymptom(), communityReport.getSeverity(), communityReport.getDuration());
        communityReport.setClassifiedAs(classification);

        return communityReportRepository.persistAndFlush(communityReport)
                .map(savedReport -> {
                    Log.infof("Saved report ref=%s channel=%s district=%s classified_as=%s", savedReport.getId(), communityReport.getChannel(), communityReport.getDistrict(), classification);

                    return "END Report submitted.\n\nReference: ".concat(communityReport.getId().toString())
                            .concat("\n\nThank you. Stay safe.");
                }).onFailure().invoke(e ->
                        Log.errorf("Failed to save report ref=%s: %s", communityReport.getId(), e.getMessage()));
    }

    // ── Label resolvers ──────────────────────────────────────

    private String resolveSymptom(String code) {
        return switch (code) {
            case "1" -> "Fever / chills";
            case "2" -> "Diarrhoea";
            case "3" -> "Vomiting";
            case "4" -> "Cough / breathing";
            case "5" -> "Rash";
            default  -> "Other";
        };
    }

    private String resolveDuration(String code) {
        return switch (code) {
            case "1" -> "< 1 day";
            case "2" -> "1-3 days";
            case "3" -> "4-7 days";
            default  -> "> 7 days";
        };
    }

    private String resolveSeverity(String code) {
        return switch (code) {
            case "1" -> "Mild";
            case "2" -> "Moderate";
            case "3" -> "Severe";
            default  -> "Emergency";
        };
    }

    private String resolveAge(String code) {
        return switch (code) {
            case "1" -> "Under 5";
            case "2" -> "5-17";
            case "3" -> "18-59";
            default  -> "60+";
        };
    }

    private String resolveDistrict(String code) {
        return switch (code) {
            case "1" -> "Lusaka Central";
            case "2" -> "Kafue";
            case "3" -> "Chongwe";
            case "4" -> "Chilanga";
            case "5" -> "Luangwa";
            default  -> "Other";
        };
    }

    // ── SHA-256 phone hash for privacy ───────────────────────
    private String hashPhone(String phone) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
