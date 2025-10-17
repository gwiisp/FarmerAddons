package gwisp.flight.farmeraddons.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

public class DeveloperBadgeRenderer {

    private static final Set<String> DEVELOPERS = new HashSet<>();

    static {
        DEVELOPERS.add("gwisp");
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;

            for (PlayerEntity player : client.world.getPlayers()) {
                if (isDeveloper(player.getName().getString())) {
                    if (player.getCustomName() == null || !player.getCustomName().getString().contains("{}")) {
                        MutableText newName = Text.literal(player.getName().getString() + " §6{}");
                        player.setCustomName(newName);
                        player.setCustomNameVisible(true);
                    }
                }
            }
        });
    }

    private static boolean isDeveloper(String playerName) {
        return DEVELOPERS.contains(playerName);
    }

    public static void addDeveloper(String playerName) {
        DEVELOPERS.add(playerName);
    }

    public static void removeDeveloper(String playerName) {
        DEVELOPERS.remove(playerName);
    }
}