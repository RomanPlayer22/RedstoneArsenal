package cofh.redstonearsenal.client.renderer;

import cofh.redstonearsenal.item.FluxElytraItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import static cofh.lib.util.constants.Constants.ID_REDSTONE_ARSENAL;

public class FluxElytraLayer<T extends LivingEntity, M extends EntityModel<T>> extends ElytraLayer<T, M> {

    protected static final ResourceLocation UNCHARGED = new ResourceLocation(ID_REDSTONE_ARSENAL + ":textures/entity/flux_elytra.png");
    //protected static final ResourceLocation CHARGED = new ResourceLocation(ID_REDSTONE_ARSENAL + ":textures/entity/flux_elytra.png");
    //protected static final ResourceLocation EMPOWERED = new ResourceLocation(ID_REDSTONE_ARSENAL + ":textures/entity/flux_elytra.png");

    public FluxElytraLayer(EntityRenderer renderer) {

        super(renderer);
    }

    @Override
    public boolean shouldRender(ItemStack stack, T entity) {

        return stack.getItem() instanceof FluxElytraItem;
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, T entity) {

        //FluxElytraItem elytra = (FluxElytraItem) stack.getItem();
        //if (elytra.getEnergyStored(stack) > 0) {
        //    return elytra.isEmpowered(stack) ? EMPOWERED : CHARGED;
        //}
        return UNCHARGED;
    }

}
