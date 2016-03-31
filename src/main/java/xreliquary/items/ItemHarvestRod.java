package xreliquary.items;


import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import org.lwjgl.input.Keyboard;
import xreliquary.Reliquary;
import xreliquary.blocks.BlockFertileLilypad;
import xreliquary.reference.Names;
import xreliquary.reference.Reference;
import xreliquary.reference.Settings;
import xreliquary.util.InventoryHelper;
import xreliquary.util.LanguageHelper;
import xreliquary.util.NBTHelper;

import java.util.List;
import java.util.Random;

/**
 * Created by Xeno on 10/11/2014.
 */
public class ItemHarvestRod extends ItemToggleable {
    public ItemHarvestRod() {
        super(Names.harvest_rod);
        this.setCreativeTab(Reliquary.CREATIVE_TAB);
        this.setMaxStackSize(1);
        canRepair = false;
    }

    @Override
    public void addInformation(ItemStack ist, EntityPlayer player, List list, boolean par4) {
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            return;
        this.formatTooltip(ImmutableMap.of("charge", Integer.toString(NBTHelper.getInteger("bonemeal", ist))), ist, list);
        if(this.isEnabled(ist))
            LanguageHelper.formatTooltip("tooltip.absorb_active", ImmutableMap.of("item", EnumChatFormatting.WHITE + Items.dye.getItemStackDisplayName(new ItemStack(Items.dye, 1, Reference.WHITE_DYE_META))), ist, list);
        LanguageHelper.formatTooltip("tooltip.absorb", null, ist, list);
    }

    public int getBonemealLimit() { return Settings.HarvestRod.bonemealLimit; }
    public int getBonemealWorth() { return Settings.HarvestRod.bonemealWorth; }
    public int getBonemealCost() { return Settings.HarvestRod.bonemealCost; }
    public int getLuckRolls() { return Settings.HarvestRod.bonemealLuckRolls; }
    public int getLuckPercent() { return Settings.HarvestRod.bonemealLuckPercentChance; }
    public int getBreakRadius() { return Settings.HarvestRod.harvestBreakRadius; }

    @Override
    public void onUpdate(ItemStack ist, World world, Entity e, int i, boolean b) {
        if (world.isRemote)
            return;
        EntityPlayer player = null;
        if (e instanceof EntityPlayer) {
            player = (EntityPlayer) e;
        }
        if (player == null)
            return;

        if (this.isEnabled(ist)) {
            if (NBTHelper.getInteger("bonemeal", ist) + getBonemealWorth() <= getBonemealLimit()) {
                if (InventoryHelper.consumeItem(new ItemStack(Items.dye, 1, Reference.WHITE_DYE_META), player)) {
                    NBTHelper.setInteger("bonemeal", ist, NBTHelper.getInteger("bonemeal", ist) + getBonemealWorth());
                }
            }
        }
    }

    @Override
    public boolean onBlockStartBreak(ItemStack ist, BlockPos pos, EntityPlayer player) {
        if (player.worldObj.isRemote)
            return false;

        Block block = player.worldObj.getBlockState(pos).getBlock();
        if (block instanceof IPlantable || block instanceof IGrowable) {
            for (int xOff = -getBreakRadius(); xOff <= getBreakRadius(); xOff++) {
                for (int yOff = -getBreakRadius(); yOff <= getBreakRadius(); yOff++) {
                    for (int zOff = -getBreakRadius(); zOff <= getBreakRadius(); zOff++) {
                        doHarvestBlockBreak(ist, pos, player, xOff, yOff, zOff);
                    }
                }
            }
        }

        return true;
    }

    public void doHarvestBlockBreak(ItemStack ist, BlockPos pos, EntityPlayer player, int xOff, int yOff, int zOff) {
        pos = pos.add( xOff, yOff, zOff );

        IBlockState blockState = player.worldObj.getBlockState( pos );

        if (!(blockState.getBlock() instanceof IPlantable) && !(blockState.getBlock() instanceof BlockCrops))
            return;
        if (blockState.getBlock() instanceof BlockFertileLilypad)
            return;

        List<ItemStack> drops = blockState.getBlock().getDrops( player.worldObj, pos, blockState,
                EnchantmentHelper.getEnchantmentLevel( Enchantment.fortune.effectId, ist ) );
        Random rand = new Random();

        if (player.worldObj.isRemote) {
            for (int particles = 0; particles <= 8; particles++)player.worldObj.playAuxSFXAtEntity(player, 2001, pos, Block.getStateId( blockState ));
        } else {
            for(ItemStack stack : drops)
            {
                float f = 0.7F;
                double d  = (double)(rand.nextFloat() * f) + (double)(1.0F - f) * 0.5D;
                double d1 = (double)(rand.nextFloat() * f) + (double)(1.0F - f) * 0.5D;
                double d2 = (double)(rand.nextFloat() * f) + (double)(1.0F - f) * 0.5D;
                EntityItem entityitem = new EntityItem(player.worldObj, (double)pos.getX() + d, (double)pos.getY() + d1, (double)pos.getZ() + d2, stack);
                entityitem.setPickupDelay(10);
                player.worldObj.spawnEntityInWorld(entityitem);
            }

            player.worldObj.setBlockState( pos, Blocks.air.getDefaultState() );
            player.addStat(StatList.mineBlockStatArray[Block.getIdFromBlock(blockState.getBlock())], 1);
            player.addExhaustion(0.01F);
        }
    }

    @Override
    public boolean onItemUse(ItemStack ist, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float xOff, float yOff, float zOff) {
        if (NBTHelper.getInteger("bonemeal", ist) >= getBonemealCost() || player.capabilities.isCreativeMode) {
            ItemStack fakeItemStack = new ItemStack(Items.dye, 1, Reference.WHITE_DYE_META);
            ItemDye fakeItemDye = (ItemDye)fakeItemStack.getItem();

            boolean usedRod = false;
            for (int repeatedUses = 0; repeatedUses <= getLuckRolls(); repeatedUses++) {
                if (repeatedUses == 0 || world.rand.nextInt(100) <= getLuckPercent()) {
                    if (fakeItemDye.onItemUse(fakeItemStack, player, world, pos, side, xOff, yOff, zOff)) {
                        if (!usedRod) usedRod = true;
                        player.worldObj.playSoundAtEntity(player, "random.orb", 0.1F, 0.5F * ((player.worldObj.rand.nextFloat() - player.worldObj.rand.nextFloat()) * 0.7F + 1.2F));
                    }
                }
            }

            if (usedRod && !player.capabilities.isCreativeMode)
                NBTHelper.setInteger("bonemeal", ist, NBTHelper.getInteger("bonemeal", ist) - getBonemealCost());
        }

        return true;
    }
}