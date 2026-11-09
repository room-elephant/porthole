package com.roomelephant.porthole.versions.registry;

import com.roomelephant.porthole.versions.RegistryLookup;
import com.roomelephant.porthole.versions.registry.distribution.RegistryProtocolException;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.client.*;

/** Translate expected remote failures at the requested resource boundary. */
@NullMarked
public final class RegistryRequests {
    private static final Logger log = LoggerFactory.getLogger(RegistryRequests.class);

    private RegistryRequests() {}

    /**
     * Runs one lookup. A failure other than not found is logged with {@code resource}, such as "tags of
     * docker.io/library/nginx", since nothing else says why a version is missing.
     */
    public static <T> RegistryLookup<T> read(String resource, Supplier<T> request) {
        try {
            return new RegistryLookup.Available<>(request.get());
        } catch (HttpClientErrorException.NotFound _) {
            return new RegistryLookup.Missing<>();
        } catch (RegistryProtocolException e) {
            return unavailable(resource, e.getMessage(), e);
        } catch (InvalidMediaTypeException e) {
            return unavailable(resource, "Invalid registry content type", e);
        } catch (HttpStatusCodeException e) {
            return unavailable(
                    resource, "Registry responded " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            return unavailable(resource, "Registry request unavailable", e);
        }
    }

    private static <T> RegistryLookup<T> unavailable(String resource, String reason, Exception cause) {
        log.warn("Could not read {}: {}", resource, reason);
        log.debug("Registry failure reading {}", resource, cause);
        return new RegistryLookup.Unavailable<>(reason);
    }
}
