package com.melkocore.ageoftransport.sublevel.binding;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SubLevelBindingRegistry extends SavedData {

    private static final String DATA_NAME = "aot_sublevel_bindings";
    private static final String TAG_BINDINGS = "Bindings";
    private static final String TAG_MOB_UUID = "MobUUID";
    private static final String TAG_BINDING = "Binding";

    private final Map<UUID, SubLevelBinding> bindings = new HashMap<>();
    private final Map<UUID, BlockPos> lastKnownGlobalPos = new HashMap<>();
    private final Set<UUID> professionAssigned = new HashSet<>();

    // --- Acceso al registry ---

    public static SubLevelBindingRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SubLevelBindingRegistry::new,
                        SubLevelBindingRegistry::load
                ),
                DATA_NAME
        );
    }

    // --- Operaciones principales ---

    public void bind(UUID mobUUID, SubLevelBinding binding) {
        // Si el mob ya tenía un binding anterior, liberar ese bloque
        SubLevelBinding old = bindings.get(mobUUID);
        if (old != null) {
            occupiedBlocks.remove(occupancyKey(old.subLevelId(), old.localPos()));
        }
        bindings.put(mobUUID, binding);
        occupiedBlocks.put(occupancyKey(binding.subLevelId(), binding.localPos()), mobUUID);
        setDirty();
    }

    public void unbind(UUID mobUUID) {
        SubLevelBinding binding = bindings.remove(mobUUID);
        if (binding != null) {
            occupiedBlocks.remove(occupancyKey(binding.subLevelId(), binding.localPos()));
        }
        lastKnownGlobalPos.remove(mobUUID);
        professionAssigned.remove(mobUUID);
        setDirty();
    }

    @Nullable
    public SubLevelBinding getBinding(UUID mobUUID) {
        return bindings.get(mobUUID);
    }

    public boolean hasBinding(UUID mobUUID) {
        return bindings.containsKey(mobUUID);
    }

    public Map<UUID, SubLevelBinding> getAllBindings() {
        return Collections.unmodifiableMap(bindings);
    }

    // --- Última posición global conocida ---

    public void updateLastKnownGlobalPos(UUID mobUUID, BlockPos pos) {
        lastKnownGlobalPos.put(mobUUID, pos);
    }

    public Optional<BlockPos> getLastKnownGlobalPos(UUID mobUUID) {
        return Optional.ofNullable(lastKnownGlobalPos.get(mobUUID));
    }

    // --- Flag de profesión asignada ---

    public boolean isProfessionAssigned(UUID mobUUID) {
        return professionAssigned.contains(mobUUID);
    }

    public void markProfessionAssigned(UUID mobUUID) {
        professionAssigned.add(mobUUID);
    }

    // --- Resolución de posición global ---

    @Nullable
    public Vec3 resolveGlobalPos(UUID mobUUID, ServerLevel level) {
        SubLevelBinding binding = bindings.get(mobUUID);
        if (binding == null) return null;

        SubLevel subLevel = getSubLevel(binding.subLevelId(), level);
        if (subLevel == null) return null;

        dev.ryanhcode.sable.companion.SubLevelAccess access =
                SableCompanion.INSTANCE.getContaining(level, binding.localPos());
        if (access == null) return null;

        return access.logicalPose().transformPosition(Vec3.atCenterOf(binding.localPos()));
    }

    // --- Validación ---

    public boolean isValid(UUID mobUUID, ServerLevel level) {
        SubLevelBinding binding = bindings.get(mobUUID);
        if (binding == null) return false;
        return getSubLevel(binding.subLevelId(), level) != null;
    }

    // --- Utilidades internas ---

    @Nullable
    private SubLevel getSubLevel(UUID subLevelId, ServerLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        return container.getSubLevel(subLevelId);
    }

    // --- Serialización ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, SubLevelBinding> entry : bindings.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_MOB_UUID, entry.getKey());
            entryTag.put(TAG_BINDING, entry.getValue().toNBT());
            list.add(entryTag);
        }
        tag.put(TAG_BINDINGS, list);
        return tag;
    }

    public static SubLevelBindingRegistry load(CompoundTag tag,
                                               HolderLookup.Provider registries) {
        SubLevelBindingRegistry registry = new SubLevelBindingRegistry();
        ListTag list = tag.getList(TAG_BINDINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID mobUUID = entryTag.getUUID(TAG_MOB_UUID);
            SubLevelBinding binding = SubLevelBinding.fromNBT(
                    entryTag.getCompound(TAG_BINDING));
            registry.bindings.put(mobUUID, binding);
        }
        return registry;
    }
    // Mapa inverso: subLevelId + localPos → mobUUID
    private final Map<String, UUID> occupiedBlocks = new HashMap<>();

    // Clave compuesta para el mapa inverso
    private static String occupancyKey(UUID subLevelId, BlockPos localPos) {
        return subLevelId + ":" + localPos.getX() + "," + localPos.getY() + "," + localPos.getZ();
    }

    public boolean isBlockOccupied(UUID subLevelId, BlockPos localPos) {
        return occupiedBlocks.containsKey(occupancyKey(subLevelId, localPos));
    }

    public UUID getBlockOccupant(UUID subLevelId, BlockPos localPos) {
        return occupiedBlocks.get(occupancyKey(subLevelId, localPos));
    }
}