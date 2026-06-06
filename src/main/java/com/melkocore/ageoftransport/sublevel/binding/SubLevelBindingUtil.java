package com.melkocore.ageoftransport.sublevel.binding;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public final class SubLevelBindingUtil {

    private SubLevelBindingUtil() {}

    /**
     * Resuelve la posición global actual del binding del mob.
     * Devuelve null si el mob no tiene binding o el sub-level no existe.
     */
    @Nullable
    public static Vec3 resolveGlobalPos(LivingEntity mob, ServerLevel level) {
        return SubLevelBindingRegistry.get(level)
                .resolveGlobalPos(mob.getUUID(), level);
    }

    /**
     * Verifica si el binding del mob sigue siendo válido.
     */
    public static boolean isBindingValid(LivingEntity mob, ServerLevel level) {
        return SubLevelBindingRegistry.get(level)
                .isValid(mob.getUUID(), level);
    }

    /**
     * Busca el bloque más cercano que cumpla el predicado en todos los
     * sub-levels dentro del radio. Devuelve un SubLevelBinding listo para
     * registrar, o null si no encuentra nada.
     */
    @Nullable
    public static SubLevelBinding findBindableBlock(
            LivingEntity mob,
            ServerLevel level,
            BindingRole role,
            Predicate<BlockState> blockFilter,
            double searchRadius) {

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        SubLevelBindingRegistry registry = SubLevelBindingRegistry.get(level);

        Vec3 mobPos = mob.position();
        double bestDistSq = searchRadius * searchRadius;
        SubLevelBinding bestBinding = null;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            AABB searchAABB = new AABB(
                    mobPos.x - searchRadius, mobPos.y - searchRadius, mobPos.z - searchRadius,
                    mobPos.x + searchRadius, mobPos.y + searchRadius, mobPos.z + searchRadius);
            if (!subLevel.boundingBox().intersects(searchAABB)) continue;

            BoundingBox3ic localBounds = subLevel.getPlot().getBoundingBox();
            if (localBounds == null) continue;

            net.minecraft.world.level.Level subLevelLevel = subLevel.getLevel();

            for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
                for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                    for (int z = localBounds.minZ(); z <= localBounds.maxZ(); z++) {
                        BlockPos localPos = new BlockPos(x, y, z);

                        if (!blockFilter.test(subLevelLevel.getBlockState(localPos)))
                            continue;

                        // Filtrar bloques ya ocupados
                        if (registry.isBlockOccupied(subLevel.getUniqueId(), localPos))
                            continue;

                        dev.ryanhcode.sable.companion.SubLevelAccess access =
                                SableCompanion.INSTANCE.getContaining(level, localPos);
                        if (access == null) continue;

                        Vec3 globalPos = access.logicalPose()
                                .transformPosition(Vec3.atCenterOf(localPos));

                        double distSq = SableCompanion.INSTANCE
                                .distanceSquaredWithSubLevels(level, mobPos, globalPos);

                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestBinding = new SubLevelBinding(
                                    subLevel.getUniqueId(),
                                    localPos.immutable(),
                                    role
                            );
                        }
                    }
                }
            }
        }
        return bestBinding;
    }

    /**
     * Verifica si el mob puede navegar hacia su binding actual.
     * Útil para decidir si iniciar pathfinding.
     */
    public static boolean canReach(LivingEntity mob, ServerLevel level, double maxDistance) {
        Vec3 globalPos = resolveGlobalPos(mob, level);
        if (globalPos == null) return false;

        double distSq = SableCompanion.INSTANCE.distanceSquaredWithSubLevels(
                level, mob.position(), globalPos);
        return distSq <= maxDistance * maxDistance;
    }
}