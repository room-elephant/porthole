package com.roomelephant.porthole.outbound.registry;

import com.roomelephant.porthole.core.model.RegistryLookup;
import com.roomelephant.porthole.outbound.registry.distribution.RegistryProtocolException;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.client.*;

/** Translate expected remote failures at the requested resource boundary. */
@NullMarked
public final class RegistryRequests {
    private RegistryRequests() {}

    public static <T> RegistryLookup<T> read(Supplier<T> request) {
        try {
            return new RegistryLookup.Available<>(request.get());
        } catch (HttpClientErrorException.NotFound _) {
            return new RegistryLookup.Missing<>();
        } catch (RegistryProtocolException e) {
            return new RegistryLookup.Unavailable<>(e.getMessage());
        } catch (InvalidMediaTypeException _) {
            return new RegistryLookup.Unavailable<>("Invalid registry content type");
        } catch (RestClientException _) {
            return new RegistryLookup.Unavailable<>("Registry request unavailable");
        }
    }
}
