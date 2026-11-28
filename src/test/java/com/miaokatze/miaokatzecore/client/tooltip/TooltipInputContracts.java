// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTableLayout;

/** Contracts for production ownership, deleted coupling and the preserved column allocation policy. */
public final class TooltipInputContracts {
    public static void run() {
        TooltipButtonState buttons = new TooltipButtonState();
        Object first = new Object(), second = new Object();
        buttons.screen(first);
        check(!buttons.release(0), "An ordinary GUI press cannot lose its release to a painted rectangle");
        buttons.claim(0);
        buttons.claim(4);
        buttons.screen(first);
        check(buttons.release(0) && !buttons.release(0), "Claimed release is independent of pointer position and occurs once");
        check(buttons.release(4), "Mouse rebindings above the three primary buttons stay paired");
        buttons.claim(1);
        buttons.screen(second);
        check(!buttons.release(1), "A new GUI cannot inherit old input ownership");
        productionBoundaries();
        columnAllocation();
        System.out.println("TooltipInputContracts: PASS (button ownership, production boundaries, batched column equivalence)");
    }

    private static void productionBoundaries() {
        try {
            ClassNode mouse = read(NativeTooltipMouse.class);
            for (MethodNode method : mouse.methods) {
                check(!method.name.equals("click") && !method.name.equals("consumeRelease"), "Old rectangle ownership was retained");
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    String owner = ((MethodInsnNode) instruction).owner;
                    check(!owner.endsWith("/NativeTooltipPage") && !owner.endsWith("/TooltipLinkBrowser")
                        && !owner.endsWith("/TooltipPinning"), "Geometry executes or decides business behavior");
                }
            }
            for (MethodNode method : read(TooltipController.class).methods)
                check(!method.name.equals("mouseKey") && !method.name.equals("handleRecipe"), "Duplicate routing survived");
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ClassNode read(Class<?> type) throws IOException {
        ClassNode node = new ClassNode();
        try (java.io.InputStream stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static void columnAllocation() {
        Random random = new Random(912);
        for (int sample = 0; sample < 160; sample++) {
            int columns = 1 + random.nextInt(4);
            List<String> headers = new ArrayList<>(), row = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                headers.add(text(1 + random.nextInt(30)));
                row.add(text(1 + random.nextInt(60)));
            }
            TooltipTable table = TooltipTable.of("", headers, Arrays.asList(row));
            TooltipTableLayout original = new TooltipTableLayout(table, String::length);
            int[] expected = new int[columns];
            int total = TooltipTableLayout.GAP * (columns - 1);
            for (int c = 0; c < columns; c++) { expected[c] = original.columnWidth(c); total += expected[c]; }
            int available = 1 + random.nextInt(180);
            // Reference policy frozen from the old implementation, independent of the new water-level algorithm.
            while (total > available) {
                int widest = -1;
                for (int c = 0; c < columns; c++)
                    if (expected[c] > 2 && (widest < 0 || expected[c] > expected[widest])) widest = c;
                if (widest < 0) break;
                expected[widest]--; total--;
            }
            TooltipTableLayout actual = new TooltipTableLayout(table, String::length, available);
            for (int c = 0; c < columns; c++) check(actual.columnWidth(c) == expected[c], "Column allocation changed");
        }
    }

    private static String text(int count) { char[] chars = new char[count]; Arrays.fill(chars, 'x'); return new String(chars); }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
