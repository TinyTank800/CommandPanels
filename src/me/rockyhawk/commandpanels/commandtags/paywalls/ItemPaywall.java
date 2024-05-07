package me.rockyhawk.commandpanels.commandtags.paywalls;

import me.rockyhawk.commandpanels.CommandPanels;
import me.rockyhawk.commandpanels.commandtags.PaywallEvent;
import me.rockyhawk.commandpanels.commandtags.PaywallOutput;
import me.rockyhawk.commandpanels.openpanelsmanager.PanelPosition;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ItemPaywall implements Listener {
    CommandPanels plugin;
    public ItemPaywall(CommandPanels pl) {
        this.plugin = pl;
    }

    @EventHandler
    public void commandTag(PaywallEvent e){
        if(e.name.equalsIgnoreCase("item-paywall=")){
            //if player uses item-paywall= [Material] [Amount] <id:#> <IGNORENBT> WILL NOT TAKE CUSTOM ITEMS. IGNORENBT lets nbt items through. Useful for spawner edge cases.
            //player can use item-paywall= [custom-item] [Amount]
            List<ItemStack> cont = new ArrayList<>(Arrays.asList(plugin.inventorySaver.getNormalInventory(e.p)));
            HashMap<Integer, ItemStack> remCont = new HashMap<>();
            try {
                int id = -1;
                boolean ignoreNBT = false;
                for (String val : e.args) {
                    //item ID for legacy minecraft versions
                    if (val.startsWith("id:")) {
                        id = Integer.parseInt(val.substring(3));
                    }
                    //This is here for when people want to take nbt items like spawners with types in a check for spawners.
                    if(val.equals("IGNORENBT")){
                        ignoreNBT = true;
                    }
                }

                //create the item to be removed
                ItemStack sellItem;
                if (Material.matchMaterial(e.args[0]) == null) {
                    //If custom item set to custom item data.
                    sellItem = plugin.itemCreate.makeCustomItemFromConfig(e.panel, PanelPosition.Top, e.panel.getConfig().getConfigurationSection("custom-item." + e.args[0]), e.p, true, true, false);
                    sellItem.setAmount(Integer.parseInt(e.args[1]));
                } else {
                    //If normal just set material.
                    sellItem = new ItemStack(Objects.requireNonNull(Material.matchMaterial(e.args[0])), Integer.parseInt(e.args[1]));
                }
                //this is not a boolean because it needs to return an int
                PaywallOutput removedItem = PaywallOutput.Blocked;

                int remainingAmount = sellItem.getAmount();
                //loop through items in the inventory
                for (int f = 0; f < 36; f++) {

                    if (cont.get(f) == null) {
                        //skip slot if empty
                        continue;
                    }

                    ItemStack itm = cont.get(f); //Get item/slot

                    //Check if the item matches the id set. If not continue to next in loop.
                    if (id != -1 && itm.getDurability() != id) {
                        continue;
                    }

                    if(plugin.itemCreate.isIdentical(sellItem,itm, !ignoreNBT)){ //Check if both sell item and item in slot are identical
                        //Adding item to the remove list then checking if we have reached the required amount.
                        ItemStack add = new ItemStack(itm.getType(), itm.getAmount());
                        remainingAmount -= add.getAmount();
                        if (e.doDelete) remCont.put(f,add);
                        if (remainingAmount <= 0) {
                            removedItem = PaywallOutput.Passed;
                            break;
                        }
                    }
                }

                if (remainingAmount <= 0) {
                    //If we have reached the end of the paywall.
                    for(Map.Entry<Integer, ItemStack> entry : remCont.entrySet()) {
                        ItemStack remItem = entry.getValue();

                        //Check if it's the last item in the loop and only subtract the remaining amount.
                        if (sellItem.getAmount() < remItem.getAmount()) {
                            if (plugin.inventorySaver.hasNormalInventory(e.p)) {
                                if (e.doDelete)
                                    //Normal inventory
                                    e.p.getInventory().getItem(entry.getKey()).setAmount(remItem.getAmount() - sellItem.getAmount());
                                e.p.updateInventory();
                            } else {
                                if (e.doDelete)
                                    //Saved inventory
                                    cont.get(entry.getKey()).setAmount(remItem.getAmount() - sellItem.getAmount());
                                plugin.inventorySaver.inventoryConfig.set(e.p.getUniqueId().toString(), plugin.itemSerializer.itemStackArrayToBase64(cont.toArray(new ItemStack[0])));
                            }
                        } else { //If its anywhere but the last in loop just get rid of the items.
                            if (plugin.inventorySaver.hasNormalInventory(e.p)) {
                                if (e.doDelete)
                                    //Normal inventory
                                    e.p.getInventory().setItem(entry.getKey(), null);
                                e.p.updateInventory();
                            } else {
                                if (e.doDelete)
                                    //Saved inventory
                                    cont.remove(entry.getValue());
                                plugin.inventorySaver.inventoryConfig.set(e.p.getUniqueId().toString(), plugin.itemSerializer.itemStackArrayToBase64(cont.toArray(new ItemStack[0])));
                            }
                        }

                        if (e.doDelete) sellItem.setAmount(sellItem.getAmount() - remItem.getAmount());
                    }

                    removedItem = PaywallOutput.Passed;
                }

                //send message and return
                if (removedItem == PaywallOutput.Blocked) {
                    if (plugin.config.getBoolean("purchase.item.enable")) {
                        //no item was found
                        plugin.tex.sendString(e.panel, PanelPosition.Top, e.p, Objects.requireNonNull(plugin.config.getString("purchase.item.failure")));
                    }
                } else {
                    if (plugin.config.getBoolean("purchase.item.enable") && e.doDelete) {
                        //item was removed
                        plugin.tex.sendString(e.panel, PanelPosition.Top, e.p, Objects.requireNonNull(plugin.config.getString("purchase.item.success")).replaceAll("%cp-args%", e.args[0]));
                    }
                }
                e.PAYWALL_OUTPUT = removedItem;
            } catch (Exception buyc) {
                plugin.debug(buyc, e.p);
                plugin.tex.sendString(e.p, plugin.tag + plugin.config.getString("config.format.error") + " " + "commands: " + e.name);
                e.PAYWALL_OUTPUT = PaywallOutput.Blocked;
            }
        }
    }
}