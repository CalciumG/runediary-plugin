package com.runediary.detectors;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests for chat message regex patterns used by event detectors.
 * These are the most likely to break when OSRS updates chat formats.
 * Run with: ./gradlew test
 */
public class ChatPatternTest
{
	// KillCountDetector patterns
	private static final Pattern KC_PATTERN = Pattern.compile(
		"Your (.+) (?:kill|harvest|completion) count is: (\\d+)");

	// SlayerDetector patterns
	private static final Pattern TASK_COMPLETE = Pattern.compile(
		"You have completed your task! You killed (\\d+) (.+)\\.");
	private static final Pattern TASK_COUNT = Pattern.compile(
		"You've completed (\\d+) (?:task|tasks)");

	// PetDetector patterns
	private static final String PET_MESSAGE = "You have a funny feeling like you're being followed";
	private static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

	// CollectionLogDetector pattern
	private static final Pattern CLOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (.+)");

	// CombatAchievementDetector pattern
	private static final Pattern CA_COMPLETE = Pattern.compile(
		"Congratulations, you've completed an? (\\w+) combat task: (.+)\\.");

	// AchievementDiaryDetector pattern
	private static final Pattern DIARY_COMPLETE = Pattern.compile(
		"Congratulations! You have completed all of the (\\w+) tasks in the (.+) area");

	// ClueDetector pattern
	private static final Pattern CLUE_COMPLETE = Pattern.compile(
		"You have completed (\\d+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.");

	// QuestDetector pattern
	private static final Pattern QUEST_COMPLETE = Pattern.compile(
		"Congratulations, you've completed a quest: (.+)");

	// ---- Kill Count tests ----

	@Test
	public void testKillCount_standard()
	{
		Matcher m = KC_PATTERN.matcher("Your Zulrah kill count is: 500");
		assertTrue(m.find());
		assertEquals("Zulrah", m.group(1));
		assertEquals("500", m.group(2));
	}

	@Test
	public void testKillCount_harvest()
	{
		Matcher m = KC_PATTERN.matcher("Your Hespori harvest count is: 10");
		assertTrue(m.find());
		assertEquals("Hespori", m.group(1));
		assertEquals("10", m.group(2));
	}

	@Test
	public void testKillCount_completion()
	{
		Matcher m = KC_PATTERN.matcher("Your Chambers of Xeric completion count is: 150");
		assertTrue(m.find());
		assertEquals("Chambers of Xeric", m.group(1));
		assertEquals("150", m.group(2));
	}

	@Test
	public void testKillCount_multiWord()
	{
		Matcher m = KC_PATTERN.matcher("Your Theatre of Blood kill count is: 75");
		assertTrue(m.find());
		assertEquals("Theatre of Blood", m.group(1));
	}

	// ---- Slayer tests ----

	@Test
	public void testSlayer_taskComplete()
	{
		Matcher m = TASK_COMPLETE.matcher("You have completed your task! You killed 185 abyssal demons.");
		assertTrue(m.find());
		assertEquals("185", m.group(1));
		assertEquals("abyssal demons", m.group(2));
	}

	@Test
	public void testSlayer_taskCount()
	{
		Matcher m = TASK_COUNT.matcher("You've completed 245 tasks");
		assertTrue(m.find());
		assertEquals("245", m.group(1));
	}

	@Test
	public void testSlayer_singleTask()
	{
		Matcher m = TASK_COUNT.matcher("You've completed 1 task");
		assertTrue(m.find());
		assertEquals("1", m.group(1));
	}

	// ---- Pet tests ----

	@Test
	public void testPet_newPet()
	{
		assertTrue("You have a funny feeling like you're being followed".contains(PET_MESSAGE));
	}

	@Test
	public void testPet_duplicate()
	{
		assertTrue("You have a funny feeling like you would have been followed".contains(PET_DUPLICATE));
	}

	@Test
	public void testPet_notPet()
	{
		assertFalse("You gained some Agility experience".contains(PET_MESSAGE));
	}

	// ---- Collection Log tests ----

	@Test
	public void testClog_newItem()
	{
		Matcher m = CLOG_PATTERN.matcher("New item added to your collection log: Abyssal whip");
		assertTrue(m.find());
		assertEquals("Abyssal whip", m.group(1));
	}

	@Test
	public void testClog_itemWithApostrophe()
	{
		Matcher m = CLOG_PATTERN.matcher("New item added to your collection log: Hydra's claw");
		assertTrue(m.find());
		assertEquals("Hydra's claw", m.group(1));
	}

	// ---- Combat Achievement tests ----

	@Test
	public void testCA_easy()
	{
		Matcher m = CA_COMPLETE.matcher("Congratulations, you've completed an easy combat task: Barrows Novice.");
		assertTrue(m.find());
		assertEquals("easy", m.group(1));
		assertEquals("Barrows Novice", m.group(2));
	}

	@Test
	public void testCA_hard()
	{
		Matcher m = CA_COMPLETE.matcher("Congratulations, you've completed a hard combat task: Alchemical Speed-Runner.");
		assertTrue(m.find());
		assertEquals("hard", m.group(1));
		assertEquals("Alchemical Speed-Runner", m.group(2));
	}

	@Test
	public void testCA_grandmaster()
	{
		Matcher m = CA_COMPLETE.matcher("Congratulations, you've completed a grandmaster combat task: Inferno Master.");
		assertTrue(m.find());
		assertEquals("grandmaster", m.group(1));
	}

	// ---- Achievement Diary tests ----

	@Test
	public void testDiary_varrock()
	{
		Matcher m = DIARY_COMPLETE.matcher("Congratulations! You have completed all of the hard tasks in the Varrock area");
		assertTrue(m.find());
		assertEquals("hard", m.group(1));
		assertEquals("Varrock", m.group(2));
	}

	@Test
	public void testDiary_kourend()
	{
		Matcher m = DIARY_COMPLETE.matcher("Congratulations! You have completed all of the elite tasks in the Kourend & Kebos area");
		assertTrue(m.find());
		assertEquals("elite", m.group(1));
		assertEquals("Kourend & Kebos", m.group(2));
	}

	// ---- Clue tests ----

	@Test
	public void testClue_hard()
	{
		Matcher m = CLUE_COMPLETE.matcher("You have completed 150 hard Treasure Trails.");
		assertTrue(m.find());
		assertEquals("150", m.group(1));
		assertEquals("hard", m.group(2));
	}

	@Test
	public void testClue_singleTrail()
	{
		Matcher m = CLUE_COMPLETE.matcher("You have completed 1 beginner Treasure Trail.");
		assertTrue(m.find());
		assertEquals("1", m.group(1));
		assertEquals("beginner", m.group(2));
	}

	@Test
	public void testClue_master()
	{
		Matcher m = CLUE_COMPLETE.matcher("You have completed 50 master Treasure Trails.");
		assertTrue(m.find());
		assertEquals("50", m.group(1));
		assertEquals("master", m.group(2));
	}

	// ---- Quest tests ----

	@Test
	public void testQuest_completion()
	{
		Matcher m = QUEST_COMPLETE.matcher("Congratulations, you've completed a quest: While Guthix Sleeps");
		assertTrue(m.find());
		assertEquals("While Guthix Sleeps", m.group(1));
	}

	@Test
	public void testQuest_withSpecialChars()
	{
		Matcher m = QUEST_COMPLETE.matcher("Congratulations, you've completed a quest: Mourning's End Part II");
		assertTrue(m.find());
		assertEquals("Mourning's End Part II", m.group(1));
	}
}
