package com.simplestructurescanner.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.simplestructurescanner.Tags;
import com.simplestructurescanner.client.capture.StructureCaptureClientController;


/**
 * Right-click tool used to mark two corners, inspect the capture, and save a structure NBT.
 */
public class ItemStructureCaptureTool extends Item {

    public static final String ITEM_NAME = "structure_capture_tool";

    public ItemStructureCaptureTool() {
        setRegistryName(new ResourceLocation(Tags.MODID, ITEM_NAME));
        setTranslationKey(Tags.MODID + "." + ITEM_NAME);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
    }

    // TODO: Add something to adjust the bounds on the fly instead of re-doing the 2 corners

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND) return new ActionResult<>(EnumActionResult.PASS, stack);

        if (world.isRemote) StructureCaptureClientController.handleToolUse(player);

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return EnumActionResult.PASS;

        if (world.isRemote) StructureCaptureClientController.handleToolUse(player);

        return EnumActionResult.SUCCESS;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add("");
        tooltip.add(I18n.format("item.simplestructurescanner.structure_capture_tool.tooltip.1"));
        tooltip.add(I18n.format("item.simplestructurescanner.structure_capture_tool.tooltip.2"));
        tooltip.add(I18n.format("item.simplestructurescanner.structure_capture_tool.tooltip.3"));
        tooltip.add(I18n.format("item.simplestructurescanner.structure_capture_tool.tooltip.4"));
        tooltip.add(I18n.format("item.simplestructurescanner.structure_capture_tool.tooltip.5"));
    }
}