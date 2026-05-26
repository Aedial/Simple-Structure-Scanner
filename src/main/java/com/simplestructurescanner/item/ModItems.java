package com.simplestructurescanner.item;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Central item registration for the mod.
 */
public final class ModItems {

    public static final Item STRUCTURE_CAPTURE_TOOL = new ItemStructureCaptureTool();

    private ModItems() {
    }

    public static void registerItems() {
        ForgeRegistries.ITEMS.register(STRUCTURE_CAPTURE_TOOL);
    }

    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        ModelLoader.setCustomModelResourceLocation(
            STRUCTURE_CAPTURE_TOOL,
            0,
            new ModelResourceLocation(STRUCTURE_CAPTURE_TOOL.getRegistryName(), "inventory")
        );
    }
}