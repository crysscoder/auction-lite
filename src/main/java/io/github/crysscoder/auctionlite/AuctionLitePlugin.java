package io.github.crysscoder.auctionlite;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class AuctionLitePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadListings();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("auction")).setExecutor(this);
        Objects.requireNonNull(getCommand("auction")).setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveListings();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof AuctionHolder holder)) {
            return;
        }

        event.setCancelled(true);
        UUID id = holder.slots().get(event.getRawSlot());

        if (id == null) {
            return;
        }

        buy(player, id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        pay(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "only-player");
            return true;
        }

        if (!player.hasPermission("auctionlite.use")) {
            send(player, "no-permission");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("auctionlite.reload")) {
                send(player, "no-permission");
                return true;
            }
            reloadConfig();
            loadListings();
            send(player, "reloaded");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            sell(player, args.length > 1 ? args[1] : "");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("payouts")) {
            pay(player);
            return true;
        }

        open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "payouts", "reload").stream().filter(item -> item.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private void sell(Player player, String priceValue) {
        int price;
        try {
            price = Integer.parseInt(priceValue);
        } catch (NumberFormatException exception) {
            send(player, "bad-price");
            return;
        }

        if (price <= 0) {
            send(player, "bad-price");
            return;
        }

        if (listings.size() >= Math.max(1, getConfig().getInt("max-listings", 45))) {
            send(player, "full");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            send(player, "no-item");
            return;
        }

        ItemStack listed = item.clone();
        player.getInventory().setItemInMainHand(null);
        UUID id = UUID.randomUUID();
        listings.put(id, new Listing(id, player.getUniqueId(), player.getName(), price, listed));
        saveListings();
        send(player, "listed", Map.of("price", String.valueOf(price)));
    }

    private void open(Player player) {
        AuctionHolder holder = new AuctionHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("AuctionLite", NamedTextColor.DARK_GREEN));
        holder.inventory(inventory);
        int slot = 0;
        for (Listing listing : listings.values()) {
            ItemStack item = listing.item().clone();
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? meta.lore() : new java.util.ArrayList<>();
            lore.add(Component.text("Price: " + listing.price() + " diamonds", NamedTextColor.GREEN));
            lore.add(Component.text("Seller: " + listing.sellerName(), NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.slots().put(slot, listing.id());
            slot++;
        }
        player.openInventory(inventory);
    }

    private void buy(Player player, UUID id) {
        Listing listing = listings.get(id);

        if (listing == null) {
            open(player);
            return;
        }

        if (listing.seller().equals(player.getUniqueId())) {
            send(player, "own");
            return;
        }

        if (!takeDiamonds(player, listing.price())) {
            send(player, "no-diamonds");
            return;
        }

        listings.remove(id);
        player.getInventory().addItem(listing.item()).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        addPayout(listing.seller(), listing.price());
        saveListings();
        saveConfig();
        send(player, "bought");
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
        if (seller instanceof Player online) {
            send(online, "sold", Map.of("price", String.valueOf(listing.price())));
            pay(online);
        }
        open(player);
    }

    private boolean takeDiamonds(Player player, int amount) {
        int left = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                left -= item.getAmount();
            }
        }
        if (left > 0) {
            return false;
        }
        left = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.DIAMOND || left <= 0) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            left -= take;
        }
        return true;
    }

    private void addPayout(UUID seller, int amount) {
        getConfig().set("payouts." + seller, getConfig().getInt("payouts." + seller, 0) + amount);
    }

    private void pay(Player player) {
        int amount = getConfig().getInt("payouts." + player.getUniqueId(), 0);
        if (amount <= 0) {
            send(player, "no-payouts");
            return;
        }
        getConfig().set("payouts." + player.getUniqueId(), null);
        saveConfig();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, amount)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        send(player, "payouts", Map.of("amount", String.valueOf(amount)));
    }

    private void loadListings() {
        listings.clear();
        if (getConfig().getConfigurationSection("listings") == null) {
            return;
        }
        for (String key : getConfig().getConfigurationSection("listings").getKeys(false)) {
            String path = "listings." + key;
            ItemStack item = getConfig().getItemStack(path + ".item");
            if (item != null) {
                UUID id = UUID.fromString(key);
                listings.put(id, new Listing(id, UUID.fromString(getConfig().getString(path + ".seller")), getConfig().getString(path + ".seller-name", "unknown"), getConfig().getInt(path + ".price"), item));
            }
        }
    }

    private void saveListings() {
        getConfig().set("listings", null);
        for (Listing listing : listings.values()) {
            String path = "listings." + listing.id();
            getConfig().set(path + ".seller", listing.seller().toString());
            getConfig().set(path + ".seller-name", listing.sellerName());
            getConfig().set(path + ".price", listing.price());
            getConfig().set(path + ".item", listing.item());
        }
        saveConfig();
    }

    private void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> values) {
        String prefix = getConfig().getString("messages.prefix", "&7[&aAuction&7]");
        String result = getConfig().getString("messages." + key, "").replace("%prefix%", prefix);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        sender.sendMessage(legacy.deserialize(result));
    }

    private record Listing(UUID id, UUID seller, String sellerName, int price, ItemStack item) {
    }

    private static final class AuctionHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, UUID> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private Map<Integer, UUID> slots() {
            return slots;
        }
    }
}
