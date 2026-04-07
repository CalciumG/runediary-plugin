package com.runediary.detectors;

import com.runediary.RuneDiaryConfig;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.PlayerContext;
import com.runediary.sync.ServerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class KillCountDetector
{
	private static final Pattern KC_PATTERN = Pattern.compile("Your (.+) (?:kill|harvest|completion) count is: (\\d+)");
	private static final Pattern PB_PATTERN = Pattern.compile("Duration: (\\S+)(?: \\(Personal best\\))?");
	private static final Pattern PB_NEW_PATTERN = Pattern.compile("Personal best: (\\S+)");

	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;
	private final ServerConfig serverConfig;

	private String pendingBoss = null;
	private int pendingCount = 0;

	public KillCountDetector(RuneDiaryConfig config, WebhookDispatcher dispatcher,
							 PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
							 PlayerContext playerContext, ServerConfig serverConfig)
	{
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.playerContext = playerContext;
		this.serverConfig = serverConfig;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		if (!serverConfig.isKillCountEnabled() || !playerContext.isValid())
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		Matcher kcMatcher = KC_PATTERN.matcher(message);
		if (kcMatcher.find())
		{
			pendingBoss = kcMatcher.group(1);
			pendingCount = Integer.parseInt(kcMatcher.group(2));

			// KC interval filter: only send every Nth kill (0 = every kill)
			int interval = serverConfig.getKillCountInterval();
			if (interval > 0 && pendingCount % interval != 0)
			{
				pendingBoss = null;
				return;
			}

			sendKillCount(message, null, false, null);
			return;
		}

		// Check for duration/PB messages that follow a KC message
		if (pendingBoss != null)
		{
			Matcher pbMatcher = PB_PATTERN.matcher(message);
			if (pbMatcher.find())
			{
				// We already sent the KC event — PB info is extra
				pendingBoss = null;
				pendingCount = 0;
			}
		}
	}

	private void sendKillCount(String gameMessage, String time, boolean isPersonalBest, String personalBest)
	{
		Map<String, Object> extra = new HashMap<>();
		extra.put("boss", pendingBoss);
		extra.put("count", pendingCount);
		extra.put("gameMessage", gameMessage);
		if (time != null)
		{
			extra.put("time", time);
		}
		extra.put("isPersonalBest", isPersonalBest);
		if (personalBest != null)
		{
			extra.put("personalBest", personalBest);
		}

		String content = playerContext.getPlayerName() + " has defeated " + pendingBoss
			+ " with a kill count of " + pendingCount;

		EventPayload payload = payloadBuilder.build(playerContext, EventType.KILL_COUNT, content, extra);

		if (config.sendScreenshots() && config.sendKillCountScreenshots())
		{
			screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}

		pendingBoss = null;
		pendingCount = 0;
	}
}
