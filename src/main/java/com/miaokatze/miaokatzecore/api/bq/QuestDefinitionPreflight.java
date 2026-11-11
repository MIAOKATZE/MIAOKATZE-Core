// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.bq;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import betterquesting.questing.QuestInstance;

/** Optional BQ validation. Call only after confirming BetterQuesting is present. */
public final class QuestDefinitionPreflight {

    private static final String[] UUID_HALVES = { "questIDHigh", "questIDLow", "questLineIDHigh", "questLineIDLow" };

    private QuestDefinitionPreflight() {}

    /**
     * Checks prerequisite structure and exercises native task/reward parsing on a detached copy.
     * Does not update live quest databases or roll back side effects inside third-party parsers.
     */
    public static void validate(NBTTagCompound definition) {
        Objects.requireNonNull(definition, "definition");
        requireId(definition, "questID");
        validatePrerequisites(definition);
        new QuestInstance().readFromNBT((NBTTagCompound) definition.copy());
    }

    /** Validate formatted JSON before NBT conversion can truncate or overwrite UUID halves. */
    public static void validateJsonIds(JsonElement element) {
        Objects.requireNonNull(element, "element");
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) validateJsonIds(child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey();
                for (String id : UUID_HALVES) {
                    if (key.equals(id) || key.startsWith(id + ":")) {
                        // Reject alternate type tags too: JSONtoNBT strips suffixes and could
                        // otherwise let a second same-name tag overwrite the checked UUID half.
                        if (!key.equals(id + ":4")) throw new IllegalStateException("Invalid BQ UUID tag: " + key);
                        requireLong(object, key);
                    }
                }
                validateJsonIds(entry.getValue());
            }
        }
    }

    private static void validatePrerequisites(NBTTagCompound quest) {
        if (!quest.hasKey("preRequisites")) return;
        // Native BQ supports legacy integer arrays, but silently drops malformed UUID-list
        // entries. Losing a prerequisite is not a safe interpretation of a damaged quest pack.
        if (quest.hasKey("preRequisites", Constants.NBT.TAG_INT_ARRAY)) return;
        if (!quest.hasKey("preRequisites", Constants.NBT.TAG_LIST)) {
            throw new IllegalStateException("Invalid BQ prerequisite list");
        }
        NBTTagList prerequisites = (NBTTagList) quest.getTag("preRequisites");
        if (prerequisites.tagCount() > 0 && prerequisites.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalStateException("Invalid BQ prerequisite entry type");
        }
        for (int i = 0; i < prerequisites.tagCount(); i++) {
            requireId(prerequisites.getCompoundTagAt(i), "questID");
        }
    }

    static long requireLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive()
                .isNumber()) {
            throw new IllegalStateException("Missing/non-numeric BQ identity or order: " + key);
        }
        // Gson getAsLong silently truncates fractional and overflowing values.
        return new BigDecimal(value.getAsString()).longValueExact();
    }

    static UUID requireId(NBTTagCompound tag, String prefix) {
        // Native BQ readId defaults absent or wrongly typed halves to zero.
        if (!tag.hasKey(prefix + "High", Constants.NBT.TAG_LONG)
            || !tag.hasKey(prefix + "Low", Constants.NBT.TAG_LONG)) {
            throw new IllegalStateException("Missing/invalid BQ identity: " + prefix);
        }
        return new UUID(tag.getLong(prefix + "High"), tag.getLong(prefix + "Low"));
    }

}
