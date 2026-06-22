package org.youthintech.repositories;


import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.youthintech.models.CommunityReport;

@ApplicationScoped
public class CommunityReportRepository implements PanacheRepository<CommunityReport> {
}
