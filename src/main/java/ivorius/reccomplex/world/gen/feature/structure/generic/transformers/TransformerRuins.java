/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.world.gen.feature.structure.generic.transformers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gson.*;
import ivorius.ivtoolkit.blocks.*;
import ivorius.ivtoolkit.tools.*;
import ivorius.ivtoolkit.world.chunk.gen.StructureBoundingBoxes;
import ivorius.reccomplex.RecurrentComplex;
import ivorius.reccomplex.block.BlockGenericSolid;
import ivorius.reccomplex.block.RCBlocks;
import ivorius.reccomplex.gui.editstructure.transformers.TableDataSourceBTRuins;
import ivorius.reccomplex.gui.table.TableDelegate;
import ivorius.reccomplex.gui.table.TableNavigator;
import ivorius.reccomplex.gui.table.datasource.TableDataSource;
import ivorius.reccomplex.json.JsonUtils;
import ivorius.reccomplex.random.BlurredValueField;
import ivorius.reccomplex.utils.NBTStorable;
import ivorius.reccomplex.utils.RCAxisAlignedTransform;
import ivorius.reccomplex.world.gen.feature.structure.context.StructureLiveContext;
import ivorius.reccomplex.world.gen.feature.structure.context.StructureLoadContext;
import ivorius.reccomplex.world.gen.feature.structure.context.StructurePrepareContext;
import ivorius.reccomplex.world.gen.feature.structure.context.StructureSpawnContext;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by lukas on 25.05.14.
 */
public class TransformerRuins extends Transformer<TransformerRuins.InstanceData>
{
    private static final List<BlockPos> neighbors;

    static
    {
        ImmutableList.Builder<BlockPos> builder = ImmutableList.builder();

        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                {
                    if (x != 0 || y != 0 || z != 0)
                        builder.add(new BlockPos(x, y, z));
                }

        neighbors = builder.build();
    }

    public EnumFacing decayDirection;
    public float minDecay;
    public float maxDecay;
    public float decayChaos;
    public float decayValueDensity;
    public boolean gravity;

    public float blockErosion;
    public float vineGrowth;

    public TransformerRuins()
    {
        this(null, EnumFacing.DOWN, 0.0f, 0.9f, 0.3f, 1f / 25.0f,
                true, 0.3f, 0.1f);
    }

    public TransformerRuins(@Nullable String id, EnumFacing decayDirection, float minDecay, float maxDecay, float decayChaos, float decayValueDensity, boolean gravity, float blockErosion, float vineGrowth)
    {
        super(id != null ? id : randomID(TransformerRuins.class));
        this.decayDirection = decayDirection;
        this.minDecay = minDecay;
        this.maxDecay = maxDecay;
        this.decayChaos = decayChaos;
        this.decayValueDensity = decayValueDensity;
        this.gravity = gravity;
        this.blockErosion = blockErosion;
        this.vineGrowth = vineGrowth;
    }

    private static int getPass(IBlockState state)
    {
        return (state.isNormalCube() || state.getMaterial() == Material.AIR) ? 0 : 1;
    }

