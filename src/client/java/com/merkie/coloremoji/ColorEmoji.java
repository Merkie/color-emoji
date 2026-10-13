package com.merkie.coloremoji;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSink;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Color emoji: Apple's emoji in place of Minecraft's one-color Unifont ones. {@code tools/emoji.py} makes the PNGs and
 * {@code emoji.txt}, which lists the code points each PNG shows. An emoji that is one code point, such as 😀, draws as
 * itself. A sequence, such as ❤️ or 👍🏽, stands in as one private-use code point only while text is drawn or
 * measured, so text you send or copy never changes.
 */
public final class ColorEmoji {
    private static final String FOLDER = "color-emoji/emoji/";
    /** The first private-use code point that stands for a sequence. */
    private static final int SEQUENCES = 0x100000;
    /** The longest sequence, a couple kissing with two skin tones, is 10 code points. */
    private static final int LONGEST = 16;
    /** 8 wide with a 1-pixel gap, like a letter. */
    private static final GlyphInfo INFO = GlyphInfo.simple(9);
    /** Variation selectors and the zero-width joiner only shape the emoji around them, so they draw as nothing. */
    private static final EmptyGlyph INVISIBLE = new EmptyGlyph(0);
    private static final int[] INVISIBLE_CODE_POINTS = {0xFE0E, 0xFE0F, 0x200D};
    private static final GlyphProvider PROVIDER = new Provider();
    private static Index index;

    private ColorEmoji() {}

    /**
     * A font's glyph providers, with color emoji first if it's a text font that falls back to Unifont, as the default
     * font does. First, because Minecraft's own glyphs include a few emoji, such as 🔥, in one color.
     */
    public static List<GlyphProvider.Conditional> addTo(List<GlyphProvider.Conditional> providers) {
        if (providers.stream().noneMatch(provider -> provider.provider() instanceof UnihexProvider)) return providers;
        List<GlyphProvider.Conditional> result = new ArrayList<>(providers);
        result.addFirst(new GlyphProvider.Conditional(PROVIDER, FontOption.Filter.ALWAYS_PASS));
        return result;
    }

    /** Runs {@code text} into {@code output} with each emoji sequence in the default font as its one code point. */
    public static boolean collapse(FormattedCharSink output, Predicate<FormattedCharSink> text) {
        Sequences sequences = new Sequences(output, index().sequences);
        return text.test(sequences) && sequences.finish();
    }

    /** What emoji.txt lists: the PNG for each code point, and the sequences that become one code point. */
    private record Index(Int2ObjectMap<String> files, Node sequences, IntSet supported) {}

    /** A step in a sequence. {@code glyph} is the code point a sequence ending here stands in as, or 0. */
    private static final class Node {
        final Int2ObjectMap<Node> next = new Int2ObjectOpenHashMap<>();
        int glyph;
    }

    private static Index index() {
        if (index != null) return index;
        Int2ObjectMap<String> files = new Int2ObjectOpenHashMap<>();
        Node sequences = new Node();
        try {
            List<String> lines = Files.readAllLines(file("emoji.txt"));
            for (int line = 0; line < lines.size(); line++) {
                String[] shown = lines.get(line).split(" ");
                String png = shown[0] + ".png";
                for (String sequence : shown) {
                    String[] parts = sequence.split("-");
                    if (parts.length == 1) {
                        files.put(Integer.parseInt(parts[0], 16), png);
                        continue;
                    }
                    Node node = sequences;
                    for (String part : parts) node = node.next.computeIfAbsent(Integer.parseInt(part, 16), ignored -> new Node());
                    node.glyph = SEQUENCES + line;
                    files.put(node.glyph, png);
                }
            }
        } catch (IOException exception) {
            LoggerFactory.getLogger("color-emoji").warn("Could not read the color emoji list", exception);
        }
        IntSet supported = new IntOpenHashSet(files.keySet());
        if (!files.isEmpty()) supported.addAll(IntSet.of(INVISIBLE_CODE_POINTS));
        index = new Index(files, sequences, IntSets.unmodifiable(supported));
        return index;
    }

    private static Path file(String name) throws IOException {
        return FabricLoader.getInstance().getModContainer("color-emoji").flatMap(mod -> mod.findPath(FOLDER + name))
                .orElseThrow(() -> new IOException("Missing " + FOLDER + name));
    }

    private static final class Provider implements GlyphProvider {
        @Override
        public UnbakedGlyph getGlyph(int codepoint) {
            String png = index().files.get(codepoint);
            if (png != null) return new Glyph(png);
            return index().supported.contains(codepoint) ? INVISIBLE : null;
        }

        @Override
        public IntSet getSupportedGlyphs() {
            return index().supported;
        }
    }

    /** Loads its PNG only when first drawn, sized for the current GUI scale so it stays sharp. */
    private record Glyph(String png) implements UnbakedGlyph {
        @Override
        public GlyphInfo info() {
            return INFO;
        }

