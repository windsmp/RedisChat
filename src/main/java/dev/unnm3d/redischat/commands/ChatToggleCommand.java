package dev.unnm3d.redischat.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.unnm3d.redischat.Permissions;
import dev.unnm3d.redischat.RedisChat;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ChatToggleCommand {
    private final RedisChat plugin;

    public CommandAPICommand getCommand() {
        return new CommandAPICommand("chattoggle")
                .withPermission(Permissions.CHAT_TOGGLE.getPermission())
                .withAliases(plugin.config.getCommandAliases("chattoggle"))
                .executesPlayer((sender, args) -> {
                    boolean disabled = plugin.getChannelManager().togglePlayerChat(sender.getName());
                    plugin.messages.sendMessage(sender, disabled ?
                            plugin.messages.chat_disabled :
                            plugin.messages.chat_enabled);
                });
    }
}
