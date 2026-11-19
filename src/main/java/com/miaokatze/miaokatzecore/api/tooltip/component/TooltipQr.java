// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.miaokatze.miaokatzecore.internal.qrcode.QrCode;

/** A readable caption and an offline QR of its exact value. No URL is fetched or opened. */
public final class TooltipQr implements TooltipBlock {

    private static final Map<String, QrCode> CODES = new LinkedHashMap<String, QrCode>(16, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, QrCode> eldest) {
            return size() > 16;
        }
    };

    public static final int QUIET = 4, BORDER = 1;
    public final String text;
    public final String value;
    private final QrCode code;

    private TooltipQr(String prefix, String value) {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(value);
        if (value.isEmpty() || value.length() > 512
            || value.getBytes(StandardCharsets.UTF_8).length > 512
            || value.chars()
                .anyMatch(Character::isISOControl)
            || value.indexOf('§') >= 0
            || prefix.indexOf('\n') >= 0
            || prefix.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Single-line QR value required (1–512 UTF-8 bytes)");
        this.value = value;
        this.text = prefix + value;
        synchronized (CODES) {
            this.code = CODES.computeIfAbsent(value, s -> QrCode.encodeText(s, QrCode.Ecc.MEDIUM));
        }
    }

    /** Supply the localized prefix, including its separator, and the plain payload once. */
    public static TooltipQr of(String prefix, String value) {
        return new TooltipQr(prefix, value);
    }

    public int modules() {
        return code.size;
    }

    public boolean dark(int x, int y) {
        return code.getModule(x, y);
    }

    public int scale() {
        return modules() <= 37 ? 2 : 1;
    }

    public int side() {
        return 2 * BORDER + (modules() + 2 * QUIET) * scale();
    }

    @Override
    public List<String> fallbackLines() {
        return Collections.singletonList(text);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TooltipQr)) return false;
        TooltipQr qr = (TooltipQr) other;
        return text.equals(qr.text) && value.equals(qr.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, value);
    }
}
