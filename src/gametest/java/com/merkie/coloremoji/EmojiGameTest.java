package com.merkie.coloremoji;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EmojiGameTest implements FabricClientGameTest {
    private static final String CHAT = "Emoji: 😀 😂 ❤️ 👍🏽 🔥 👨‍👩‍👧 🇺🇸 💀 #️⃣";
    private static final String SIGN = "😀 👍🏽 🇺🇸 ❤️";
    public static volatile String sentChat;
    public static volatile String sentSign;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                Font font = client.font;
                for (String emoji : new String[] {"😀", "❤️", "👍🏽", "👨‍👩‍👧", "🇺🇸", "#️⃣",
                        "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"}) {
                    require(font.width(emoji) == 9, emoji + " is one emoji, 9 wide: " + font.width(emoji));
                    require(font.width(Component.literal(emoji)) == 9, "Component width matches string width");
                    require(font.width(Component.literal(emoji).getVisualOrderText()) == 9,
                            "Visual sequence width matches string width");
                }
                require(font.width("a😀b") == font.width("a") + 9 + font.width("b"),
                        "An emoji sits between letters");
                require(font.width(Component.literal("❤️").withStyle(ChatFormatting.GRAY)) == 9,
                        "Styled hearts measure as one emoji");
                var supported = ColorEmoji.addTo(java.util.List.of());
                require(supported.isEmpty(), "A font with no Unicode fallback is left alone");
                StringBuilder rebuilt = new StringBuilder();
                StringDecomposer.iterateFormatted(CHAT, Style.EMPTY, (position, style, codepoint) -> {
                    rebuilt.appendCodePoint(codepoint);
                    return true;
                });
                require(CHAT.contentEquals(rebuilt), "Shared text decomposition preserves original Unicode");
                sentChat = null;
                client.getConnection().sendChat(CHAT);
                require(CHAT.equals(sentChat), "Sent chat keeps its original Unicode");
                client.gui.hud.getChat().addClientSystemMessage(Component.literal(CHAT).withStyle(ChatFormatting.GRAY));
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("Red bold 🔥 and italic 💀")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.ITALIC));
            });
            context.waitTicks(2);
            context.takeScreenshot("color-emoji-chat");

            // Use a black backdrop so yellow pixels can only come from emoji, including a real item tooltip.
            context.runOnClient(client -> client.gui.setScreen(new PreviewScreen()));
            context.waitTicks(2);
            require(yellowPixels(context.takeScreenshot("color-emoji-preview")) > 100,
                    "Emoji keep their own colors in gray and red text and item tooltips");

            // The real sign editor draws string text and sends its original text on close.
            context.runOnClient(client -> openSign(client, "Text symbols: ❤ ✔ ★"));
            context.waitTicks(2);
            int baseline = yellowPixels(context.takeScreenshot("color-emoji-sign-text"));
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                openSign(client, SIGN);
            });
            context.waitTicks(2);
            int colored = yellowPixels(context.takeScreenshot("color-emoji-sign"));
            require(colored > baseline + 30, "Sign emoji draw in color: " + colored + " vs " + baseline);
            context.runOnClient(client -> {
                sentSign = null;
                client.gui.setScreen(null);
                require(SIGN.equals(sentSign), "Saved sign text keeps its original Unicode");
            });
        }
        LoggerFactory.getLogger("color-emoji-test").info("PASS: Color Emoji renders single emoji and sequences, "
                + "chat, signs, and named-item tooltips in color; sent chat and sign text keep their original Unicode.");
    }

    private static void openSign(Minecraft client, String text) {
        SignBlockEntity sign = new SignBlockEntity(client.player.blockPosition(), Blocks.OAK_SIGN.defaultBlockState());
        sign.setLevel(client.level);
        sign.setText(new SignText().setMessage(0, Component.literal(text)), true);
        client.gui.setScreen(new SignEditScreen(sign, true, false));
    }

    private static final class PreviewScreen extends Screen {
        private final ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);

        private PreviewScreen() {
            super(Component.literal("Color Emoji"));
            tool.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky 😀 pickaxe 👍🏽"));
            require(tool.getHoverName().getString().equals("Lucky 😀 pickaxe 👍🏽"), "Item names keep Unicode");
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF141414);
            graphics.text(font, "Color Emoji", 24, 24, 0xFFFFFFFF);
            graphics.text(font, Component.literal(CHAT).withStyle(ChatFormatting.GRAY), 24, 48, 0xFFFFFFFF);
            graphics.text(font, Component.literal("Red bold 😀 🔥 and italic 💀")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.ITALIC), 24, 72, 0xFFFFFFFF);
            graphics.text(font, "Text symbols: ❤ ✔ ★   Emoji: ❤️ ✔️ ⭐", 24, 96, 0xFFAAAAAA);
            graphics.item(tool, 24, 128);
            graphics.setTooltipForNextFrame(font, tool, 48, 128);
        }
    }

    private static int yellowPixels(Path screenshot) {
        try (NativeImage image = NativeImage.read(Files.newInputStream(screenshot))) {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    if (ARGB.red(pixel) > 200 && ARGB.green(pixel) > 150 && ARGB.blue(pixel) < 90) count++;
                }
            }
            return count;
        } catch (IOException exception) {
            throw new AssertionError("Could not read " + screenshot, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
