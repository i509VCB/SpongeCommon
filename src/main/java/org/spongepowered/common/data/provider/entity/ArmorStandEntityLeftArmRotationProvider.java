package org.spongepowered.common.data.provider.entity;

import net.minecraft.entity.item.ArmorStandEntity;
import org.spongepowered.api.data.Keys;
import org.spongepowered.common.data.provider.GenericMutableDataProvider;
import org.spongepowered.common.mixin.accessor.entity.item.ArmorStandEntityAccessor;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.math.vector.Vector3d;

import java.util.Optional;

public class ArmorStandEntityLeftArmRotationProvider extends GenericMutableDataProvider<ArmorStandEntity, Vector3d> {

    public ArmorStandEntityLeftArmRotationProvider() {
        super(Keys.LEFT_ARM_ROTATION);
    }

    @Override
    protected Optional<Vector3d> getFrom(ArmorStandEntity dataHolder) {
        return Optional.of(VecHelper.toVector3d(((ArmorStandEntityAccessor) dataHolder).accessor$getLeftArmRotation()));
    }

    @Override
    protected boolean set(ArmorStandEntity dataHolder, Vector3d value) {
        dataHolder.setLeftArmRotation(VecHelper.toRotation(value));
        return true;
    }
}
