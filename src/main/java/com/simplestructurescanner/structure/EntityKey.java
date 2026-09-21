package com.simplestructurescanner.structure;

import javax.annotation.Nonnull;

import net.minecraft.util.ResourceLocation;


public final class EntityKey {

    private final ResourceLocation entityId;
    private final boolean spawner;

    public EntityKey(@Nonnull ResourceLocation entityId, boolean spawner) {
        this.entityId = entityId;
        this.spawner = spawner;
    }

    public ResourceLocation getEntityId() {
        return entityId;
    }

    public boolean isSpawner() {
        return spawner;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        EntityKey that = (EntityKey) obj;
        return spawner == that.spawner && entityId.equals(that.entityId);
    }

    @Override
    public int hashCode() {
        int result = entityId.hashCode();
        return 31 * result + Boolean.hashCode(spawner);
    }
}