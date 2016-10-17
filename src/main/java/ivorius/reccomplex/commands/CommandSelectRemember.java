/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.reccomplex.commands;

import ivorius.reccomplex.RCConfig;
import ivorius.reccomplex.capability.SelectionOwner;
import ivorius.reccomplex.utils.RCBlockAreas;
import ivorius.reccomplex.utils.ServerTranslations;
import ivorius.reccomplex.world.gen.feature.StructureGenerationData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/**
 * Created by lukas on 09.06.14.
 */
public class CommandSelectRemember extends CommandBase
{
    @Override
    public String getCommandName()
    {
        return RCConfig.commandPrefix + "remember";
    }

    @Override
    public String getCommandUsage(ICommandSender var1)
    {
        return ServerTranslations.usage("commands.rcremember.usage");
    }

    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) throws CommandException
    {
        if (args.length < 1)
            throw ServerTranslations.wrongUsageException("commands.rcremember.usage");

        StructureGenerationData generationData = StructureGenerationData.get(commandSender.getEntityWorld());
        SelectionOwner owner = RCCommands.getSelectionOwner(commandSender, null, true);

        String name = buildString(args, 0);

        generationData.addCustomEntry(name, RCBlockAreas.toBoundingBox(owner.getSelection()));
        commandSender.addChatMessage(ServerTranslations.format("commands.rcremember.success", name));
    }
}
