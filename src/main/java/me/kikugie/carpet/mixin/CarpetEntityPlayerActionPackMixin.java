package me.kikugie.carpet.mixin;

import carpet.helpers.EntityPlayerActionPack;
import me.kikugie.carpet.access.ServerPlayerEntityAccess;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(targets = "carpet/helpers/EntityPlayerActionPack$ActionType$1")
public class CarpetEntityPlayerActionPackMixin {
    private BlockHitResult target;

    private static void mergeInventories(Inventory source, Inventory target) {
        ArrayList<Integer> sourceSlots = getPopulatedSlots(source);
        ArrayList<Integer> targetSlots = getAvailableSlots(target);
        if (sourceSlots.isEmpty() || targetSlots.isEmpty()) return;

        int sourceIndex = 0;
        int targetIndex = 0;

        while (sourceIndex < sourceSlots.size()) {
            if (targetIndex == targetSlots.size()) {
                targetIndex = 0;
                sourceIndex++;
                continue;
            }

            int currentSourceSlot = sourceSlots.get(sourceIndex);
            int currentTargetSlot = targetSlots.get(targetIndex);

            ItemStack sourceItemStack = source.getStack(currentSourceSlot);
            ItemStack targetItemStack = target.getStack(currentTargetSlot);

            if (targetItemStack.getCount() >= targetItemStack.getMaxCount()) {
                targetIndex++;
                continue;
            }

            if (targetItemStack.isEmpty()) {
                source.setStack(currentSourceSlot, ItemStack.EMPTY);
                target.setStack(currentTargetSlot, sourceItemStack);
                sourceIndex++;
                targetIndex = 0;
                continue;
            }

            if (ItemStack.canCombine(sourceItemStack, targetItemStack)) {
                int stackSizeDiff = Math.min(sourceItemStack.getCount(), targetItemStack.getMaxCount() - targetItemStack.getCount());
                sourceItemStack.decrement(stackSizeDiff);
                targetItemStack.increment(stackSizeDiff);
                if (sourceItemStack.isEmpty()) {
                    sourceIndex++;
                    targetIndex = 0;
                }
                continue;
            }
            targetIndex++;
        }
        target.markDirty();
    }

    private static ArrayList<Integer> getAvailableSlots(Inventory inventory) {
        ArrayList<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack itemStack = inventory.getStack(i);
            if (itemStack.getCount() >= itemStack.getMaxCount()) continue;
            availableSlots.add(i);
        }
        return availableSlots;
    }

    private static ArrayList<Integer> getPopulatedSlots(Inventory inventory) {
        ArrayList<Integer> populatedSlots = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack itemStack = inventory.getStack(i);
            if (itemStack.isEmpty()) continue;
            populatedSlots.add(i);
        }
        return populatedSlots;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(method = "execute",
            at = @At(value = "INVOKE_ASSIGN",
                    target = "Lcarpet/helpers/EntityPlayerActionPack;getTarget(Lnet/minecraft/server/network/ServerPlayerEntity;)Lnet/minecraft/util/hit/HitResult;"),
            index = 4)
    private HitResult getTarget(HitResult original) {
        target = (BlockHitResult) original;
        return original;
    }

    @Inject(method = "execute",
            at = @At(value = "RETURN",
                    target = "Lcarpet/helpers/EntityPlayerActionPack$ActionType$1;execute(Lnet/minecraft/server/network/ServerPlayerEntity;Lcarpet/helpers/EntityPlayerActionPack$Action;)Z",
                    ordinal = 2))
    private void dumpItems(ServerPlayerEntity player, EntityPlayerActionPack.Action action, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntityAccess playerAccess = (ServerPlayerEntityAccess) player;
        if (!playerAccess.getDumpItemsFlag()) return;

        BlockEntity targetedBlockEntity = player.world.getBlockEntity(target.getBlockPos());
        if (!(targetedBlockEntity instanceof Inventory inventory) || !((Inventory) targetedBlockEntity).canPlayerUse(player))
            return;
        mergeInventories(player.getInventory(), inventory);
        player.closeHandledScreen();
    }
}
