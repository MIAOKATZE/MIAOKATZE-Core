package com.miaokatze.miaokatzecore.api.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Publishes caller-encoded bytes; namespaces, codecs, merging and commit decisions remain with the caller.
 * 
 * @since 0.2.8
 */
public final class AtomicFilePublisher {

    private AtomicFilePublisher() {}

    public enum PublishResult {
        ATOMIC_REPLACE,
        REPLACE_FALLBACK
    }

    /**
     * Writes and forces a temporary file in the existing target directory before replacing the target. Only an
     * unsupported atomic move permits a plain replace fallback; all other failures propagate. A fallback has the
     * provider's ordinary move semantics, without a crash-atomicity guarantee. This does not force directory metadata
     * or certify another application's queued save. The caller must serialize read/merge/publish operations.
     *
     * @return the replacement mode actually used, only after the move completed
     */
    public static PublishResult publish(Path target, byte[] bytes) throws IOException {
        return publish(target, bytes, true);
    }

    /** Same forced single-file publication, but refuses providers without atomic replacement. */
    public static PublishResult publishAtomic(Path target, byte[] bytes) throws IOException {
        return publish(target, bytes, false);
    }

    private static PublishResult publish(Path target, byte[] bytes, boolean allowFallback) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bytes, "bytes");
        Path destination = target.toAbsolutePath()
            .normalize();
        if (destination.getFileName() == null) throw new IOException("File target required: " + destination);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
            && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Regular file target required: " + destination);
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".miaokatze-publish-", ".tmp");
        Throwable failure = null;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return PublishResult.ATOMIC_REPLACE;
            } catch (AtomicMoveNotSupportedException unsupported) {
                if (!allowFallback) throw unsupported;
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                return PublishResult.REPLACE_FALLBACK;
            }
        } catch (IOException | RuntimeException | Error thrown) {
            failure = thrown;
            throw thrown;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | RuntimeException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }
}
