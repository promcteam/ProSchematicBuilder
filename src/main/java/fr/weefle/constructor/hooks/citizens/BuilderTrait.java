package fr.weefle.constructor.hooks.citizens;

import fr.weefle.constructor.Config;
import fr.weefle.constructor.SchematicBuilder;
import fr.weefle.constructor.hooks.citizens.persistence.MaterialIntegerMapPersistenceLoader;
import fr.weefle.constructor.hooks.citizens.persistence.MaterialMapWrapper;
import fr.weefle.constructor.hooks.citizens.persistence.SchematicPersistenceLoader;
import fr.weefle.constructor.menu.menus.ParameterMenu;
import fr.weefle.constructor.nms.NMS;
import fr.weefle.constructor.schematic.Schematic;
import fr.weefle.constructor.schematic.blocks.DataBuildBlock;
import fr.weefle.constructor.schematic.blocks.EmptyBuildBlock;
import fr.weefle.constructor.util.Util;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.persistence.DelegatePersistence;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.trait.Toggleable;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapCommonAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

// TODO fix hanging blocks (like ladders or torches) dropping during excavation
@TraitName("builder")
public class BuilderTrait extends Trait implements Toggleable {
    @Persist
    boolean          toggled            = true;
    @Persist("IgnoreAir")
    boolean ignoreAir     = false;
    @Persist("IgnoreLiquid")
    boolean ignoreLiquids = false;
    @Persist("Excavate")
    boolean excavate      = false;
    @Persist("Silent")
    boolean          silent             = false;
    @Persist("State")
    BuilderState     state              = BuilderState.IDLE;
    @Persist("PatternXY")
    BuildPatternXZ   buildPatternXZ     = BuildPatternXZ.SPIRAL;
    @Persist("HoldItems")
    boolean          holdItems          = SchematicBuilder.getInstance().config().isHoldItems();
    @Persist("RequireMaterials")
    boolean          requireMaterials   = SchematicBuilder.getInstance().config().isRequireMaterials();
    @Persist("MoveTimeoutSeconds")
    double           moveTimeoutSeconds = 1.0;
    @Persist("YLayers")
    int              buildYLayers       = 1;
    @Persist("Schematic")
    @DelegatePersistence(SchematicPersistenceLoader.class)
    Schematic        schematic          = null;
    @Persist("Origin")
    Location         origin             = null;
    @Persist("ContinueLoc")
    Location         continueLoc        = null; // Fixme after server restart, builder should continue the building where it left off
    @Persist("Materials")
    @DelegatePersistence(MaterialIntegerMapPersistenceLoader.class)
    MaterialMapWrapper materials        = new MaterialMapWrapper(new TreeMap<>());

    @Persist("oncancel")
    String onCancel   = null;
    @Persist("oncomplete")
    String onComplete = null;
    @Persist("onstart")
    String onStart    = null;

    public Map<Material, Integer> ExcavateMaterials = new HashMap<>();

    public boolean GroupByLayer = true;

    public long     startingcount = 1;
    public Location start         = null;

    private Queue<EmptyBuildBlock> queue = new LinkedList<>();

    private boolean clearingMarks = false;

    private final Map<Player, Long> sessions = new HashMap<>();

    private Location mypos = null;

    private CommandSender sender = null;

    private EmptyBuildBlock next    = null;
    private Block           pending = null;

    private BukkitTask canceltaskid;

    private final Queue<EmptyBuildBlock> marks  = new LinkedList<>();
    private final Queue<EmptyBuildBlock> _marks = new LinkedList<>();

    public BuilderTrait() {super("builder");}

    public boolean isIgnoreAir() {return ignoreAir;}

    public void setIgnoreAir(boolean ignoreAir) {this.ignoreAir = ignoreAir;}

    public boolean isIgnoreLiquids() {return ignoreLiquids;}

    public void setIgnoreLiquids(boolean ignoreLiquids) {this.ignoreLiquids = ignoreLiquids;}

    public boolean isExcavate() {return excavate;}

    public void setExcavate(boolean excavate) {this.excavate = excavate;}

    public boolean isSilent() {return silent;}

    public void setSilent(boolean silent) {this.silent = silent;}

    @NotNull
    public BuilderState getState() {return state;}

    @NotNull
    public BuildPatternXZ getBuildPatternXZ() {return buildPatternXZ;}

