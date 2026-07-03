package quoi.mixins;

import quoi.module.impl.misc.ChatReplacements;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(Gui.class)
public class GuiMixin {

    @Unique private static final Pattern DATE_LINE_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2}).*$");
    @Unique private static final Pattern STRIP_ALL_COLOR_PATTERN = Pattern.compile("(?i)§.");

    @Redirect(
            method = "displayScoreboardSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/Scoreboard;listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;"
            )
    )
    private Collection<PlayerScoreEntry> filterScores(Scoreboard scoreboard, Objective objective) {
        Collection<PlayerScoreEntry> originalList = scoreboard.listPlayerScores(objective);

        if (!ChatReplacements.getShouldHideServerId()) {
            return originalList;
        }

        List<PlayerScoreEntry> filteredList = new ArrayList<>();

        for (PlayerScoreEntry entry : originalList) {
            String ownerName = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(ownerName);

            Component baseComponent = entry.display() != null ? entry.display() : Component.literal(ownerName);
            Component formattedComponent = PlayerTeam.formatNameForTeam(team, baseComponent);
            String visibleText = formattedComponent.getString();

            String rawVisible = STRIP_ALL_COLOR_PATTERN.matcher(visibleText).replaceAll("").toLowerCase().trim();
            String rawOwner = STRIP_ALL_COLOR_PATTERN.matcher(ownerName).replaceAll("").toLowerCase().trim();

            if (rawVisible.contains("hypixel.net") || rawOwner.contains("hypixel.net")) {
                continue;
            }

            String cleanTextForDate = STRIP_ALL_COLOR_PATTERN.matcher(visibleText).replaceAll("").trim();
            Matcher matcher = DATE_LINE_PATTERN.matcher(cleanTextForDate);

            if (matcher.find()) {
                String dateOnly = matcher.group(1);
                String newText = "§7" + dateOnly;

                PlayerScoreEntry newEntry = new PlayerScoreEntry(
                        newText,
                        entry.value(),
                        entry.display(),
                        entry.numberFormatOverride()
                );

                filteredList.add(newEntry);
                continue;
            }

            filteredList.add(entry);
        }

        return filteredList;
    }
}
