package com.runediary.sync;

import com.runediary.model.PlayerContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SessionTracker
{
	private final Client client;
	private final PlayerContext playerContext;

	private volatile long sessionStartTime = 0;
	private final Map<Skill, Integer> startXp = new ConcurrentHashMap<>();
	private final AtomicLong lootValueGained = new AtomicLong(0);
	private final AtomicInteger deathCount = new AtomicInteger(0);
	private final AtomicInteger killCount = new AtomicInteger(0);

	public SessionTracker(Client client, PlayerContext playerContext)
	{
		this.client = client;
		this.playerContext = playerContext;
	}

	public void startSession()
	{
		sessionStartTime = System.currentTimeMillis();
		lootValueGained.set(0);
		deathCount.set(0);
		killCount.set(0);
		startXp.clear();

		// Capture starting XP for all skills
		for (Skill skill : Skill.values())
		{
			if (skill.ordinal() > Skill.SAILING.ordinal())
			{
				continue;
			}
			startXp.put(skill, client.getSkillExperience(skill));
		}

		log.info("Session started for {}", playerContext.getPlayerName());
	}

	public void addLootValue(long value)
	{
		lootValueGained.addAndGet(value);
	}

	public void addDeath()
	{
		deathCount.incrementAndGet();
	}

	public void addKill()
	{
		killCount.incrementAndGet();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// Track starting XP if we missed it during init
		Skill skill = event.getSkill();
		startXp.putIfAbsent(skill, event.getXp());
	}

	public Map<String, Object> getSessionData()
	{
		if (sessionStartTime == 0)
		{
			return null;
		}

		long durationMs = System.currentTimeMillis() - sessionStartTime;

		// Calculate total XP gained
		long totalXpGained = 0;
		Map<String, Long> xpGainedPerSkill = new HashMap<>();

		for (Skill skill : Skill.values())
		{
			if (skill.ordinal() > Skill.SAILING.ordinal())
			{
				continue;
			}
			Integer start = startXp.get(skill);
			if (start != null)
			{
				int current = client.getSkillExperience(skill);
				long gained = current - start;
				if (gained > 0)
				{
					xpGainedPerSkill.put(skill.getName(), gained);
					totalXpGained += gained;
				}
			}
		}

		Map<String, Object> session = new HashMap<>();
		session.put("startTime", sessionStartTime);
		session.put("durationMs", durationMs);
		session.put("totalXpGained", totalXpGained);
		session.put("xpPerSkill", xpGainedPerSkill);
		session.put("lootValueGained", lootValueGained.get());
		session.put("deaths", deathCount.get());
		session.put("kills", killCount.get());

		return session;
	}
}
