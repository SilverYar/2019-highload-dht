package ru.mail.polis.service.yaroslav;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class AmmoGenerator {
    private static final int VALUE_LENGTH = 256;
    private static final String DILIM = "\r\n";

    private AmmoGenerator() {

    }

    @NotNull
    private static byte[] randVal() {
        final byte[] result = new byte[VALUE_LENGTH];
        ThreadLocalRandom.current().nextBytes(result);
        return result;
    }

    @NotNull
    private static List<String> getUniqkeys(final int amount) {
        final List<String> keys = new ArrayList<>(amount);
        for (long i = 0; i < amount; i++) {
            keys.add(Long.toHexString(i));
        }
        return keys;
    }


    private static int getRandNumbInRange(final int min, final int max) {
        final Random r = new Random();
        return r.ints(min, max).findFirst().getAsInt();
    }

    private static void generateUniqPuts(final int amount) throws IOException {
        final List<String> keys = getUniqkeys(amount);
        for (final String key : keys) {
            final byte[] value = randVal();
            putKeyVal(key, value);
        }
    }

    private static void generate10PercentOverwritePuts(final int amount) throws IOException {
        final List<String> keys = getUniqkeys(amount);
        final Double repeated = keys.size() * 0.1;
        final int repeatedAmount = repeated.intValue();
        for (int i = 0; i < repeatedAmount; i++) {
            keys.set(getRandNumbInRange(0, amount), keys.get(getRandNumbInRange(0, amount)));
        }
        for (final String key : keys) {
            final byte[] value = randVal();
            putKeyVal(key, value);
        }
    }


    private static void generateExistGets(final int amount) throws IOException {
        final List<String> keys = getUniqkeys(amount);
        Collections.shuffle(keys);
        for (final String key : keys) {
            getKey(key);
        }
    }

    private static void generateExistGetsNewestFirst(final int amount) throws IOException {
        final List<String> keys = getUniqkeys(amount);
        final Random r = new Random();
        for (int i = 0; i < amount; i++) {
            final double val = r.nextGaussian() * amount * 0.1 + amount * 0.9;
            int keyInd = (int) Math.round(val);
            if (keyInd >= amount) {
                keyInd = amount - 1;
            } else if (keyInd < 0) {
                keyInd = 0;
            }
            getKey(keys.get(keyInd));
        }
    }

    private static void generateMixedPutsAndGets(final int amount) throws IOException {
        final int halfAmount = amount / 2;
        final List<String> keys = getUniqkeys(halfAmount);
        final List<String> puttedKeys = new ArrayList<>();
        for (int i = 0; i < halfAmount; i++) {
            final int choice = getRandNumbInRange(0, 2);
            if (choice == 0) {
                final byte[] value = randVal();
                putKeyVal(keys.get(0), value);
                puttedKeys.add(keys.get(0));
            } else if (choice == 1 && puttedKeys.size() > 2) {
                final int getIdx = getRandNumbInRange(0, puttedKeys.size());
                getKey(puttedKeys.get(getIdx));
            }
        }
    }

    private static void putKeyVal(
        final String key, 
        final byte[] value) throws IOException {

        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(request, StandardCharsets.US_ASCII)) {
            writer.write("PUT /v0/entity?id=" + key + " HTTP/1.1" + DILIM);
            writer.write("Content-Length: " + value.length + DILIM);
            writer.write(DILIM);
        }
        request.write(value);
        System.out.write(Integer.toString(request.size()).getBytes(StandardCharsets.US_ASCII));
        System.out.write(" put\n".getBytes(StandardCharsets.US_ASCII));
        request.writeTo(System.out);
        System.out.write(DILIM.getBytes(StandardCharsets.US_ASCII));
    }

    private static void getKey(final String key) throws IOException {
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(request, StandardCharsets.US_ASCII)) {
            writer.write("GET /v0/entity?id=" + key + " HTTP/1.1" + DILIM);
            writer.write(DILIM);
        }
        System.out.write(Integer.toString(request.size()).getBytes(StandardCharsets.US_ASCII));
        System.out.write(" get\n".getBytes(StandardCharsets.US_ASCII));
        request.writeTo(System.out);
        System.out.write(DILIM.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     *  Parsing input agruments and create the ammo for tank.
     *
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage:\n\tjava -cp build/classes/java/main ru.mail.polis.service.<login>"
                    + ".AmmoGenerator <put|get> <requests>");
            System.exit(-1);
        }

        final String mode = args[0];
        final int requests = Integer.parseInt(args[1]);

        switch (mode) {
            case "puts_uniq":
                generateUniqPuts(requests);
                break;
            case "puts_overwrite":
                generate10PercentOverwritePuts(requests);
                break;
            case "gets_exist":
                generateExistGets(requests);
                break;
            case "gets_latest":
                generateExistGetsNewestFirst(requests);
                break;
            case "mixed":
                generateMixedPutsAndGets(requests);
                break;
            default:
                throw new UnsupportedOperationException("bad console command, type other! " + mode);
        }
    }
}

