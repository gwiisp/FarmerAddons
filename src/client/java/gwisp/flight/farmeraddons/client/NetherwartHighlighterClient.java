package gwisp.flight.farmeraddons.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class NetherwartHighlighterClient implements ClientModInitializer {

    private static final Set<BlockPos> highlightedWarts = Collections.synchronizedSet(new HashSet<>());
    private static KeyBinding toggleKey;
    private static KeyBinding flipAxisKey;
    private static boolean enabled = true;
    private static boolean highlightAlongZ = true;
    private int tickCounter = 0;
    /*
    private int titleCounter = 0;
    private boolean lastHadNetherWart = true;
     */

    @Override
    public void onInitializeClient() {
        DeveloperBadgeRenderer.register();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmeraddons.toggle_highlight",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "category.farmeraddons"
        ));

        flipAxisKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmeraddons.flip_axis",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.farmeraddons"
        ));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal("§6[FarmerAddons] Netherwart highlight "
                        + (enabled ? "§aenabled" : "§cdisabled")), true);
            }

            while (flipAxisKey.wasPressed()) {
                highlightAlongZ = !highlightAlongZ;
                client.player.sendMessage(Text.literal("§6[FarmerAddons] Rows run along "
                        + (highlightAlongZ ? "§aZ axis (north-south)" : "§aX axis (east-west)")), true);
            }

            if (!enabled) return;

            tickCounter = (tickCounter + 1) % 20;
            if (tickCounter == 0) {
                scanNearbyForRows(client);
            }

           /* if (client.player.getMainHandStack().getItem() instanceof HoeItem) {
                boolean hasNetherWart = client.player.getInventory().contains(Items.NETHER_WART.getDefaultStack());

                if (!hasNetherWart && lastHadNetherWart && titleCounter == 0) {
                    client.inGameHud.setTitle(Text.literal("§cNo Netherwart"));
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1.0F));
                    titleCounter = 10;
                }

                lastHadNetherWart = hasNetherWart;
            }

            if (titleCounter > 0) {
                titleCounter--;
                if (titleCounter == 0) client.inGameHud.setTitle(null);
            }

            */
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!enabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            synchronized (highlightedWarts) {
                for (BlockPos pos : highlightedWarts) {
                    matrices.push();

                    double camX = context.camera().getPos().x;
                    double camY = context.camera().getPos().y;
                    double camZ = context.camera().getPos().z;
                    matrices.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);

                    DebugRenderer.drawBox(
                            matrices,
                            consumers,
                            0, 0, 0,
                            1.0, 1.0, 1.0,
                            1.0F, 0.0F, 0.0F, 0.5F
                    );

                    matrices.pop();
                }
            }
        });
    }

    private void scanNearbyForRows(MinecraftClient client) {
        BlockPos playerPos = client.player.getBlockPos();
        int range = 100;
        int rowWidth = 7;
        highlightedWarts.clear();

        Map<Integer, List<BlockPos>> rows = new HashMap<>();

        for (int y = playerPos.getY() - 1; y <= playerPos.getY() + 1; y++) {
            for (int x = playerPos.getX() - range; x <= playerPos.getX() + range; x++) {
                for (int z = playerPos.getZ() - range; z <= playerPos.getZ() + range; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isValidBlock(client, pos)) {
                        int rowKey = highlightAlongZ ? x : z;
                        rows.computeIfAbsent(rowKey, k -> new ArrayList<>()).add(pos);
                    }
                }
            }
        }

        for (List<BlockPos> row : rows.values()) {
            if (row.size() < rowWidth) continue;

            if (highlightAlongZ) {
                row.sort(Comparator.comparingInt(BlockPos::getZ));
            } else {
                row.sort(Comparator.comparingInt(BlockPos::getX));
            }

            List<BlockPos> currentSegment = new ArrayList<>();
            int expectedPos = -1;

            for (BlockPos pos : row) {
                int currentPos = highlightAlongZ ? pos.getZ() : pos.getX();

                if (expectedPos == -1 || currentPos == expectedPos + 1) {
                    currentSegment.add(pos);
                    expectedPos = currentPos;
                } else {
                    if (currentSegment.size() >= rowWidth) {
                        int centerIndex = currentSegment.size() / 2;
                        highlightedWarts.add(currentSegment.get(centerIndex));
                    }
                    currentSegment = new ArrayList<>();
                    currentSegment.add(pos);
                    expectedPos = currentPos;
                }
            }

            if (currentSegment.size() >= rowWidth) {
                int centerIndex = currentSegment.size() / 2;
                highlightedWarts.add(currentSegment.get(centerIndex));
            }
        }
    }

    private boolean isValidBlock(MinecraftClient client, BlockPos pos) {
        var state = client.world.getBlockState(pos);
        if (!state.isOf(Blocks.NETHER_WART)) return false;
        if (!state.getProperties().contains(NetherWartBlock.AGE)) return false;
        int age = state.get(NetherWartBlock.AGE);
        if (age < 3) return false;
        return client.world.getBlockState(pos.down()).isOf(Blocks.SOUL_SAND);
    }
}