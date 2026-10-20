package com.miaokatze.miaokatzecore.client.camera;

/** Slice publication phase; backend capability is reported separately. */
public enum SliceState {
    DISABLED,
    PREVIEW,
    APPLYING,
    READY,
    RESTORING
}
