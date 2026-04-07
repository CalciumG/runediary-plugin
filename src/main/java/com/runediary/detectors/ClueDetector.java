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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ClueDetector
{
	private static final Pattern CLUE_COMPLETE = Pattern.compile(
		"You have completed (\\d+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.");

	// TrailRewardscreen widget: group 73, items at child 3
	private static final int TRAIL_REWARD_GROUP = 73;
	private static final int TRAIL_REWARD_ITEMS_CHILD = 3;

	private final Client client;
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;
	private final ItemManager itemManager;
	private final ServerConfig serverConfig;

	private String pendingClueType = null;
	private int pendingCount = 0;
	private final List<ItemData> rewardItems = new ArrayList<>();
	private boolean rewardWidgetLoaded = false;

	public ClueDetector(Client client, RuneDiaryConfig config, WebhookDispatcher dispatcher,
						PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
						PlayerContext playerContext, ItemManager itemManager, ServerConfig serverConfig)
	{
		this.client = client;
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.playerContext = playerContext;
		this.itemManager = itemManager;
		this.serverConfig = serverConfig;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		if (!serverConfig.isClueEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		Matcher matcher = CLUE_COMPLETE.matcher(message);
		if (matcher.find())
		{
			pendingCount = Integer.parseInt(matcher.group(1));
			pendingClueType = matcher.group(2).toUpperCase();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == TRAIL_REWARD_GROUP)
		{
			rewardWidgetLoaded = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!rewardWidgetLoaded)
		{
			return;
		}
		rewardWidgetLoaded = false;

		// Read reward items from widget
		readRewardItems();

		// If we have chat message data + items, send the event
		if (pendingClueType != null)
		{
			sendClueEvent();
		}
	}

	private void readRewardItems()
	{
		rewardItems.clear();

		Widget itemsWidget = client.getWidget(TRAIL_REWARD_GROUP, TRAIL_REWARD_ITEMS_CHILD);
		if (itemsWidget == null)
		{
			return;
		}

		Widget[] children = itemsWidget.getChildren();
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			int itemId = child.getItemId();
			int quantity = child.getItemQuantity();
			if (itemId > 0 && itemId != 6512)
			{
				int price = itemManager.getItemPrice(itemId);
				String name = itemManager.getItemComposition(itemId).getName();
				rewardItems.add(ItemData.builder()
					.id(itemId)
					.name(name)
					.quantity(quantity)
					.priceEach(price)
					.build());
			}
		}
	}

	private void sendClueEvent()
	{
		// Check clue min value threshold
		long totalValue = rewardItems.stream()
			.mapToLong(item -> (long) item.getPriceEach() * item.getQuantity())
			.sum();

		if (totalValue < serverConfig.getClueMinValue())
		{
			log.debug("Clue reward worth {} GP below threshold {}, skipping",
				totalValue, serverConfig.getClueMinValue());
			pendingClueType = null;
			pendingCount = 0;
			rewardItems.clear();
			return;
		}

		Map<String, Object> extra = new HashMap<>();
		extra.put("clueType", pendingClueType);
		extra.put("numberCompleted", pendingCount);
		extra.put("items", ItemData.toMaps(rewardItems));

		String content = playerContext.getPlayerName() + " has completed a "
			+ pendingClueType.toLowerCase() + " clue scroll (" + pendingCount + " total)";

		EventPayload payload = payloadBuilder.build(playerContext, EventType.CLUE, content, extra);

		if (config.sendScreenshots() && config.sendClueScreenshots())
		{
			screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}

		pendingClueType = null;
		pendingCount = 0;
		rewardItems.clear();
	}
}
