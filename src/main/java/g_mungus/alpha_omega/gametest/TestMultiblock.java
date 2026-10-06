package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * A test-only multiblock for the claim gametests (orbifold plan phase 8): a controller and a line of {@link #SIZE}
 * parts in front of it, in the style of modded multiblocks whose block entities store <b>absolute</b> positions. The
 * controller forms by finding the parts, storing their positions and writing its own position into each part's block
 * entity; once formed it checks every tick that each stored position still holds a part pointing back at it, and counts
 * its working ticks. A part is linked when the controller at its stored position lists the part's own position.
 *
 * <p>Registered only outside production ({@link FMLEnvironment#production}: dev runs and gametests), as blocks without
 * items, so it never appears in a creative tab or a released world.
 */
public final class TestMultiblock {

    public static final int SIZE = 5;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlphaOmegaMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AlphaOmegaMod.MOD_ID);

    public static final DeferredBlock<ControllerBlock> CONTROLLER = BLOCKS.register("test_controller", () -> new ControllerBlock(properties(MapColor.GOLD)));
    public static final DeferredBlock<PartBlock> PART = BLOCKS.register("test_part", () -> new PartBlock(properties(MapColor.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerEntity>> CONTROLLER_ENTITY = BLOCK_ENTITIES.register("test_controller",
        () -> BlockEntityType.Builder.of(ControllerEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartEntity>> PART_ENTITY = BLOCK_ENTITIES.register("test_part",
        () -> BlockEntityType.Builder.of(PartEntity::new, PART.get()).build(null));

    private TestMultiblock() {
    }

    /** Whether the blocks exist in this run. */
    public static boolean registered() {
        return !FMLEnvironment.production;
    }

    public static void register(IEventBus modBus) {
        if (!registered()) return;
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private static BlockBehaviour.Properties properties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.0F).sound(SoundType.METAL);
    }

    // ---- Blocks ----

    public static final class ControllerBlock extends Block implements EntityBlock {

        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        ControllerBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ControllerEntity(pos, state);
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != CONTROLLER_ENTITY.get()) return null;
            return (BlockEntityTicker<T>) (BlockEntityTicker<ControllerEntity>) (l, pos, s, entity) -> entity.tick();
        }
    }

    public static final class PartBlock extends Block implements EntityBlock {

        PartBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PartEntity(pos, state);
        }
    }

    // ---- Block entities ----

    public static final class ControllerEntity extends BlockEntity {

        private final List<BlockPos> parts = new ArrayList<>();
        private boolean formed;
        private long workTicks;
        private int formations;

        public ControllerEntity(BlockPos pos, BlockState state) {
            super(CONTROLLER_ENTITY.get(), pos, state);
        }

        public boolean formed() {
            return this.formed;
        }

        public long workTicks() {
            return this.workTicks;
        }

        public int formations() {
            return this.formations;
        }

        public List<BlockPos> parts() {
            return List.copyOf(this.parts);
        }

        void tick() {
            if (!this.formed) {
                this.tryForm();
            } else if (this.valid()) {
                this.workTicks++;
                this.setChanged();
            } else {
                this.formed = false;
                this.parts.clear();
                this.setChanged();
            }
        }

        /** Looks for {@link #SIZE} parts in a line in front, in this block entity's own frame. */
        private void tryForm() {
            Level level = this.getLevel();
            if (level == null) return;
            Direction facing = this.getBlockState().getValue(ControllerBlock.FACING);
            List<BlockPos> found = new ArrayList<>();
            for (int i = 1; i <= SIZE; i++) {
                BlockPos pos = this.worldPosition.relative(facing, i);
                if (!level.getBlockState(pos).is(PART.get()) || !(level.getBlockEntity(pos) instanceof PartEntity)) return;
                found.add(pos);
            }
            for (BlockPos pos : found) {
                PartEntity part = (PartEntity) level.getBlockEntity(pos);
                part.controller = this.worldPosition;
                part.setChanged();
            }
            this.parts.clear();
            this.parts.addAll(found);
            this.formed = true;
            this.formations++;
            this.setChanged();
        }

        /** Every stored position still holds a part whose stored controller is this one. */
        private boolean valid() {
            Level level = this.getLevel();
            if (level == null) return false;
            for (BlockPos pos : this.parts) {
                if (!level.getBlockState(pos).is(PART.get()) || !(level.getBlockEntity(pos) instanceof PartEntity part) || !this.worldPosition.equals(part.controller)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("parts", new LongArrayTag(this.parts.stream().mapToLong(BlockPos::asLong).toArray()));
            tag.putBoolean("formed", this.formed);
            tag.putLong("workTicks", this.workTicks);
            tag.putInt("formations", this.formations);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.parts.clear();
            for (long pos : tag.getLongArray("parts")) this.parts.add(BlockPos.of(pos));
            this.formed = tag.getBoolean("formed");
            this.workTicks = tag.getLong("workTicks");
            this.formations = tag.getInt("formations");
        }
    }

    public static final class PartEntity extends BlockEntity {

        @Nullable
        private BlockPos controller;

        public PartEntity(BlockPos pos, BlockState state) {
            super(PART_ENTITY.get(), pos, state);
        }

        @Nullable
        public BlockPos controller() {
            return this.controller;
        }

        /** Whether the controller at the stored position is formed and lists this part's own position. */
        public boolean linked() {
            Level level = this.getLevel();
            return level != null && this.controller != null && level.getBlockEntity(this.controller) instanceof ControllerEntity entity && entity.formed
                && entity.parts.contains(this.worldPosition);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (this.controller != null) tag.putLong("controller", this.controller.asLong());
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.controller = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
        }
    }
}
