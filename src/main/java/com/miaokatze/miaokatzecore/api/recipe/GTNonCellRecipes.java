// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.GTMod;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipe.GTRecipe_WithAlt;
import gregtech.api.util.GTRecipeBuilder;
import gtPlusPlus.core.util.recipe.RecipeHashStrat;
import gtPlusPlus.xmod.gregtech.loaders.RecipeGenMultisUsingFluidInsteadOfCells;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

/** Retains only recipes added by Core consumers after GT's one-shot NonCell generation has already run. */
final class GTNonCellRecipes {

    private static final String INPUT_MAP = "miaokatzecore.recipe.noncell_input";
    private static final String OUTPUT_MAP = "miaokatzecore.recipe.noncell_output";
    // Native equality is only a coarse key: it omits metadata, NBT, chances, categories and flags.
    private static final Hash.Strategy<GTRecipe> EQUIVALENCE = new Hash.Strategy<GTRecipe>() {

        @Override
        public int hashCode(GTRecipe recipe) {
            return RecipeHashStrat.RecipeHashingStrategy.hashCode(recipe);
        }

        @Override
        public boolean equals(GTRecipe left, GTRecipe right) {
            if (left == right) return true;
            if (left == null || right == null || left.getClass() != right.getClass()) return false;
            if (!RecipeHashStrat.areRecipesEqual(left, right) || left.getRecipeCategory() != right.getRecipeCategory()
                || !left.getMetadataStorage()
                    .getEntries()
                    .equals(
                        right.getMetadataStorage()
                            .getEntries())
                || left.mSpecialItems != right.mSpecialItems
                || left.mSpecialValue != right.mSpecialValue
                || left.mEnabled != right.mEnabled
                || left.mHidden != right.mHidden
                || left.mFakeRecipe != right.mFakeRecipe
                || left.mCanBeBuffered != right.mCanBeBuffered
                || left.mNeedsEmptyOutput != right.mNeedsEmptyOutput
                || left.isNBTSensitive != right.isNBTSensitive
                || !Arrays.equals(left.getNeiDesc(), right.getNeiDesc())
                || !sameItems(left.mInputs, left.mInputChances, right.mInputs, right.mInputChances)
                || !sameItems(left.mOutputs, left.mOutputChances, right.mOutputs, right.mOutputChances)
                || !sameFluids(left.mFluidInputs, left.mFluidInputChances, right.mFluidInputs, right.mFluidInputChances)
                || !sameFluids(
                    left.mFluidOutputs,
                    left.mFluidOutputChances,
                    right.mFluidOutputs,
                    right.mFluidOutputChances)
                || left.mAltFluidInputs != right.mAltFluidInputs) return false;
            if (left instanceof GTRecipe_WithAlt) {
                GTRecipe_WithAlt leftAlt = (GTRecipe_WithAlt) left;
                GTRecipe_WithAlt rightAlt = (GTRecipe_WithAlt) right;
                return Arrays.equals(leftAlt.mOreDictIds, rightAlt.mOreDictIds)
                    && Arrays.deepEquals(leftAlt.mOreDictAlt, rightAlt.mOreDictAlt);
            }
            return true;
        }
    };
    private final Batch[] batches = { new Batch(RecipeMaps.centrifugeRecipes, RecipeMaps.centrifugeNonCellRecipes),
        new Batch(RecipeMaps.electrolyzerRecipes, RecipeMaps.electrolyzerNonCellRecipes),
        new Batch(RecipeMaps.mixerRecipes, RecipeMaps.mixerNonCellRecipes),
        new Batch(RecipeMaps.chemicalDehydratorRecipes, RecipeMaps.chemicalDehydratorNonCellRecipes),
        new Batch(RecipeMaps.coldTrapRecipes, RecipeMaps.nuclearSaltProcessingPlantRecipes),
        new Batch(RecipeMaps.reactorProcessingUnitRecipes, RecipeMaps.nuclearSaltProcessingPlantRecipes) };

    GTNonCellRecipes() {}

    void beforeMain() {
        for (Batch batch : batches) {
            batch.before = Collections.newSetFromMap(new IdentityHashMap<>());
            batch.before.addAll(batch.source.getAllRecipes());
        }
    }

    /** Freeze the delta after all Core main callbacks and before any consumer derived callback. */
    private void finishRegistration() {
        for (Batch batch : batches) {
            batch.added.clear();
            for (GTRecipe recipe : batch.source.getAllRecipes())
                if (!batch.before.contains(recipe)) batch.added.add(recipe);
            batch.before = null;
        }
    }

    /** Use the native converter unchanged; never re-import complete source maps or edit their recipes. */
    void derive() {
        finishRegistration();
        boolean empty = true;
        for (Batch batch : batches) empty &= batch.added.isEmpty();
        if (empty) return;
        // The public GT converter accepts only RecipeMap. These private conversion maps have no machine/NEI consumer.
        RecipeMap<?> input = temporaryMap(INPUT_MAP);
        RecipeMap<?> output = null;
        try {
            output = temporaryMap(OUTPUT_MAP);
            Map<RecipeMap<?>, Set<GTRecipe>> seenByTarget = new IdentityHashMap<>();
            for (Batch batch : batches) {
                if (batch.added.isEmpty()) continue;
                Set<GTRecipe> seen = seenByTarget.computeIfAbsent(batch.target, target -> {
                    Set<GTRecipe> recipes = new ObjectOpenCustomHashSet<>(EQUIVALENCE);
                    recipes.addAll(target.getAllRecipes());
                    return recipes;
                });
                for (GTRecipe recipe : batch.added) {
                    // copy/copyShallow in GT .265 drops metadata, including SKIP. The original is read only.
                    input.add(recipe);
                    RecipeGenMultisUsingFluidInsteadOfCells.generateRecipesNotUsingCells(input, output);
                    for (GTRecipe converted : output.getAllRecipes()) {
                        GTRecipe restored = withMetadata(recipe, converted, batch.target);
                        // Custom source categories have no target-category mapping; retain their separate entries.
                        if (recipe.getRecipeCategory() != batch.source.getDefaultRecipeCategory()
                            || seen.add(restored)) {
                            batch.target.add(restored);
                        }
                    }
                    input.getBackend()
                        .clearRecipes();
                    output.getBackend()
                        .clearRecipes();
                }
                batch.added.clear();
            }
        } finally {
            dispose(input);
            if (output != null) dispose(output);
        }
    }

