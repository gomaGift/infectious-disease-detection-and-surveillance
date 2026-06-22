package org.youthintech.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "community_report")
@Data
public class CommunityReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "phone_hash", nullable = false)
    private String phoneHash;

    @Column(nullable = false)
    private String symptom;

    @Column(nullable = false)
    private String duration;

    @Column(nullable = false)
    private String severity;

    @Column(name = "age_group")
    private String ageGroup;

    @Column(nullable = false)
    private String district;

    @Column
    private Double lat;

    @Column
    private Double lng;

    @Column(name = "classified_as")
    private String classifiedAs;
}