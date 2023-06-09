package fr.weefle.constructor.api;

import com.github.shynixn.structureblocklib.api.bukkit.StructureBlockLibApi;
import fr.weefle.constructor.SchematicBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.logging.Level;

public class StructureUtil {

    public static void save(Location start, Vector size, String name) {

        Path path = SchematicBuilder.getInstance().getDataFolder().toPath().resolve("schematics\\" + name + ".nbt");

        StructureBlockLibApi.INSTANCE
                .saveStructure(SchematicBuilder.getInstance())
                .at(new Location(start.getWorld(), start.getX(), start.getY(), start.getZ()))
                .sizeX(size.getBlockX())
                .sizeY(size.getBlockY())
                .sizeZ(size.getBlockZ())
                .saveToPath(path)
                .onException(e -> SchematicBuilder.getInstance().getLogger().log(Level.SEVERE, "Failed to save structure.", e))
                .onResult(e -> SchematicBuilder.getInstance().getLogger().log(Level.INFO, ChatColor.GREEN + "Saved structure '" + name + "'."));

    }

}
