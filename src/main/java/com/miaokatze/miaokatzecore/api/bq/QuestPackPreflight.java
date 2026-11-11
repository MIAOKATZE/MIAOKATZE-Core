// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.bq;

import static com.miaokatze.miaokatzecore.api.bq.QuestDefinitionPreflight.requireId;
import static com.miaokatze.miaokatzecore.api.bq.QuestDefinitionPreflight.requireLong;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Optional BQ pack preflight shared by injectors. Resource lookup, JSON validation before NBT conversion,
 * business rewrites, ownership, stamps, pruning and all live database changes remain with the caller.
 * The returned map and tags belong to the caller; each resource path is resolved at most once.
 */
public final class QuestPackPreflight {

    private QuestPackPreflight() {}

    /** Preflight custom packs as well as the bundled pack; an explicitly empty entries list is valid. */
    public static Map<String, NBTTagCompound> prepareResources(JsonArray lines,
        Function<String, NBTTagCompound> reader) {
        Map<String, NBTTagCompound> resources = new HashMap<>();
        Set<UUID> lineIds = new HashSet<>();
        Map<UUID, String> questFiles = new HashMap<>();
        for (JsonElement element : lines) {
            JsonObject line = element.getAsJsonObject();
            UUID lineId = new UUID(requireLong(line, "idHigh"), requireLong(line, "idLow"));
            Math.toIntExact(requireLong(line, "orderIndex"));
            if (!lineIds.add(lineId)) throw new IllegalStateException("Duplicate BQ quest line: " + lineId);
            NBTTagCompound lineTag = requireResource(
                resources,
                reader,
                line.get("lineFile")
                    .getAsString());
            if (!lineId.equals(requireId(lineTag, "questLineID"))) {
                throw new IllegalStateException("BQ quest line identity disagrees with manifest: " + lineId);
            }
            Set<UUID> entriesSeen = new HashSet<>();
            JsonArray entries = line.getAsJsonArray("entries");
            if (entries == null) throw new IllegalStateException("Missing BQ entries array: " + lineId);
            for (JsonElement entryElement : entries) {
                JsonObject entry = entryElement.getAsJsonObject();
                String questFile = entry.get("questFile")
                    .getAsString();
                NBTTagCompound quest = requireResource(resources, reader, questFile);
                UUID questId = requireId(quest, "questID");
                String priorFile = questFiles.putIfAbsent(questId, questFile);
                if ((priorFile != null && !priorFile.equals(questFile)) || !entriesSeen.add(questId)) {
                    throw new IllegalStateException("Conflicting BQ quest definition/entry: " + questId);
                }
                NBTTagCompound entryTag = requireResource(
                    resources,
                    reader,
                    entry.get("entryFile")
                        .getAsString());
                if (!questId.equals(requireId(entryTag, "questID"))) {
                    throw new IllegalStateException("BQ quest entry identity disagrees with definition: " + questId);
                }
            }
        }
        // JSON/NBT and UUID validity do not imply that a registered BQ task/reward parser
        // accepts the definition. For example, Standard's completion reward throws on a
        // malformed target UUID, after readFromNBT has already replaced a live quest's
        // properties and tasks. Exercise native deserialization on detached instances for
        // the entire pack before the first live write. This is not plugin-side-effect rollback.
        for (String questFile : new HashSet<>(questFiles.values())) {
            try {
                NBTTagCompound definition = resources.get(questFile);
                QuestDefinitionPreflight.validate(definition);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid BQ quest definition: " + questFile, e);
            }
        }
        return resources;
    }

    private static NBTTagCompound requireResource(Map<String, NBTTagCompound> resources,
        Function<String, NBTTagCompound> reader, String path) {
        NBTTagCompound tag = resources.get(path);
        if (tag == null) {
            tag = reader.apply(path);
            if (tag == null) throw new IllegalStateException("Incomplete BQ quest pack: " + path);
            resources.put(path, tag);
        }
        return tag;
    }

}
