package com.runediary.detectors;

import com.runediary.RuneDiaryConfig;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.PlayerContext;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class LevelDetector
{
	private final Client client;
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;

	private final Map<Skill, Integer> previousLevels = new HashMap<>();
	private final Map<Skill, Integer> pendingLevelUps = new HashMap<>();
	private boolean initialized = false;

	public LevelDetector(Client client, RuneDiaryConfig config, WebhookDispatcher dispatcher,
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
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			previousLevels.clear();
			initialized = false;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.levelEnabled() || !playerContext.isValid())
		{
			return;
		}

		Skill skill = event.getSkill();
		// Use real skill level, not boosted level
		int newLevel = client.getRealSkillLevel(skill);
		Integer oldLevel = previousLevels.get(skill);

		if (oldLevel == null)
		{
			// First time seeing this skill — store baseline, don't fire event
			previousLevels.put(skill, newLevel);
			return;
		}

		if (newLevel > oldLevel)
		{
			log.info("{} levelled up: {} -> {}", skill.getName(), oldLevel, newLevel);
			pendingLevelUps.put(skill, newLevel);
		}

		previousLevels.put(skill, newLevel);
		initialized = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (pendingLevelUps.isEmpty())
		{
			return;
		}

		// Flush all level-ups that occurred this tick as a single event
		Map<String, Integer> levelledSkills = new HashMap<>();
		for (Map.Entry<Skill, Integer> entry : pendingLevelUps.entrySet())
		{
			levelledSkills.put(entry.getKey().getName(), entry.getValue());
		}

		Map<String, Integer> allSkills = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill.ordinal() > Skill.SAILING.ordinal())
			{
				continue;
			}
			allSkills.put(skill.getName(), client.getRealSkillLevel(skill));
		}

		int combatLevel = client.getLocalPlayer().getCombatLevel();
		boolean combatIncreased = false;
		for (Skill skill : pendingLevelUps.keySet())
		{
			if (isCombatSkill(skill))
			{
				combatIncreased = true;
				break;
			}
		}

		Map<String, Object> extra = new HashMap<>();
		extra.put("levelledSkills", levelledSkills);
		extra.put("allSkills", allSkills);

		Map<String, Object> combatLevelMap = new HashMap<>();
		combatLevelMap.put("value", combatLevel);
		combatLevelMap.put("increased", combatIncreased);
		extra.put("combatLevel", combatLevelMap);

		StringBuilder content = new StringBuilder(playerContext.getPlayerName());
		if (levelledSkills.size() == 1)
		{
			Map.Entry<String, Integer> entry = levelledSkills.entrySet().iterator().next();
			content.append(" has levelled ").append(entry.getKey()).append(" to ").append(entry.getValue());
		}
		else
		{
			content.append(" has levelled up: ");
			boolean first = true;
			for (Map.Entry<String, Integer> entry : levelledSkills.entrySet())
			{
				if (!first)
				{
					content.append(", ");
				}
				content.append(entry.getKey()).append(" (").append(entry.getValue()).append(")");
				first = false;
			}
		}

		EventPayload payload = payloadBuilder.build(playerContext, EventType.LEVEL, content.toString(), extra);

		if (config.sendScreenshots() && config.sendLevelScreenshots())
		{
			CompletableFuture<BufferedImage> screenshot = screenshotCapture.capture();
			screenshot.thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}

		pendingLevelUps.clear();
	}

	private boolean isCombatSkill(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
			case STRENGTH:
			case DEFENCE:
			case RANGED:
			case PRAYER:
			case MAGIC:
			case HITPOINTS:
				return true;
			default:
				return false;
		}
	}
}
