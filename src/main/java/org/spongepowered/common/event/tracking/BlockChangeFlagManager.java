/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.event.tracking;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.world.SpongeBlockChangeFlag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class BlockChangeFlagManager {

    private final Map<String, SpongeBlockChangeFlag> flags = new LinkedHashMap<>();
    private final Int2ObjectMap<SpongeBlockChangeFlag> maskedFlags = new Int2ObjectLinkedOpenHashMap<>(70);
    private static BlockChangeFlagManager INSTANCE = new BlockChangeFlagManager();
    private static final SpongeBlockChangeFlag PHYSICS_OBSERVER = new SpongeBlockChangeFlag("PHYSICS_OBSERVER", Constants.BlockChangeFlags.PHYSICS_OBSERVER);
    private static final SpongeBlockChangeFlag DEFAULT = new SpongeBlockChangeFlag("PHYSICS_OBSERVER", Constants.BlockChangeFlags.DEFAULT);

    public static BlockChangeFlagManager getInstance() {
        return BlockChangeFlagManager.INSTANCE;
    }

    public static SpongeBlockChangeFlag fromNativeInt(final int flag) {
        if (flag == Constants.BlockChangeFlags.DEFAULT) {
            return BlockChangeFlagManager.DEFAULT;
        }
        if (flag == 2) {
            return BlockChangeFlagManager.PHYSICS_OBSERVER;
        }
        final BlockChangeFlagManager instance = BlockChangeFlagManager.getInstance();
        final SpongeBlockChangeFlag spongeBlockChangeFlag = instance.maskedFlags.get(flag);
        if (spongeBlockChangeFlag != null) {
            return spongeBlockChangeFlag;
        } else {
            final SpongeBlockChangeFlag newFlag = new SpongeBlockChangeFlag(BlockChangeFlagManager.getFlagName(flag)
                .toString(), flag);
            instance.register(newFlag);
            return newFlag;
        }
    }

    public static SpongeBlockChangeFlag andNotifyClients(final BlockChangeFlag flag) {
        final int rawFlag = ((SpongeBlockChangeFlag) flag).getRawFlag();
        if ((rawFlag & Constants.BlockChangeFlags.NOTIFY_CLIENTS) != 0){
            return (SpongeBlockChangeFlag) flag; // We don't need to rerun the flag
        }
        return BlockChangeFlagManager.fromNativeInt(rawFlag & ~Constants.BlockChangeFlags.NOTIFY_CLIENTS);
    }

    private BlockChangeFlagManager() {
        this.registerDefaults();
    }

    public void registerDefaults() {
        // A documentation note:
        /*
        Due to the way that Mojang handles block physics, there are four flags inverted here:
        1) BlockChangeFlags.IGNORE_RENDER - Prevents the block from being re-rendered in the client world
        2) BlockChangeFlags.FORCE_RE_RENDER - Requires the block to be re-rendered on the main thread for a client, as long as IGNORE_RENDER is clear
        3) BlockChangeFlags.OBSERVER - Prevents observer blocks from being told about block changes, separate from neighbor notifications
        4) BlockChangeFlags.PHYSICS - Sponge specific, prevents block.onAdd logic being called

        The other two flags:
        1) BlockChangeFlags.NEIGHBOR - Notify neighbor blocks
        2) BlockChangeFlags.NOTIFY_CLIENTS - Notify clients of block change

        are always true based. If they are set, they will process those two flags.
        This is why there are so many permutations.
         */

        // devise all permutations
        for (int i = 0; i < 128; i++) { // 64 because we get to the 6th bit of possible combinations
            final StringJoiner builder = BlockChangeFlagManager.getFlagName(i);
            if (Constants.BlockChangeFlags.NONE == i) {
                this.register(new SpongeBlockChangeFlag("NONE".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.ALL == i) {
                this.register(new SpongeBlockChangeFlag("ALL".toLowerCase(Locale.ENGLISH), i));
                this.register(new SpongeBlockChangeFlag("NEIGHBOR_PHYSICS_OBSERVER".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.NEIGHBOR == i) {
                this.register(new SpongeBlockChangeFlag("NEIGHBOR".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.PHYSICS == i) {
                this.register(new SpongeBlockChangeFlag("PHYSICS".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.OBSERVER == i) {
                this.register(new SpongeBlockChangeFlag("OBSERVER".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.NEIGHBOR_PHYSICS == i) {
                this.register(new SpongeBlockChangeFlag("NEIGHBOR_PHYSICS".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.NEIGHBOR_OBSERVER == i) {
                this.register(new SpongeBlockChangeFlag("NEIGHBOR_OBSERVER".toLowerCase(Locale.ENGLISH), i));
            } else if (Constants.BlockChangeFlags.PHYSICS_OBSERVER == i) {
                this.register(new SpongeBlockChangeFlag("PHYSICS_OBSERVER".toLowerCase(Locale.ENGLISH), i));
            } else {
                this.register(new SpongeBlockChangeFlag(builder.toString().toLowerCase(Locale.ENGLISH), i));
            }
        }

    }

    @NonNull
    private static StringJoiner getFlagName(int i) {
        final StringJoiner builder = new StringJoiner("|");
        if ((i & Constants.BlockChangeFlags.NEIGHBOR_MASK) != 0) {
            builder.add(Flag.NOTIFY_NEIGHBOR.name);
        }
        if ((i & Constants.BlockChangeFlags.NOTIFY_CLIENTS) != 0) {
            // We don't want to confuse that there are going to be multiple flags
            // but with slight differences because of the notify flag
            builder.add(Flag.NOTIFY_CLIENTS.name);
        }
        if ((i & Constants.BlockChangeFlags.IGNORE_RENDER) != 0) {
            // We don't want to confuse that there are going to be multiple flags
            // but with a slight difference because of the ignore render flag
            builder.add(Flag.IGNORE_RENDER.name);
        }
        if ((i & Constants.BlockChangeFlags.FORCE_RE_RENDER) != 0) {
            // We don't want to confuse that there are going to be multiple flags
            // but with a slight difference due to the client only flag.
            builder.add(Flag.FORCE_RE_RENDER.name);
        }
        if ((i & Constants.BlockChangeFlags.DENY_NEIGHBOR_SHAPE_UPDATE) == 0) {
            builder.add(Flag.DENY_NEIGHBOR_SHAPE_UPDATE.name);
        }
        if ((i & Constants.BlockChangeFlags.PHYSICS_MASK) == 0) {
            builder.add(Flag.IGNORE_PHYSICS.name);
        }
        return builder;
    }

    private void register(final SpongeBlockChangeFlag flag) {
        this.maskedFlags.put(flag.getRawFlag(), flag);
        this.flags.put(flag.getName(), flag);
    }

    public Collection<SpongeBlockChangeFlag> getValues() {
        return Collections.unmodifiableCollection(this.flags.values());
    }

    public static final class Flag {

        public static final Flag NOTIFY_NEIGHBOR = new Flag("NEIGHBOR", Constants.BlockChangeFlags.NEIGHBOR_MASK);
        public static final Flag NOTIFY_CLIENTS = new Flag("NOTIFY_CLIENTS", Constants.BlockChangeFlags.NOTIFY_CLIENTS);
        public static final Flag IGNORE_RENDER = new Flag("IGNORE_RENDER", Constants.BlockChangeFlags.IGNORE_RENDER);
        public static final Flag FORCE_RE_RENDER = new Flag("FORCE_RE_RENDER", Constants.BlockChangeFlags.FORCE_RE_RENDER);
        public static final Flag DENY_NEIGHBOR_SHAPE_UPDATE = new Flag("NEIGHBOR_SHAPE_UPDATE", Constants.BlockChangeFlags.DENY_NEIGHBOR_SHAPE_UPDATE);
        public static final Flag IGNORE_PHYSICS = new Flag("PHYSICS", Constants.BlockChangeFlags.PHYSICS_MASK);

        private static final ImmutableList<Flag> flags = ImmutableList.of(Flag.NOTIFY_NEIGHBOR, Flag.NOTIFY_CLIENTS, Flag.IGNORE_RENDER, Flag.FORCE_RE_RENDER, Flag.DENY_NEIGHBOR_SHAPE_UPDATE, Flag.IGNORE_PHYSICS);

        private final String name;
        private final int mask;

        public static Collection<Flag> values() {
            return Flag.flags;
        }

        private Flag(final String name, final int mask) {
            this.name = name;
            this.mask = mask;
        }
    }

    public static final class Factory implements BlockChangeFlag.Factory {

        @Nullable private BlockChangeFlag none;

        @Override
        public BlockChangeFlag empty() {
            if (this.none == null) {
                this.none = BlockChangeFlagManager.getInstance().maskedFlags.get(0);
            }
            return this.none;
        }
        public Factory() {}
    }
}
