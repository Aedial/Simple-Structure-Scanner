package com.simplestructurescanner.client.capture;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import com.simplestructurescanner.capture.StructureCaptureSummary;
import com.simplestructurescanner.client.gui.GuiStructureCapture;
import com.simplestructurescanner.network.NetworkHandler;
import com.simplestructurescanner.network.PacketRequestStructureCapturePreview;


/**
 * Client-only state machine for the ruler item selection flow.
 */
public final class StructureCaptureClientController {

    @Nullable
    private static BlockPos firstCorner;
    @Nullable
    private static BlockPos secondCorner;
    private static boolean previewRequestPending;

    private StructureCaptureClientController() {
    }

    public static void handleToolUse(EntityPlayer player) {
        if (player == null) return;

        if (player.isSneaking()) {
            resetSelection();
            return;
        }

        BlockPos feetPos = player.getPosition();
        if (firstCorner == null) {
            firstCorner = feetPos;
            secondCorner = null;
            previewRequestPending = false;
            sendMessage("chat.structurescanner.capture.firstCorner", feetPos.getX(), feetPos.getY(), feetPos.getZ());
            return;
        }

        if (secondCorner == null) {
            secondCorner = feetPos;
            sendMessage("chat.structurescanner.capture.secondCorner", feetPos.getX(), feetPos.getY(), feetPos.getZ());
            return;
        }

        if (previewRequestPending) return;

        previewRequestPending = true;
        sendMessage("chat.structurescanner.capture.loading");
        NetworkHandler.INSTANCE.sendToServer(new PacketRequestStructureCapturePreview(firstCorner, secondCorner));
    }

    public static void handlePreviewResponse(@Nullable StructureCaptureSummary summary, String errorKey) {
        previewRequestPending = false;

        if (firstCorner == null || secondCorner == null) {
            clearSelection();
            return;
        }

        if (summary == null) {
            clearSelection();
            sendMessage(errorKey == null || errorKey.isEmpty() ? "chat.structurescanner.capture.empty" : errorKey);
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiStructureCapture(summary, firstCorner, secondCorner));
    }

    public static void resetSelection() {
        if (!hasSelection() && !previewRequestPending) return;

        clearSelection();
        sendMessage("chat.structurescanner.capture.reset");
    }

    public static void clearSelection() {
        firstCorner = null;
        secondCorner = null;
        previewRequestPending = false;
    }

    public static boolean hasSelection() {
        return firstCorner != null;
    }

    public static boolean hasCompleteSelection() {
        return firstCorner != null && secondCorner != null;
    }

    @Nullable
    public static BlockPos getFirstCorner() {
        return firstCorner;
    }

    @Nullable
    public static BlockPos getSecondCorner() {
        return secondCorner;
    }

    @Nullable
    public static BlockPos getRenderSecondCorner(@Nullable EntityPlayer player) {
        if (firstCorner == null) return null;
        if (secondCorner != null) return secondCorner;
        if (player == null) return null;

        return player.getPosition();
    }

    public static boolean isPreviewRequestPending() {
        return previewRequestPending;
    }

    private static void sendMessage(String translationKey, Object... args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        mc.player.sendMessage(new TextComponentTranslation(translationKey, args));
    }
}