package com.roomelephant.porthole.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EndpointSelectionTest {
    private static Candidate routed(String url) {
        return new Candidate("traefik:" + url, "traefik", url, CandidateKind.ROUTED);
    }

    private static Candidate direct(int port) {
        return new Candidate(
                "published-port:" + port, "published-port", "http://docker:" + port + "/", CandidateKind.DIRECT);
    }

    @Test
    void noCandidatesSelectNone() {
        assertThat(EndpointSelection.automatic(List.of())).isEqualTo(-1);
    }

    @Test
    void onlyDirectIsSelected() {
        assertThat(EndpointSelection.automatic(List.of(direct(8080)))).isZero();
    }

    @Test
    void severalDirectSelectNone() {
        assertThat(EndpointSelection.automatic(List.of(direct(8080), direct(9000))))
                .isEqualTo(-1);
    }

    @Test
    void routedPreferredOverDirect() {
        assertThat(EndpointSelection.automatic(List.of(direct(8080), routed("https://app.example.com/download/"))))
                .isEqualTo(1);
    }

    @Test
    void uniqueRootAmongRoutedSelected() {
        assertThat(EndpointSelection.automatic(List.of(
                        routed("https://app.example.com/admin/"), routed("https://app.example.com/"), direct(8080))))
                .isEqualTo(1);
    }

    @Test
    void severalRootsSelectNone() {
        assertThat(EndpointSelection.automatic(
                        List.of(routed("https://a.example.com/"), routed("https://b.example.com/"))))
                .isEqualTo(-1);
    }

    @Test
    void noRootAmongSeveralRoutedSelectsNone() {
        assertThat(EndpointSelection.automatic(
                        List.of(routed("https://a.example.com/x/"), routed("https://a.example.com/y/"))))
                .isEqualTo(-1);
    }

    @ParameterizedTest
    @CsvSource({
        "https://app.example.com/, true",
        "https://app.example.com, true",
        "https://app.example.com?view=main#top, true",
        "https://app.example.com/./, true",
        "https://app.example.com/a/../, true",
        "https://app.example.com/a/.., true",
        "https://app.example.com/a/b/.., false",
        "https://app.example.com/download/, false",
        "https://app.example.com/download, false",
        "https://app.example.com/., true",
        "https://app.example.com/.., true",
        "https://app.example.com/../.., true",
        "https://app.example.com/app/.././, true",
        "https://app.example.com/app/../.., true",
        "https://app.example.com//., false",
        "https://app.example.com/.//, false",
        "https://app.example.com//.., true"
    })
    void classifiesRootPathsLikeTheBrowser(String url, boolean root) {
        assertThat(EndpointSelection.isRoot(url)).isEqualTo(root);
    }
}
