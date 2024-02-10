package dev.benndorf.miniphysics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import com.jme3.system.NativeLibraryLoader;
import org.joml.AxisAngle4f;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MiniPhysics extends JavaPlugin implements Listener {

    private final Map<UUID, Data> dataMap = new HashMap<>();
    private BukkitTask bukkitTask;
    private boolean shouldStep = true;
    private float step = 0.01f;
    private double t = 0;

    private PhysicsSpace space;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }

        switch (args[0]) {
            case "start" -> start();
            case "stop" -> stop();
        }
        return true;
    }

    @EventHandler
    public void playerInteract(PlayerInteractAtEntityEvent e) {
        Data data = dataMap.get(e.getRightClicked().getUniqueId());
        if (data == null) {
            e.getPlayer().sendMessage(Component.text("No data found"));
            return;
        }

        e.getPlayer().sendMessage(Component.text("interact " + e.getClickedPosition() + ": " + data.name()));

        // TODO figure out the right force
        // TODO figure out why this isn't doing shit
        //data.body().getBody().addForceAtRelPos(0, 10, 0, e.getClickedPosition().getX(), e.getClickedPosition().getY(), e.getClickedPosition().getZ());
    }

    record Data(BlockDisplay blockDisplay, Interaction interaction, String name, PhysicsBody body) {
        public void update() {
            Vector3f physicsLocation = body.getPhysicsLocation(null);
            BoxCollisionShape box = (BoxCollisionShape) body.getCollisionShape();
            Vector3f halfExtents = box.getHalfExtents(null);
            blockDisplay.teleport(new Location(blockDisplay.getWorld(), physicsLocation.x, physicsLocation.y, physicsLocation.z));
            blockDisplay.setTransformation(new Transformation(
                            new org.joml.Vector3f(-halfExtents.x, -halfExtents.y, -halfExtents.z),
                            new AxisAngle4f(0, 0, 0, 0),
                            new org.joml.Vector3f(halfExtents.x * 2, halfExtents.y * 2, halfExtents.z * 2),
                            new AxisAngle4f(0, 0, 0, 0)
                    )
            );

            interaction.teleport(new Location(blockDisplay.getWorld(), physicsLocation.x, physicsLocation.y - halfExtents.y, physicsLocation.z));
            interaction.setInteractionHeight(halfExtents.y * 2);
            interaction.setInteractionWidth(halfExtents.x * 2);
        }

        public void remove() {
            blockDisplay.remove();
            interaction.remove();
        }
    }

    private void spawnEntity(PhysicsBody body, String name) {
        Vector3f physicsLocation = body.getPhysicsLocation(null);
        BoxCollisionShape box = (BoxCollisionShape) body.getCollisionShape();
        Vector3f halfExtents = box.getHalfExtents(null);

        World bukkitWorld = Bukkit.getWorlds().get(0);
        BlockDisplay blockDisplay = bukkitWorld.spawn(new Location(bukkitWorld, physicsLocation.x, physicsLocation.y, physicsLocation.z), BlockDisplay.class);
        blockDisplay.setCustomNameVisible(true);
        blockDisplay.customName(Component.text(name));
        blockDisplay.setBlock(Bukkit.createBlockData(Material.GLASS));
        blockDisplay.setPersistent(true);
        blockDisplay.setTeleportDuration(1);
        blockDisplay.setInterpolationDelay(0);
        blockDisplay.setInterpolationDuration(1);
        blockDisplay.setShadowRadius((halfExtents.x + halfExtents.z) / 2);
        blockDisplay.setShadowStrength(blockDisplay.getShadowRadius() / 2 + 1);
        blockDisplay.setDisplayHeight(halfExtents.y * 8);
        blockDisplay.setBrightness(new Display.Brightness(15, 15));

        Interaction interaction = bukkitWorld.spawn(blockDisplay.getLocation(), Interaction.class);
        interaction.setCustomNameVisible(true);
        interaction.customName(Component.text(name + " Interaction"));
        interaction.setResponsive(true);

        Data data = new Data(blockDisplay, interaction, name, body);
        body.setUserObject(data);
        dataMap.put(interaction.getUniqueId(), data);
    }

    public void start() {
        Thread physicsThread = new Thread(() -> {
            String homePath = System.getProperty("user.home");
            File downloadDirectory = new File(homePath, "Downloads");
            NativeLibraryLoader.loadLibbulletjme(true, downloadDirectory, "Debug", "Sp");

            space = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);

            Plane plane = new Plane(Vector3f.UNIT_Y, 101);
            CollisionShape planeShape = new PlaneCollisionShape(plane);
            PhysicsRigidBody floor = new PhysicsRigidBody(planeShape, PhysicsBody.massForStatic);
            space.addCollisionObject(floor);

            {
                CollisionShape boxShape = new BoxCollisionShape(0.5f);
                PhysicsRigidBody box = new PhysicsRigidBody(boxShape, 1);
                box.setPhysicsLocation(new Vector3f(0, 110, 1));
                space.addCollisionObject(box);
                Bukkit.getScheduler().runTask(this, () -> spawnEntity(box, "Box 1"));
            }

            {
                CollisionShape boxShape = new BoxCollisionShape(1);
                PhysicsRigidBody box = new PhysicsRigidBody(boxShape, 2);
                box.setPhysicsLocation(new Vector3f(0, 110, 4));
                space.addCollisionObject(box);
                Bukkit.getScheduler().runTask(this, () -> spawnEntity(box, "Box 2"));
            }

            bukkitTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (PhysicsCollisionObject collisionObject : space.getPcoList()) {
                    if (collisionObject.getUserObject() instanceof Data data) {
                        data.update();
                    }
                }
            }, 0, 1);

            shouldStep = true;
            while (shouldStep) {
                step();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    shouldStep = false;
                }
            }
        });
        physicsThread.setName("Physics Thread");
        physicsThread.start();
    }

    public void stop() {
        shouldStep = false;
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }

        for (PhysicsCollisionObject collisionObject : space.getPcoList()) {
            if (collisionObject.getUserObject() instanceof Data data) {
                data.remove();
            }
        }

        dataMap.clear();
        space.destroy();
    }

    public void step() {
        t += step;
        space.update(step);
    }
}
