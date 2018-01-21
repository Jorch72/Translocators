package codechicken.translocator.client.render;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.item.IItemRenderer;
import codechicken.lib.texture.TextureUtils;
import codechicken.lib.util.TransformUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.model.IModelState;

/**
 * Created by covers1624 on 7/16/2016.
 */
public class TranslocatorItemRender implements IItemRenderer {

    @Override
    public void renderItem(ItemStack stack, TransformType transformType) {
        GlStateManager.pushMatrix();

        TextureUtils.changeTexture("translocator:textures/model/tex.png");
        CCRenderState ccrs = CCRenderState.instance();
        ccrs.startDrawing(4, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        TileTranslocatorRenderer.renderAttachment(ccrs, 2, stack.getItemDamage(), 1D, 0, 0.0D, 0.0D, 0.5D);
        ccrs.draw();

        GlStateManager.popMatrix();
    }

    @Override
    public IModelState getTransforms() {
        return TransformUtils.DEFAULT_BLOCK;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }
}
