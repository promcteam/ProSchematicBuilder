package fr.weefle.constructor.commands;

import fr.weefle.constructor.SchematicBuilder;
import fr.weefle.constructor.essentials.BuilderTrait;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarkSubCommand extends AbstractCommand {
    public MarkSubCommand(@Nullable SchematicBuilderCommand parent) {
        super("mark", parent);
        this.permission = "schematicbuilder.mark";
        addAllowedSender(Player.class);
    }

    @Override
    public List<String> getArguments(CommandSender sender) {
        List<Material> markMaterials = SchematicBuilder.getInstance().config().getMarkMats();
        List<String> list = new ArrayList<>(markMaterials.size());
        for (Material material : markMaterials) {
            list.add(material.name().toLowerCase());
        }
        return list;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull List<String> args) {
        BuilderTrait builder = getSelectedBuilder(sender);
        if (builder == null) {return;}
        if (args.size() != 1) {
            sendUsage(sender);
            return;
        }
        NPC npc = builder.getNPC();

        Material material = null;
        try {
            material = Material.valueOf(args.get(0).toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        if (material == null || !SchematicBuilder.getInstance().config().getMarkMats().contains(material)) {
            sender.sendMessage(ChatColor.GOLD + npc.getName() + " can not mark with " + args.get(0) + ".The specified item is not allowed.");
            return;
        }
        if (builder.StartMark(material)) {
            sender.sendMessage(SchematicBuilder.format(SchematicBuilder.getInstance().config().getMarkMessage(), npc,
                    builder.schematic, sender, null, "0"));
        } else {
            sender.sendMessage(ChatColor.RED + npc.getName() + " could not mark. Already building or no schematic loaded?");
        }
    }
}
