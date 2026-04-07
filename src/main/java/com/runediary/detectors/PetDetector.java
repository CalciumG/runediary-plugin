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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class PetDetector
{
	private static final String PET_MESSAGE = "You have a funny feeling like you're being followed";
	private static final String PET_INVENTORY = "You feel something weird sneaking into your backpack";
	private static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;

	public PetDetector(RuneDiaryConfig config, WebhookDispatcher dispatcher,
					   PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
					   PlayerContext playerContext)
	{
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.playerContext = playerContext;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (!config.petEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		boolean isPet = message.contains(PET_MESSAGE) || message.contains(PET_INVENTORY);
		boolean isDuplicate = message.contains(PET_DUPLICATE);

		if (!isPet && !isDuplicate)
		{
			return;
		}

		Map<String, Object> extra = new HashMap<>();
		extra.put("petName", "Unknown"); // Pet name not easily available from chat
		extra.put("duplicate", isDuplicate);

		String content;
		if (isDuplicate)
		{
			content = playerContext.getPlayerName() + " has a funny feeling like they would have been followed";
		}
		else
		{
			content = playerContext.getPlayerName() + " has a funny feeling like they're being followed";
		}

		EventPayload payload = payloadBuilder.build(playerContext, EventType.PET, content, extra);

		if (config.sendScreenshots() && config.sendPetScreenshots())
		{
			screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}
	}
}
