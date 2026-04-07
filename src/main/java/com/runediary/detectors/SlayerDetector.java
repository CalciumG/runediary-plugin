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
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SlayerDetector
{
	private static final Pattern TASK_COMPLETE = Pattern.compile("You have completed your task! You killed (\\d+) (.+)\\.");
	private static final Pattern TASK_COUNT = Pattern.compile("You've completed (\\d+) (?:task|tasks)");
	private static final Pattern POINTS = Pattern.compile("(\\d+) points");

	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;

	private String pendingMonster = null;
	private int pendingKillCount = 0;

	public SlayerDetector(RuneDiaryConfig config, WebhookDispatcher dispatcher,
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

		if (!config.slayerEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		Matcher taskMatcher = TASK_COMPLETE.matcher(message);
		if (taskMatcher.find())
		{
			pendingKillCount = Integer.parseInt(taskMatcher.group(1));
			pendingMonster = taskMatcher.group(2);
			return;
		}

		// The task count message follows the completion message
		if (pendingMonster != null)
		{
			Matcher countMatcher = TASK_COUNT.matcher(message);
			Matcher pointsMatcher = POINTS.matcher(message);

			String taskCount = countMatcher.find() ? countMatcher.group(1) : "0";
			String points = pointsMatcher.find() ? pointsMatcher.group(1) : "0";

			Map<String, Object> extra = new HashMap<>();
			extra.put("slayerTask", pendingMonster);
			extra.put("slayerCompleted", taskCount);
			extra.put("slayerPoints", points);
			extra.put("killCount", pendingKillCount);
			extra.put("monster", pendingMonster);

			String content = playerContext.getPlayerName() + " has completed a slayer task: "
				+ pendingKillCount + " " + pendingMonster;

			EventPayload payload = payloadBuilder.build(playerContext, EventType.SLAYER, content, extra);

			if (config.sendScreenshots() && config.sendSlayerScreenshots())
			{
				screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
			}
			else
			{
				dispatcher.dispatch(payload, null);
			}

			pendingMonster = null;
			pendingKillCount = 0;
		}
	}
}
