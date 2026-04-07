package com.runediary.detectors;

import com.runediary.RuneDiaryConfig;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.ItemData;
import com.runediary.model.PlayerContext;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
public class DeathDetector
{
	private final Client client;
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final ItemManager itemManager;
	private final PlayerContext playerContext;

	public DeathDetector(Client client, RuneDiaryConfig config, WebhookDispatcher dispatcher,
						 PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
						 ItemManager itemManager, PlayerContext playerContext)
	{
		this.client = client;
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.itemManager = itemManager;
		this.playerContext = playerContext;
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!config.deathEnabled() || !playerContext.isValid())
		{
			return;
		}

		Actor actor = event.getActor();
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		log.debug("Player death detected");

		Actor interacting = client.getLocalPlayer().getInteracting();
		boolean isPvp = interacting instanceof Player;
		String killerName = null;
		Integer killerNpcId = null;

		if (interacting != null)
		{
			killerName = interacting.getName();
			if (interacting instanceof NPC)
			{
				killerNpcId = ((NPC) interacting).getId();
			}
		}

		// Get inventory and equipment items (these represent what the player had)
		List<ItemData> inventoryItems = getItemsFromContainer(net.runelite.api.gameval.InventoryID.INV);
		List<ItemData> equipmentItems = getItemsFromContainer(net.runelite.api.gameval.InventoryID.WORN);

		List<ItemData> allItems = new ArrayList<>();
		allItems.addAll(inventoryItems);
		allItems.addAll(equipmentItems);

		WorldPoint wp = client.getLocalPlayer().getWorldLocation();

		Map<String, Object> extra = new HashMap<>();
		// Approximate kept items: 3 most valuable (or 4 with Protect Item)
		// Sort by value descending, take top 3
		allItems.sort((a, b) -> Long.compare(
			(long) b.getPriceEach() * b.getQuantity(),
			(long) a.getPriceEach() * a.getQuantity()));
		int keepCount = Math.min(3, allItems.size());
		List<ItemData> keptItems = new ArrayList<>(allItems.subList(0, keepCount));
		List<ItemData> lostItems = new ArrayList<>(allItems.subList(keepCount, allItems.size()));

		long valueLost = lostItems.stream()
			.mapToLong(item -> (long) item.getPriceEach() * item.getQuantity())
			.sum();

		extra.put("valueLost", valueLost);
		extra.put("isPvp", isPvp);
		extra.put("keptItems", ItemData.toMaps(keptItems));
		extra.put("lostItems", ItemData.toMaps(lostItems));
		if (killerName != null)
		{
			extra.put("killerName", killerName);
		}
		if (killerNpcId != null)
		{
			extra.put("killerNpcId", killerNpcId);
		}

		Map<String, Object> location = new HashMap<>();
		location.put("regionId", wp.getRegionID());
		location.put("plane", wp.getPlane());
		location.put("instanced", client.getTopLevelWorldView().isInstance());
		extra.put("location", location);

		String content = playerContext.getPlayerName() + " has died";
		if (killerName != null)
		{
			content += " to " + killerName;
		}

		EventPayload payload = payloadBuilder.build(playerContext, EventType.DEATH, content, extra);

		if (config.sendScreenshots() && config.sendDeathScreenshots())
		{
			CompletableFuture<BufferedImage> screenshot = screenshotCapture.capture();
			screenshot.thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}
	}

	private List<ItemData> getItemsFromContainer(int inventoryId)
	{
		List<ItemData> result = new ArrayList<>();
		ItemContainer container = client.getItemContainer(inventoryId);
		if (container == null)
		{
			return result;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() == -1 || item.getQuantity() == 0)
			{
				continue;
			}
			int price = itemManager.getItemPrice(item.getId());
			String name = itemManager.getItemComposition(item.getId()).getName();
			result.add(ItemData.builder()
				.id(item.getId())
				.name(name)
				.quantity(item.getQuantity())
				.priceEach(price)
				.build());
		}
		return result;
	}

}
