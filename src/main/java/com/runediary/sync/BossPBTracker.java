package com.runediary.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class BossPBTracker
{
	// "Duration: 2:34.20 (Personal best)" or "Duration: 2:34.20" then "Personal best: 2:30.00"
	private static final Pattern DURATION_PATTERN = Pattern.compile(
		"Duration: (\\S+?)( \\(Personal best\\))?$");
	private static final Pattern PB_PATTERN = Pattern.compile(
		"Personal best: (\\S+)");
	private static final Pattern KC_PATTERN = Pattern.compile(
		"Your (.+) (?:kill|harvest|completion) count is: (\\d+)");

	// boss → { pb, kc }
	private final ConcurrentHashMap<String, Map<String, Object>> bossData = new ConcurrentHashMap<>();

	private String lastBoss = null;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		// Track which boss we're looking at
		Matcher kcMatcher = KC_PATTERN.matcher(message);
		if (kcMatcher.find())
		{
			lastBoss = kcMatcher.group(1);
			int count = Integer.parseInt(kcMatcher.group(2));

			bossData.computeIfAbsent(lastBoss, k -> new ConcurrentHashMap<>());
			bossData.get(lastBoss).put("kc", count);
			return;
		}

		// Check for duration (comes after KC message)
		Matcher durationMatcher = DURATION_PATTERN.matcher(message);
		if (durationMatcher.find() && lastBoss != null)
		{
			String time = durationMatcher.group(1);
			boolean isPB = durationMatcher.group(2) != null;

			bossData.computeIfAbsent(lastBoss, k -> new ConcurrentHashMap<>());
			if (isPB)
			{
				bossData.get(lastBoss).put("personalBest", time);
				log.debug("New PB for {}: {}", lastBoss, time);
			}
			return;
		}

		// Check for separate PB message
		Matcher pbMatcher = PB_PATTERN.matcher(message);
		if (pbMatcher.find() && lastBoss != null)
		{
			String pbTime = pbMatcher.group(1);
			bossData.computeIfAbsent(lastBoss, k -> new ConcurrentHashMap<>());
			bossData.get(lastBoss).put("personalBest", pbTime);
			log.debug("PB for {}: {}", lastBoss, pbTime);
		}
	}

	public Map<String, Object> getBossData()
	{
		if (bossData.isEmpty())
		{
			return null;
		}
		return new HashMap<>(bossData);
	}
}
