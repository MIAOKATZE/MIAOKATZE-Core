package com.miaokatze.miaokatzecore.api.animation;

/**
 * Distinct network connection, world and playback-session identities. Not a
 * loop counter.
 */
public final class EpochKey {

    public final long connectionEpoch, worldEpoch, sessionEpoch;

    public EpochKey(long connectionEpoch, long worldEpoch, long sessionEpoch) {
        AnimationV1.require(connectionEpoch >= 0 && worldEpoch >= 0 && sessionEpoch >= 0, "negative epoch");
        this.connectionEpoch = connectionEpoch;
        this.worldEpoch = worldEpoch;
        this.sessionEpoch = sessionEpoch;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EpochKey)) return false;
        EpochKey k = (EpochKey) o;
        return connectionEpoch == k.connectionEpoch && worldEpoch == k.worldEpoch && sessionEpoch == k.sessionEpoch;
    }

    @Override
    public int hashCode() {
        long n = connectionEpoch * 31 + worldEpoch;
        n = n * 31 + sessionEpoch;
        return (int) (n ^ (n >>> 32));
    }
}
