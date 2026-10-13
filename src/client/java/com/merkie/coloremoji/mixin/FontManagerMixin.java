package com.merkie.coloremoji.mixin;

import com.merkie.coloremoji.ColorEmoji;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(FontManager.class)
abstract class FontManagerMixin {
    @ModifyVariable(method = "createFontSet", at = @At("HEAD"), argsOnly = true)
    private List<GlyphProvider.Conditional> colorEmoji$colorEmoji(List<GlyphProvider.Conditional> providers) {
        return ColorEmoji.addTo(providers);
    }
}
