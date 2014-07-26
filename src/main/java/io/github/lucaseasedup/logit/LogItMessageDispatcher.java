/*
 * LogItMessageDelegate.java
 *
 * Copyright (C) 2012-2014 LucasEasedUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.lucaseasedup.logit;

import static io.github.lucaseasedup.logit.util.MessageHelper._;
import static io.github.lucaseasedup.logit.util.MessageHelper.broadcastMsgExcept;
import static io.github.lucaseasedup.logit.util.MessageHelper.sendMsg;
import io.github.lucaseasedup.logit.hooks.VanishNoPacketHook;
import io.github.lucaseasedup.logit.locale.Locale;
import io.github.lucaseasedup.logit.util.JoinMessageGenerator;
import io.github.lucaseasedup.logit.util.QuitMessageGenerator;
import java.util.Arrays;
import java.util.Hashtable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class LogItMessageDispatcher extends LogItCoreObject implements Listener
{
    @Override
    public void dispose()
    {
        if (forceLoginPromptIntervals != null)
        {
            forceLoginPromptIntervals.clear();
            forceLoginPromptIntervals = null;
        }
    }
    
    public void dispatchMessage(final String username, final String message, long delay)
    {
        if (username == null || message == null || delay < 0)
            throw new IllegalArgumentException();
        
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                Player player = Bukkit.getPlayerExact(username);
                
                if (player != null)
                {
                    player.sendMessage(message);
                }
            }
        }.runTaskLater(getPlugin(), delay);
    }
    
    public void dispatchMessage(Player player, String message, long delay)
    {
        dispatchMessage(player.getName(), message, delay);
    }
    
    /**
     * Sends a message to the given player telling them either to log in or to register.
     * 
     * <p> This method's behavior may be altered by the configuration file.
     * 
     * @param player the player to whom the message will be sent.
     * 
     * @throws IllegalArgumentException if {@code player} is {@code null}.
     */
    public void sendForceLoginMessage(Player player)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        long minInterval = getConfig("config.yml")
                .getTime("force-login.prompt.min-interval", TimeUnit.MILLISECONDS);
        
        if (minInterval > 0)
        {
            long currentTimeMillis = System.currentTimeMillis();
            Long playerInterval = forceLoginPromptIntervals.get(player);
            
            if (playerInterval != null && currentTimeMillis - playerInterval < minInterval)
                return;
            
            forceLoginPromptIntervals.put(player, currentTimeMillis);
        }
        
        if (getAccountManager().isRegistered(player.getName()))
        {
            if (getConfig("config.yml").getBoolean("force-login.prompt.login"))
            {
                if (!getConfig("config.yml").getBoolean("password.disable-passwords"))
                {
                    sendMsg(player, _("pleaseLogIn"));
                }
                else
                {
                    sendMsg(player, _("pleaseLogIn_noPassword"));
                }
            }
        }
        else
        {
            if (getConfig("config.yml").getBoolean("force-login.prompt.register"))
            {
                if (!getConfig("config.yml").getBoolean("password.disable-passwords"))
                {
                    sendMsg(player, _("pleaseRegister"));
                }
                else
                {
                    sendMsg(player, _("pleaseRegister_noPassword"));
                }
            }
        }
    }
    
    public void dispatchForceLoginPrompter(String username, long delay)
    {
        if (username == null || delay < 0)
            throw new IllegalArgumentException();
        
        new ForceLoginPrompter(username).runTaskLater(getPlugin(), delay);
    }
    
    public void dispatchRepeatingForceLoginPrompter(String username, long delay, long period)
    {
        if (username == null || delay < 0 || period <= 0)
            throw new IllegalArgumentException();
        
        new ForceLoginPrompter(username).runTaskTimer(getPlugin(), delay, period);
    }
    
    /**
     * Broadcasts a join message.
     * 
     * @param player the player who joined.
     */
    public void broadcastJoinMessage(Player player)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        if (VanishNoPacketHook.isVanished(player))
            return;
        
        String joinMessage = JoinMessageGenerator.generate(player,
                getConfig("config.yml").getBoolean("messages.join.show-world"));
        
        broadcastMsgExcept(joinMessage, Arrays.asList(player.getName()));
    }
    
    /**
     * Broadcasts a quit message.
     * 
     * @param player the player who quit.
     */
    public void broadcastQuitMessage(Player player)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        if (VanishNoPacketHook.isVanished(player))
            return;
        
        String quitMessage = QuitMessageGenerator.generate(player);
        
        broadcastMsgExcept(quitMessage, Arrays.asList(player.getName()));
    }
    
    public void sendCooldownMessage(String username, long cooldownMillis)
    {
        if (username == null)
            throw new IllegalArgumentException();
        
        Locale activeLocale = getLocaleManager().getActiveLocale();
        int cooldownSecs = (int) TimeUnit.MILLISECONDS.convert(cooldownMillis, TimeUnit.SECONDS);
        String cooldownText = activeLocale.stringifySeconds(cooldownSecs);
        
        if (cooldownMillis >= 2000L)
        {
            sendMsg(username, _("cooldown.moreThanSecond")
                    .replace("{0}", cooldownText));
        }
        else
        {
            sendMsg(username, _("cooldown.secondOrLess")
                    .replace("{0}", cooldownText));
        }
    }
    
    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event)
    {
        forceLoginPromptIntervals.remove(event.getPlayer());
    }
    
    @EventHandler
    private void onPlayerKick(PlayerKickEvent event)
    {
        forceLoginPromptIntervals.remove(event.getPlayer());
    }
    
    private final class ForceLoginPrompter extends BukkitRunnable
    {
        public ForceLoginPrompter(String username)
        {
            this.username = username;
        }
        
        @Override
        public void run()
        {
            Player player = Bukkit.getPlayerExact(username);
            
            if (player == null || !isCoreStarted())
            {
                cancel();
            }
            else if (getCore().isPlayerForcedToLogIn(player))
            {
                if (!getSessionManager().isSessionAlive(player))
                {
                    sendForceLoginMessage(player);
                }
                else
                {
                    cancel();
                }
            }
        }
        
        private final String username;
    }
    
    private Hashtable<Player, Long> forceLoginPromptIntervals = new Hashtable<>();
}
