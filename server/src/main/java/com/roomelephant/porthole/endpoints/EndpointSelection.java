package com.roomelephant.porthole.endpoints;

import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

/** Which candidate opens when the user saved no choice. */
@NullMarked
public final class EndpointSelection {
    private EndpointSelection() {}

    /**
     * Considers routed candidates if any exist, otherwise direct ones, and selects the only one, else the only one at
     * the root path, else none.
     *
     * @return the index of the selected candidate, or -1
     */
    public static int automatic(List<Candidate> candidates) {
        List<Integer> routed = indexesOf(candidates, CandidateKind.ROUTED);
        List<Integer> considered = routed.isEmpty() ? indexesOf(candidates, CandidateKind.DIRECT) : routed;
        if (considered.size() == 1) return considered.getFirst();
        List<Integer> roots = considered.stream()
                .filter(index -> isRoot(candidates.get(index).url()))
                .toList();
        return roots.size() == 1 ? roots.getFirst() : -1;
    }

    /** Whether a browser resolves the URL's path to the root, so dot segments count as it would. */
    static boolean isRoot(String url) {
        String path = URI.create(url).getRawPath();
        if (path.isEmpty()) return true;
        String[] segments = path.substring(1).split("/", -1);
        int depth = 0;
        for (int index = 0; index < segments.length - 1; index++) {
            switch (segments[index]) {
                case "." -> {}
                case ".." -> depth = Math.max(0, depth - 1);
                default -> depth++;
            }
        }
        return switch (segments[segments.length - 1]) {
            case "", "." -> depth == 0;
            case ".." -> depth <= 1;
            default -> false;
        };
    }

    private static List<Integer> indexesOf(List<Candidate> candidates, CandidateKind kind) {
        return IntStream.range(0, candidates.size())
                .filter(index -> candidates.get(index).kind() == kind)
                .boxed()
                .toList();
    }
}
