package com.roomelephant.porthole.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.roomelephant.porthole.core.enrichment.endpoint.*;
import com.roomelephant.porthole.core.enrichment.endpointavailability.EndpointAvailabilityFilter;
import com.roomelephant.porthole.core.enrichment.state.ContainerStateFilter;
import com.roomelephant.porthole.core.enrichment.version.VersionEnricher;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PipelineWiringTest {
    @Test
    void productionPipelineContainsAllFourHandlersInDependencyOrder() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(VersionRefreshService.class, () -> mock(VersionRefreshService.class));
            context.registerBean(EndpointDiscovery.class, () -> new EndpointDiscovery(List.of()));
            context.register(
                    ContainerEnrichment.class,
                    ContainerStateFilter.class,
                    UrlCandidateEnricher.class,
                    EndpointAvailabilityFilter.class,
                    VersionEnricher.class);
            context.refresh();
            assertThat(context.getBeanProvider(ContainerEnricher.class)
                            .orderedStream()
                            .map(Object::getClass))
                    .containsExactly(
                            ContainerStateFilter.class,
                            UrlCandidateEnricher.class,
                            EndpointAvailabilityFilter.class,
                            VersionEnricher.class);
        }
    }
}
