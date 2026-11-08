package com.roomelephant.porthole.shared;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostsTest {
    @ParameterizedTest
    @ValueSource(strings = {"docker.example.com", "192.168.50.6", "2001:db8::6", "[2001:db8::6]", "localhost"})
    void acceptsBareHostnamesAndAddresses(String host) {
        assertThat(Hosts.isValidHost(host)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " host", "http://host", "host:9753", "host/path", "host?query", "user@host", "${HOST}"})
    void rejectsAnythingThatIsNotABareHost(String host) {
        assertThat(Hosts.isValidHost(host)).isFalse();
    }
}
