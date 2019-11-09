package ru.mail.polis.service.seliverstov;

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

    String primaryFor(@NotNull final ByteBuffer key) {
        return nodes.get((key.hashCode() & Integer.MAX_VALUE) % nodes.size());
    }
}
