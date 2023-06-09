package fr.weefle.constructor.schematic;

import fr.weefle.constructor.SchematicBuilder;
import fr.weefle.constructor.hooks.citizens.BuilderTrait;
import fr.weefle.constructor.schematic.blocks.EmptyBuildBlock;
import mc.promcteam.engine.utils.StringUT;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class YAMLSchematic extends Schematic {
    public static final String DISPLAY_NAME = "display-name";
    public static final String PATH         = "path";
    public static final String OFFSET       = "offset";
    public static final String MATERIAL     = "material";

    private final List<SchematicTier> tiers;
    private       int                 nextTier = 0;

    public YAMLSchematic(Path path) {
        super(path);
        YamlConfiguration config      = YamlConfiguration.loadConfiguration(path.toFile());
        Set<String> keys = config.getKeys(false);
        if (keys.size() == 0) {throw new IllegalArgumentException("Must contain at least one tier");}
        this.tiers = new ArrayList<>(keys.size());
        for (String key : keys) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {return;}
            tiers.add(new SchematicTier(new File(SchematicBuilder.getInstance().config().getSchematicsFolder(),
                    Objects.requireNonNull(section.getString(PATH), "Missing 'path' field")).toPath(), section));
        }
    }

    public int getTotalTiers() {return this.tiers.size();}

    public void setNextTier(int tier) {
        if (tier >= this.getTotalTiers()) {
            throw new IllegalArgumentException("Max tier for this build is " + this.getTotalTiers() + ", but got " + tier);
        }
        this.nextTier = tier;
    }

    @Override
    @NotNull
    public String getDisplayName() {return this.tiers.get(this.nextTier).getDisplayName();}

    @Override
    public @Nullable Vector getAbsolutePosition() {return this.tiers.get(this.nextTier).getAbsolutePosition();}

    @Override
    public int getWidth() {return this.tiers.get(this.nextTier).getWidth();}

    @Override
    public int getHeight() {return this.tiers.get(this.nextTier).getHeight();}

    @Override
    public int getLength() {return this.tiers.get(this.nextTier).getLength();}

    @Override
    public @NotNull EmptyBuildBlock getBlockAt(int x, int y, int z) {return this.tiers.get(this.nextTier).getBlockAt(x, y, z);}

    @Override
    public Location offset(Location origin, int x, int y, int z, int emptyLayers) {return this.tiers.get(this.nextTier).offset(origin, x, y, z, emptyLayers);}

    @Override
    public @NotNull Map<Material, Integer> getMaterials() {return this.tiers.get(this.nextTier).getMaterials();}

    @Override
    public @NotNull Queue<EmptyBuildBlock> buildQueue(BuilderTrait builder) {return this.tiers.get(this.nextTier).buildQueue(builder);}

    private static class SchematicTier extends Schematic {
        private static final Vector ZERO = new Vector(0, 0 ,0);

        private final RawSchematic           handle;
        private final Map<Material, Integer> materials;
        private final Vector                 offset;

        public SchematicTier(Path path, ConfigurationSection config) {
            super(path);
            Schematic schematic = SchematicBuilder.getSchematic(path);
            if (!(schematic instanceof RawSchematic)) {
                throw new ClassCastException("Referenced schematics must be .schem or .nbt files");
            }
            this.handle = (RawSchematic) schematic;
            this.displayName = config.getString(DISPLAY_NAME);
            if (this.displayName != null) {this.displayName = StringUT.color(this.displayName);}
            ConfigurationSection section = config.getConfigurationSection(MATERIAL);
            if (section == null) {
                this.materials = null;
            } else {
                this.materials = new TreeMap<>();
                for (String key : section.getKeys(false)) {
                    Material material = Material.valueOf(key.toUpperCase());
                    this.materials.put(material, this.materials.getOrDefault(material, 0) + section.getInt(key));
                }
            }
            section = config.getConfigurationSection(OFFSET);
            this.offset = section == null ? ZERO : new Vector(
                    section.getInt("x", 0),
                    section.getInt("y", 0),
                    section.getInt("z", 0));
        }

        @Override
        public @Nullable Vector getAbsolutePosition() {return this.handle.getAbsolutePosition();}

        @Override
        public int getWidth() {return this.handle.getWidth();}

        @Override
        public int getHeight() {return this.handle.getHeight();}

        @Override
        public int getLength() {return this.handle.getLength();}

        @Override
        @NotNull
        public EmptyBuildBlock getBlockAt(int x, int y, int z) {return this.handle.getBlockAt(x, y, z);}

        @Override
        public Location offset(Location origin, int x, int y, int z, int emptyLayers) {
            return this.handle.offset(origin, x, y, z, emptyLayers).add(this.offset);
        }

        @Override
        @NotNull
        public Map<Material, Integer> getMaterials() {
            return this.materials == null
                    ? this.handle.getMaterials()
                    : this.materials;
        }

        @Override
        @NotNull
        public Queue<EmptyBuildBlock> buildQueue(BuilderTrait builder) {return this.handle.buildQueue(builder);}
    }
}
