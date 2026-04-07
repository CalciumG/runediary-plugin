package com.runediary.detectors;

import com.runediary.RuneDiaryConfig;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.PlayerContext;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
public class CollectionLogDetector
{
	private static final Pattern CLOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (.+)");

	// VarPlayer IDs from net.runelite.api.gameval.VarPlayerID
	private static final int VARP_COLLECTION_COUNT = 2942;
	private static final int VARP_COLLECTION_COUNT_MAX = 2943;

	private final Client client;
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;
	private final ItemManager itemManager;

	public CollectionLogDetector(Client client, RuneDiaryConfig config, WebhookDispatcher dispatcher,
								PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
								PlayerContext playerContext, ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.playerContext = playerContext;
		this.itemManager = itemManager;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (!config.collectionLogEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		Matcher matcher = CLOG_PATTERN.matcher(message);
		if (matcher.find())
		{
			String itemName = matcher.group(1);

			// Read completion counts from VarPlayer
			int completedEntries;
			int totalEntries;
			try
			{
				completedEntries = client.getVarpValue(VARP_COLLECTION_COUNT);
				totalEntries = client.getVarpValue(VARP_COLLECTION_COUNT_MAX);
			}
			catch (Exception e)
			{
				completedEntries = 0;
				totalEntries = 0;
			}

			Map<String, Object> extra = new HashMap<>();
			extra.put("itemName", itemName);
			extra.put("itemId", 0);
			extra.put("price", 0);
			extra.put("title", "Collection log (" + completedEntries + "/" + totalEntries + "): " + itemName);
			extra.put("description", playerContext.getPlayerName() + " received a new collection log item");
			extra.put("completedEntries", completedEntries);
			extra.put("totalEntries", totalEntries);

			String content = playerContext.getPlayerName() + " has added a new item to their collection log: "
				+ itemName + " (" + completedEntries + "/" + totalEntries + ")";

			EventPayload payload = payloadBuilder.build(playerContext, EventType.COLLECTION, content, extra);

			if (config.sendScreenshots() && config.sendCollectionLogScreenshots())
			{
				screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
			}
			else
			{
				dispatcher.dispatch(payload, null);
			}
		}
	}
}
