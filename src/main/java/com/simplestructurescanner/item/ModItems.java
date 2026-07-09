package com.simplestructurescanner.item;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.simplestructurescanner.Tags;


/**
 * Central item registration for the mod.
 */
public final class ModItems {

    public static final Item STRUCTURE_CAPTURE_TOOL = new ItemStructureCaptureTool();
    /** Hidden item used only as a JEI focus anchor for structure navigation. */
    public static final Item JEI_ANCHOR = new Item()
        .setRegistryName(new ResourceLocation(Tags.MODID, "jei_anchor"))
        .setTranslationKey(Tags.MODID + ".jei_anchor")
        .setMaxStackSize(1);

    private ModItems() {
    }

    public static void registerItems() {
        ForgeRegistries.ITEMS.register(STRUCTURE_CAPTURE_TOOL);
        ForgeRegistries.ITEMS.register(JEI_ANCHOR);
    }

    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        ModelLoader.setCustomModelResourceLocation(
            STRUCTURE_CAPTURE_TOOL,
            0,
            new ModelResourceLocation(STRUCTURE_CAPTURE_TOOL.getRegistryName(), "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
            JEI_ANCHOR,
            0,
            new ModelResourceLocation(JEI_ANCHOR.getRegistryName(), "inventory")
        );
    }
}