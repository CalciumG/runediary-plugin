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

@Slf4j
public class CombatAchievementDetector
{
	private static final Pattern CA_COMPLETE = Pattern.compile(
		"Congratulations, you've completed an? (\\w+) combat task: (.+)\\.");
	private static final Pattern CA_TIER_COMPLETE = Pattern.compile(
		"You have completed all (\\w+) combat tasks");

	// Varbits from net.runelite.api.gameval.VarbitID
	private static final int VARBIT_CA_POINTS = 14814;
	private static final int VARBIT_CA_THRESHOLD_GRANDMASTER = 14813;

	private final Client client;
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;

	public CombatAchievementDetector(Client client, RuneDiaryConfig config, WebhookDispatcher dispatcher,
									 PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
									 PlayerContext playerContext)
	{
		this.client = client;
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

		if (!config.combatAchievementEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		Matcher caMatcher = CA_COMPLETE.matcher(message);
		if (caMatcher.find())
		{
			String tier = caMatcher.group(1).toUpperCase();
			String taskName = caMatcher.group(2);

			Map<String, Object> extra = new HashMap<>();
			int totalPoints;
			int totalPossiblePoints;
			try
			{
				totalPoints = client.getVarbitValue(VARBIT_CA_POINTS);
				totalPossiblePoints = client.getVarbitValue(VARBIT_CA_THRESHOLD_GRANDMASTER);
			}
			catch (Exception e)
			{
				totalPoints = 0;
				totalPossiblePoints = 0;
			}

			extra.put("tier", tier);
			extra.put("task", taskName);
			extra.put("taskPoints", getPointsForTier(tier));
			extra.put("totalPoints", totalPoints);
			extra.put("tierProgress", 0);
			extra.put("tierTotalPoints", 0);
			extra.put("totalPossiblePoints", totalPossiblePoints);

			// Check if this also completed a tier
			Matcher tierMatcher = CA_TIER_COMPLETE.matcher(message);
			if (tierMatcher.find())
			{
				extra.put("justCompletedTier", tierMatcher.group(1).toUpperCase());
			}

			String content = playerContext.getPlayerName() + " has completed a " + tier.toLowerCase()
				+ " combat task: " + taskName;

			EventPayload payload = payloadBuilder.build(playerContext, EventType.COMBAT_ACHIEVEMENT, content, extra);

			if (config.sendScreenshots() && config.sendCombatAchievementScreenshots())
			{
				screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
			}
			else
			{
				dispatcher.dispatch(payload, null);
			}
		}
	}

	private int getPointsForTier(String tier)
	{
		switch (tier)
		{
			case "EASY": return 1;
			case "MEDIUM": return 2;
			case "HARD": return 3;
			case "ELITE": return 4;
			case "MASTER": return 5;
			case "GRANDMASTER": return 6;
			default: return 0;
		}
	}
}
