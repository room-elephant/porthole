package com.roomelephant.porthole.config;

import com.roomelephant.porthole.core.enrichment.ContainerEnrichment;
import com.roomelephant.porthole.core.enrichment.endpoint.UrlCandidateEnricher;
import com.roomelephant.porthole.core.enrichment.endpointavailability.EndpointAvailabilityFilter;
import com.roomelephant.porthole.core.enrichment.icon.IconEnricher;
import com.roomelephant.porthole.core.enrichment.name.DisplayNameEnricher;
import com.roomelephant.porthole.core.enrichment.state.ContainerStateFilter;
import com.roomelephant.porthole.core.enrichment.version.VersionEnricher;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@NullMarked
public class EnrichmentConfiguration {
    @Bean
    ContainerEnrichment containerEnrichment(
            DisplayNameEnricher names,
            ContainerStateFilter state,
            UrlCandidateEnricher urls,
            EndpointAvailabilityFilter availability,
            IconEnricher icons,
            VersionEnricher versions) {
        return new ContainerEnrichment(List.of(names, state, urls, availability, icons, versions));
    }
}
