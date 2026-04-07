package com.runediary.detectors;

import com.runediary.model.PlayerContext;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
public class BankValueDetector
{
	private final Client client;
	private final ItemManager itemManager;
	private final PlayerContext playerContext;

	@Getter
	private volatile long lastBankValue = 0;
	@Getter
	private volatile long lastBankUpdate = 0;
	@Getter
	private volatile int lastBankItemCount = 0;

	private volatile Runnable onBankUpdated;

	public BankValueDetector(Client client, ItemManager itemManager, PlayerContext playerContext)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.playerContext = playerContext;
	}

	public void setOnBankUpdated(Runnable callback)
	{
		this.onBankUpdated = callback;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != net.runelite.api.gameval.InventoryID.BANK)
		{
			return;
		}

		if (!playerContext.isValid())
		{
			return;
		}

		ItemContainer bank = event.getItemContainer();
		if (bank == null)
		{
			return;
		}

		long totalValue = 0;
		int itemCount = 0;

		for (Item item : bank.getItems())
		{
			if (item.getId() == -1 || item.getQuantity() == 0)
			{
				continue;
			}
			long price = itemManager.getItemPrice(item.getId());
			totalValue += price * item.getQuantity();
			itemCount++;
		}

		lastBankValue = totalValue;
		lastBankItemCount = itemCount;
		lastBankUpdate = System.currentTimeMillis();

		log.debug("Bank value updated: {} GP ({} items)", totalValue, itemCount);

		if (onBankUpdated != null)
		{
			onBankUpdated.run();
		}
	}

	public Map<String, Object> getBankSnapshot()
	{
		if (lastBankUpdate == 0)
		{
			return null;
		}

		Map<String, Object> snapshot = new HashMap<>();
		snapshot.put("totalValue", lastBankValue);
		snapshot.put("itemCount", lastBankItemCount);
		snapshot.put("timestamp", lastBankUpdate);
		return snapshot;
	}
}
