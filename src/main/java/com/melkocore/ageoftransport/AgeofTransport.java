package com.melkocore.ageoftransport;

import com.melkocore.ageoftransport.item.ModItems;
import com.melkocore.ageoftransport.villager.VillagerEventHandler;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import com.melkocore.ageoftransport.villager.SubLevelJobSiteTickHandler;
import net.minecraft.server.level.ServerLevel;

import com.melkocore.ageoftransport.sublevel.binding.SubLevelCleanupObserver;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingRegistry;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import com.melkocore.ageoftransport.vehicle.VehicleNavigator;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AgeofTransport.MODID)
public class AgeofTransport {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "ageoftransport";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();


    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public AgeofTransport(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        ModItems.register(modEventBus);
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.

        NeoForge.EVENT_BUS.register(VillagerEventHandler.class);
        NeoForge.EVENT_BUS.register(DebugCommands.class);
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, SubLevelJobSiteTickHandler::onVillagerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(serverLevel);
                if (container != null) {
                    container.addObserver(new SubLevelCleanupObserver(serverLevel));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent event) -> {
            com.melkocore.ageoftransport.sublevel.binding.ServerLevelContext.setServer(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager)) return;
            if (villager.level().isClientSide()) return;

            ServerLevel level = (ServerLevel) villager.level();
            SubLevelBindingRegistry registry = SubLevelBindingRegistry.get(level);
            if (!registry.hasBinding(villager.getUUID())) return;

            registry.getLastKnownGlobalPos(villager.getUUID()).ifPresent(pos -> {
                try {
                    // Solo release si el POI existe y tiene tickets
                    if (level.getPoiManager().existsAtPosition(
                            net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN, pos)) {
                        level.getPoiManager().release(pos);
                    }
                    level.getPoiManager().remove(pos);
                } catch (Exception ignored) {}
            });

            registry.unbind(villager.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            var server = event.getServer();
            var level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (level == null) return;

            var toRemove = new java.util.ArrayList<VehicleNavigator>();

            for (VehicleNavigator nav : VehicleNavigator.ACTIVE_NAVIGATORS) {
                if (!nav.getController().isValid()) {
                    toRemove.add(nav);
                    continue;
                }
                nav.tick(level);
            }
            VehicleNavigator.ACTIVE_NAVIGATORS.removeAll(toRemove);
        });

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Dentro del constructor o del mtodo init del mod:

    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.CONO);
        }

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
