package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import quoi.module.impl.general.Tweaks;
import quoi.module.impl.misc.catmode.CatMode;
import quoi.module.impl.render.NickHider;

@Mixin(Font.class)
public class FontMixin {
    @ModifyVariable(
            at = @At("HEAD"),
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            ordinal = 0,
            argsOnly = true
    )
    public FormattedCharSequence prepareOrderedText(FormattedCharSequence value) {
        return NickHider.replaceName(CatMode.replaceText(value));
    }

    @ModifyExpressionValue(
            method = "getGlyph",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GlyphSource;getRandomGlyph(Lnet/minecraft/util/RandomSource;I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"
            )
    )
    private BakedGlyph stabilizeObfuscatedAdvance(BakedGlyph randomGlyph, @Local BakedGlyph originalGlyph) {
        if (Tweaks.obfuscatedChatRender.isBound()) {
            return new GlyphWithOriginalMetrics(randomGlyph, originalGlyph.info());
        }
        return randomGlyph;
    }

    private record GlyphWithOriginalMetrics(BakedGlyph glyph, GlyphInfo info) implements BakedGlyph {
        @Override
        public TextRenderable.Styled createGlyph(
                float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset
        ) {
            return glyph.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset);
        }
    }

    @ModifyVariable(
            at = @At("HEAD"),
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            ordinal = 0,
            argsOnly = true
    )
    public String prepareString(String value) {
        return NickHider.replaceName(CatMode.replaceText(value));
    }

    @ModifyVariable(
            at = @At("HEAD"),
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            ordinal = 0,
            argsOnly = true
    )
    public FormattedCharSequence getWidth(FormattedCharSequence value) {
        return NickHider.replaceName(CatMode.replaceText(value));
    }

    @ModifyVariable(
            at = @At("HEAD"),
            method = "width(Ljava/lang/String;)I",
            ordinal = 0,
            argsOnly = true
    )
    public String getWidth(String value) {
        return NickHider.replaceName(CatMode.replaceText(value));
    }
}
