package edgruberman.bukkit.sleep;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockActivity extends org.bukkit.event.block.BlockListener implements ActivityMonitor {
    
    /**
     * Events this listener recognizes and can monitor player activity for.
     */
    static final Set<Event.Type> SUPPORTS = new HashSet<Event.Type>(Arrays.asList(
              Event.Type.BLOCK_BREAK
            , Event.Type.BLOCK_DAMAGE
            , Event.Type.BLOCK_IGNITE
            , Event.Type.BLOCK_PLACE
    ));
    
    @Override
    public Set<Type> supports() {
        return BlockActivity.SUPPORTS;
    }
    
    @Override
    public void onBlockBreak(final BlockBreakEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onBlockDamage(final BlockDamageEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onBlockIgnite(final BlockIgniteEvent event) {
        if (event.isCancelled()) return;
        
        if (event.getPlayer() == null) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
}