package com.roomelephant.porthole.endpoints;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public final class PublishedPortEndpointProvider implements EndpointProvider {
    private final PublishedHostResolver hostResolver;

    public PublishedPortEndpointProvider(PublishedHostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    @Override
    public List<Candidate> discover(EndpointTarget target) {
        return target.publishedPorts().stream()
                .sorted()
                .map(port -> new Candidate(
                        "published-port:" + port,
                        "published-port",
                        EndpointUrls.publishedPortUrl(hostResolver.resolve(target.publishedHost()), port),
                        CandidateKind.DIRECT))
                .toList();
    }
}
