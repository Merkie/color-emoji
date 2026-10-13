package com.merkie.coloremoji;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/** Readable documentation screenshots captured directly from Minecraft. */
final class Showcase {
    static void capture(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos ground = context.computeOnClient(client -> client.player.blockPosition().below());
        BlockPos signPos = ground.offset(0, 2, -2);
        world.getServer().runCommand("time set noon");
        world.getServer().runCommand("weather clear");
        world.getServer().runOnServer(server -> {
            var level = server.overworld();
            for (int x = -9; x <= 9; x++) {
                for (int z = -10; z <= 7; z++) {
                    level.setBlockAndUpdate(ground.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    if (x >= -1 && x <= 1 && z >= -1) {
                        level.setBlockAndUpdate(ground.offset(x, 0, z), Blocks.DIRT_PATH.defaultBlockState());
                    } else if ((x + z) % 3 == 0) {
                        level.setBlockAndUpdate(ground.offset(x, 1, z),
                                (x % 2 == 0 ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState());
                    }
                }
            }
            for (int x : new int[] {-5, 5}) {
                for (int z : new int[] {-7, -3}) {
                    for (int y = 1; y <= 4; y++) {
                        level.setBlockAndUpdate(ground.offset(x, y, z), Blocks.OAK_LOG.defaultBlockState());
                    }
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            for (int y = 4; y <= 6; y++) {
                                if (Math.abs(dx) + Math.abs(dz) + y <= 8) {
                                    level.setBlockAndUpdate(ground.offset(x + dx, y, z + dz), Blocks.OAK_LEAVES.defaultBlockState());
                                }
                            }
                        }
                    }
                }
            }
            level.setBlockAndUpdate(signPos.below(), Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(signPos,
                    Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
            SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(signPos);
            sign.setText(new SignText().setColor(DyeColor.BLACK)
                    .setMessage(0, Component.literal("Welcome!"))
                    .setMessage(1, Component.literal("🌻 🌸 🐝"))
                    .setMessage(2, Component.literal("🌈 ❤️ ✨"))
                    .setMessage(3, Component.literal("Made with love")), true);
            level.sendBlockUpdated(signPos, sign.getBlockState(), sign.getBlockState(), 3);
            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky Pickaxe 🍀 💎"));
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(9, pickaxe);
            player.containerMenu.broadcastChanges();
        });
        context.waitFor(client -> client.level.getBlockEntity(signPos) instanceof SignBlockEntity sign
                && sign.getFrontText().getMessage(0, false).getString().equals("Welcome!"));
        context.runOnClient(client -> {
            client.getWindow().setWindowed(1280, 720);
            client.options.showAutosaveIndicator().set(false);
            client.options.guiScale().set(3);
            client.options.fov().set(35);
            client.options.bobView().set(false);
            client.options.menuBackgroundBlurriness().set(0);
            client.player.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.8, 180, -8);
            client.gui.hud.getChat().clearMessages(true);
            client.gui.hud.toggle();
        });
        context.waitTicks(20);
        context.runOnClient(client -> client.resizeGui());
        context.takeScreenshot(TestScreenshotOptions.of("showcase-sign").disableCounterPrefix());
        context.runOnClient(client -> {
            client.gui.hud.toggle();
            client.options.fov().set(60);
            client.player.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 2.5, 170, 5);
            var chat = client.gui.hud.getChat();
            chat.addClientSystemMessage(Component.literal("<Mia> hey everyone 👋 🌻"));
            chat.addClientSystemMessage(Component.literal("<Alex> found diamonds! 💎 ⛏️"));
            chat.addClientSystemMessage(Component.literal("<Mia> love this garden 🌸 🐝 ❤️"));
            chat.addClientSystemMessage(Component.literal("<Alex> nice build! 🏡 ✨").withStyle(ChatFormatting.GRAY));
            client.gui.setScreen(new ChatScreen("", false));
        });
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of("showcase-chat").disableCounterPrefix());
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(true);
            client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
            client.gui.setScreen(new InventoryScreen(client.player));
        });
        // Inventory slot 9 is the first slot in the main inventory grid.
        double[] cursor = context.computeOnClient(client -> {
            var window = client.getWindow();
            double x = (window.getGuiScaledWidth() - 176) / 2 + 16;
            double y = (window.getGuiScaledHeight() - 166) / 2 + 84 + 8;
            return new double[] {x * window.getScreenWidth() / window.getGuiScaledWidth(),
                    y * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of("showcase-item").disableCounterPrefix());
        context.runOnClient(client -> client.gui.setScreen(null));
    }
}
