package com.supabillford;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SupaBillford extends JavaPlugin implements Listener {
    private String guiTitle;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SupaBillford enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setbillforditem")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color("&cThis command can only be used by a player."));
                return true;
            }
            if (!player.hasPermission("supabillford.admin")) {
                player.sendMessage(message("no-permission"));
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(color("&cUsage: /setbillforditem <key>"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                player.sendMessage(color("&cHold an item in your main hand first."));
                return true;
            }
            String key = args[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
            if (key.isBlank()) {
                player.sendMessage(color("&cInvalid key."));
                return true;
            }
            getConfig().set("trade-items." + key, hand.clone());
            saveConfig();
            player.sendMessage(color(message("item-saved").replace("%key%", key)));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("supabillford.admin")) {
                sender.sendMessage(message("no-permission"));
                return true;
            }
            reloadConfig();
            sender.sendMessage(message("reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.hasPermission("supabillford.use")) {
            player.sendMessage(message("no-permission"));
            play(player, "sounds.deny");
            return true;
        }
        openTrade(player);
        return true;
    }

    private void openTrade(Player player) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("gui.rows", 6)));
        guiTitle = color(getConfig().getString("gui.title", "<##3F3F3F>ʙɪʟʟꜰᴏʀᴅ"));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, guiTitle);

        ItemStack filler = createItem(getConfig().getConfigurationSection("gui.filler"));
        for (int slot : getConfig().getIntegerList("gui.filler-slots")) {
            if (validSlot(slot, inventory)) inventory.setItem(slot, filler.clone());
        }

        int resultSlot = getConfig().getInt("gui.result-slot", 25);
        int tradeSlot = getConfig().getInt("gui.trade-slot", 23);
        int bookSlot = getConfig().getInt("gui.book-slot", 49);
        if (validSlot(resultSlot, inventory)) {
            inventory.setItem(resultSlot, configuredItem("trade.reward"));
        }
        if (validSlot(tradeSlot, inventory)) {
            inventory.setItem(tradeSlot, createItem(getConfig().getConfigurationSection("gui.trade-button")));
        }

        List<Map<?, ?>> requirements = getConfig().getMapList("trade.required-items");
        List<Integer> previewSlots = getConfig().getIntegerList("gui.preview-slots");
        for (int i = 0; i < requirements.size() && i < previewSlots.size(); i++) {
            int slot = previewSlots.get(i);
            if (validSlot(slot, inventory)) {
                inventory.setItem(slot, createItem(requirements.get(i)));
            }
        }

        ItemStack book = createItem(getConfig().getConfigurationSection("gui.summary-book"));
        ItemMeta bookMeta = book.getItemMeta();
        if (bookMeta != null) {
            List<String> lore = new ArrayList<>();
            for (Map<?, ?> requirement : requirements) {
                String line = Objects.toString(requirement.get("lore-line"), null);
                if (line == null || line.isBlank()) {
                    ItemStack item = createItem(requirement);
                    lore.add(color("&7" + item.getAmount() + "x &f" + readableName(item.getType())));
                } else {
                    lore.add(color(line));
                }
            }
            lore.add(color("&7for"));
            String rewardLine = getConfig().getString("trade.reward.lore-line");
            if (rewardLine == null || rewardLine.isBlank()) {
                ItemStack reward = configuredItem("trade.reward");
                lore.add(color("&7" + reward.getAmount() + "x &f" + readableName(reward.getType())));
            } else {
                lore.add(color(rewardLine));
            }
            bookMeta.setLore(lore);
            book.setItemMeta(bookMeta);
        }
        if (validSlot(bookSlot, inventory)) inventory.setItem(bookSlot, book);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isBillfordInventory(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int tradeSlot = getConfig().getInt("gui.trade-slot", 23);
        if (event.getRawSlot() != tradeSlot) {
            if (getConfig().getIntegerList("gui.preview-slots").contains(event.getRawSlot())) {
                play(player, "sounds.fail");
            }
            return;
        }
        completeTrade(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isBillfordInventory(event.getView().getTitle())) event.setCancelled(true);
    }

    private void completeTrade(Player player) {
        List<ItemStack> required = getRequiredItems();
        if (required.isEmpty()) {
            player.sendMessage(color("&cBillford's trade is not configured correctly. Please contact an administrator."));
            play(player, "sounds.fail");
            return;
        }
        if (!hasRequiredItems(player, required)) {
            player.sendMessage(message("missing-items"));
            play(player, "sounds.fail");
            play(player, "sounds.deny");
            return;
        }

        ItemStack reward = configuredItem("trade.reward");
        if (reward.getType().isAir()) {
            player.sendMessage(color("&cThe trade reward is configured incorrectly. Please contact an administrator."));
            play(player, "sounds.fail");
            return;
        }
        if (player.getInventory().firstEmpty() == -1 && !canFitReward(player, reward)) {
            player.sendMessage(message("inventory-full"));
            play(player, "sounds.fail");
            return;
        }

        // Validate everything before removing anything, then remove the exact configured quantities.
        for (ItemStack item : required) removeRequired(player, item);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(message("success"));
        player.sendActionBar(message("success"));
        play(player, "sounds.fail");
        play(player, "sounds.success");
    }

    private List<ItemStack> getRequiredItems() {
        List<ItemStack> result = new ArrayList<>();
        for (Map<?, ?> entry : getConfig().getMapList("trade.required-items")) {
            ItemStack item = createItem(entry);
            if (item.getType().isAir() || item.getAmount() <= 0) return List.of();
            result.add(item);
        }
        return result;
    }

    private boolean hasRequiredItems(Player player, List<ItemStack> required) {
        for (ItemStack requirement : required) {
            int count = 0;
            for (ItemStack inventoryItem : player.getInventory().getStorageContents()) {
                if (inventoryItem == null || inventoryItem.getType().isAir()) continue;
                if (matches(inventoryItem, requirement)) count += inventoryItem.getAmount();
            }
            if (count < requirement.getAmount()) return false;
        }
        // Prevent one inventory stack from being counted for two different requirements.
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            simulated.add(stack == null ? null : stack.clone());
        }
        for (ItemStack requirement : required) {
            int remaining = requirement.getAmount();
            for (int i = 0; i < simulated.size() && remaining > 0; i++) {
                ItemStack stack = simulated.get(i);
                if (stack == null || stack.getType().isAir() || !matches(stack, requirement)) continue;
                int taken = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - taken);
                remaining -= taken;
                if (stack.getAmount() <= 0) simulated.set(i, null);
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void removeRequired(Player player, ItemStack requirement) {
        int remaining = requirement.getAmount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir() || !matches(stack, requirement)) continue;
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            remaining -= taken;
            if (stack.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean matches(ItemStack actual, ItemStack required) {
        if (actual.getType() != required.getType()) return false;
        if (!getConfig().getBoolean("trade.match-item-meta", false)) return true;
        ItemStack normalized = required.clone();
        normalized.setAmount(1);
        ItemStack actualNormalized = actual.clone();
        actualNormalized.setAmount(1);
        return actualNormalized.isSimilar(normalized);
    }

    private boolean canFitReward(Player player, ItemStack reward) {
        int remaining = reward.getAmount();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) return true;
            if (stack.isSimilar(reward)) remaining -= Math.max(0, stack.getMaxStackSize() - stack.getAmount());
        }
        return remaining <= 0;
    }

    private ItemStack configuredItem(String path) {
        return createItem(getConfig().getConfigurationSection(path));
    }

    private ItemStack createItem(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.AIR);
        return createItem(section.getValues(false));
    }

    private ItemStack createItem(Map<?, ?> values) {
        String materialName = Objects.toString(values.get("material"), "AIR");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            getLogger().warning("Invalid item material in config: " + materialName);
            return new ItemStack(Material.AIR);
        }
        int amount = Math.max(1, Math.min(99, parseInt(values.get("amount"), 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Object name = values.get("name");
        if (name != null) meta.setDisplayName(color(name.toString()));
        Object loreValue = values.get("lore");
        if (loreValue instanceof List<?> lines) {
            List<String> lore = new ArrayList<>();
            for (Object line : lines) lore.add(color(Objects.toString(line, "")));
            meta.setLore(lore);
        }
        Object modelData = values.get("custom-model-data");
        if (modelData != null) {
            int model = parseInt(modelData, -1);
            if (model >= 0) meta.setCustomModelData(model);
        }
        Object enchantments = values.get("enchantments");
        if (enchantments instanceof Map<?, ?> enchants) {
            for (Map.Entry<?, ?> enchant : enchants.entrySet()) {
                NamespacedKey key = NamespacedKey.minecraft(enchant.getKey().toString().toLowerCase(Locale.ROOT));
                var found = org.bukkit.Registry.ENCHANTMENT.get(key);
                if (found != null) meta.addEnchant(found, parseInt(enchant.getValue(), 1), true);
            }
        }
        Object flags = values.get("flags");
        if (flags instanceof List<?> flagList) {
            for (Object flag : flagList) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toString().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Unknown item flag in config: " + flag);
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(Objects.toString(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private boolean validSlot(int slot, Inventory inventory) {
        return slot >= 0 && slot < inventory.getSize();
    }

    private boolean isBillfordInventory(String title) {
        return title != null && title.equals(color(getConfig().getString("gui.title", "<##3F3F3F>ʙɪʟʟꜰᴏʀᴅ")));
    }

    private String message(String path) {
        return color(getConfig().getString("messages." + path, "&cMessage not configured."));
    }

    private String color(String text) {
        if (text == null) return "";
        // Convert <##RRGGBB> and <#RRGGBB> to legacy hex, then translate & color codes.
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)<##?([0-9a-f]{6})>").matcher(text);
        StringBuffer converted = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacyHex = new StringBuilder("§x");
            for (char digit : hex.toCharArray()) legacyHex.append('§').append(digit);
            matcher.appendReplacement(converted, java.util.regex.Matcher.quoteReplacement(legacyHex.toString()));
        }
        matcher.appendTail(converted);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', converted.toString());
    }

    private String readableName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void play(Player player, String path) {
        if (!getConfig().getBoolean("sounds.enabled", true)) return;
        String name = getConfig().getString(path, "");
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
            getLogger().warning("Unknown sound configured at " + path + ": " + name);
        }
    }
}