    public void setBuildPatternXZ(@NotNull BuildPatternXZ buildPatternXZ) {this.buildPatternXZ = buildPatternXZ;}

    public boolean isHoldItems() {return holdItems;}

    public void setHoldItems(boolean holdItems) {this.holdItems = holdItems;}

    public boolean isRequireMaterials() {return requireMaterials;}

    public void setRequireMaterials(boolean requireMaterials) {this.requireMaterials = requireMaterials;}

    public double getMoveTimeoutSeconds() {return moveTimeoutSeconds;}

    public void setMoveTimeoutSeconds(double moveTimeoutSeconds) {this.moveTimeoutSeconds = moveTimeoutSeconds;}

    public int getBuildYLayers() {return buildYLayers;}

    public void setBuildYLayers(int buildYLayers) {this.buildYLayers = buildYLayers;}

    @Nullable
    public Schematic getSchematic() {return schematic;}

    public void setSchematic(Schematic schematic) {this.schematic = schematic;}

    @NotNull
    public Location getOrigin() {return origin == null ? this.npc.getEntity().getLocation() : this.origin;}

    public void setOrigin(@Nullable Location origin) {this.origin = origin;}

    @Nullable
    public Location getContinueLoc() {return continueLoc == null ? null : continueLoc.clone();}

    public Map<Material, Integer> getStoredMaterials() {return Collections.unmodifiableMap(this.materials.getHandle());}

    @NotNull
    public Map<Material, Integer> getMissingMaterials() {
        Map<Material, Integer> missingMaterials = new TreeMap<>();
        for (Map.Entry<Material, Integer> entry : this.schematic.getMaterials().entrySet()) {
            Material material = entry.getKey();
            int      missing  = entry.getValue() - this.materials.getHandle().getOrDefault(material, 0);
            if (missing <= 0) {continue;}
            missingMaterials.put(material, missing);
        }
        return missingMaterials;
    }

    @Nullable
    public String getOnCancel() {return onCancel;}

    public void setOnCancel(@Nullable String onCancel) {this.onCancel = onCancel;}

    @Nullable
    public String getOnComplete() {return onComplete;}

    public void setOnComplete(@Nullable String onComplete) {this.onComplete = onComplete;}

    @Nullable
    public String getOnStart() {return onStart;}

    public void setOnStart(@Nullable String onStart) {this.onStart = onStart;}

    public int getQueuedBlocks() { return queue.size(); }

    @Override
    public void onSpawn() {
        npc.getNavigator().getDefaultParameters().avoidWater(false);

        if (state == BuilderState.BUILDING || state == BuilderState.COLLECTING) {
            new BukkitRunnable() {
                public void run() {
                    state = BuilderState.IDLE;
                    TryBuild(Bukkit.getServer().getConsoleSender());
                }
            }.runTaskLater(SchematicBuilder.getInstance(), 20);
        } else state = BuilderState.IDLE;
    }

