package cofh.redstonearsenal.item;

import cofh.core.config.CoreClientConfig;
import cofh.core.util.ProxyUtils;
import cofh.lib.capability.CapabilityAreaEffect;
import cofh.lib.capability.IAreaEffect;
import cofh.lib.energy.EnergyContainerItemWrapper;
import cofh.lib.energy.IEnergyContainerItem;
import cofh.lib.item.impl.ExcavatorItem;
import cofh.lib.util.Utils;
import cofh.lib.util.helpers.AreaEffectHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cofh.lib.util.helpers.StringHelper.getTextComponent;

public class FluxExcavatorItem extends ExcavatorItem implements IMultiModeFluxItem {

    protected final float damage;
    protected final float attackSpeed;

    protected final int maxEnergy;
    protected final int extract;
    protected final int receive;

    public FluxExcavatorItem(Tier tier, float attackDamageIn, float attackSpeedIn, Properties builder, int energy, int xfer) {

        super(tier, attackDamageIn, attackSpeedIn, builder);

        this.damage = getAttackDamage();
        this.attackSpeed = attackSpeedIn;

        this.maxEnergy = energy;
        this.extract = xfer;
        this.receive = xfer;

        ProxyUtils.registerItemModelProperty(this, new ResourceLocation("charged"), this::getChargedModelProperty);
        ProxyUtils.registerItemModelProperty(this, new ResourceLocation("empowered"), this::getEmpoweredModelProperty);
    }

