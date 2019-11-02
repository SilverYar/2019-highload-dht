package ru.mail.polis.service.yaroslav;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Node {

    private final List<String> nodes;
    private final String id;

    public Node(@NotNull final Set<String> nodes, @NotNull final String id) {
        this.nodes = new ArrayList<>(nodes);
        this.id = id;
    }

    String getId() {
        return this.id;
    }

    Set<String> getNodes() {
        return new HashSet<>(this.nodes);
    }

    /**
     * Get the clusters id.
     */
    String[] replicas(final int count, @NotNull final ByteBuffer key) {
        final String[] res = new String[count];
        int index = (key.hashCode() & Integer.MAX_VALUE) % nodes.size();
        for (int j = 0; j < count; j++) {
            res[j] = nodes.get(index);
            index = (index + 1) % nodes.size();
        }
        return res;
    }
}
