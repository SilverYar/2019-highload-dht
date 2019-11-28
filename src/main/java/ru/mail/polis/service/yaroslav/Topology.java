package ru.mail.polis.service.yaroslav;

import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.ByteBuffer;
import java.util.Set;

@ThreadSafe
public interface Topology<T> {

    @NotNull
    T primaryFor(@NotNull ByteBuffer key);

    boolean isMe(@NotNull T node);

    @NotNull
    String getMe();

    @NotNull
    Set<T> all();

    @NotNull
    String[] replicas(int count, ByteBuffer id);
}