        @Override
        public BakedGlyph bake(Stitcher stitcher) {
            int size = 8 * Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
            try (NativeImage image = load(png, size)) {
                return new Colored(stitcher.stitch(INFO, new Bitmap(image)));
            } catch (IOException exception) {
                LoggerFactory.getLogger("color-emoji").warn("Could not load emoji {}", png, exception);
                return stitcher.getMissing();
            }
        }
    }

    private static NativeImage load(String png, int size) throws IOException {
        NativeImage source = NativeImage.read(Files.newInputStream(file(png)));
        if (source.getWidth() == size && source.getHeight() == size) return source;
        try (source) {
            return resized(source, size);
        }
    }

    /** Scales a square image by averaging the area each pixel covers, with transparent pixels adding no color. */
    private static NativeImage resized(NativeImage source, int size) {
        int from = source.getWidth();
        double step = (double) from / size;
        NativeImage result = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double top = y * step, bottom = top + step, left = x * step, right = left + step;
                double alpha = 0, red = 0, green = 0, blue = 0;
                for (int sourceY = (int) top; sourceY < Math.min(from, Math.ceil(bottom)); sourceY++) {
                    double height = Math.min(bottom, sourceY + 1) - Math.max(top, sourceY);
                    for (int sourceX = (int) left; sourceX < Math.min(from, Math.ceil(right)); sourceX++) {
                        double weight = height * (Math.min(right, sourceX + 1) - Math.max(left, sourceX));
                        int pixel = source.getPixel(sourceX, sourceY);
                        double opacity = ARGB.alpha(pixel) * weight;
                        alpha += opacity;
                        red += ARGB.red(pixel) * opacity;
                        green += ARGB.green(pixel) * opacity;
                        blue += ARGB.blue(pixel) * opacity;
                    }
                }
                result.setPixel(x, y, alpha == 0 ? 0 : ARGB.color((int) Math.round(alpha / (step * step)),
                        (int) Math.round(red / alpha), (int) Math.round(green / alpha), (int) Math.round(blue / alpha)));
            }
        }
        return result;
    }

    /** Fills the 8 × 8 box a capital letter and its descender fill. */
    private record Bitmap(NativeImage image) implements GlyphBitmap {
        @Override
        public int getPixelWidth() {
            return image.getWidth();
        }

        @Override
        public int getPixelHeight() {
            return image.getHeight();
        }

        @Override
        public float getOversample() {
            return image.getWidth() / 8.0F;
        }

        @Override
        public void upload(int x, int y, GpuTexture texture) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, 0, 0, x, y);
        }

        @Override
        public boolean isColored() {
            return true;
        }
    }

    /** Draws in the emoji's own colors whatever the text color, and without shadow, bold, or italics. */
    private record Colored(BakedGlyph glyph) implements BakedGlyph {
        @Override
        public GlyphInfo info() {
            return glyph.info();
        }

        @Override
        public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style,
                                                 float boldOffset, float shadowOffset) {
            Style plain = style.isBold() || style.isItalic() ? style.withBold(false).withItalic(false) : style;
            return glyph.createGlyph(x, y, ARGB.white(ARGB.alpha(color)), 0, plain, 0, 0);
        }
    }

    /**
     * Passes code points on, holding back any that may start a sequence until it's clear whether they do. A sequence
     * goes on as its one code point, at the position of its first.
     */
    private static final class Sequences implements FormattedCharSink {
        private final FormattedCharSink output;
        private final Node root;
        private final int[] codepoints = new int[LONGEST];
        private final int[] positions = new int[LONGEST];
        private final Style[] styles = new Style[LONGEST];
        private int size;

        private Sequences(FormattedCharSink output, Node root) {
            this.output = output;
            this.root = root;
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            if (size == 0 && (!root.next.containsKey(codepoint) || !FontDescription.DEFAULT.equals(style.getFont()))) {
                return output.accept(position, style, codepoint);
            }
            codepoints[size] = codepoint;
            positions[size] = position;
            styles[size] = style;
            size++;
            while (size > 0 && (size == LONGEST || !startsSequence())) {
                if (!send()) return false;
            }
            return true;
        }

        /** Sends what's held back once the text ends. */
        private boolean finish() {
            while (size > 0) {
                if (!send()) return false;
            }
            return true;
        }

        private boolean startsSequence() {
            Node node = root;
            for (int i = 0; i < size && node != null; i++) node = node.next.get(codepoints[i]);
            return node != null;
        }

        /** Sends the longest sequence held back, or else the first code point alone. */
        private boolean send() {
            int length = 1;
            int glyph = codepoints[0];
            Node node = root;
            for (int i = 0; i < size; i++) {
                node = node.next.get(codepoints[i]);
                if (node == null) break;
                if (node.glyph != 0) {
                    length = i + 1;
                    glyph = node.glyph;
                }
            }
            boolean more = output.accept(positions[0], styles[0], glyph);
            size -= length;
            System.arraycopy(codepoints, length, codepoints, 0, size);
            System.arraycopy(positions, length, positions, 0, size);
            System.arraycopy(styles, length, styles, 0, size);
            return more;
        }
    }
}
