package com.roomelephant.porthole.entries;

import java.nio.file.Path;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param dir where {@code state.json} and uploaded icons live
 * @param iconSweepInterval how often unreferenced uploaded icons are removed
 * @param unreferencedIconAge how long an unreferenced upload is kept, so a client can save it after uploading
 */
@NullMarked
@ConfigurationProperties("porthole.data")
public record EntriesProperties(Path dir, Duration iconSweepInterval, Duration unreferencedIconAge) {}