    public void handleRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        if (this.state == BuilderState.IDLE || this.state == BuilderState.BUILDING) {
            player.performCommand("npc select " + npc.getId());
            new ParameterMenu(event.getClicker(), npc).open();
        } else if (this.state == BuilderState.COLLECTING) {
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem.getType().isBlock() && !(heldItem.getType() == Material.AIR)) {
                if (!player.hasPermission("schematicbuilder.donate")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to donate");
                    return;
                }
                Material               material          = heldItem.getType();
                Map<Material, Integer> requiredMaterials = this.schematic.getMaterials();
                int                    needed            = requiredMaterials.getOrDefault(material, 0) - this.materials.getHandle().getOrDefault(material, 0);
                Config                 config            = SchematicBuilder.getInstance().config();
                if (needed > 0) {

                    //yup, i need it
                    int taking = Math.min(heldItem.getAmount(), needed);

                    Long session = this.sessions.get(player);
                    if (session != null && System.currentTimeMillis() < session + 5 * 1000) {
                        //take it

                        //update player hand item
                        ItemStack newItem;
                        if (heldItem.getAmount() - taking > 0) {
                            newItem = heldItem.clone();
                            newItem.setAmount(heldItem.getAmount() - taking);
                        } else {newItem = new ItemStack(Material.AIR);}
                        event.getClicker().getInventory().setItemInMainHand(newItem);

                        //update needed

                        this.materials.getHandle().put(material, this.materials.getHandle().getOrDefault(material, 0) + taking);
                        player.sendMessage(SchematicBuilder.format(config.getSupplyTakenMessage(), this.npc,
                                this.schematic,
                                player,
                                heldItem.getType().name().toLowerCase(),
                                taking + ""));
                        //check if can start
                        this.TryBuild(null);

                    } else {
                        player.sendMessage(SchematicBuilder.format(config.getSupplyNeedMessage(),
                                this.npc,
                                this.schematic,
                                player,
                                heldItem.getType().name().toLowerCase(),
                                needed + ""));
                        this.sessions.put(player, System.currentTimeMillis());
                    }

                } else {
                    player.sendMessage(SchematicBuilder.format(config.getSupplyDontNeedMessage(),
                            this.npc,
                            this.schematic,
                            player,
                            heldItem.getType().name().toLowerCase(),
                            "0"));
                    //don't need it or already have it.
                }
            }
        }
    }

    @Override
    public void onRemove() {this.state = BuilderState.IDLE;}

    @Override
    public boolean toggle() {
        toggled = !toggled;
        return toggled;
    }

    public boolean isToggled() {return toggled;}

    public boolean TryBuild(CommandSender sender) {
        if (sender == null) sender = this.sender;
        this.sender = sender;

        if (this.requireMaterials) {
            int missing = 0;
            for (int value : this.getMissingMaterials().values()) {missing += value;}
            if (missing > 0) {
                if (!silent)
                    sender.sendMessage(
                            SchematicBuilder.format(SchematicBuilder.getInstance().config().getCollectingMessage(),
                                    npc,
                                    schematic, sender,
                                    schematic.getDisplayName(), String.valueOf(missing)));
                this.state = BuilderState.COLLECTING;
                return true;
            }
        }

        return StartBuild(sender);
    }

    private boolean StartBuild(CommandSender player) {
        if (!npc.isSpawned()) return false;
        if (schematic == null) return false;
        if (this.state == BuilderState.BUILDING) {
            return false;
        }

        if (origin != null) {start = origin.clone();}
        else if (continueLoc != null) {start = continueLoc.clone();}
        else {start = npc.getEntity().getLocation().clone();}

        queue = schematic.buildQueue(this);

        startingcount = queue.size();
        continueLoc = start.clone();

        mypos = npc.getEntity().getLocation().clone();

        this.state = BuilderState.BUILDING;

        if (!silent) sender.sendMessage(SchematicBuilder.format(
                SchematicBuilder.getInstance().config().getStartedMessage(),
                npc,
                schematic, player, null,
                "0"));

        if (onStart != null) {
            String resp = SchematicBuilder.runTask(onStart, npc);
            if (!silent) {
                if (resp == null) sender.sendMessage("Task " + onStart + " completed.");
                else sender.sendMessage("Task " + onStart + " could not be run: " + resp);
            }

        }

        SchematicBuilder.denizenAction(npc, "Build Start");
        SchematicBuilder.denizenAction(npc, "Build " + schematic.getDisplayName() + " Start");

        SetupNextBlock();

        return true;
    }

    public boolean StartMark(Material mat) {
        if (!npc.isSpawned()) return false;
        if (schematic == null) return false;
        if (this.state != BuilderState.IDLE) return false;

        onComplete = null;
        onCancel = null;
        onStart = null;

        mypos = npc.getEntity().getLocation().clone();

        if (origin == null) {
            continueLoc = this.npc.getEntity().getLocation().clone();
        } else {
            continueLoc = origin.clone();
        }
        queue = schematic.createMarks(mat);
        this.state = BuilderState.MARKING;

        SetupNextBlock();

        return true;
    }

    public void SetupNextBlock() {
        if (marks.isEmpty()) {
            if (schematic == null) {
                CancelBuild();
                return;
            }


            next = queue.poll();

            if (next == null) {
                CompleteBuild();
                return;
            }

            pending = Objects.requireNonNull(continueLoc.getWorld()).getBlockAt(schematic.offset(continueLoc, next.X, next.Y, next.Z));


        } else {
            clearingMarks = true;
            next = marks.remove();
            pending = Objects.requireNonNull(continueLoc.getWorld()).getBlockAt(next.X, next.Y, next.Z);

        }

        if (next.getMat().equals(pending.getLocation().getBlock().getBlockData())) {
            SetupNextBlock();
        } else {
            if (npc.isSpawned()) {
                if ((npc.getEntity() instanceof org.bukkit.entity.HumanEntity || npc.getEntity() instanceof org.bukkit.entity.Enderman) && this.holdItems) {
                    if ((npc.getEntity() instanceof org.bukkit.entity.HumanEntity) && this.holdItems) {
                        ((org.bukkit.entity.HumanEntity) npc.getEntity()).getInventory().setItemInHand(new ItemStack(next.getMat().getMaterial()));
                    } else if ((npc.getEntity() instanceof org.bukkit.entity.Enderman) && this.holdItems) {
                        ((org.bukkit.entity.Enderman) npc.getEntity()).setCarriedMaterial(new MaterialData(next.getMat().getMaterial()));
                    }
                }
            }


            new BukkitRunnable() {
                @Override
                public void run() {
                    if (npc.isSpawned()) {
                        //((Player)npc.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 1));
                        //npc.getEntity().playEffect(EntityEffect.VILLAGER_HAPPY);
                        Location loc = findaspot(pending).add(0.5, 0.5, 0.5);
                        //npc.faceLocation(loc);
                        npc.getNavigator().setTarget(loc);
                        npc.getNavigator().getLocalParameters().stationaryTicks((int) (moveTimeoutSeconds * 20));
                        npc.getNavigator().getLocalParameters().stuckAction(BuilderTeleportStuckAction.INSTANCE);
                        //npc.getNavigator().getLocalParameters().useNewPathfinder();
                        //npc.getNavigator().setPaused(false);
                        //npc.getNavigator().getPathStrategy().clearCancelReason();
                        npc.getNavigator().getPathStrategy().update();
                    }
                }
            }.runTask(SchematicBuilder.getInstance());

            canceltaskid = new BukkitRunnable() {
                public void run() {
                    if (npc.isSpawned()) {
                        if (npc.getNavigator().isNavigating()) {
                            npc.getEntity().teleport(npc.getNavigator().getTargetAsLocation());
                            //Location loc = findaspot(pending).add(0.5, 0.5, 0.5);
                            //npc.faceLocation(loc);
                            //npc.getNavigator().setTarget(loc);
                            //npc.getNavigator().getLocalParameters().useNewPathfinder();
                            //npc.getNavigator().setPaused(false);
                            //npc.getNavigator().getPathStrategy().clearCancelReason();
                            npc.getNavigator().getPathStrategy().update();
                            npc.getNavigator().cancelNavigation();
                        }
                    }
                }
            }.runTaskLater(SchematicBuilder.getInstance(), (long) (moveTimeoutSeconds * 20) + 1);
        }
    }

    public void CancelBuild() {
        if (onCancel != null) SchematicBuilder.runTask(onCancel, npc);
        SchematicBuilder.denizenAction(npc, "Build Cancel");
        if (schematic != null) SchematicBuilder.denizenAction(npc, "Build " + schematic.getDisplayName() + " Cancel");
        stop();
    }

    public void CompleteBuild() {
        if (sender == null) sender = Bukkit.getServer().getConsoleSender();

        if (this.state == BuilderState.BUILDING) {
            this.materials.getHandle().clear();
            if (!silent) sender.sendMessage(
                    SchematicBuilder.format(SchematicBuilder.getInstance().config().getCompleteMessage(),
                            npc,
                            schematic, sender,
                            null, "0"));

            if (onComplete != null) {
                String resp = SchematicBuilder.runTask(onComplete, npc);
                if (!silent) {
                    if (resp == null) sender.sendMessage("Task " + onComplete + " completed.");
                    else sender.sendMessage("Task " + onComplete + " could not be run: " + resp);
                }
            }

            SchematicBuilder.denizenAction(npc, "Build Complete");
            SchematicBuilder.denizenAction(npc, "Build " + schematic.getDisplayName() + " Complete");
        }
        stop();
    }

    private void stop() {
        boolean stop = state == BuilderState.BUILDING;
        if (canceltaskid != null && !canceltaskid.isCancelled()) canceltaskid.cancel();

        if (this.state == BuilderState.MARKING) {
            this.state = BuilderState.IDLE;
            if (origin != null) npc.getNavigator().setTarget(origin);
            else npc.getEntity().teleport(mypos);
            marks.addAll(_marks);
            _marks.clear();
        } else {
            this.state = BuilderState.IDLE;
            if (stop && npc.isSpawned()) {
                if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
                npc.getNavigator().setTarget(mypos);

				/*if(sender instanceof Player) {
					Player player = (Player) sender;
					HashMap<UUID, String> players = new HashMap<>();
					players.put(player.getUniqueId(), player.getName());
					Region region = new Region(player.getName()+"_"+UUID.randomUUID(), players, schematic.getSchematicOrigin(this), new int[]{},null, 0.0);
					RegionManager.getInstance().addRegion(region);
				}*/

            }
        }

        if ((npc.getEntity() instanceof org.bukkit.entity.HumanEntity) && this.holdItems)
            ((org.bukkit.entity.HumanEntity) npc.getEntity()).getInventory().setItemInHand(new ItemStack(Material.AIR));
        else if ((npc.getEntity() instanceof org.bukkit.entity.Enderman) && this.holdItems)
            ((org.bukkit.entity.Enderman) npc.getEntity()).setCarriedMaterial(new MaterialData(Material.AIR));

        Plugin dynmap;
        if (stop && (dynmap = Bukkit.getServer().getPluginManager().getPlugin("dynmap")) != null && dynmap.isEnabled()) {
            org.dynmap.DynmapCommonAPI dyn = (DynmapCommonAPI) dynmap;
            Objects.requireNonNull(dyn).triggerRenderOfVolume(npc.getEntity().getWorld().getName(), this.continueLoc.getBlockX() - schematic.getWidth() / 2, this.continueLoc.getBlockY(), this.continueLoc.getBlockZ() - schematic.getLength() / 2, this.continueLoc.getBlockX() + schematic.getWidth() / 2, this.continueLoc.getBlockY() + schematic.getHeight() / 2, this.continueLoc.getBlockZ() + schematic.getLength() / 2);
        }

        sender = null;
        onComplete = null;
        onCancel = null;
        onStart = null;
        continueLoc = null;
    }

    public void PlaceNextBlock() throws IllegalArgumentException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, ClassNotFoundException, InstantiationException {

        if (canceltaskid != null && !canceltaskid.isCancelled()) {
            canceltaskid.cancel();
        }

        BlockData bdata = next.getMat();


        if (state == BuilderState.MARKING && !clearingMarks) {
            _marks.add(new DataBuildBlock(pending.getX(), pending.getY(), pending.getZ(), pending.getBlockData()));
        }

        pending.setBlockData(bdata);
        pending.getWorld().playEffect(pending.getLocation(), Effect.STEP_SOUND, pending.getType());
        NMS.getInstance().getChecker().check(next, pending);

        if (this.npc.getEntity() instanceof Player) {
            //arm swing
            net.citizensnpcs.util.PlayerAnimation.ARM_SWING.play((Player) this.npc.getEntity(), 64);
        }

        if (marks.size() == 0) clearingMarks = false;


        SetupNextBlock();


    }

    //Given a BuildBlock to place, find a good place to stand to place it.
    private Location findaspot(Block base) {
        if (base == null) return null;

        for (int a = 3; a >= -5; a--) {
            if (Util.canStand(base.getRelative(0, a, -1)))
                return base.getRelative(0, a - 1, -1).getLocation();
            if (Util.canStand(base.getRelative(0, a, 1)))
                return base.getRelative(0, a - 1, 1).getLocation();
            if (Util.canStand(base.getRelative(1, a, 0)))
                return base.getRelative(1, a - 1, 0).getLocation();
            if (Util.canStand(base.getRelative(-1, a, 0)))
                return base.getRelative(-1, a - 1, 0).getLocation();
            if (Util.canStand(base.getRelative(-1, a, -1)))
                return base.getRelative(-1, a - 1, -1).getLocation();
            if (Util.canStand(base.getRelative(-1, a, 1)))
                return base.getRelative(-1, a - 1, 1).getLocation();
            if (Util.canStand(base.getRelative(1, a, 1)))
                return base.getRelative(1, a - 1, 1).getLocation();
            if (Util.canStand(base.getRelative(1, a, -1)))
                return base.getRelative(1, a - 1, -1).getLocation();
        }
        return base.getLocation();
    }

    public enum BuilderState {IDLE, BUILDING, MARKING, COLLECTING}

    public enum BuildPatternXZ {SPIRAL, REVERSE_SPIRAL, LINEAR, REVERSE_LINEAR}
}





