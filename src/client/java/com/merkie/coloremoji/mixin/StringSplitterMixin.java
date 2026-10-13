package com.merkie.coloremoji.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merkie.coloremoji.ColorEmoji;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Measures each emoji sequence as the one color emoji it draws as, so centered text and backgrounds line up. */
@Mixin(StringSplitter.class)
abstract class StringSplitterMixin {
    @WrapOperation(method = "stringWidth(Ljava/lang/String;)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z"))
    private boolean colorEmoji$emojiInString(String text, Style style, FormattedCharSink output,
                                                 Operation<Boolean> original) {
        return ColorEmoji.collapse(output, sink -> original.call(text, style, sink));
    }

    @WrapOperation(method = "stringWidth(Lnet/minecraft/network/chat/FormattedText;)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Lnet/minecraft/network/chat/FormattedText;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z"))
    private boolean colorEmoji$emojiInText(FormattedText text, Style style, FormattedCharSink output,
                                               Operation<Boolean> original) {
        return ColorEmoji.collapse(output, sink -> original.call(text, style, sink));
    }

    @WrapOperation(method = "stringWidth(Lnet/minecraft/util/FormattedCharSequence;)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/FormattedCharSequence;accept(Lnet/minecraft/util/FormattedCharSink;)Z"))
    private boolean colorEmoji$emojiInSequence(FormattedCharSequence text, FormattedCharSink output,
                                                   Operation<Boolean> original) {
        return ColorEmoji.collapse(output, sink -> original.call(text, sink));
    }
}
