package com.willfp.talismans.bag

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.config.updating.ConfigUpdater
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.profile
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.integrations.placeholder.PlaceholderManager
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.placeholder.PlayerPlaceholder
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.util.MenuUtils
import com.willfp.talismans.talismans.util.TalismanChecks
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

object TalismanBag {
    private val menus = mutableMapOf<Int, Menu>()
    private lateinit var key: PersistentDataKey<List<String>>

    private val savedItems = mutableMapOf<UUID, List<ItemStack>>()

    private val Player.bagSize: Int
        get() {
            val prefix = "talismans.bagsize."
            var highest = -1
            for (permission in this.effectivePermissions.map { it.permission }) {
                if (!permission.startsWith(prefix)) {
                    continue
                }

                val limit = permission.substring(permission.lastIndexOf(".") + 1).toInt()
                if (limit > highest) {
                    highest = limit
                }
            }
            return if (highest < 0) {
                10000
            } else {
                highest
            }
        }

    @JvmStatic
    @ConfigUpdater
    fun update(plugin: EcoPlugin) {
        key = PersistentDataKey(
            plugin.namespacedKeyFactory.create("talisman_bag"),
            PersistentDataKeyType.STRING_LIST,
            emptyList()
        ).player()

        for (rows in 1..6) {
            menus[rows] = menu(rows) {
                for (row in 1..rows) {
                    for (column in 1..9) {
                        setSlot(row, column, slot({ player, _ ->
                            val bagSize = player.bagSize

                            val inBag = player.profile.read(key).map { Items.lookup(it).item }
                            val index = MenuUtils.rowColumnToSlot(row, column)

                            if (index >= bagSize) {
                                ItemStackBuilder(Items.lookup(plugin.configYml.getString("bag.blocked-item")))
                                    .addLoreLines(plugin.configYml.getStrings("bag.blocked-item-lore"))
                                    .build()
                            } else {
                                inBag.toList().getOrNull(index)?.clone() ?: ItemStack(Material.AIR)
                            }
                        }) {
                            setCaptive(true)
                            notCaptiveFor { MenuUtils.rowColumnToSlot(row, column) >= it.bagSize }
                        })
                    }
                }

                setTitle(plugin.configYml.getFormattedString("bag.title"))

                onRender { player, menu ->
                    val items = menu.getCaptiveItems(player)
                        .filterNot { EmptyTestableItem().matches(it) }

                    val toWrite = items
                        .filter { TalismanChecks.getTalismanOnItem(it) != null }

                    savedItems[player.uniqueId] = toWrite.toList()
                }

                onClose { event, menu ->
                    val player = event.player as Player

                    val items = menu.getCaptiveItems(player)
                        .filterNot { EmptyTestableItem().matches(it) }

                    val toWrite = items
                        .filter { TalismanChecks.getTalismanOnItem(it) != null }

                    savedItems[player.uniqueId] = toWrite.toList()

                    player.profile.write(key, toWrite.map { Items.toLookupString(it) })

                    val toDrop = items.filter { TalismanChecks.getTalismanOnItem(it) == null }

                    DropQueue(player)
                        .setLocation(player.eyeLocation)
                        .forceTelekinesis()
                        .addItems(toDrop)
                        .push()
                }
            }
        }

        PlaceholderManager.registerPlaceholder(
            PlayerPlaceholder(
                plugin,
                "bagsize"
            ) { it.bagSize.toString() }
        )
    }

    fun open(player: Player) {
        val bagRows = min(6, ceil(player.bagSize / 9.0).toInt())
        menus[bagRows]!!.open(player)
    }

    fun getTalismans(player: Player): List<ItemStack> {
        return savedItems[player.uniqueId] ?: emptyList()
    }
}
