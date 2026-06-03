package me.desht.clicksort.commands;

import me.desht.clicksort.ClickSortPlugin;
import me.desht.clicksort.LanguageLoader;
import me.desht.clicksort.PlayerSortingPrefs;
import me.desht.clicksort.SortingMethod;
import me.desht.dhutils.CompatUtil;
import me.desht.dhutils.DHValidate;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class ChangeSortModeCommand extends AbstractCommand {

    public ChangeSortModeCommand() {
        super("clicksort sort", 1, 1);
        setPermissionNode("clicksort.commands.sort");
        if (CompatUtil.isMaterialIdAllowed()) {
            setUsage("/clicksort sort <id|name|group>");
        } else {
            setUsage("/clicksort sort <name|group>");
        }
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        notFromConsole(sender);

        try {
            PlayerSortingPrefs prefs = ((ClickSortPlugin) plugin).getSortingPrefs();
            SortingMethod sortMethod = SortingMethod.valueOf(args[0].toUpperCase());
            DHValidate.isTrue(sortMethod.isAvailable(),
                    LanguageLoader.getColoredMessage("sortingMethodNotAvailable",
                            Placeholder.unparsed("method", sortMethod.toString())));
            prefs.setSortingMethod((Player) sender, sortMethod);
            MiscUtil.statusMessage(sender,
                    LanguageLoader.getColoredMessage("setSortingMethodTo",
                            Placeholder.unparsed("method", sortMethod.toString())));
        } catch (IllegalArgumentException e) {
            showUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(Plugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return getEnumCompletions(sender, SortingMethod.class, args[0]);
        } else {
            return noCompletions(sender);
        }
    }
}
