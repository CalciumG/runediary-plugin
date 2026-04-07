package com.runediary.sync;

import com.google.gson.Gson;
import com.runediary.RuneDiaryConfig;
import com.runediary.model.PlayerContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class ProfileSyncService
{
	private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
	private final Gson gson;

	private final Client client;
	private final RuneDiaryConfig config;
	private final OkHttpClient httpClient;
	private final ScheduledExecutorService executor;
	private final ClientThread clientThread;
	private final PlayerContext playerContext;
	private final net.runelite.client.game.ItemManager itemManager;

	private static final int COLLECTION_LOG_ITEM_SCRIPT = 4100;
	private static final int COLLECTION_LOG_SETUP_SCRIPT = 7797;
	private static final int COLLECTION_LOG_GROUP_ID = 621;
	private static final int COLLECTION_LOG_HEADER_TEXT = 20;
	private static final int COLLECTION_LOG_ITEMS = 40;

	private final Set<Integer> collectionLogItems = Collections.synchronizedSet(new HashSet<>());
	private final ConcurrentHashMap<Integer, Integer> collectionLogItemQuantities = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, String> collectionLogItemNames = new ConcurrentHashMap<>();
	private volatile int collectionLogTotal = 0;
	// Full category→source→items structure from game cache, for frontend rendering
	private volatile Map<String, Object> collectionLogStructure = null;
	private volatile Supplier<Map<String, Object>> bankSnapshotSupplier;
	private volatile Supplier<Map<String, Object>> sessionDataSupplier;
	private volatile Supplier<Map<String, Object>> bossDataSupplier;

	public void setBankSnapshotSupplier(Supplier<Map<String, Object>> supplier)
	{
		this.bankSnapshotSupplier = supplier;
	}

	public void setSessionDataSupplier(Supplier<Map<String, Object>> supplier)
	{
		this.sessionDataSupplier = supplier;
	}

	public void setBossDataSupplier(Supplier<Map<String, Object>> supplier)
	{
		this.bossDataSupplier = supplier;
	}

	public ProfileSyncService(Gson gson, Client client, RuneDiaryConfig config, OkHttpClient httpClient,
							  ScheduledExecutorService executor, ClientThread clientThread,
							  PlayerContext playerContext, net.runelite.client.game.ItemManager itemManager)
	{
		this.gson = gson;
		this.client = client;
		this.config = config;
		this.httpClient = httpClient;
		this.executor = executor;
		this.clientThread = clientThread;
		this.playerContext = playerContext;
		this.itemManager = itemManager;
	}

	public void onLoginReady()
	{
		log.info("Profile sync ready (syncs on logout and key events)");
	}

	private void buildCollectionLogStructure()
	{
		try
		{
			Map<String, Object> structure = new LinkedHashMap<>();
			int[] topIds = client.getEnum(2102).getIntVals();

			for (int topId : topIds)
			{
				net.runelite.api.StructComposition topStruct = client.getStructComposition(topId);
				String tabName = topStruct.getStringValue(682);
				if (tabName == null || tabName.isEmpty()) continue;

				int subtabEnumId = topStruct.getIntValue(683);
				if (subtabEnumId == -1) continue;

				Map<String, Object> sources = new LinkedHashMap<>();
				int[] subtabIds = client.getEnum(subtabEnumId).getIntVals();

				for (int subtabId : subtabIds)
				{
					net.runelite.api.StructComposition sub = client.getStructComposition(subtabId);
					String sourceName = sub.getStringValue(689);
					if (sourceName == null || sourceName.isEmpty()) continue;

					int itemsEnumId = sub.getIntValue(690);
					if (itemsEnumId == -1) continue;

					int[] itemIds = client.getEnum(itemsEnumId).getIntVals();
					List<Integer> itemList = new ArrayList<>();
					for (int id : itemIds) itemList.add(id);
					sources.put(sourceName, itemList);
				}

				structure.put(tabName, sources);
			}

			collectionLogStructure = structure;
			log.info("Built collection log structure: {} tabs", structure.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to build clog structure: {}", e.getMessage());
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// Script 7797 fires when the collection log page renders
		if (event.getScriptId() == COLLECTION_LOG_SETUP_SCRIPT)
		{
			// Wait 2 ticks for widget to fully populate, then read + sync immediately
			clientThread.invokeLater(() ->
				clientThread.invokeLater(() ->
				{
					int before = collectionLogItems.size();
					readCollectionLogWidget();
					if (collectionLogItems.size() > before)
					{
						collectAndSync();
					}
				}));
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == COLLECTION_LOG_ITEM_SCRIPT)
		{
			try
			{
				// WikiSync approach: read from script event arguments
				Object[] args = event.getScriptEvent().getArguments();
				if (args != null && args.length >= 3)
				{
					int itemId = (int) args[1];
					int quantity = (int) args[2];
					if (itemId > 0)
					{
						collectionLogItems.add(itemId);
						collectionLogItemQuantities.put(itemId, Math.max(quantity, 0));
						try
						{
							String name = itemManager.getItemComposition(itemId).getName();
							collectionLogItemNames.put(itemId, name);
						}
						catch (Exception ignored) {}
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Error reading collection log script: {}", e.getMessage());
			}
		}
	}

	/**
	 * Read ALL collection log items from the game cache.
	 * Uses enum 2102 to navigate the collection log structure,
	 * same approach as WikiSync.
	 */
	public void loadCollectionLogFromCache()
	{
		try
		{
			Set<Integer> allItemIds = new HashSet<>();

			// Enum 2102 contains top-level tab struct IDs
			int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();
			for (int topLevelTabStructId : topLevelTabStructIds)
			{
				net.runelite.api.StructComposition topStruct = client.getStructComposition(topLevelTabStructId);
				int subtabEnumId = topStruct.getIntValue(683);
				if (subtabEnumId == -1) continue;

				int[] subtabStructIds = client.getEnum(subtabEnumId).getIntVals();
				for (int subtabStructId : subtabStructIds)
				{
					net.runelite.api.StructComposition subtabStruct = client.getStructComposition(subtabStructId);
					int itemsEnumId = subtabStruct.getIntValue(690);
					if (itemsEnumId == -1) continue;

					int[] itemIds = client.getEnum(itemsEnumId).getIntVals();
					for (int itemId : itemIds)
					{
						allItemIds.add(itemId);
					}
				}
			}

			// Apply replacements from enum 3721
			try
			{
				net.runelite.api.EnumComposition replacements = client.getEnum(3721);
				for (int badId : replacements.getKeys())
				{
					allItemIds.remove(badId);
				}
				for (int goodId : replacements.getIntVals())
				{
					allItemIds.add(goodId);
				}
			}
			catch (Exception e)
			{
				log.debug("No replacement enum 3721: {}", e.getMessage());
			}

			collectionLogTotal = allItemIds.size();
			log.info("Loaded {} total collection log items from cache", collectionLogTotal);

			// Also build the full category structure for the frontend
			buildCollectionLogStructure();

			// Add all items with quantity 0 (not obtained) as baseline
			for (int itemId : allItemIds)
			{
				if (!collectionLogItemQuantities.containsKey(itemId))
				{
					collectionLogItems.add(itemId);
					collectionLogItemQuantities.put(itemId, 0);
					try
					{
						String name = itemManager.getItemComposition(itemId).getName();
						collectionLogItemNames.put(itemId, name);
					}
					catch (Exception ignored) {}
				}
			}

			log.info("Collection log baseline: {} total items, {} obtained so far",
				collectionLogItems.size(),
				collectionLogItemQuantities.values().stream().filter(q -> q > 0).count());
		}
		catch (Exception e)
		{
			log.warn("Failed to load collection log from cache: {}", e.getMessage());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID)
		{
			// Collection log opened — cycle through all pages to capture everything
			clientThread.invokeLater(() ->
				clientThread.invokeLater(this::readAllCollectionLogPages));
		}
	}

	private void readAllCollectionLogPages()
	{
		try
		{
			// Child 37 is the left sidebar list containing all page entries
			Widget sidebar = client.getWidget(COLLECTION_LOG_GROUP_ID, 37);
			if (sidebar == null)
			{
				log.debug("Collection log sidebar not found");
				return;
			}

			Widget[] entries = sidebar.getDynamicChildren();
			if (entries == null || entries.length == 0)
			{
				log.debug("No sidebar entries found");
				return;
			}

			// Count actual page entries (they have text/names)
			int pageCount = 0;
			for (Widget entry : entries)
			{
				String name = entry.getText();
				if (name != null && !name.isEmpty())
				{
					pageCount++;
				}
			}
			log.info("Collection log has {} pages in sidebar, cycling through all...", pageCount);

			// Click each sidebar entry to load its items
			// We schedule each click with a small delay so the widget can render
			int delay = 0;
			for (Widget entry : entries)
			{
				String name = entry.getText();
				if (name == null || name.isEmpty())
				{
					continue;
				}

				final int idx = entry.getIndex();
				final int d = delay;
				executor.schedule(() ->
					clientThread.invokeLater(() ->
					{
						try
						{
							// Simulate selecting this page entry
							// RunScript 2240 triggers collection log page load
							Widget sidebarWidget = client.getWidget(COLLECTION_LOG_GROUP_ID, 37);
							if (sidebarWidget != null)
							{
								Widget[] children = sidebarWidget.getDynamicChildren();
								if (children != null && idx < children.length)
								{
									// Trigger the page by interacting with the widget
									client.runScript(2240, idx);
								}
							}
						}
						catch (Exception e)
						{
							log.debug("Error cycling page {}: {}", idx, e.getMessage());
						}
					}),
					d, TimeUnit.MILLISECONDS
				);
				delay += 100; // 100ms between pages
			}

			// After cycling all pages, trigger a sync
			executor.schedule(this::collectAndSync,
				delay + 500, TimeUnit.MILLISECONDS);
		}
		catch (Exception e)
		{
			log.warn("Error reading collection log pages: {}", e.getMessage());
		}
	}

	private void readCollectionLogWidget()
	{
		try
		{
			// Scan widget children to find header text and items
			// Try multiple child indices since they vary by RuneLite version
			for (int childId = 0; childId < 50; childId++)
			{
				Widget widget = client.getWidget(COLLECTION_LOG_GROUP_ID, childId);
				if (widget == null)
				{
					continue;
				}

				// Look for the header "Unique Items - 146/1699"
				String text = widget.getText();
				if (text != null && text.contains("/") && text.toLowerCase().contains("unique"))
				{
					String cleaned = text.replaceAll("<[^>]+>", "").replaceAll("[^0-9/]", "");
					String[] parts = cleaned.split("/");
					if (parts.length == 2)
					{
						try
						{
							collectionLogTotal = Integer.parseInt(parts[1]);
							log.info("Collection log header found at child {}: total={}", childId, collectionLogTotal);
						}
						catch (NumberFormatException ignored) {}
					}
				}

				// Look for item containers (dynamic children with item IDs)
				Widget[] dynChildren = widget.getDynamicChildren();
				if (dynChildren != null && dynChildren.length > 0)
				{
					int newItems = 0;
					for (Widget item : dynChildren)
					{
						int itemId = item.getItemId();
						int quantity = item.getItemQuantity();
						int opacity = item.getOpacity();
						// In collection log: opacity 0 = obtained, opacity > 0 = not obtained
						boolean obtained = opacity == 0;
						if (itemId > 0 && itemId != 6512)
						{
							boolean isNew = collectionLogItems.add(itemId);
							collectionLogItemQuantities.put(itemId, obtained ? Math.max(quantity, 1) : 0);
							try
							{
								String name = itemManager.getItemComposition(itemId).getName();
								collectionLogItemNames.put(itemId, name);
							}
							catch (Exception ignored) {}
							if (isNew)
							{
								newItems++;
							}
						}
					}
					if (newItems > 0)
					{
						log.info("Read {} items from child {} (total tracked: {})",
							newItems, childId, collectionLogItems.size());
					}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Error reading collection log widget: {}", e.getMessage());
		}
	}

	public int getCollectionLogCount()
	{
		return collectionLogItems.size();
	}

	public void triggerSync()
	{
		collectAndSync();
	}

	public void syncOnLogout()
	{
		// On logout we're still on the client thread so we can read game state
		try
		{
			Map<String, Object> payload = buildProfilePayload();
			executor.submit(() -> sendSync(payload));
		}
		catch (Exception e)
		{
			log.error("Failed to build logout sync payload", e);
		}
	}

	private void collectAndSync()
	{
		if (!playerContext.isValid())
		{
			log.debug("Skipping profile sync: player context not valid");
			return;
		}

		String webhookUrl = config.webhookUrl();
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			return;
		}

		// Read game state on client thread, then send on executor thread
		clientThread.invokeLater(() ->
		{
			try
			{
				Map<String, Object> payload = buildProfilePayload();
				executor.submit(() -> sendSync(payload));
			}
			catch (Exception e)
			{
				log.error("Profile sync failed to collect data", e);
			}
		});
	}

	private void sendSync(Map<String, Object> payload)
	{
		String webhookUrl = config.webhookUrl();
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			return;
		}

		try
		{
			String json = gson.toJson(payload);

			// Extract token from webhook URL and build sync URL from base
			String token = extractToken(webhookUrl);
			if (token == null)
			{
				log.warn("Could not extract token from webhook URL");
				return;
			}
			String syncUrl = PluginAuthService.HUB_BASE_URL + "/api/runescape-events/profile-sync?token=" + token;

			HttpUrl url = HttpUrl.parse(syncUrl);
			if (url == null)
			{
				log.warn("Invalid sync URL: {}", syncUrl);
				return;
			}

			RequestBody body = RequestBody.create(JSON_TYPE, json);
			Request request = new Request.Builder()
				.url(url)
				.post(body)
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (response.isSuccessful())
				{
					log.info("Profile sync successful for {}", playerContext.getPlayerName());
				}
				else
				{
					log.warn("Profile sync failed: {} {}", response.code(), response.message());
				}
			}
		}
		catch (IOException e)
		{
			log.error("Profile sync error", e);
		}
	}

	private String extractToken(String webhookUrl)
	{
		HttpUrl parsed = HttpUrl.parse(webhookUrl);
		if (parsed == null)
		{
			return null;
		}
		return parsed.queryParameter("token");
	}

	private Map<String, Object> buildProfilePayload()
	{
		Map<String, Object> payload = new HashMap<>();
		payload.put("playerName", playerContext.getPlayerName());
		payload.put("accountType", playerContext.getAccountType());
		payload.put("dinkAccountHash", playerContext.getAccountHash());

		// Skills
		payload.put("skills", buildSkillsData());

		// Quests
		payload.put("quests", buildQuestData());

		// Achievement Diaries
		payload.put("achievementDiaries", buildDiaryData());

		// Combat Achievements
		payload.put("combatAchievements", buildCombatAchievementData());

		// Collection Log — { "itemId": { "name": "Abyssal whip", "quantity": 5 } }
		Map<String, Object> clogData = new HashMap<>();
		Map<String, Object> itemMap = new HashMap<>();
		int obtained = 0;
		for (Map.Entry<Integer, Integer> entry : collectionLogItemQuantities.entrySet())
		{
			Map<String, Object> itemInfo = new HashMap<>();
			itemInfo.put("quantity", entry.getValue());
			String name = collectionLogItemNames.get(entry.getKey());
			if (name != null)
			{
				itemInfo.put("name", name);
			}
			itemMap.put(String.valueOf(entry.getKey()), itemInfo);
			if (entry.getValue() > 0)
			{
				obtained++;
			}
		}
		clogData.put("completedEntries", obtained);
		clogData.put("totalEntries", collectionLogTotal > 0 ? collectionLogTotal : null);
		clogData.put("items", itemMap);
		if (collectionLogStructure != null)
		{
			clogData.put("sources", collectionLogStructure);
		}
		payload.put("collectionLog", clogData);

		// Player appearance (equipment + model)
		PlayerAppearanceTracker appearanceTracker = new PlayerAppearanceTracker(client, itemManager);
		Map<String, Object> appearance = appearanceTracker.getAppearanceData();
		if (appearance != null)
		{
			payload.put("appearance", appearance);
		}

		// Pet collection (derived from collection log items)
		PetTracker petTracker = new PetTracker();
		payload.put("pets", petTracker.getPetData(collectionLogItemQuantities, collectionLogItemNames));

		// Bank value snapshot (if player has opened bank this session)
		if (bankSnapshotSupplier != null)
		{
			Map<String, Object> bankData = bankSnapshotSupplier.get();
			if (bankData != null)
			{
				payload.put("bankValue", bankData);
			}
		}

		// Boss PBs and kill counts
		if (bossDataSupplier != null)
		{
			Map<String, Object> bossData = bossDataSupplier.get();
			if (bossData != null)
			{
				payload.put("bosses", bossData);
			}
		}

		// Session data (XP gained, loot, deaths, duration)
		if (sessionDataSupplier != null)
		{
			Map<String, Object> sessionData = sessionDataSupplier.get();
			if (sessionData != null)
			{
				payload.put("session", sessionData);
			}
		}

		return payload;
	}

	private Map<String, Object> buildSkillsData()
	{
		Map<String, Object> skills = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill.ordinal() > Skill.SAILING.ordinal())
			{
				continue;
			}
			Map<String, Object> skillData = new HashMap<>();
			skillData.put("level", client.getRealSkillLevel(skill));
			skillData.put("xp", client.getSkillExperience(skill));
			skills.put(skill.getName(), skillData);
		}
		return skills;
	}

	// Known miniquest names — these are separate from the main quest count
	private static final Set<String> MINIQUEST_NAMES = new HashSet<>(Arrays.asList(
		"Alfred Grimhand's Barcrawl", "Barbarian Training", "Bear Your Soul",
		"Curse of the Empty Lord", "Daddy's Home", "The Enchanted Key",
		"Enter the Abyss", "Family Pest", "The Frozen Door",
		"The General's Shadow", "His Faithful Servants", "Hopespear's Will",
		"In Search of Knowledge", "Into the Tombs", "Lair of Tarn Razorlor",
		"Mage Arena I", "Mage Arena II", "Skippy and the Mogres",
		"Vale Totems"
	));

	// RFD sub-quests — part of "Recipe for Disaster", not counted separately
	private static final Set<String> EXCLUDED_QUESTS = new HashSet<>(Arrays.asList(
		"Recipe for Disaster - Another Cook's Quest",
		"Recipe for Disaster - Culinaromancer",
		"Recipe for Disaster - Evil Dave",
		"Recipe for Disaster - King Awowogei",
		"Recipe for Disaster - Lumbridge Guide",
		"Recipe for Disaster - Mountain Dwarf",
		"Recipe for Disaster - Pirate Pete",
		"Recipe for Disaster - Sir Amik Varze",
		"Recipe for Disaster - Skrach Uglogwee",
		"Recipe for Disaster - Wartface & Bentnoze"
	));

	// Known F2P quest names
	private static final Set<String> F2P_QUEST_NAMES = new HashSet<>(Arrays.asList(
		"Below Ice Mountain", "Black Knights' Fortress", "Cook's Assistant",
		"The Corsair Curse", "Demon Slayer", "Doric's Quest",
		"Dragon Slayer I", "Ernest the Chicken", "Goblin Diplomacy",
		"Imp Catcher", "The Knight's Sword", "Misthalin Mystery",
		"Pirate's Treasure", "Prince Ali Rescue", "The Restless Ghost",
		"Romeo & Juliet", "Rune Mysteries", "Sheep Shearer",
		"Shield of Arrav", "Vampyre Slayer", "Witch's Potion",
		"X Marks the Spot", "The Ides of Milk", "Learning the Ropes"
	));

	private Map<String, Object> buildQuestData()
	{
		Map<String, Object> questData = new HashMap<>();
		Map<String, Integer> questStates = new HashMap<>();

		// Categorized quest lists (preserve insertion order)
		List<Map<String, Object>> freeQuests = new ArrayList<>();
		List<Map<String, Object>> memberQuests = new ArrayList<>();
		List<Map<String, Object>> miniQuests = new ArrayList<>();

		int completedQuests = 0;
		int totalQuests = 0;
		int completedMini = 0;
		int totalMini = 0;

		for (Quest quest : Quest.values())
		{
			try
			{
				QuestState state = quest.getState(client);
				int stateValue;
				switch (state)
				{
					case FINISHED:
						stateValue = 2;
						break;
					case IN_PROGRESS:
						stateValue = 1;
						break;
					default:
						stateValue = 0;
						break;
				}

				String name = quest.getName();
				questStates.put(name, stateValue);

				// Skip RFD sub-quests — they're part of the main quest
				if (EXCLUDED_QUESTS.contains(name))
				{
					continue;
				}

				Map<String, Object> entry = new HashMap<>();
				entry.put("name", name);
				entry.put("state", stateValue);

				if (MINIQUEST_NAMES.contains(name))
				{
					miniQuests.add(entry);
					totalMini++;
					if (stateValue == 2) completedMini++;
				}
				else
				{
					totalQuests++;
					if (stateValue == 2) completedQuests++;
					if (F2P_QUEST_NAMES.contains(name))
					{
						freeQuests.add(entry);
					}
					else
					{
						memberQuests.add(entry);
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Failed to read quest state for {}: {}", quest.getName(), e.getMessage());
			}
		}

		// Sort each list alphabetically
		Comparator<Map<String, Object>> byName = (a, b) ->
			((String) a.get("name")).compareTo((String) b.get("name"));
		freeQuests.sort(byName);
		memberQuests.sort(byName);
		miniQuests.sort(byName);

		int questPoints;
		try
		{
			questPoints = client.getVarpValue(101);
		}
		catch (Exception e)
		{
			questPoints = 0;
		}

		questData.put("states", questStates);
		// Try game varbits for accurate counts, fall back to enum counting
		int varbitCompleted = 0;
		int varbitTotal = 0;
		try
		{
			varbitCompleted = client.getVarbitValue(6338); // QUESTS_COMPLETED_COUNT
			varbitTotal = client.getVarbitValue(11876); // QUESTS_TOTAL_COUNT
		}
		catch (Exception e)
		{
			log.debug("Failed to read quest count varbits: {}", e.getMessage());
		}
		questData.put("completedQuests", varbitCompleted > 0 ? varbitCompleted : completedQuests);
		questData.put("totalQuests", varbitTotal > 0 ? varbitTotal : totalQuests);
		questData.put("completedMini", completedMini);
		questData.put("totalMini", totalMini);
		questData.put("questPoints", questPoints);

		// Categorized lists for the frontend
		Map<String, Object> categories = new LinkedHashMap<>();
		categories.put("Free Quests", freeQuests);
		categories.put("Members' Quests", memberQuests);
		categories.put("Miniquests", miniQuests);
		questData.put("categories", categories);
		// totalQuestPoints derived from total quest count — hub can compute the real max

		return questData;
	}

	// Diary areas with their Script 2200 IDs
	// The script ID for each area can be found by matching against the in-game diary list
	// Using the RuneProfile approach: iterate all 12 IDs but log which data maps to which
	private static final String[][] DIARY_AREAS = {
		// { name, script2200_id }
		{"Ardougne", "0"},
		{"Desert", "1"},
		{"Falador", "2"},
		{"Fremennik", "3"},
		{"Kandarin", "4"},
		{"Karamja", "5"},
		{"Kourend & Kebos", "6"},
		{"Lumbridge & Draynor", "7"},
		{"Morytania", "8"},
		{"Varrock", "9"},
		{"Western Provinces", "10"},
		{"Wilderness", "11"},
	};

	private Map<String, Object> buildDiaryData()
	{
		Map<String, Object> diaries = new LinkedHashMap<>();

		// First try to find the correct mapping by using diary completion varbits
		// to verify which index maps to which area
		int[][] diaryVarbits = {
			{4458, 4459, 4460, 4461}, // Ardougne
			{4483, 4484, 4485, 4486}, // Desert
			{4462, 4463, 4464, 4465}, // Falador
			{4491, 4492, 4493, 4494}, // Fremennik
			{4475, 4476, 4477, 4478}, // Kandarin
			{3578, 3599, 3611, 4566}, // Karamja
			{7925, 7926, 7927, 7928}, // Kourend & Kebos
			{4495, 4496, 4497, 4498}, // Lumbridge & Draynor
			{4487, 4488, 4489, 4490}, // Morytania
			{4479, 4480, 4481, 4482}, // Varrock
			{4471, 4472, 4473, 4474}, // Western Provinces
			{4466, 4467, 4468, 4469}, // Wilderness
		};

		String[] areaNames = {
			"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin",
			"Karamja", "Kourend & Kebos", "Lumbridge & Draynor",
			"Morytania", "Varrock", "Western Provinces", "Wilderness"
		};

		// Use varbits for tier completion (reliable) and try Script 2200 for task counts
		for (int i = 0; i < areaNames.length; i++)
		{
			try
			{
				Map<String, Object> area = new HashMap<>();

				// Tier completion from varbits (reliable, known mapping)
				boolean easy = client.getVarbitValue(diaryVarbits[i][0]) == 1;
				boolean medium = client.getVarbitValue(diaryVarbits[i][1]) == 1;
				boolean hard = client.getVarbitValue(diaryVarbits[i][2]) == 1;
				boolean elite = client.getVarbitValue(diaryVarbits[i][3]) == 1;

				area.put("easy", easy);
				area.put("medium", medium);
				area.put("hard", hard);
				area.put("elite", elite);

				// Try Script 2200 for detailed task counts
				// Use the same index as varbit order
				try
				{
					client.runScript(2200, i);
					int[] stack = client.getIntStack();
					if (stack.length >= 11)
					{
						int easyDone = stack[0];
						int easyTotal = stack[1];
						int medDone = stack[3];
						int medTotal = stack[4];
						int hardDone = stack[6];
						int hardTotal = stack[7];
						int eliteDone = stack[9];
						int eliteTotal = stack[10];

						area.put("easyTasks", easyDone + "/" + easyTotal);
						area.put("mediumTasks", medDone + "/" + medTotal);
						area.put("hardTasks", hardDone + "/" + hardTotal);
						area.put("eliteTasks", eliteDone + "/" + eliteTotal);
						area.put("tasksCompleted", easyDone + medDone + hardDone + eliteDone);
						area.put("tasksTotal", easyTotal + medTotal + hardTotal + eliteTotal);
					}
				}
				catch (Exception scriptErr)
				{
					// Script failed — just use varbit data without task counts
					log.debug("Script 2200 failed for {}: {}", areaNames[i], scriptErr.getMessage());
				}

				diaries.put(areaNames[i], area);
			}
			catch (Exception e)
			{
				log.debug("Failed to read diary data for {}: {}", areaNames[i], e.getMessage());
			}
		}

		return diaries;
	}

	private Map<String, Object> buildCombatAchievementData()
	{
		Map<String, Object> caData = new HashMap<>();

		// Script 4784 per tier ID returns completed count
		// Tier IDs: 1=Easy, 2=Medium, 3=Hard, 4=Elite, 5=Master, 6=Grandmaster
		// Points per task: Easy=1, Medium=2, Hard=3, Elite=4, Master=5, Grandmaster=6
		String[] tierNames = {"easy", "medium", "hard", "elite", "master", "grandmaster"};
		int[] pointsPerTask = {1, 2, 3, 4, 5, 6};

		int totalPoints = 0;
		int totalCompleted = 0;
		Map<String, Object> tierData = new HashMap<>();

		for (int tierIdx = 0; tierIdx < tierNames.length; tierIdx++)
		{
			try
			{
				int tierId = tierIdx + 1;
				client.runScript(4784, tierId);
				int[] stack = client.getIntStack();
				if (stack.length == 0)
				{
					continue;
				}
				int completed = stack[0];

				Map<String, Object> tier = new HashMap<>();
				tier.put("completed", completed);
				tier.put("points", completed * pointsPerTask[tierIdx]);

				tierData.put(tierNames[tierIdx], tier);
				totalPoints += completed * pointsPerTask[tierIdx];
				totalCompleted += completed;
			}
			catch (Exception e)
			{
				log.debug("Failed to read CA tier {}: {}", tierNames[tierIdx], e.getMessage());
			}
		}

		caData.put("totalPoints", totalPoints);
		caData.put("totalCompleted", totalCompleted);
		caData.put("tiers", tierData);
		// Note: totalPossiblePoints and totalTasks not sent — hub maintains canonical totals
		// since these change when Jagex adds new tasks

		return caData;
	}
}
