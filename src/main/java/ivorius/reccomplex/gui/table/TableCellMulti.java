/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.gui.table;

import gnu.trove.impl.Constants;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.hash.TIntFloatHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Created by lukas on 02.06.14.
 */
public class TableCellMulti implements TableCell
{
    @Nullable
    protected String id;

    private Bounds bounds = new Bounds(0, 0, 0, 0);
    protected boolean hidden;

    @Nonnull
    protected TableCell[] cells;
    protected TIntFloatMap cellsSize = new TIntFloatHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, 1);

    public TableCellMulti(String id, @Nonnull TableCell... cells)
    {
        this.id = id;
        this.cells = cells;
    }

    public TableCellMulti(@Nonnull TableCell... cells)
    {
        this(null, cells);
    }

    public TableCellMulti(String id, @Nonnull List<? extends TableCell> cells)
    {
        this(id, cells.toArray(new TableCell[cells.size()]));
    }

    public TableCellMulti(@Nonnull List<? extends TableCell> cells)
    {
        this(null, cells.toArray(new TableCell[cells.size()]));
    }

    @Nullable
    @Override
    public String getID()
    {
        return id;
    }

    public void setId(@Nonnull String id)
    {
        this.id = id;
    }

    @Nonnull
    public TableCell[] getCells()
    {
        return cells;
    }

    public void setCells(@Nonnull TableCell[] cells)
    {
        this.cells = cells;
    }

    public void setSize(int cell, float size)
    {
        cellsSize.put(cell, size);
    }

    public float getSize(int cell)
    {
        return cellsSize.get(cell);
    }

    @Override
    public void initGui(GuiTable screen)
    {
        for (TableCell cell : cells)
            cell.initGui(screen);
    }

    @Override
    public void setBounds(Bounds bounds)
    {
        float total = 0;
        for (int i = 0; i < cells.length; i++)
            total += getSize(i);

        int curPos = 0;
        for (int i = 0; i < cells.length; i++)
        {
            int buttonWidth = (int) (bounds.getWidth() * (getSize(i) / total));
            TableCell cell = cells[i];
            int realWidth = buttonWidth - (i == cells.length - 1 ? 0 : 2);
            cell.setBounds(Bounds.fromAxes(bounds.getMinX() + curPos, realWidth, bounds.getMinY(), bounds.getHeight()));

            curPos += buttonWidth;
        }

        this.bounds = bounds;
    }

    @Override
    public Bounds bounds()
    {
        return bounds;
    }

    @Override
    public void setHidden(boolean hidden)
    {
        this.hidden = hidden;
    }

    @Override
    public boolean isHidden()
    {
        return hidden;
    }
    
    @Override
    public void draw(GuiTable screen, int mouseX, int mouseY, float partialTicks)
    {
        for (TableCell cell : cells)
            if (!cell.isHidden())
                cell.draw(screen, mouseX, mouseY, partialTicks);
    }

    @Override
    public void drawFloating(GuiTable screen, int mouseX, int mouseY, float partialTicks)
    {
        for (TableCell cell : cells)
            if (!cell.isHidden())
                cell.drawFloating(screen, mouseX, mouseY, partialTicks);
    }

    @Override
    public void update(GuiTable screen)
    {
        for (TableCell cell : cells)
            cell.update(screen);
    }

    @Override
    public boolean keyTyped(char keyChar, int keyCode)
    {
        for (TableCell cell : cells)
        {
            if (cell.keyTyped(keyChar, keyCode))
                return true;
        }

        return false;
    }

    @Override
    public void mouseClicked(int button, int x, int y)
    {
        for (TableCell cell : cells)
            cell.mouseClicked(button, x, y);
    }

    @Override
    public void buttonClicked(int buttonID)
    {
        for (TableCell cell : cells)
            cell.buttonClicked(buttonID);
    }
}
