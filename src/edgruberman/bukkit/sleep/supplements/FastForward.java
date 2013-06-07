package edgruberman.bukkit.sleep.supplements;

import java.text.MessageFormat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.Reason;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.Supplement;
import edgruberman.bukkit.sleep.events.SleepAdd;
import edgruberman.bukkit.sleep.events.SleepEnter;
import edgruberman.bukkit.sleep.events.SleepIgnoredChanged;
import edgruberman.bukkit.sleep.events.SleepLeave;
import edgruberman.bukkit.sleep.events.SleepNotify;
import edgruberman.bukkit.sleep.events.SleepRemove;
import edgruberman.bukkit.sleep.events.SleepStatus;
import edgruberman.bukkit.sleep.util.CustomLevel;

public final class FastForward extends Supplement implements Runnable {

    private final double min;
    private final double max;
    private final boolean scale;
    private final long speed;

    private double percent = 0D;
    private double ticks = 0D;
    private double carry = 0D;

    private int taskId = -1;
    private boolean notify = false;

    public FastForward(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
        this.min = config.getInt("min") / 100D;
        this.max = config.getInt("max") / 100D;
        this.scale = config.getBoolean("scale");
        this.speed = config.getLong("speed");

        this.logConfig(MessageFormat.format("Fast-Forward min: {0,number,#.##%}; max: {1,number,#.##%}; scale: {2}; speed: {3} ticks", this.min, this.max, this.scale, this.speed));
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepNotify(final SleepNotify notify) {
        final int needed = (int) Math.ceil(this.max * notify.getPossible());
        if (needed < notify.getNeeded()) notify.setNeeded(needed);
        if (!this.notify) return;

        final int start = (int) Math.ceil(notify.getPossible() * this.min);
        final int force = (int) Math.ceil(notify.getPossible() * this.max);
        if (force < notify.getNeeded()) notify.setNeeded(force);
        this.state.courier.world(this.state.world, "fast-forward.notify", this.percent, notify.getNeeded(), notify.getSleeping(), notify.getPossible(), start);
        this.notify = false;
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepStatus(final SleepStatus status) {
        if (!status.getWorld().equals(this.state.world)) return;

        final int start = (int) Math.ceil(status.getPossible() * this.min);
        final int force = (int) Math.ceil(status.getPossible() * this.max);
        if (force < status.getNeeded()) status.setNeeded(force);
        this.state.courier.send(status.getRequestor(), "fast-forward.status", this.percent, status.getNeeded(), status.getSleeping(), status.getPossible(), start);
    }

    private void update() {
        final double sleeping = this.state.sleeping.size();
        final double possible = this.state.possible().size();
        this.percent = sleeping / possible ;
        if (Double.isNaN(this.percent)) this.percent = 0D;
        this.implementor.getLogger().log(CustomLevel.DEBUG, "[{0}] Fast-Forward percent: {1,number,#.##%} = {2,number,#} in bed / {3,number,#} possible"
                , new Object[] { this.state.world.getName(), this.percent, sleeping, possible });

        if (this.percent < this.min) {
            this.stop();
            return;
        }

        if (this.percent >= this.max) {
            this.stop();
            if (this.state.needed() > 0) this.state.force(null);
            return;
        }

        if (this.scale) this.percent = (this.percent - this.min) / (this.max - this.min);
        final double ticksNow = this.speed * this.percent / (1 - this.percent); // ticks * (percent / percent allotted to apply)
        if (ticksNow == this.ticks) return;

        this.notify = true;
        this.implementor.getLogger().log(CustomLevel.DEBUG, "[{0}] Fast-Forward scaled: {1,number,#.##%}; ticks: {2,number,#.##} (Previously: {3,number,#.##})"
                , new Object[] { this.state.world.getName(), this.percent, ticksNow, this.ticks });

        if (ticksNow == 0) {
            this.stop();
            return;
        }

        Bukkit.getScheduler().cancelTask(this.taskId);
        this.ticks = ticksNow;
        this.carry = 0;
        this.taskId = Bukkit.getScheduler().runTaskTimer(this.implementor, this, 0, this.speed).getTaskId();
    }

    private void stop() {
        Bukkit.getScheduler().cancelTask(this.taskId);
        this.percent = 0D;
        this.carry = 0D;
        this.ticks = 0D;
    }

    @Override
    public void run() {
        this.carry += this.ticks % 1;
        final long from = this.state.world.getTime();
        final long to = from + (long) this.ticks + (long) this.carry;
        this.state.world.setTime(to);
        this.carry = this.carry % 1;
        this.implementor.getLogger().log(CustomLevel.TRACE, "[{0}] Fast-Forward from: {1} to: {2} (carry: {3,number,#.##})"
                , new Object[] { this.state.world.getName(), from, to, this.carry });
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepIgnoredChange(final SleepIgnoredChanged changed) {
        if (!changed.getPlayer().getWorld().equals(this.state.world)) return;
        if (changed.getReason() == Reason.FORCE) return;
        this.update();
    }

    @EventHandler
    private void onSleepAdd(final SleepAdd add) {
        if (add.getState() != this.state) return;
        this.update();
    }

    @EventHandler
    private void onSleepRemove(final SleepRemove remove) {
        if (remove.getState() != this.state) return;
        this.update();
    }

    @EventHandler
    private void onSleepEnter(final SleepEnter enter) {
        if (enter.getState() != this.state) return;
        this.update();
    }

    @EventHandler
    private void onSleepLeave(final SleepLeave leave) {
        if (leave.getState() != this.state) return;
        this.update();
    }

}
