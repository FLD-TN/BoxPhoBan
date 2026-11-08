package com.dex.boxphoban.reward;

import com.dex.boxcore.api.GemResult;
import com.dex.boxcore.api.GemService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/** Money (Vault) and gem (BoxCore GemService). Both optional: missing ones are skipped with a log line. */
public final class Economy {

    private final Logger log;
    private Object vault;
    private Object gems;

    public Economy(Logger log) {
        this.log = log;
    }

    public void hook() {
        vault = null;
        gems = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp != null) vault = rsp.getProvider();
            } catch (Throwable t) {
                log.warning("Không kết nối được Vault: " + t.getMessage());
            }
        }
        if (Bukkit.getPluginManager().getPlugin("BoxCore") != null) {
            try {
                gems = Bukkit.getServicesManager().load(GemService.class);
            } catch (Throwable t) {
                log.warning("Không kết nối được BoxCore GemService: " + t.getMessage());
            }
        }
        log.info("Tiền: " + (vault != null ? "Vault" : "tắt") + ", Gem: " + (gems != null ? "BoxCore" : "tắt"));
    }

    public boolean hasMoney() {
        return vault != null;
    }

    public boolean has(Player p, double amount) {
        if (amount <= 0) return true;
        if (vault == null) return false;
        return ((net.milkbowl.vault.economy.Economy) vault).has(p, amount);
    }

    public boolean withdraw(Player p, double amount) {
        if (amount <= 0) return true;
        if (vault == null) return false;
        return ((net.milkbowl.vault.economy.Economy) vault).withdrawPlayer(p, amount).transactionSuccess();
    }

    public void deposit(Player p, double amount) {
        if (amount <= 0) return;
        if (vault == null) {
            log.warning("Thưởng tiền bị bỏ qua (không có Vault): " + p.getName() + " " + amount);
            return;
        }
        ((net.milkbowl.vault.economy.Economy) vault).depositPlayer(p, amount);
    }

    public void depositGem(Player p, long amount, String reason) {
        if (amount <= 0) return;
        if (gems == null) {
            log.warning("Thưởng gem bị bỏ qua (không có BoxCore): " + p.getName() + " " + amount);
            return;
        }
        GemResult r = ((GemService) gems).deposit(p.getUniqueId(), amount, reason);
        if (r != GemResult.SUCCESS) log.warning("Thưởng gem cho " + p.getName() + " lỗi: " + r);
    }
}
