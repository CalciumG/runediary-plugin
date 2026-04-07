package com.runediary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("runediary")
public interface RuneDiaryConfig extends Config
{
	@ConfigSection(
		name = "Event Notifications",
		description = "Choose which in-game events are sent to your RuneDiary profile",
		position = 0
	)
	String eventTogglesSection = "eventToggles";

	@ConfigSection(
		name = "Screenshots",
		description = "Control which events include a screenshot when sent",
		position = 1
	)
	String screenshotsSection = "screenshots";

	// Internal — hidden from user
	@ConfigItem(keyName = "webhookUrl", name = "Webhook URL",
		description = "Auto-configured", hidden = true, position = 99)
	default String webhookUrl()
	{
		return "";
	}

	// Event Toggles

	@ConfigItem(keyName = "deathEnabled", name = "Deaths",
		description = "Send death events including lost items, killer, and location",
		section = eventTogglesSection, position = 0)
	default boolean deathEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "levelEnabled", name = "Level Ups",
		description = "Send level-up milestones for each skill",
		section = eventTogglesSection, position = 1)
	default boolean levelEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "lootEnabled", name = "Loot Drops",
		description = "Send valuable loot drops from NPCs",
		section = eventTogglesSection, position = 2)
	default boolean lootEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "killCountEnabled", name = "Kill Counts",
		description = "Send boss kill count milestones",
		section = eventTogglesSection, position = 3)
	default boolean killCountEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "slayerEnabled", name = "Slayer Tasks",
		description = "Send slayer task completions with task details",
		section = eventTogglesSection, position = 4)
	default boolean slayerEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "questEnabled", name = "Quests",
		description = "Send quest completion events",
		section = eventTogglesSection, position = 5)
	default boolean questEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "petEnabled", name = "Pets",
		description = "Send pet drop events",
		section = eventTogglesSection, position = 6)
	default boolean petEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "clueEnabled", name = "Clue Scrolls",
		description = "Send clue scroll completion rewards",
		section = eventTogglesSection, position = 7)
	default boolean clueEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "combatAchievementEnabled", name = "Combat Achievements",
		description = "Send combat achievement completions and tier unlocks",
		section = eventTogglesSection, position = 8)
	default boolean combatAchievementEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "diaryEnabled", name = "Achievement Diaries",
		description = "Send achievement diary tier completions",
		section = eventTogglesSection, position = 9)
	default boolean diaryEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "collectionLogEnabled", name = "Collection Log",
		description = "Send new collection log slot unlocks",
		section = eventTogglesSection, position = 10)
	default boolean collectionLogEnabled()
	{
		return true;
	}

	// Server-controlled thresholds — hidden
	@ConfigItem(keyName = "minLootValue", name = "Min Loot Value",
		description = "Server-controlled", hidden = true, position = 90)
	default int minLootValue() { return 100000; }

	@ConfigItem(keyName = "clueMinValue", name = "Min Clue Value",
		description = "Server-controlled", hidden = true, position = 91)
	default int clueMinValue() { return 100000; }

	@ConfigItem(keyName = "killCountInterval", name = "KC Interval",
		description = "Server-controlled", hidden = true, position = 92)
	default int killCountInterval() { return 0; }

	@ConfigItem(keyName = "lootImageMinValue", name = "Loot Screenshot Min Value",
		description = "Server-controlled", hidden = true, position = 93)
	default int lootImageMinValue() { return 0; }

	// Screenshots

	@ConfigItem(keyName = "sendScreenshots", name = "Send Screenshots",
		description = "Master toggle — include screenshots with events",
		section = screenshotsSection, position = 0)
	default boolean sendScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendDeathScreenshots", name = "Death Screenshots",
		description = "Capture a screenshot on death",
		section = screenshotsSection, position = 1)
	default boolean sendDeathScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendLevelScreenshots", name = "Level Up Screenshots",
		description = "Capture a screenshot on level up",
		section = screenshotsSection, position = 2)
	default boolean sendLevelScreenshots()
	{
		return false;
	}

	@ConfigItem(keyName = "sendLootScreenshots", name = "Loot Screenshots",
		description = "Capture a screenshot for valuable loot drops",
		section = screenshotsSection, position = 3)
	default boolean sendLootScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendKillCountScreenshots", name = "Kill Count Screenshots",
		description = "Capture a screenshot on boss KC milestones",
		section = screenshotsSection, position = 4)
	default boolean sendKillCountScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendSlayerScreenshots", name = "Slayer Screenshots",
		description = "Capture a screenshot on slayer task completion",
		section = screenshotsSection, position = 5)
	default boolean sendSlayerScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendQuestScreenshots", name = "Quest Screenshots",
		description = "Capture a screenshot on quest completion",
		section = screenshotsSection, position = 6)
	default boolean sendQuestScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendPetScreenshots", name = "Pet Screenshots",
		description = "Capture a screenshot on pet drops",
		section = screenshotsSection, position = 7)
	default boolean sendPetScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendClueScreenshots", name = "Clue Scroll Screenshots",
		description = "Capture a screenshot on clue scroll completion",
		section = screenshotsSection, position = 8)
	default boolean sendClueScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendCombatAchievementScreenshots", name = "Combat Achievement Screenshots",
		description = "Capture a screenshot on combat achievement completion",
		section = screenshotsSection, position = 9)
	default boolean sendCombatAchievementScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendDiaryScreenshots", name = "Diary Screenshots",
		description = "Capture a screenshot on diary tier completion",
		section = screenshotsSection, position = 10)
	default boolean sendDiaryScreenshots()
	{
		return true;
	}

	@ConfigItem(keyName = "sendCollectionLogScreenshots", name = "Collection Log Screenshots",
		description = "Capture a screenshot on new collection log entries",
		section = screenshotsSection, position = 11)
	default boolean sendCollectionLogScreenshots()
	{
		return true;
	}
}
