package com.melkocore.ageoftransport;

import com.melkocore.ageoftransport.sublevel.binding.BindingRole;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBinding;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingRegistry;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingUtil;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.arguments.FloatArgumentType;
import java.util.UUID;

public class DebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("aot_debug")
                // Bindear manualmente al mob más cercano con un barril en sub-level
                .then(Commands.literal("bind_nearest")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            Vec3 pos = ctx.getSource().getPosition();

                            // Buscar mob más cercano que no sea el jugador
                            LivingEntity target = level.getEntitiesOfClass(
                                    LivingEntity.class,
                                    new net.minecraft.world.phys.AABB(pos, pos).inflate(10),
                                    e -> {
                                        if (!(e instanceof net.minecraft.world.entity.npc.Villager v)) return false;
                                        if (v.getVillagerData().getProfession() !=
                                                net.minecraft.world.entity.npc.VillagerProfession.NONE) return false;
                                        ServerLevel lvl = ctx.getSource().getLevel();
                                        return !SubLevelBindingRegistry.get(lvl).hasBinding(v.getUUID());
                                    }
                            ).stream().findFirst().orElse(null);

                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("[AOT] No hay mob cercano"));
                                return 0;
                            }

                            // Buscar barril en sub-levels cercanos
                            SubLevelBinding binding = SubLevelBindingUtil.findBindableBlock(
                                    target, level, BindingRole.JOB_SITE,
                                    state -> state.is(Blocks.BARREL), 48.0);

                            if (binding == null) {
                                ctx.getSource().sendFailure(Component.literal("[AOT] No hay barril en sub-level cercano"));
                                return 0;
                            }

                            SubLevelBindingRegistry.get(level).bind(target.getUUID(), binding);
                            // Asignar profesión si es aldeano
                            if (target instanceof net.minecraft.world.entity.npc.Villager villager) {
                                // Asignar JOB_SITE en la memoria del Brain
                                net.minecraft.core.GlobalPos globalPos = net.minecraft.core.GlobalPos.of(
                                        level.dimension(),
                                        net.minecraft.core.BlockPos.containing(
                                                SubLevelBindingUtil.resolveGlobalPos(villager, level)));
                                villager.getBrain().setMemory(
                                        net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, globalPos);

                                // Asignar profesión basada en el bloque
                                // Para barril = Fisherman
                                villager.setVillagerData(villager.getVillagerData()
                                        .setProfession(net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN));
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AOT] Binding creado: mob=" + target.getUUID()
                                            + " subLevel=" + binding.subLevelId()
                                            + " localPos=" + binding.localPos()
                                            + " role=" + binding.role()), false);
                            return 1;
                        }))

                // Mostrar posición global actual del binding del mob más cercano
                .then(Commands.literal("resolve_nearest")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            Vec3 pos = ctx.getSource().getPosition();

                            LivingEntity target = level.getEntitiesOfClass(
                                    LivingEntity.class,
                                    new net.minecraft.world.phys.AABB(pos, pos).inflate(10),
                                    e -> !(e instanceof net.minecraft.world.entity.player.Player)
                            ).stream().findFirst().orElse(null);

                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("[AOT] No hay mob cercano"));
                                return 0;
                            }

                            Vec3 globalPos = SubLevelBindingUtil.resolveGlobalPos(target, level);
                            if (globalPos == null) {
                                ctx.getSource().sendFailure(Component.literal("[AOT] Mob sin binding o sub-level inválido"));
                                return 0;
                            }

                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AOT] Posición global actual: " + globalPos), false);
                            return 1;
                        }))

                // Limpiar binding del mob más cercano
                .then(Commands.literal("unbind_nearest")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            Vec3 pos = ctx.getSource().getPosition();

                            LivingEntity target = level.getEntitiesOfClass(
                                    LivingEntity.class,
                                    new net.minecraft.world.phys.AABB(pos, pos).inflate(10),
                                    e -> !(e instanceof net.minecraft.world.entity.player.Player)
                            ).stream().findFirst().orElse(null);

                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("[AOT] No hay mob cercano"));
                                return 0;
                            }

                            SubLevelBindingRegistry.get(level).unbind(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AOT] Binding removido"), false);
                            return 1;
                        }))
                .then(Commands.literal("test_steer")
                        .then(Commands.argument("heading", FloatArgumentType.floatArg(0, 360))
                                .executes(ctx -> {
                                    float heading = FloatArgumentType.getFloat(ctx, "heading");
                                    ServerLevel level = ctx.getSource().getLevel();

                                    // Buscar la contraption más cercana con steering wheel
                                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                                            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                                    if (container == null) {
                                        ctx.getSource().sendFailure(
                                                net.minecraft.network.chat.Component.literal("[AOT] No hay sub-levels"));
                                        return 0;
                                    }

                                    for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : container.getAllSubLevels()) {
                                        com.melkocore.ageoftransport.vehicle.SteeringWheelController controller =
                                                com.melkocore.ageoftransport.vehicle.VehicleControllerFactory
                                                        .createSteeringWheelController(subLevel.getUniqueId(), level);

                                        if (controller == null) continue;

                                        controller.setThrottle(1.0f);


                                        ctx.getSource().sendSuccess(() ->
                                                net.minecraft.network.chat.Component.literal(
                                                        "[AOT] Steering wheel encontrado. Heading actual: "
                                                                + controller.getCurrentHeading()
                                                                + " → target: " + heading), false);
                                        return 1;
                                    }

                                    ctx.getSource().sendFailure(
                                            net.minecraft.network.chat.Component.literal(
                                                    "[AOT] No se encontró steering wheel en ninguna contraption"));
                                    return 0;
                                })))
                .then(Commands.literal("test_pursue")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            net.minecraft.world.entity.player.Player player =
                                    ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendFailure(
                                        net.minecraft.network.chat.Component.literal(
                                                "[AOT] Solo funciona en juego"));
                                return 0;
                            }

                            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                                    .getContainer(level);
                            if (container == null) {
                                ctx.getSource().sendFailure(
                                        net.minecraft.network.chat.Component.literal(
                                                "[AOT] No hay sub-levels"));
                                return 0;
                            }

                            for (var subLevel : container.getAllSubLevels()) {
                                var controller = com.melkocore.ageoftransport.vehicle
                                        .VehicleControllerFactory
                                        .createSteeringWheelController(subLevel.getUniqueId(), level);
                                if (controller == null) continue;

                                var navigator = new com.melkocore.ageoftransport.vehicle
                                        .VehicleNavigator(controller);
                                navigator.setPursuitTarget((net.minecraft.world.entity.LivingEntity) player);

                                // Guardar el navigator para tickearlo — necesitamos un registro
                                // Por ahora lo tickeamos desde un scheduled task simple
                                final var nav = navigator;
                                com.melkocore.ageoftransport.vehicle.VehicleNavigator.ACTIVE_NAVIGATORS.add(navigator);

                                ctx.getSource().sendSuccess(() ->
                                                net.minecraft.network.chat.Component.literal(
                                                        "[AOT] Navigator activado — persiguiendo jugador por 10 segundos"),
                                        false);
                                return 1;
                            }

                            ctx.getSource().sendFailure(
                                    net.minecraft.network.chat.Component.literal(
                                            "[AOT] No se encontró contraption con steering wheel"));
                            return 0;
                        }))
        );
    }
}