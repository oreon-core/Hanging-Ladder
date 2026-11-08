package com.hangladder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ПКМ драбиною по вже поставленій драбині (без Shift) -> нова драбина ставиться
 * під самим низом стовпчика, як у ліан. З Shift — звичайна ванільна поведінка.
 */
public final class HangingLadderPlacement {
	private HangingLadderPlacement() {}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || player.isShiftKeyDown()) {
			return InteractionResult.PASS; // Shift = ванільне розміщення
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return InteractionResult.PASS;
		}

		BlockState clicked = level.getBlockState(hit.getBlockPos());
		// Клікнули саме по драбині, і в руці така сама драбина
		if (!(clicked.getBlock() instanceof LadderBlock) || blockItem.getBlock() != clicked.getBlock()) {
			return InteractionResult.PASS;
		}
		if (!player.getAbilities().mayBuild) {
			return InteractionResult.PASS;
		}

		Direction facing = clicked.getValue(LadderBlock.FACING);

		// Ідемо вниз до найнижчої драбини цього стовпчика
		BlockPos bottom = hit.getBlockPos();
		while (isSameLadder(level.getBlockState(bottom.below()), clicked, facing)) {
			bottom = bottom.below();
		}
		BlockPos target = bottom.below();

		if (level.isOutsideBuildHeight(target)
				|| !level.getBlockState(target).canBeReplaced()
				|| !level.mayInteract(player, target)) {
			return InteractionResult.FAIL; // нікуди подовжувати
		}

		if (!level.isClientSide()) {
			boolean water = level.getFluidState(target).getType() == Fluids.WATER;
			BlockState placed = clicked.getBlock().defaultBlockState()
					.setValue(LadderBlock.FACING, facing)
					.setValue(BlockStateProperties.WATERLOGGED, water);

			if (!placed.canSurvive(level, target)) {
				return InteractionResult.FAIL;
			}

			level.setBlock(target, placed, Block.UPDATE_ALL);
			level.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(player, placed));

			SoundType sound = placed.getSoundType();
			level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS,
					(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);

			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean isSameLadder(BlockState state, BlockState reference, Direction facing) {
		return state.is(reference.getBlock()) && state.getValue(LadderBlock.FACING) == facing;
	}
}
