package org.youthintech.report;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


public class ReportRequest {

    // Set by USSD handler; web form sets "web"
    public String channel = "web";

    // SHA-256 hash of phone number — only set by USSD channel
    public String phoneHash;

    @NotBlank(message = "symptom is required")
    public String symptom;

    @NotBlank(message = "duration is required")
    public String duration;

    @NotBlank(message = "severity is required")
    @Pattern(regexp = "Mild|Moderate|Severe|Emergency",
             message = "severity must be Mild, Moderate, Severe, or Emergency")
    public String severity;

    @NotBlank(message = "ageGroup is required")
    public String ageGroup;

    @NotBlank(message = "district is required")
    public String district;

    // Optional — web channel only
    public Double lat;
    public Double lng;
}
