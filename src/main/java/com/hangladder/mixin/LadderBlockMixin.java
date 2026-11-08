package com.hangladder.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Робить драбину "висячою": вона може триматися за таку саму драбину зверху
 * (з тим самим напрямком), навіть якщо позаду неї немає блока-опори.
 */
@Mixin(LadderBlock.class)
public abstract class LadderBlockMixin {

	/**
	 * Додатковий варіант опори: драбина зверху з тим самим FACING — але ЛИШЕ якщо весь ланцюжок
	 * драбин над нами десь зверху справді тримається за блок. Інакше "в повітрі" нічого не висить.
	 */
	@Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
	private void hangingladder$canHang(BlockState state, LevelReader level, BlockPos pos,
									   CallbackInfoReturnable<Boolean> cir) {
		Direction facing = state.getValue(LadderBlock.FACING);
		BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));

		// Драбина ніколи не може триматися за іншу драбину позаду ("спина до спини").
		boolean ladderBehind = behind.getBlock() instanceof LadderBlock;

		if (cir.getReturnValueZ() && !ladderBehind) {
			return; // нормальна опора-блок позаду є — нічого не міняємо
		}
		// Опори позаду немає (або там лише драбина): лишається тільки закріплений ланцюжок зверху
		cir.setReturnValue(hangingladder$isAnchoredAbove(level, pos, state));
	}

	/** Іде вгору по ланцюжку драбин з тим самим FACING і шукає ту, що має реальну опору-блок позаду. */
	private static boolean hangingladder$isAnchoredAbove(LevelReader level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(LadderBlock.FACING);
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 0; i < 384; i++) {
			cursor.move(Direction.UP);
			BlockState above = level.getBlockState(cursor);
			if (!above.is(state.getBlock()) || above.getValue(LadderBlock.FACING) != facing) {
				return false; // ланцюжок обірвався, опори не знайшли
			}
			BlockPos wall = cursor.relative(facing.getOpposite());
			BlockState wallState = level.getBlockState(wall);
			if (!(wallState.getBlock() instanceof LadderBlock) && wallState.isFaceSturdy(level, wall, facing)) {
				return true; // ця драбина тримається за блок — весь ланцюжок під нею теж
			}
		}
		return false;
	}

	/**
	 * Ванільна драбина реагує лише на зміну блока-опори позаду. Додаємо реакцію на зміну
	 * блока ЗВЕРХУ: якщо драбина більше ніде не тримається — вона "обвалюється" (випадає як предмет).
	 * Це створює каскад: зламав верхню — нижні без опори падають одна за одною.
	 */
	@Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
	private void hangingladder$collapse(BlockState state, LevelReader level,
										ScheduledTickAccess scheduledTickAccess, BlockPos pos,
										Direction directionToNeighbour, BlockPos neighbourPos,
										BlockState neighbourState, RandomSource random,
										CallbackInfoReturnable<BlockState> cir) {
		if (directionToNeighbour == Direction.UP
				&& !cir.getReturnValue().isAir()
				&& !state.canSurvive(level, pos)) {
			cir.setReturnValue(Blocks.AIR.defaultBlockState());
		}
	}
}