    public static void shuffleArray(Object[] ar, Random rand)
    {
        for (int i = ar.length - 1; i > 0; i--)
        {
            int index = rand.nextInt(i + 1);

            Object a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

    public static int product(int[] surfaceSize)
    {
        return Arrays.stream(surfaceSize).reduce(1, (left, right) -> left * right);
    }

    @Override
    public boolean skipGeneration(InstanceData instanceData, StructureLiveContext context, BlockPos pos, IBlockState state, IvWorldData worldData, BlockPos sourcePos)
    {
        if (instanceData.fallingBlocks.contains(sourcePos))
            return true;

        BlurredValueField surfaceField = instanceData.surfaceField;
        BlurredValueField volumeField = instanceData.volumeField;

        IvBlockCollection blockCollection = worldData.blockCollection;

        double decay = (instanceData.baseDecay != null ? instanceData.baseDecay : 0)
                + (surfaceField != null ? surfaceField.getValue(Math.min(sourcePos.getX(), surfaceField.getSize()[0]), Math.min(sourcePos.getY(), surfaceField.getSize()[1]), Math.min(sourcePos.getZ(), surfaceField.getSize()[2])) : 0)
                + (volumeField != null ? volumeField.getValue(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()) : 0);

        if (decay < 0.000001)
            return false;

        double stability = decayDirection.getFrontOffsetX() * (sourcePos.getX() / (double) blockCollection.getWidth())
                + decayDirection.getFrontOffsetY() * (sourcePos.getY() / (double) blockCollection.getHeight())
                + decayDirection.getFrontOffsetZ() * (sourcePos.getZ() / (double) blockCollection.getLength());
        if (stability < 0) // Negative direction, not special case
            stability += 1;

        return stability < decay;
    }

    @Override
    public void transform(InstanceData instanceData, Phase phase, StructureSpawnContext context, IvWorldData worldData, RunTransformer transformer)
    {
        if (phase == Phase.AFTER)
        {
            WorldServer world = context.environment.world;
            IvBlockCollection blockCollection = worldData.blockCollection;
            int[] areaSize = new int[]{blockCollection.width, blockCollection.height, blockCollection.length};

            BlockPos lowerCoord = StructureBoundingBoxes.min(context.boundingBox);

            BlockPos.MutableBlockPos dest = new BlockPos.MutableBlockPos(lowerCoord);
            for (BlockPos sourcePos : instanceData.fallingBlocks)
            {
                // TODO Bounce left/right
                IvMutableBlockPos.add(RCAxisAlignedTransform.apply(sourcePos, dest, areaSize, context.transform), lowerCoord);

                IBlockState state;
                while (dest.getY() > 0
                        && (state = world.getBlockState(dest)).getBlock().isAir(state, world, dest))
                {
                    IvMutableBlockPos.offset(dest, dest, EnumFacing.DOWN);
                }

                IvMutableBlockPos.offset(dest, dest, EnumFacing.UP);
                world.setBlockState(dest, blockCollection.getBlockState(sourcePos), 2);
            }

            StructureBoundingBox dropAreaBB = context.boundingBox;
            RecurrentComplex.forgeEventHandler.disabledTileDropAreas.add(dropAreaBB);

            if (blockErosion > 0.0f || vineGrowth > 0.0f)
            {
                for (BlockPos sourceCoord : BlockAreas.mutablePositions(blockCollection.area()))
                {
                    BlockPos worldCoord = context.transform.apply(sourceCoord, areaSize).add(StructureBoundingBoxes.min(context.boundingBox));

                    if (context.includes(worldCoord))
                    {
                        IBlockState state = world.getBlockState(worldCoord);

                        if (!transformer.transformer.skipGeneration(transformer.instanceData, context, worldCoord, state, worldData, sourceCoord))
                            decayBlock(world, context.random, state, worldCoord);
                    }
                }
            }

            RecurrentComplex.forgeEventHandler.disabledTileDropAreas.remove(dropAreaBB);
        }
    }

    public void decayBlock(World world, Random random, IBlockState state, BlockPos coord)
    {
        IBlockState newState = state;

        if (random.nextFloat() < blockErosion)
        {
            if (newState.getBlock() == Blocks.STONEBRICK
                    && newState.getProperties().get(BlockStoneBrick.VARIANT) != BlockStoneBrick.EnumType.MOSSY)
                newState = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
            else if (newState.getBlock() == Blocks.SANDSTONE)
                newState = Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.DEFAULT);
        }

        if (random.nextFloat() < vineGrowth)
        {
            if (newState.getBlock() == Blocks.STONEBRICK)
                newState = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
            else if (newState.getBlock() == Blocks.COBBLESTONE)
                newState = Blocks.MOSSY_COBBLESTONE.getDefaultState();
            else if (newState.getBlock() == Blocks.COBBLESTONE_WALL)
                newState = Blocks.COBBLESTONE_WALL.getDefaultState().withProperty(BlockWall.VARIANT, BlockWall.EnumType.MOSSY);
        }

        if (newState.getBlock() == Blocks.AIR)
        {
            newState = null;
            for (EnumFacing direction : EnumFacing.HORIZONTALS)
            {
                if (random.nextFloat() < vineGrowth && Blocks.VINE.canPlaceBlockOnSide(world, coord, direction))
                {
                    IBlockState downState = world.getBlockState(coord.offset(EnumFacing.DOWN));
                    downState = downState.getBlock() == Blocks.VINE ? downState : Blocks.VINE.getDefaultState();
                    downState = downState.withProperty(BlockVine.getPropertyFor(direction.getOpposite()), true);

                    int length = 1 + random.nextInt(MathHelper.floor_float(vineGrowth * 10.0f + 3));
                    for (int y = 0; y < length; y++)
                    {
                        BlockPos downPos = coord.offset(EnumFacing.DOWN, y);
                        if (world.getBlockState(downPos).getMaterial() == Material.AIR)
                            world.setBlockState(downPos, downState, 3);
                        else
                            break;
                    }

                    break;
                }
            }
        }

        if (newState != null && state != newState)
            world.setBlockState(coord, newState, 3);
    }

    @Override
    public String getDisplayString()
    {
        return IvTranslations.get("reccomplex.transformer.ruins");
    }

    @Override
    public TableDataSource tableDataSource(TableNavigator navigator, TableDelegate delegate)
    {
        return new TableDataSourceBTRuins(this, navigator, delegate);
    }

    @Override
    public InstanceData prepareInstanceData(StructurePrepareContext context, IvWorldData worldData)
    {
        InstanceData instanceData = new InstanceData();

        if (minDecay > 0.0f || maxDecay > 0.0f)
        {
            BlockArea sourceArea = BlockArea.areaFromSize(BlockPos.ORIGIN, StructureBoundingBoxes.size(context.boundingBox));

            float decayChaos = context.random.nextFloat() * this.decayChaos;
            if (this.maxDecay - this.minDecay > decayChaos)
                decayChaos = this.maxDecay - this.minDecay;

            instanceData.baseDecay = context.random.nextDouble() * (this.maxDecay - this.minDecay) + this.minDecay;

            int[] surfaceSize = BlockAreas.side(sourceArea, decayDirection).areaSize();
            instanceData.surfaceField = new BlurredValueField(surfaceSize);
            int surfaceValues = MathHelper.floor_double(product(surfaceSize) * decayValueDensity + 0.5);
            for (int i = 0; i < surfaceValues; i++)
                instanceData.surfaceField.addValue((context.random.nextDouble() - context.random.nextDouble()) * decayChaos * 1.25, context.random);

            int[] volumeSize = sourceArea.areaSize();
            instanceData.volumeField = new BlurredValueField(volumeSize);
            int volumeValues = MathHelper.floor_double(product(volumeSize) * decayValueDensity * 0.25 + 0.5);
            for (int i = 0; i < volumeValues; i++)
                instanceData.volumeField.addValue((context.random.nextDouble() - context.random.nextDouble()) * decayChaos * 0.75, context.random);
        }

        return instanceData;
    }

    @Override
    public void configureInstanceData(InstanceData instanceData, StructurePrepareContext context, IvWorldData worldData, RunTransformer transformer)
    {
        if (gravity)
        {
            IvBlockCollection blockCollection = worldData.blockCollection;
            int[] areaSize = new int[]{blockCollection.width, blockCollection.height, blockCollection.length};

            BlockPos lowerCoord = StructureBoundingBoxes.min(context.boundingBox);

            Set<BlockPos> complete = new HashSet<>(product(areaSize));

            BlockPos.MutableBlockPos dest = new BlockPos.MutableBlockPos(lowerCoord);
            HashSet<BlockPos> connected = new HashSet<>();
            boolean[] hasFloor = new boolean[1];

            for (BlockPos startPos : BlockAreas.positions(blockCollection.area()))
            {
                if (complete.contains(startPos))
                    continue;

                hasFloor[0] = false;
                connected.clear();

                // Try to fall
                TransformerAbstractCloud.visitRecursively(Sets.newHashSet(startPos), (changed, sourcePos) ->
                {
                    if (!complete.add(sourcePos))
                        return true;

                    IvMutableBlockPos.add(RCAxisAlignedTransform.apply(sourcePos, dest, areaSize, context.transform), lowerCoord);

                    IBlockState state = blockCollection.getBlockState(sourcePos);

                    if (!transformer.transformer.skipGeneration(transformer.instanceData, context, dest, state, worldData, sourcePos)
                            && state.getMaterial() != Material.AIR)
                    {
                        if (state.getBlock() == RCBlocks.genericSolid
                                && (int) state.getValue(BlockGenericSolid.TYPE) == 0)
                            hasFloor[0] = true; // TODO Make configurable?

                        connected.add(sourcePos);
                        neighbors.stream().map(sourcePos::add).forEach(changed::add);
                    }

                    return true;
                });

                if (connected.size() > 0 && connected.size() < 200 && !hasFloor[0]) // Now we fall
                    instanceData.fallingBlocks.addAll(connected);
            }
        }
    }

    @Override
    public InstanceData loadInstanceData(StructureLoadContext context, NBTBase nbt)
    {
        return new InstanceData(nbt instanceof NBTTagCompound ? (NBTTagCompound) nbt : new NBTTagCompound());
    }

    public static class InstanceData implements NBTStorable
    {
        public Double baseDecay;
        public BlurredValueField surfaceField;
        public BlurredValueField volumeField;
        public final Set<BlockPos> fallingBlocks = new HashSet<>();

        public InstanceData()
        {
        }

        public InstanceData(NBTTagCompound compound)
        {
            baseDecay = compound.hasKey("baseDecay") ? compound.getDouble("baseDecay") : null;
            surfaceField = compound.hasKey("field", Constants.NBT.TAG_COMPOUND)
                    ? NBTCompoundObjects.read(compound.getCompoundTag("field"), BlurredValueField::new)
                    : null;
            volumeField = compound.hasKey("volumeField", Constants.NBT.TAG_COMPOUND)
                    ? NBTCompoundObjects.read(compound.getCompoundTag("volumeField"), BlurredValueField::new)
                    : null;
            fallingBlocks.addAll(NBTTagLists.intArraysFrom(compound, "fallingBlocks").stream().map(BlockPositions::fromIntArray).collect(Collectors.toList()));
        }

        @Override
        public NBTBase writeToNBT()
        {
            NBTTagCompound compound = new NBTTagCompound();
            if (baseDecay != null)
                compound.setDouble("baseDecay", baseDecay);
            if (surfaceField != null)
                compound.setTag("field", NBTCompoundObjects.write(surfaceField));
            if (volumeField != null)
                compound.setTag("volumeField", NBTCompoundObjects.write(volumeField));
            NBTTagLists.writeIntArraysTo(compound, "fallingBlocks", fallingBlocks.stream().map(BlockPositions::toIntArray).collect(Collectors.toList()));
            return compound;
        }
    }

    public static class Serializer implements JsonDeserializer<TransformerRuins>, JsonSerializer<TransformerRuins>
    {
        private MCRegistry registry;

        public Serializer(MCRegistry registry)
        {
            this.registry = registry;
        }

        @Override
        public TransformerRuins deserialize(JsonElement jsonElement, Type par2Type, JsonDeserializationContext context)
        {
            JsonObject jsonObject = JsonUtils.asJsonObject(jsonElement, "transformerRuins");

            String id = JsonUtils.getString(jsonObject, "id", null);

            EnumFacing decayDirection = Directions.deserialize(JsonUtils.getString(jsonObject, "decayDirection", "DOWN"));
            float minDecay = JsonUtils.getFloat(jsonObject, "minDecay", 0.0f);
            float maxDecay = JsonUtils.getFloat(jsonObject, "maxDecay", 0.9f);
            float decayChaos = JsonUtils.getFloat(jsonObject, "decayChaos", 0.3f);
            float decayValueDensity = JsonUtils.getFloat(jsonObject, "decayValueDensity", 1.0f / 25.0f);

            boolean gravity = JsonUtils.getBoolean(jsonObject, "gravity", true);

            float blockErosion = JsonUtils.getFloat(jsonObject, "blockErosion", 0.0f);
            float vineGrowth = JsonUtils.getFloat(jsonObject, "vineGrowth", 0.0f);

            return new TransformerRuins(id, decayDirection, minDecay, maxDecay, decayChaos, decayValueDensity, gravity, blockErosion, vineGrowth);
        }

        @Override
        public JsonElement serialize(TransformerRuins transformer, Type par2Type, JsonSerializationContext context)
        {
            JsonObject jsonObject = new JsonObject();

            jsonObject.addProperty("id", transformer.id());

            jsonObject.addProperty("decayDirection", Directions.serialize(transformer.decayDirection));
            jsonObject.addProperty("minDecay", transformer.minDecay);
            jsonObject.addProperty("maxDecay", transformer.maxDecay);
            jsonObject.addProperty("decayChaos", transformer.decayChaos);
            jsonObject.addProperty("decayValueDensity", transformer.decayValueDensity);
            jsonObject.addProperty("gravity", transformer.gravity);

            jsonObject.addProperty("blockErosion", transformer.blockErosion);
            jsonObject.addProperty("vineGrowth", transformer.vineGrowth);

            return jsonObject;
        }
    }
}
