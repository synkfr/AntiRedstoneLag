package org.ayosynk.antiRedstoneLag.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Typed InventoryHolder binding GUI screens and per-slot click handlers without fragile title matching.
 */
public class ArlMenuHolder implements InventoryHolder {

    public enum MenuPage {
        MAIN,
        HOTSPOTS,
        SNAPSHOTS,
        LOCKED,
        CLEARLAGG
    }

    private final MenuPage menuPage;
    private final int pageIndex;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Inventory inventory;

    public ArlMenuHolder(MenuPage menuPage, int pageIndex) {
        this.menuPage = menuPage;
        this.pageIndex = pageIndex;
    }

    public void bindInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setAction(int slot, Consumer<InventoryClickEvent> handler) {
        if (handler != null) {
            actions.put(slot, handler);
        }
    }

    public void handleClick(int slot, InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(slot);
        if (action != null) {
            action.accept(event);
        }
    }

    public MenuPage getMenuPage() {
        return menuPage;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
