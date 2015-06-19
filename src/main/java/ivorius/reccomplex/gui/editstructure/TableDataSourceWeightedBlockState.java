/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.gui.editstructure;

import ivorius.reccomplex.gui.GuiValidityStateIndicator;
import ivorius.reccomplex.gui.editstructure.transformers.TableDataSourceBTNatural;
import ivorius.reccomplex.gui.table.*;
import ivorius.reccomplex.structures.generic.WeightedBlockState;
import ivorius.reccomplex.utils.IvTranslations;
import net.minecraft.block.Block;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Created by lukas on 05.06.14.
 */
public class TableDataSourceWeightedBlockState extends TableDataSourceSegmented implements TableCellPropertyListener
{
    private WeightedBlockState weightedBlockState;

    private TableDelegate tableDelegate;
    private TableCellTitle parsed;

    public TableDataSourceWeightedBlockState(WeightedBlockState weightedBlockState, TableDelegate tableDelegate)
    {
        this.weightedBlockState = weightedBlockState;
        this.tableDelegate = tableDelegate;
    }

    public static GuiValidityStateIndicator.State stateForNBTCompoundJson(String json)
    {
        if (json.length() == 0)
            return GuiValidityStateIndicator.State.VALID;

        NBTBase nbtbase;

        try
        {
            nbtbase = JsonToNBT.func_150315_a(json);
            if (nbtbase instanceof NBTTagCompound)
                return GuiValidityStateIndicator.State.VALID;
        }
        catch (NBTException ignored)
        {

        }

        return GuiValidityStateIndicator.State.INVALID;
    }

    @Override
    public int numberOfSegments()
    {
        return 3;
    }

    @Override
    public int sizeOfSegment(int segment)
    {
        return segment == 1 ? 2 : 1;
    }

    @Override
    public TableElement elementForIndexInSegment(GuiTable table, int index, int segment)
    {
        if (segment == 0)
        {
            TableCellFloatNullable cell = new TableCellFloatNullable("weight", TableElements.toFloat(weightedBlockState.weight), 1.0f, 0, 10, "D", "C");
            cell.addPropertyListener(this);
            cell.setTooltip(IvTranslations.formatLines("structures.gui.random.weight.tooltip"));
            return new TableElementCell(IvTranslations.get("structures.gui.random.weight"), cell);
        }
        else if (segment == 1)
        {
            if (index == 0)
            {
                TableCellString cell = TableDataSourceBTNatural.elementForBlock("block", weightedBlockState.block);
                cell.addPropertyListener(this);
                return new TableElementCell("Block", cell);
            }
            else if (index == 1)
            {
                TableCellInteger cell = new TableCellInteger("metadata", weightedBlockState.metadata, 0, 15);
                cell.addPropertyListener(this);
                return new TableElementCell("Metadata", cell);
            }
        }
        else if (segment == 2)
        {
            TableCellString cell = new TableCellString("tileEntityInfo", weightedBlockState.tileEntityInfo);
            cell.addPropertyListener(this);
            cell.setShowsValidityState(true);
            cell.setValidityState(stateForNBTCompoundJson(weightedBlockState.tileEntityInfo));
            return new TableElementCell("Tile Entity NBT", cell);
        }

        return null;
    }

    @Override
    public void valueChanged(TableCellPropertyDefault cell)
    {
        if ("weight".equals(cell.getID()))
        {
            weightedBlockState.weight = TableElements.toDouble((Float) cell.getPropertyValue());
        }
        else if ("block".equals(cell.getID()))
        {
            weightedBlockState.block = (Block) Block.blockRegistry.getObject(cell.getPropertyValue());
            TableDataSourceBTNatural.setStateForBlockTextfield(((TableCellString) cell));
        }
        else if ("metadata".equals(cell.getID()))
        {
            weightedBlockState.metadata = (int) cell.getPropertyValue();
        }
        else if ("tileEntityInfo".equals(cell.getID()))
        {
            weightedBlockState.tileEntityInfo = (String) cell.getPropertyValue();
            ((TableCellString) cell).setValidityState(stateForNBTCompoundJson(weightedBlockState.tileEntityInfo));
        }
    }
}
