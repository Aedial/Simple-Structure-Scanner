package com.simplestructurescanner.client.integration;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import net.darkhax.gamestages.GameStageHelper;
import net.darkhax.gamestages.data.IStageData;


/**
 * Captures the client's GameStages state when the structure scanner GUI opens.
 */
public final class GameStagesIntegration {
    public static final String MOD_ID = "gamestages";

    private GameStagesIntegration() {
    }

    @Nullable
    public static Set<String> captureClientStages() {
        if (!Loader.isModLoaded(MOD_ID)) return null;

        return captureClientStagesOptional();
    }

    @Nullable
    @Optional.Method(modid = MOD_ID)
    private static Set<String> captureClientStagesOptional() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return null;

        IStageData stageData = GameStageHelper.getPlayerData(minecraft.player);
        if (stageData == null) return null;

        Collection<String> stages = stageData.getStages();
        if (stages == null || stages.isEmpty()) return Collections.emptySet();

        return new LinkedHashSet<>(stages);
    }
}