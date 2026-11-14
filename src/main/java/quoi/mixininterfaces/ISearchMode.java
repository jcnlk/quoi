package quoi.mixininterfaces;

import net.minecraft.client.multiplayer.chat.GuiMessage;

public interface ISearchMode {
    boolean quoi$isSearchActive();

    void quoi$queueMessage(GuiMessage message);
}
