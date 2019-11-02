package ru.mail.polis.dao;

import org.jetbrains.annotations.NotNull;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import ru.mail.polis.Record;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.lang.Byte.MIN_VALUE;
import static org.rocksdb.BuiltinComparator.BYTEWISE_COMPARATOR;

public final class DAOImpl implements DAO {

    private final RocksDB db;

    private DAOImpl(final RocksDB db) {
        this.db = db;
    }

    @NotNull
    @Override
    public Iterator<Record> iterator(@NotNull final ByteBuffer from) {
        final var iterator = db.newIterator();
        iterator.seek(convertSub(from));
        return new MyIterator(iterator);
    }

    /**
     * Get record from Rocks.
     */
    @NotNull
    public ValueTm getRecordWithTimestamp(@NotNull final ByteBuffer keys)
            throws IOException, NoSuchElementException {
        try {
            final byte[] packedKey = convertSub(keys);
            final byte[] valueByteArray = db.get(packedKey);
            return ValueTm.fromBytes(valueByteArray);
        } catch (RocksDBException exception) {
            throw new IOException("Error while get", exception);
        }
    }

    /**
     * Put record in DB.
     */
    public void upsertRecordWithTimestamp(@NotNull final ByteBuffer keys,
                                         @NotNull final ByteBuffer values) throws IOException {
        try {
            final var record = ValueTm.fromValue(values, System.currentTimeMillis());
            final byte[] packedKey = convertSub(keys);
            final byte[] arrayValue = record.toBytes();
            db.put(packedKey, arrayValue);
        } catch (RocksDBException e) {
            throw new IOException("Upsert method exception!", e);
        }
    }

    /**
     * Delete record from DB.
     *
     * @param key to define key
     */
    public void removeRecordWithTimestamp(@NotNull final ByteBuffer key) throws IOException {
        try {
            final byte[] packedKey = convertSub(key);
            final var record = ValueTm.tombstone(System.currentTimeMillis());
            final byte[] arrayValue = record.toBytes();
            db.put(packedKey, arrayValue);
        } catch (RocksDBException e) {
            throw new IOException("Remove method exception!", e);
        }
    }

    @NotNull
    @Override
    public ByteBuffer get(@NotNull final ByteBuffer key) throws IOException {
        try {
            final var result = db.get(convertSub(key));
            if (result == null) {
                throw new NoSuchElementExceptionLite("cant find element " + key.toString());
            }
            return ByteBuffer.wrap(result);
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void upsert(@NotNull final ByteBuffer key, @NotNull final ByteBuffer value) throws IOException {
        try {
            db.put(convertSub(key), array(value));
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void remove(@NotNull final ByteBuffer key) throws IOException {
        try {
            db.delete(convertSub(key));
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void compact() throws IOException {
        try {
            db.compactRange();
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            db.syncWal();
            db.closeE();
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    private static byte[] array(@NotNull final ByteBuffer buffer) {
        final var copy = buffer.duplicate();
        final var arr = new byte[copy.remaining()];
        copy.get(arr);
        return arr;
    }

    private static byte[] convertSub(@NotNull final ByteBuffer byteBuffer) {
        final var arr = array(byteBuffer);
        for (int i = 0; i < arr.length; i++) {
            arr[i] -= MIN_VALUE;
        }
        return arr;
    }

    private static ByteBuffer convertAdd(@NotNull final byte[] array) {
        final var clone = Arrays.copyOf(array, array.length);
        for (int i = 0; i < array.length; i++) {
            clone[i] += MIN_VALUE;
        }
        return ByteBuffer.wrap(clone);
    }

    public static class MyIterator implements Iterator<Record>, AutoCloseable {
        private final RocksIterator iterator;

        MyIterator(final RocksIterator iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.isValid();
        }

        @Override
        public Record next() throws IllegalStateException {
            if (!hasNext()) {
                throw new IllegalStateException("");
            }

            final var key = DAOImpl.convertAdd(iterator.key());
            final var record = Record.of(key, ByteBuffer.wrap(iterator.value()));
            iterator.next();
            return record;
        }

        @Override
        public void close() {
            iterator.close();
        }
    }

    static DAO init(final File data) throws IOException {
        RocksDB.loadLibrary();
        try {
            final var options = new Options()
                    .setCreateIfMissing(true)
                    .setComparator(BYTEWISE_COMPARATOR);
            final var db = RocksDB.open(options, data.getAbsolutePath());
            return new DAOImpl(db);
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }
}
