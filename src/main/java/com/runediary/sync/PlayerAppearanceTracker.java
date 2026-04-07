package com.runediary.sync;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.client.game.ItemManager;

@Slf4j
public class PlayerAppearanceTracker
{
	private static final String[] EQUIPMENT_SLOTS = {
		"head", "cape", "neck", "weapon", "body",
		"shield", "legs", "hands", "feet", "ring", "ammo"
	};

	private final Client client;
	private final ItemManager itemManager;

	public PlayerAppearanceTracker(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	public Map<String, Object> getAppearanceData()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		Map<String, Object> appearance = new HashMap<>();

		// Combat level
		appearance.put("combatLevel", localPlayer.getCombatLevel());

		// Equipment
		Map<String, Object> equipment = new HashMap<>();
		ItemContainer equipContainer = client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
		if (equipContainer != null)
		{
			Item[] items = equipContainer.getItems();
			for (int i = 0; i < Math.min(items.length, EQUIPMENT_SLOTS.length); i++)
			{
				Item item = items[i];
				if (item.getId() > 0 && item.getId() != 6512)
				{
					Map<String, Object> slotData = new HashMap<>();
					slotData.put("id", item.getId());
					try
					{
						slotData.put("name", itemManager.getItemComposition(item.getId()).getName());
					}
					catch (Exception e)
					{
						slotData.put("name", "Unknown");
					}
					equipment.put(EQUIPMENT_SLOTS[i], slotData);
				}
			}
		}
		appearance.put("equipment", equipment);

		// Player model colours/appearance
		PlayerComposition comp = localPlayer.getPlayerComposition();
		if (comp != null)
		{
			Map<String, Object> model = new HashMap<>();
			try
			{
				int[] colours = comp.getColors();
				if (colours != null)
				{
					model.put("colors", colours);
				}
			}
			catch (Exception e)
				{
					log.debug("Failed to read player colours: {}", e.getMessage());
				}

			model.put("isFemale", comp.getGender() == 1);
			appearance.put("model", model);
		}

		return appearance;
	}
}