    private static boolean sameItems(ItemStack[] left, int[] leftChances, ItemStack[] right, int[] rightChances) {
        boolean[] matched = new boolean[right.length];
        for (int i = 0; i < left.length; i++) {
            int match = -1;
            for (int j = 0; j < right.length; j++) {
                if (!matched[j] && chance(leftChances, i) == chance(rightChances, j)
                    && ItemStack.areItemStacksEqual(left[i], right[j])) {
                    match = j;
                    break;
                }
            }
            if (match < 0) return false;
            matched[match] = true;
        }
        return true;
    }

    private static boolean sameFluids(FluidStack[] left, int[] leftChances, FluidStack[] right, int[] rightChances) {
        boolean[] matched = new boolean[right.length];
        for (int i = 0; i < left.length; i++) {
            int match = -1;
            for (int j = 0; j < right.length; j++) {
                if (!matched[j] && chance(leftChances, i) == chance(rightChances, j)
                    && left[i].isFluidStackIdentical(right[j])) {
                    match = j;
                    break;
                }
            }
            if (match < 0) return false;
            matched[match] = true;
        }
        return true;
    }

    private static int chance(int[] chances, int index) {
        return chances == null || index >= chances.length ? 10000 : chances[index];
    }

    private static RecipeMap<?> temporaryMap(String name) {
        return RecipeMapBuilder.of(name)
            .maxIO(1, 1, 1, 1)
            .disableRegisterNEI()
            .build();
    }

    private static void dispose(RecipeMap<?> map) {
        map.getBackend()
            .clearRecipes();
        RecipeMap.ALL_RECIPE_MAPS.remove(map.unlocalizedName, map);
        RecipeCategory category = map.getDefaultRecipeCategory();
        RecipeCategory.ALL_RECIPE_CATEGORIES.remove(category.unlocalizedName, category);
    }

    /** GT .265's native container conversion is retained; only its discarded metadata is restored. */
    private static GTRecipe withMetadata(GTRecipe source, GTRecipe converted, RecipeMap<?> target) {
        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .itemInputsUnsafe(converted.mInputs)
            .itemOutputs(converted.mOutputs)
            .fluidInputs(converted.mFluidInputs)
            .fluidOutputs(converted.mFluidOutputs)
            .duration(converted.mDuration)
            .eut(converted.mEUt)
            .special(converted.mSpecialItems)
            .specialValue(converted.mSpecialValue)
            .recipeCategory(target.getDefaultRecipeCategory())
            .setNEIDesc(source.getNeiDesc());
        if (!converted.mEnabled) builder.disabled();
        if (converted.mHidden) builder.hidden();
        if (converted.mFakeRecipe) builder.fake();
        if (!converted.mCanBeBuffered) builder.noBuffer();
        if (converted.mNeedsEmptyOutput) builder.needsEmptyOutput();
        if (converted.isNBTSensitive) builder.nbtSensitive();
        for (Map.Entry<RecipeMetadataKey<?>, Object> entry : source.getMetadataStorage()
            .getEntries()) {
            copyMetadata(builder, entry.getKey(), entry.getValue());
        }
        GTRecipe result;
        if (converted instanceof GTRecipe_WithAlt) {
            GTRecipe_WithAlt alt = (GTRecipe_WithAlt) converted;
            GTRecipe_WithAlt restored = builder.forceOreDictInput()
                .buildWithAlt()
                .orElseThrow(() -> new IllegalStateException("Native NonCell alternative rejected"));
            restored.mOreDictAlt = alt.mOreDictAlt;
            restored.mOreDictIds = alt.mOreDictIds;
            restored.mAltFluidInputs = alt.mAltFluidInputs;
            result = restored;
        } else {
            result = builder.build()
                .orElseThrow(() -> new IllegalStateException("Native NonCell recipe rejected"));
        }
        // Native cell conversion may retain longer chance arrays after removing containers.
        // Builder setters reject those lengths; preserve the native arrays exactly after construction.
        result.mInputChances = converted.mInputChances;
        result.mOutputChances = converted.mOutputChances;
        result.mFluidInputChances = converted.mFluidInputChances;
        result.mFluidOutputChances = converted.mFluidOutputChances;
        result.owners = converted.owners;
        result.stackTraces = converted.stackTraces;
        return result;
    }

    private static <T> void copyMetadata(GTRecipeBuilder builder, RecipeMetadataKey<T> key, Object value) {
        builder.metadata(key, key.cast(value));
    }

    static void reloadRecipeCache() {
        GTMod.proxy.reloadNEICache();
    }

    private static final class Batch {

        private final RecipeMap<?> source, target;
        private Set<GTRecipe> before;
        private final List<GTRecipe> added = new ArrayList<>();

        private Batch(RecipeMap<?> source, RecipeMap<?> target) {
            this.source = source;
            this.target = target;
        }
    }
}
