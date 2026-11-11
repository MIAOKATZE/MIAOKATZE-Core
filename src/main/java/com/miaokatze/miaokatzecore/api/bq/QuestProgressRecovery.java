// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.bq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.storage.DBEntry;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;

/** Optional BQ integration. Load only after the caller has confirmed BetterQuesting is present. */
public final class QuestProgressRecovery {

    private QuestProgressRecovery() {}

    /**
     * Replay only quests that this injection just recreated. Existing quests, including administrator
     * resets and newer claims, must never be passed here. Reads are side effect free on disk; normal
     * BQ saves persist recovered offline users. Backups are deliberately not recovery inputs.
     */
    public static Summary restore(File worldDir, Set<UUID> recreatedTargets) {
        Summary summary = new Summary(recreatedTargets.size());
        if (recreatedTargets.isEmpty()) return summary;
        restoreFile(new File(worldDir, "QuestProgress.json"), recreatedTargets, false, summary);
        File[] files = new File(worldDir, "QuestProgress").listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName()
                    .endsWith(".json")) restoreFile(file, recreatedTargets, true, summary);
            }
        }
        SaveLoadHandler.INSTANCE.addDirtyPlayers(summary.users);
        return summary;
    }

    private static void restoreFile(File file, Set<UUID> targets, boolean merge, Summary summary) {
        if (!file.isFile()) return;
        summary.scannedFiles++;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
            32768)) {
            JsonObject json = new JsonParser().parse(reader)
                .getAsJsonObject();
            validateIds(json);
            NBTTagCompound root = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
            if (!root.hasKey("questProgress", Constants.NBT.TAG_LIST)) {
                throw new IllegalStateException("Missing BQ questProgress list");
            }
            NBTTagList entries = root.getTagList("questProgress", Constants.NBT.TAG_COMPOUND);
            NBTTagList selected = new NBTTagList();
            Set<UUID> selectedIds = new HashSet<>();
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                UUID id;
                if (entry.hasKey("questIDHigh") || entry.hasKey("questIDLow")) {
                    if (!entry.hasKey("questIDHigh", Constants.NBT.TAG_LONG)
                        || !entry.hasKey("questIDLow", Constants.NBT.TAG_LONG)) {
                        throw new IllegalStateException("Invalid BQ progress identity");
                    }
                    id = new UUID(entry.getLong("questIDHigh"), entry.getLong("questIDLow"));
                } else if (entry.hasKey("questID", Constants.NBT.TAG_ANY_NUMERIC)) {
                    id = UuidConverter.convertLegacyId(entry.getInteger("questID"));
                } else {
                    throw new IllegalStateException("Missing BQ progress identity");
                }
                if (targets.contains(id)) {
                    selected.appendTag(entry);
                    selectedIds.add(id);
                }
            }
            QuestDatabase.INSTANCE.readProgressFromNBT(selected, merge);
            summary.matchedRecords += selected.tagCount();
            for (UUID id : selectedIds) {
                IQuest quest = QuestDatabase.INSTANCE.get(id);
                if (quest instanceof QuestInstance) collectUsers((QuestInstance) quest, summary.users);
            }
        } catch (Exception e) {
            // One summary per injection keeps corrupt or absent-world cases from flooding startup logs.
            summary.parseFailures++;
        }
    }

    private static void validateIds(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) validateIds(child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey();
                for (String half : new String[] { "questIDHigh", "questIDLow" }) {
                    if (key.equals(half) || key.startsWith(half + ":")) {
                        if (!key.equals(half + ":4") || !entry.getValue()
                            .isJsonPrimitive()
                            || !entry.getValue()
                                .getAsJsonPrimitive()
                                .isNumber()) {
                            throw new IllegalStateException("Invalid BQ UUID tag: " + key);
                        }
                        new BigDecimal(
                            entry.getValue()
                                .getAsString()).longValueExact();
                    }
                }
                validateIds(entry.getValue());
            }
        }
    }

    private static void collectUsers(QuestInstance quest, Set<UUID> users) {
        quest.getUsersWithCompletionData(users);
        for (DBEntry<ITask> entry : quest.getTasks()
            .getEntries()) {
            ITask task = entry.getValue();
            String type = task.getClass()
                .getName();
            // Only the known native envelopes used by these packs; never inspect custom task payloads.
            if (!type.equals("bq_standard.tasks.TaskCheckbox") && !type.equals("bq_standard.tasks.TaskRetrieval")
                && !type.equals("bq_standard.tasks.TaskCrafting")
                && !type.equals("bq_standard.tasks.TaskLocation")) continue;
            NBTTagCompound progress = task.writeProgressToNBT(new NBTTagCompound(), null);
            NBTTagList completed = progress.getTagList("completeUsers", Constants.NBT.TAG_STRING);
            for (int i = 0; i < completed.tagCount(); i++) users.add(UUID.fromString(completed.getStringTagAt(i)));
            NBTTagList partial = progress.getTagList("userProgress", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < partial.tagCount(); i++) {
                users.add(
                    UUID.fromString(
                        partial.getCompoundTagAt(i)
                            .getString("uuid")));
            }
        }
    }

    public static final class Summary {

        public final int rebuiltQuests;
        private int scannedFiles;
        private int matchedRecords;
        private int parseFailures;
        private final Set<UUID> users = new HashSet<>();

        private Summary(int rebuiltQuests) {
            this.rebuiltQuests = rebuiltQuests;
        }

        public int scannedFiles() {
            return scannedFiles;
        }

        public int matchedRecords() {
            return matchedRecords;
        }

        public int parseFailures() {
            return parseFailures;
        }

        public int restoredUsers() {
            return users.size();
        }

        @Override
        public String toString() {
            return "rebuilt=" + rebuiltQuests
                + ", files="
                + scannedFiles
                + ", records="
                + matchedRecords
                + ", users="
                + restoredUsers()
                + ", failures="
                + parseFailures;
        }
    }
}
