// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.testlab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;
import com.miaokatze.miaokatzecore.api.testlab.ArtifactIdentity;
import com.miaokatze.miaokatzecore.api.testlab.LabEvents;
import com.miaokatze.miaokatzecore.api.testlab.LabModule;
import com.miaokatze.miaokatzecore.api.testlab.LabRegistry;

/** OP2 entry. One machine-readable acknowledgement follows each request; accepted never means completed. */
public final class MiaoTestCommand extends CommandBase {

    private static final Gson JSON = new GsonBuilder().serializeNulls()
        .create();
    private final Path receiptDirectory;

    public MiaoTestCommand() {
        this(Paths.get("logs", "miao-testlab"));
    }

    public MiaoTestCommand(Path receiptDirectory) {
        this.receiptDirectory = receiptDirectory;
    }

    public String getCommandName() {
        return "miaotest";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "/miaotest <module> list|status|run|stop|reset|report [args] [--request UUID]";
    }

    public int getRequiredPermissionLevel() {
        return 2;
    }

    public void processCommand(ICommandSender sender, String[] rawArgs) {
        if (!canCommandSenderUseCommand(sender))
            throw new net.minecraft.command.CommandException("commands.generic.permission");
        String requestId = UUID.randomUUID()
            .toString();
        String[] args = rawArgs;
        if (args.length >= 2 && "--request".equals(args[args.length - 2])) {
            try {
                requestId = UUID.fromString(args[args.length - 1])
                    .toString();
            } catch (IllegalArgumentException invalid) {
                throw new net.minecraft.command.CommandException("Invalid request UUID");
            }
            args = Arrays.copyOf(args, args.length - 2);
        }
        String moduleId = args.length == 0 ? "*" : args[0].toLowerCase(Locale.ROOT);
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("schema", 1);
        response.put("requestId", requestId);
        response.put("module", moduleId);
        response.put("action", action);
        response.put("accepted", false);
        response.put("snapshot", null);
        response.put("events", java.util.Collections.emptyList());
        response.put("eventStats", LabEvents.stats(moduleId));
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put(
            "core",
            ArtifactIdentity.capture(LabRegistry.class)
                .asMap());
        response.put("artifact", artifact);
        LabModule module = LabRegistry.find(moduleId);
        try {
            if ("*".equals(moduleId)) {
                List<String> ids = new ArrayList<String>();
                for (LabModule registered : LabRegistry.modules()) if (registered.enabled()) ids.add(registered.id());
                response.put("snapshot", java.util.Collections.singletonMap("modules", ids));
                response.put("accepted", true);
            } else if (module == null || !module.enabled()) {
                response.put("error", "BLOCKED: unknown or disabled module");
            } else {
                artifact.put(
                    "module",
                    ArtifactIdentity.capture(module.getClass())
                        .asMap());
                if ("list".equals(action)) {
                    response.put("cases", module.cases());
                    response.put("accepted", true);
                } else if ("status".equals(action)) response.put("accepted", true);
                else if (Arrays.asList("run", "stop", "reset", "report")
                    .contains(action)) {
                        String[] tail = Arrays.copyOfRange(args, 1, args.length);
                        tail[0] = action;
                        module.execute(sender, tail, requestId);
                        response.put("accepted", true);
                    } else response.put("error", getCommandUsage(sender));
                response.put("snapshot", module.snapshot());
                response.put("events", LabEvents.snapshot(module.id()));
            }
        } catch (RuntimeException failure) {
            response.put("accepted", false);
            response.put(
                "error",
                failure.getClass()
                    .getSimpleName() + ": "
                    + failure.getMessage());
            if (module != null) response.put("events", LabEvents.snapshot(module.id()));
        }
        response.put("eventStats", LabEvents.stats(moduleId));
        // UUID validation keeps every acknowledgement inside this fixed receipt directory.
        try {
            Path directory = receiptDirectory;
            Files.createDirectories(directory);
            Path target = directory.resolve(requestId + ".json");
            response.put("receiptPath", target.toString());
            AtomicFilePublisher.publishAtomic(
                target,
                JSON.toJson(response)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            response.put(
                "receiptError",
                failure.getClass()
                    .getSimpleName() + ": "
                    + failure.getMessage());
        }
        sender.addChatMessage(new ChatComponentText("[MIAO-TESTLAB] " + JSON.toJson(response)));
    }

    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> ids = new ArrayList<String>();
            for (LabModule module : LabRegistry.modules()) if (module.enabled()) ids.add(module.id());
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        if (args.length == 2)
            return getListOfStringsMatchingLastWord(args, "list", "status", "run", "stop", "reset", "report");
        return null;
    }
}
