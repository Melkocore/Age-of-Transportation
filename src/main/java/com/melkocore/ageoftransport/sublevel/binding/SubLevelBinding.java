package com.melkocore.ageoftransport.sublevel.binding;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record SubLevelBinding(
        UUID subLevelId,
        BlockPos localPos,
        BindingRole role
) {
    public static final String TAG_SUB_LEVEL_ID = "SubLevelId";
    public static final String TAG_LOCAL_POS = "LocalPos";
    public static final String TAG_ROLE = "Role";

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_SUB_LEVEL_ID, subLevelId);
        tag.putInt(TAG_LOCAL_POS + "X", localPos.getX());
        tag.putInt(TAG_LOCAL_POS + "Y", localPos.getY());
        tag.putInt(TAG_LOCAL_POS + "Z", localPos.getZ());
        tag.putString(TAG_ROLE, role.name());
        return tag;
    }

    public static SubLevelBinding fromNBT(CompoundTag tag) {
        UUID subLevelId = tag.getUUID(TAG_SUB_LEVEL_ID);
        BlockPos localPos = new BlockPos(
                tag.getInt(TAG_LOCAL_POS + "X"),
                tag.getInt(TAG_LOCAL_POS + "Y"),
                tag.getInt(TAG_LOCAL_POS + "Z")
        );
        BindingRole role = BindingRole.valueOf(tag.getString(TAG_ROLE));
        return new SubLevelBinding(subLevelId, localPos, role);
    }
}