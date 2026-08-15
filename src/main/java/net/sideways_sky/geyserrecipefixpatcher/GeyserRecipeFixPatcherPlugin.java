package net.sideways_sky.geyserrecipefixpatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class GeyserRecipeFixPatcherPlugin extends JavaPlugin {

    private PatchRunner runner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            runner = new PatchRunner(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize patcher state: " + e);
            return;
        }

        if (getConfig().getBoolean("check-on-startup", true)) {
            // Network I/O - keep it off the main thread so it never delays
            // server startup or trips the watchdog.
            getServer().getScheduler().runTaskAsynchronously(this, () ->
                    runner.run(msg -> getLogger().info(msg)));
        } else {
            getLogger().info("check-on-startup is disabled in config.yml. Use /grfpatcher update to run manually.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserrecipefixpatcher.admin")) {
            sender.sendMessage("You don't have permission to use this.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";
        switch (sub) {
            case "status" -> {
                String installed = runner.state().installedVersion();
                sender.sendMessage("Installed GeyserRecipeFix version: " + (installed == null ? "none" : installed));
            }
            case "check", "update" -> {
                sender.sendMessage("Checking Modrinth for the latest GeyserRecipeFix build...");
                getServer().getScheduler().runTaskAsynchronously(this, () ->
                        runner.run(sender::sendMessage));
            }
            default -> sender.sendMessage("Usage: /grfpatcher <status|update>");
        }
        return true;
    }
}
