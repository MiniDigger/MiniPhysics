package dev.benndorf.miniphysics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.ode4j.ode.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MiniPhysics extends JavaPlugin implements Listener {

    private final Map<UUID, Data> dataMap = new HashMap<>();
    private BukkitTask bukkitTask;
    private boolean shouldStep = true;
    private double step = 0.01;
    private final int maxContacts = 16;
    private double t = 0;

    private DWorld world;
    private DSpace space;
    private DJointGroup contactgroup;

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
        data.box().getBody().addForceAtRelPos(0, 10, 0, e.getClickedPosition().getX(), e.getClickedPosition().getY(), e.getClickedPosition().getZ());
    }

    record Data(BlockDisplay blockDisplay, Interaction interaction, String name, DBox box) {
        public void update() {
            blockDisplay.teleport(new Location(blockDisplay.getWorld(), box.getPosition().get0(), box.getPosition().get1(), box.getPosition().get2()));
            blockDisplay.setTransformation(new Transformation(
                            new Vector3f((float) (box.getLengths().get0() * -0.5f), (float) (box.getLengths().get1() * -0.5f), (float) (box.getLengths().get2() * -0.5f)),
                            new AxisAngle4f(0, 0, 0, 0),
                            new Vector3f((float) box.getLengths().get0(), (float) box.getLengths().get1(), (float) box.getLengths().get2()),
                            new AxisAngle4f(0, 0, 0, 0)
                    )
            );

            interaction.teleport(new Location(blockDisplay.getWorld(), box.getPosition().get0(), box.getPosition().get1() - (0.5* box.getLengths().get1()), box.getPosition().get2()));
            interaction.setInteractionHeight((float) box.getLengths().get1());
            interaction.setInteractionWidth((float) box.getLengths().get0());
        }

        public void remove() {
            blockDisplay.remove();
            interaction.remove();
        }
    }

    private void spawnEntity(DBox box, String name) {
        World bukkitWorld = Bukkit.getWorlds().get(0);
        BlockDisplay blockDisplay = bukkitWorld.spawn(new Location(bukkitWorld, box.getPosition().get0(), box.getPosition().get1(), box.getPosition().get2()), BlockDisplay.class);
        blockDisplay.setCustomNameVisible(true);
        blockDisplay.customName(Component.text(name));
        blockDisplay.setBlock(Bukkit.createBlockData(Material.GLASS));
        blockDisplay.setPersistent(true);
        blockDisplay.setTeleportDuration(1);
        blockDisplay.setInterpolationDelay(0);
        blockDisplay.setInterpolationDuration(1);
        blockDisplay.setShadowRadius((float) ((box.getLengths().get0() + box.getLengths().get2()) / 4));
        blockDisplay.setShadowStrength(blockDisplay.getShadowRadius() / 2 + 1);
        blockDisplay.setDisplayHeight((float) box.getLengths().get1() * 4);
        blockDisplay.setBrightness(new Display.Brightness(15, 15));

        Interaction interaction = bukkitWorld.spawn(blockDisplay.getLocation(), Interaction.class);
        interaction.setCustomNameVisible(true);
        interaction.customName(Component.text(name + " Interaction"));
        interaction.setResponsive(true);

        Data data = new Data(blockDisplay, interaction, name, box);
        box.setData(data);
        dataMap.put(interaction.getUniqueId(), data);
    }

    public void start() {
        OdeHelper.initODE();
        world = OdeHelper.createWorld();
        world.setGravity(0, -9.81, 0);
        world.setDamping(1e-4, 1e-5);
        world.setQuickStepNumIterations(50);

        space = OdeHelper.createSimpleSpace();
        contactgroup = OdeHelper.createJointGroup();

        {
            DBody body = OdeHelper.createBody(world);
            body.setPosition(0, 110, 1);
            DBox box = OdeHelper.createBox(space, 1, 1, 1);
            box.setBody(body);
            DMass mass = OdeHelper.createMass();
            mass.setBox(1, 1, 1, 1);
            body.setMass(mass);
            spawnEntity(box, "Box 1");
        }
        {
            DBody body = OdeHelper.createBody(world);
            body.setPosition(0, 110, 4);
            DBox box = OdeHelper.createBox(space, 2, 2, 2);
            box.setBody(body);
            DMass mass = OdeHelper.createMass();
            mass.setBox(20, 2, 2, 2);
            body.setMass(mass);
            spawnEntity(box, "Box 2");
        }

        OdeHelper.createPlane(space, 0, 1, 0, 101);

        Thread physicsThread = new Thread(() -> {
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

        bukkitTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (DGeom geom : space.getGeoms()) {
                if (geom.getData() instanceof Data data) {
                    data.update();
                }
            }
        }, 0, 1);
    }

    public void stop() {
        shouldStep = false;
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }

        for (DGeom geom : space.getGeoms()) {
            if (geom.getData() instanceof Data data) {
                data.remove();
            }
        }

        dataMap.clear();
        contactgroup.destroy();
        space.destroy();
        world.destroy();
        OdeHelper.closeODE();
    }

    public void step() {
        t += step;

        space.collide(0, (data, o1, o2) -> {
            DBody b1 = o1.getBody();
            DBody b2 = o2.getBody();

            DContactBuffer contacts = new DContactBuffer(maxContacts);

            int numc = OdeHelper.collide(o1, o2, maxContacts,contacts.getGeomBuffer());

            for (int i = 0; i < numc; i++) {
                contacts.get(i).surface.mode = OdeConstants.dContactBounce | OdeConstants.dContactSoftCFM;
                // friction parameter
                contacts.get(i).surface.mu = Double.MAX_VALUE;
                contacts.get(i).surface.bounce = 0.9;
                contacts.get(i).surface.bounce_vel = 0.01;
                contacts.get(i).surface.soft_cfm = 0.001;
                DJoint c = OdeHelper.createContactJoint(world, contactgroup, contacts.get(i));
                c.attach(b1, b2);
            }
        });
        world.quickStep(step);
        contactgroup.empty();
    }
}
