package fr.weefle.constructor.commands;

import fr.weefle.constructor.NMS.NMS;
import fr.weefle.constructor.SchematicBuilder;
import fr.weefle.constructor.essentials.BuilderSchematic;
import fr.weefle.constructor.essentials.BuilderTrait;
import fr.weefle.constructor.util.Structure;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class LoadSubCommand extends AbstractCommand {
    public LoadSubCommand(@Nullable SchematicBuilderCommand parent) {
        super("load", parent);
        this.permission = "schematicbuilder.load";
    }

    @Override
    public List<String> getUsages(CommandSender sender) {
        List<String> list = new ArrayList<>();
        list.add('/'+getFullCommand()+" <schematic>");
        return list;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull List<String> args) {
        BuilderTrait builder = getSelectedBuilder(sender);
        if (builder == null) {return;}
        if (args.size() == 0) {
            sendUsage(sender);
            return;
        }

        if (builder.State != BuilderTrait.BuilderState.idle) {
            sender.sendMessage(ChatColor.RED + "Please cancel current build before loading new schematic.");
            return;
        }

        StringBuilder stringBuilder = new StringBuilder(args.get(0));
        for (int i = 1; i < args.size(); i++) {
            stringBuilder.append(" ").append(args.get(i));
        }
        String arg = stringBuilder.toString().trim();
        File dir = new File(SchematicBuilder.schematicsFolder);
        File file = new File(dir, arg);
        if (!file.exists()) {
            sender.sendMessage(ChatColor.RED + "no such file "+arg);
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                BuilderSchematic schematic;
                try {
                    if (arg.endsWith(".schem")) {
                        schematic = NMS.getInstance().getChooser().setSchematic(dir, arg);
                    } else {
                        schematic = new Structure(dir, arg).load(dir, arg);
                    }
                    /*if(NMS.getInstance().getVersion().equals("v1_15_R1")){
                    MCEditSchematicFormat_1_15_R1 format = new MCEditSchematicFormat_1_15_R1();
                    inst.schematic = format.load(dir, arg);
                    }else if(NMS.getInstance().getVersion().equals("v1_14_R1")) {
                        MCEditSchematicFormat_1_14_R1 format = new MCEditSchematicFormat_1_14_R1();
                        inst.schematic = format.load(dir, arg);
                    }
                    else if(NMS.getInstance().getVersion().equals("v1_13_R2")) {
                        MCEditSchematicFormat_1_13_R2 format = new MCEditSchematicFormat_1_13_R2();
                        inst.schematic = format.load(dir, arg);
                    }*/
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            builder.schematic = schematic;
                            builder.SchematicName = builder.schematic.Name;
                            sender.sendMessage(ChatColor.GREEN + "Loaded Sucessfully");
                            sender.sendMessage(builder.schematic.GetInfo());
                        }
                    }.runTask(SchematicBuilder.instance);
                } catch (Exception e) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(ChatColor.RED + "Failed to load schematic "+arg+", check console for more details.");
                            SchematicBuilder.getInstance().getLogger().log(Level.WARNING, "Failed to load schematic: " + file);
                            e.printStackTrace();
                        }
                    }.runTask(SchematicBuilder.getInstance());
                }
            }
        }.runTaskAsynchronously(SchematicBuilder.getInstance());
    }
}