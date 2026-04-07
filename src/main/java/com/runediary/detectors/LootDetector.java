package com.runediary.detectors;

import com.runediary.RuneDiaryConfig;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.ItemData;
import com.runediary.model.PlayerContext;
import com.runediary.sync.ServerConfig;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

@Slf4j
public class LootDetector
{
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final ItemManager itemManager;
	private final PlayerContext playerContext;
	private final ServerConfig serverConfig;

	public LootDetector(RuneDiaryConfig config, WebhookDispatcher dispatcher,
						PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
						ItemManager itemManager, PlayerContext playerContext, ServerConfig serverConfig)
	{
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.itemManager = itemManager;
		this.playerContext = playerContext;
		this.serverConfig = serverConfig;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!serverConfig.isLootEnabled() || !playerContext.isValid())
		{
			return;
		}

		String source = event.getNpc().getName();
		int npcId = event.getNpc().getId();
		Collection<ItemStack> items = event.getItems();

		processLoot(source, "NPC", npcId, items);
	}

	private void processLoot(String source, String category, int npcId, Collection<ItemStack> items)
	{
		List<ItemData> itemDataList = new ArrayList<>();
		long totalValue = 0;

		for (ItemStack stack : items)
		{
			int price = itemManager.getItemPrice(stack.getId());
			ItemComposition comp = itemManager.getItemComposition(stack.getId());
			String name = comp.getName();

			ItemData data = ItemData.builder()
				.id(stack.getId())
				.name(name)
				.quantity(stack.getQuantity())
				.priceEach(price)
				.build();

			itemDataList.add(data);
			totalValue += (long) price * stack.getQuantity();
		}

		if (totalValue < serverConfig.getMinLootValue())
		{
			log.debug("Loot from {} worth {} GP is below threshold of {}, skipping",
				source, totalValue, serverConfig.getMinLootValue());
			return;
		}

		log.debug("Loot from {} worth {} GP ({} items)", source, totalValue, itemDataList.size());

		Map<String, Object> extra = new HashMap<>();
		extra.put("items", ItemData.toMaps(itemDataList));
		extra.put("source", source);
		extra.put("category", category);
		extra.put("npcId", npcId);

		String content = playerContext.getPlayerName() + " received loot worth "
			+ String.format("%,d", totalValue) + " GP from " + source;

		EventPayload payload = payloadBuilder.build(playerContext, EventType.LOOT, content, extra);

		boolean shouldScreenshot = config.sendScreenshots() && config.sendLootScreenshots()
			&& (serverConfig.getLootImageMinValue() <= 0 || totalValue >= serverConfig.getLootImageMinValue());

		if (shouldScreenshot)
		{
			CompletableFuture<BufferedImage> screenshot = screenshotCapture.capture();
			screenshot.thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}
	}
}