    @Override
    @OnlyIn (Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {

        if (Screen.hasShiftDown() || CoreClientConfig.alwaysShowDetails) {
            tooltipDelegate(stack, worldIn, tooltip, flagIn);
        } else if (CoreClientConfig.holdShiftForDetails) {
            tooltip.add(getTextComponent("info.cofh.hold_shift_for_details").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {

        return super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {

        return getItemEnchantability(stack) > 0;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {

        return new FluxExcavatorItemWrapper(stack, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {

        Level world = context.getLevel();
        BlockPos clickPos = context.getClickedPos();
        ItemStack tool = context.getItemInHand();
        BlockState state = world.getBlockState(clickPos);
        Player player = context.getPlayer();
        if (player == null || !player.mayUseItemAt(clickPos, context.getClickedFace(), tool)) {
            return InteractionResult.PASS;
        }
        ImmutableList<BlockPos> blocks = AreaEffectHelper.getPlaceableBlocksRadius(tool, clickPos, player, 1 + getMode(tool));
        if (blocks.size() < 1) {
            return InteractionResult.FAIL;
        }
        if (!world.isClientSide()) {
            BlockPlaceContext blockContext = new BlockPlaceContext(context);
            BlockPos playerPos = player.blockPosition();
            BlockPos eyePos = new BlockPos(player.getEyePosition(1));
            if (player.abilities.instabuild) {
                for (BlockPos pos : blocks) {
                    BlockPos fillPos = pos.relative(context.getClickedFace());
                    if (world.getBlockState(fillPos).canBeReplaced(blockContext) && !fillPos.equals(playerPos) && !fillPos.equals(eyePos)) {
                        world.setBlock(fillPos, world.getBlockState(pos).getBlock().defaultBlockState(), 2);
                    }
                }
            } else {
                Map<Block, List<BlockPos>> sorted = new HashMap<>();
                for (BlockPos pos : blocks) {
                    BlockPos fillPos = pos.relative(context.getClickedFace());
                    if (world.getBlockState(fillPos).canBeReplaced(blockContext) && !fillPos.equals(playerPos) && !fillPos.equals(eyePos)) {
                        Block block = world.getBlockState(pos).getBlock();
                        sorted.putIfAbsent(block, new ArrayList<>());
                        sorted.get(block).add(fillPos);
                    }
                }
                NonNullList<ItemStack> inventory = player.inventory.items;
                for (Block block : sorted.keySet()) {
                    int slot = -1;
                    if (!hasEnergy(context.getItemInHand(), false)) {
                        break;
                    }
                    List<Item> validItems = Block.getDrops(block.defaultBlockState(), (ServerLevel) world, sorted.get(block).get(0), null).stream().map(ItemStack::getItem).filter(i -> i instanceof BlockItem).collect(Collectors.toList());
                    Predicate<ItemStack> matches = stack -> stack.getItem() instanceof BlockItem && (stack.getItem().equals(block.asItem()) || validItems.contains(stack.getItem()));
                    for (BlockPos pos : sorted.get(block)) {
                        if (slot < 0 || inventory.get(slot).isEmpty()) {
                            slot = findFirstInventory(inventory, matches, slot + 1);
                            if (slot < 0) {
                                break;
                            }
                        }
                        if (!useEnergy(context.getItemInHand(), false, false)) {
                            break;
                        }
                        if (world.setBlock(pos, ((BlockItem) inventory.get(slot).getItem()).getBlock().defaultBlockState(), 2)) {
                            inventory.get(slot).shrink(1);
                        }
                    }
                }
            }
        } else {
            world.playLocalSound(clickPos.getX() + 0.5, clickPos.getY() + 0.5, clickPos.getZ() + 0.5, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        return InteractionResult.sidedSuccess(world.isClientSide());
    }

    public static int findFirstInventory(NonNullList<ItemStack> inventory, Predicate<ItemStack> filter, int start) {

        for (int i = start; i < inventory.size(); ++i) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    protected float getEfficiency(ItemStack stack) {

        return hasEnergy(stack, false) ? speed : 1.0F;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {

        return getToolTypes(stack).stream().anyMatch(state::isToolEffective) ? getEfficiency(stack) : 1.0F;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {

        useEnergy(stack, false, attacker);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {

        if (Utils.isServerWorld(worldIn) && state.getDestroySpeed(worldIn, pos) != 0.0F) {
            useEnergy(stack, false, entityLiving);
        }
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {

        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();
        if (slot == EquipmentSlot.MAINHAND) {
            multimap.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Tool modifier", getAttackDamage(stack), AttributeModifier.Operation.ADDITION));
            multimap.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Tool modifier", getAttackSpeed(stack), AttributeModifier.Operation.ADDITION));
        }
        return multimap;
    }

    protected float getAttackDamage(ItemStack stack) {

        return hasEnergy(stack, false) ? damage : 0.0F;
    }

    protected float getAttackSpeed(ItemStack stack) {

        return attackSpeed;
    }

    // region IEnergyContainerItem
    @Override
    public int getExtract(ItemStack container) {

        return extract;
    }

    @Override
    public int getReceive(ItemStack container) {

        return receive;
    }

    @Override
    public int getMaxEnergyStored(ItemStack container) {

        return getMaxStored(container, maxEnergy);
    }
    // endregion

    // region CAPABILITY WRAPPER
    protected class FluxExcavatorItemWrapper extends EnergyContainerItemWrapper implements IAreaEffect {

        private final LazyOptional<IAreaEffect> holder = LazyOptional.of(() -> this);

        FluxExcavatorItemWrapper(ItemStack containerIn, IEnergyContainerItem itemIn) {

            super(containerIn, itemIn, itemIn.getEnergyCapability());
        }

        @Override
        public ImmutableList<BlockPos> getAreaEffectBlocks(BlockPos pos, Player player) {

            return AreaEffectHelper.getBreakableBlocksRadius(container, pos, player, 1 + getMode(container));
        }

        // region ICapabilityProvider
        @Override
        @Nonnull
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {

            if (cap == CapabilityAreaEffect.AREA_EFFECT_ITEM_CAPABILITY) {
                return CapabilityAreaEffect.AREA_EFFECT_ITEM_CAPABILITY.orEmpty(cap, holder);
            }
            return super.getCapability(cap, side);
        }
        // endregion
    }
    // endregion
}
