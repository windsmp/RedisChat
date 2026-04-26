package dev.unnm3d.redischat.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.unnm3d.redischat.Permissions;
import dev.unnm3d.redischat.RedisChat;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MsgToggleCommand {
    private final RedisChat plugin;

    public CommandAPICommand getCommand() {
        return new CommandAPICommand("msgtoggle")
                .withPermission(Permissions.MSG_TOGGLE.getPermission())
                .withAliases(plugin.config.getCommandAliases("msgtoggle"))
                .executesPlayer((sender, args) -> {
                    boolean disabled = plugin.getChannelManager().togglePrivateMessages(sender.getName());
                    plugin.messages.sendMessage(sender, disabled ?
                            plugin.messages.private_messages_disabled :
                            plugin.messages.private_messages_enabled);
                });
    }
}
