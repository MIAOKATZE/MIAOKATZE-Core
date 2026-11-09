package com.miaokatze.miaokatzecore.api.io;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Stream;

/** Real filesystem publication and move rejection; no game state. */
public final class AtomicFilePublisherContracts {

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("core-publish-contract-");
        Path target = directory.resolve("stamp.json");
        try {
            byte[] original = { 1, 2, 3 };
            AtomicFilePublisher.publish(target, original);
            check(
                AtomicFilePublisher.publishAtomic(target, original) == AtomicFilePublisher.PublishResult.ATOMIC_REPLACE,
                "strict entry completes only by atomic replacement on this filesystem");
            byte[] replacement = new byte[131073];
            Arrays.fill(replacement, (byte) 99);
            AtomicFilePublisher.PublishResult result = AtomicFilePublisher.publish(target, replacement);
            check(Arrays.equals(replacement, Files.readAllBytes(target)), "complete replacement");
            AtomicFilePublisher.publish(target, new byte[0]);
            check(Files.size(target) == 0, "empty publication");
            AtomicFilePublisher.publish(target, original);
            try (FileChannel locked = FileChannel.open(target, StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileLock ignored = locked.lock()) {
                if (System.getProperty("os.name")
                    .startsWith("Windows")) {
                    try {
                        AtomicFilePublisher.publish(target, replacement);
                        throw new AssertionError("locked target move must fail");
                    } catch (IOException expected) {
                        check(Files.size(target) == original.length, "move failure preserves old file");
                    }
                }
            }
            check(Arrays.equals(original, Files.readAllBytes(target)), "move failure preserves old bytes");
            try {
                AtomicFilePublisher.publish(directory, original);
                throw new AssertionError("directory target accepted");
            } catch (IOException expected) {
                check(Files.isDirectory(directory), "directory target untouched");
            }
            try (Stream<Path> entries = Files.list(directory)) {
                check(entries.count() == 1, "failed publication leaves no temporary file");
            }
            System.out.println("AtomicFilePublisherContracts: PASS mode=" + result);
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
