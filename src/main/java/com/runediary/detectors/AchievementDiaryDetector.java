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
public class AchievementDiaryDetector
{
	// "Congratulations! You have completed all of the easy tasks in the Varrock area."
	private static final Pattern DIARY_COMPLETE = Pattern.compile(
		"Congratulations! You have completed all of the (\\w+) tasks in the (.+) area");

	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;

	public AchievementDiaryDetector(RuneDiaryConfig config, WebhookDispatcher dispatcher,
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

		if (!config.diaryEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		Matcher matcher = DIARY_COMPLETE.matcher(message);
		if (matcher.find())
		{
			String difficulty = matcher.group(1).toUpperCase();
			String area = matcher.group(2);

			Map<String, Object> extra = new HashMap<>();
			extra.put("area", area);
			extra.put("difficulty", difficulty);
			extra.put("total", 0);
			extra.put("tasksCompleted", 0);
			extra.put("tasksTotal", 0);
			extra.put("areaTasksCompleted", 0);
			extra.put("areaTasksTotal", 0);

			String content = playerContext.getPlayerName() + " has completed all "
				+ difficulty.toLowerCase() + " tasks in the " + area + " diary";

			EventPayload payload = payloadBuilder.build(playerContext, EventType.ACHIEVEMENT_DIARY, content, extra);

			if (config.sendScreenshots() && config.sendDiaryScreenshots())
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
