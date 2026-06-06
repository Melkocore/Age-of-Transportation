package com.melkocore.ageoftransport.villager;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class SableBarrelJobSiteGoal extends Goal {

    private final Villager villager;
    private final Level level;
    private Vec3 targetGlobalPos = null;
    private static final double SEARCH_RADIUS = 48.0;
    private static final double CLAIM_RADIUS_SQ = 2.5 * 2.5;

    public SableBarrelJobSiteGoal(Villager villager) {
        this.villager = villager;
        this.level = villager.level();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (villager.getVillagerData().getProfession() != VillagerProfession.NONE) return false;
        targetGlobalPos = findBarrelInContraption();
        return targetGlobalPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetGlobalPos != null;
    }

    @Override
    public void tick() {
        if (targetGlobalPos == null) return;

        double distSq = SableCompanion.INSTANCE.distanceSquaredWithSubLevels(
                level, villager.position(), targetGlobalPos);

        if (distSq > CLAIM_RADIUS_SQ) {
            villager.getNavigation().moveTo(
                    targetGlobalPos.x, targetGlobalPos.y, targetGlobalPos.z, 1.0);
        } else {
            villager.getNavigation().stop();
            // Aldeano llegó al barril en la contraption
            if (!level.isClientSide() && level.getServer() != null) {
                level.getServer().sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "[AOT] Aldeano alcanzó el barril en contraption: " + targetGlobalPos));
            }
            targetGlobalPos = null;
        }
    }

    @Override
    public void stop() {
        targetGlobalPos = null;
        villager.getNavigation().stop();
    }

    private Vec3 findBarrelInContraption() {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return null;

        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : container.getAllSubLevels()) {
            dev.ryanhcode.sable.companion.math.BoundingBox3dc globalBounds = subLevel.boundingBox();
            if (globalBounds == null) continue;

            // Verificar si el sub-level está dentro del radio de búsqueda
            Vec3 villagerPos = villager.position();
            net.minecraft.world.phys.AABB searchAABB = new net.minecraft.world.phys.AABB(
                    villagerPos.x - SEARCH_RADIUS, villagerPos.y - SEARCH_RADIUS, villagerPos.z - SEARCH_RADIUS,
                    villagerPos.x + SEARCH_RADIUS, villagerPos.y + SEARCH_RADIUS, villagerPos.z + SEARCH_RADIUS);
            if (!globalBounds.intersects(searchAABB)) continue;
            // Iterar bloques dentro del bounding box local del plot
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = subLevel.getPlot();
            dev.ryanhcode.sable.companion.math.BoundingBox3ic localBounds = plot.getBoundingBox();
            if (localBounds == null) continue;
            net.minecraft.world.level.Level subLevelLevel = subLevel.getLevel();

            for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
                for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                    for (int z = localBounds.minZ(); z <= localBounds.maxZ(); z++) {
                        BlockPos localPos = new BlockPos(x, y, z);
                        if (!subLevelLevel.getBlockState(localPos).is(Blocks.BARREL)) continue;

                        // Transformar posición local a global via Sable Companion
                        dev.ryanhcode.sable.companion.SubLevelAccess access =
                                SableCompanion.INSTANCE.getContaining(level, localPos);
                        if (access == null) continue;

                        Vec3 globalPos = access.logicalPose()
                                .transformPosition(Vec3.atCenterOf(localPos));

                        double distSq = SableCompanion.INSTANCE.distanceSquaredWithSubLevels(
                                level, villagerPos, globalPos);
                        if (distSq <= SEARCH_RADIUS * SEARCH_RADIUS) {
                            return globalPos;
                        }
                    }
                }
            }
        }
        return null;
    }
}