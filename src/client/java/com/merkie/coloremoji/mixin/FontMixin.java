package com.merkie.coloremoji.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merkie.coloremoji.ColorEmoji;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws each emoji sequence as one color emoji. */
@Mixin(Font.class)
abstract class FontMixin {
    @WrapOperation(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z"))
    private boolean colorEmoji$emojiInString(String text, Style style, FormattedCharSink output,
                                                 Operation<Boolean> original) {
        return ColorEmoji.collapse(output, sink -> original.call(text, style, sink));
    }

    @WrapOperation(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/FormattedCharSequence;accept(Lnet/minecraft/util/FormattedCharSink;)Z"))
    private boolean colorEmoji$emojiInSequence(FormattedCharSequence text, FormattedCharSink output,
                                                   Operation<Boolean> original) {
        return ColorEmoji.collapse(output, sink -> original.call(text, sink));
    }
}
