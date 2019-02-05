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
package org.spongepowered.common.event.tracking.phase.packet;

import com.google.common.collect.Lists;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.CPacketEnchantItem;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.entity.AffectEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.item.inventory.container.ClickContainerEvent;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.interfaces.IMixinContainer;
import org.spongepowered.common.item.inventory.util.ContainerUtil;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.List;

public class EnchantItemPacketState extends BasicInventoryPacketState {

    @Override
    public void unwind(InventoryPacketContext context) {
        // TODO - Pre changes of merging PacketFunction into the phase states, enchantments did NOT have any processing....
        final EntityPlayerMP player = context.getPacketPlayer();

        // The server will disable the player's crafting after receiving a
        // client packet
        // that did not pass validation (server click item != packet click item)
        // The server then sends a SPacketConfirmTransaction and waits for a
        // CPacketConfirmTransaction to re-enable crafting confirming that the
        // client
        // acknowledged the denied transaction.
        // To detect when this happens, we turn off capturing so we can avoid
        // firing
        // invalid events.
        // See MixinNetHandlerPlayServer processClickWindow redirect for rest of
        // fix.
        // --bloodmc
        final IMixinContainer mixinContainer = ContainerUtil.toMixin(player.openContainer);
        if (!mixinContainer.capturingInventory()) {
            mixinContainer.getCapturedTransactions().clear();
            return;
        }

        // TODO clear this shit out of the context
        final CPacketEnchantItem packetIn = context.getPacket();
        final Transaction<ItemStackSnapshot> transaction = getCursorTransaction(context, player);

        final net.minecraft.inventory.Container openContainer = player.openContainer;
        final List<SlotTransaction> slotTransactions = mixinContainer.getCapturedTransactions();

        final int usedButton = packetIn.getButton();
        final List<Entity> capturedItems = new ArrayList<>();
        for (EntityItem entityItem : context.getCapturedItems()) {
            capturedItems.add(EntityUtil.fromNative(entityItem));
        }
        context.getCapturedItems().clear();

        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            Sponge.getCauseStackManager().pushCause(openContainer);
            Sponge.getCauseStackManager().pushCause(player);
            final ClickContainerEvent inventoryEvent;

            inventoryEvent = this.createInventoryEvent(player, ContainerUtil.fromNative(openContainer), transaction,
                        Lists.newArrayList(slotTransactions), capturedItems, usedButton, null);

            // Some mods may override container detectAndSendChanges method and prevent captures
            // If this happens and we captured no entities, avoid firing events
            if (mixinContainer.getCapturedTransactions().isEmpty() && capturedItems.isEmpty()) {
                mixinContainer.setCaptureInventory(false);
                return;
            }
            if (inventoryEvent != null) {
                // Don't fire inventory drop events when there are no entities
                if (inventoryEvent instanceof AffectEntityEvent && ((AffectEntityEvent) inventoryEvent).getEntities().isEmpty()) {
                    slotTransactions.clear();
                    mixinContainer.setCaptureInventory(false);
                    return;
                }

                // The client sends several packets all at once for drag events
                // we only care about the last one.
                // Therefore, we never add any 'fake' transactions, as the final
                // packet has everything we want.
                if (!(inventoryEvent instanceof ClickContainerEvent.Drag)) {
                    PacketPhaseUtil.validateCapturedTransactions(packetIn.getWindowId(), openContainer, inventoryEvent.getTransactions());
                }

                SpongeImpl.postEvent(inventoryEvent);

                // Handle cursor
                PacketPhaseUtil.handleCustomCursor(player, inventoryEvent.getCursorTransaction(), inventoryEvent.isCancelled());
                // Handle slots
                PacketPhaseUtil.handleSlotRestore(player, openContainer, inventoryEvent.getTransactions(), inventoryEvent.isCancelled());

                if (inventoryEvent.isCancelled()) {
                    if (inventoryEvent instanceof ClickContainerEvent.Drop) {
                        capturedItems.clear();
                    }
                } else if (inventoryEvent instanceof SpawnEntityEvent) {
                    processSpawnedEntities(player, (SpawnEntityEvent) inventoryEvent);
                } else if (!context.getCapturedEntitySupplier().isEmpty()) {
                    frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DROPPED_ITEM);
                    SpongeCommonEventFactory.callSpawnEntity(context.getCapturedEntities(), context);
                }

            }
        }
        finally {
            slotTransactions.clear();
            mixinContainer.setCaptureInventory(false);
        }
    }

}