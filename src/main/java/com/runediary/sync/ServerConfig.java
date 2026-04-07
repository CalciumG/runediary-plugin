package com.runediary.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.runediary.RuneDiaryConfig;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Server-enforced configuration fetched from the hub.
 * These values override local config for thresholds — users cannot lower them.
 * Event toggles from the server can disable events globally.
 */
@Slf4j
public class ServerConfig
{
	private final Gson gson;

	private final RuneDiaryConfig localConfig;
	private final OkHttpClient httpClient;
	private final ScheduledExecutorService executor;

	// Server-enforced thresholds (defaults match hub's config.json)
	@Getter private volatile int minLootValue = 100000;
	@Getter private volatile int clueMinValue = 100000;
	@Getter private volatile int lootImageMinValue = 500000;
	@Getter private volatile int killCountInterval = 300;

	// Server-enforced event toggles (accessed via is*Enabled() which combines server + local)
	private volatile boolean deathEnabled = true;
	private volatile boolean levelEnabled = true;
	private volatile boolean lootEnabled = true;
	private volatile boolean killCountEnabled = true;
	private volatile boolean slayerEnabled = true;
	private volatile boolean questEnabled = true;
	private volatile boolean petEnabled = true;
	private volatile boolean clueEnabled = true;
	private volatile boolean combatAchievementEnabled = true;
	private volatile boolean diaryEnabled = true;
	private volatile boolean collectionLogEnabled = true;

	private volatile boolean loaded = false;

	public ServerConfig(Gson gson, RuneDiaryConfig localConfig, OkHttpClient httpClient, ScheduledExecutorService executor)
	{
		this.gson = gson;
		this.localConfig = localConfig;
		this.httpClient = httpClient;
		this.executor = executor;
	}

	/**
	 * Fetch config from hub. Call after auth has set the webhook URL.
	 */
	public void fetchFromHub()
	{
		executor.submit(this::doFetch);
	}

	/**
	 * Check if an event type is enabled.
	 * Server can disable globally; user can disable locally. Both must agree.
	 */
	public boolean isDeathEnabled()
	{
		return deathEnabled && localConfig.deathEnabled();
	}
	public boolean isLevelEnabled()
	{
		return levelEnabled && localConfig.levelEnabled();
	}
	public boolean isLootEnabled()
	{
		return lootEnabled && localConfig.lootEnabled();
	}
	public boolean isKillCountEnabled()
	{
		return killCountEnabled && localConfig.killCountEnabled();
	}
	public boolean isSlayerEnabled()
	{
		return slayerEnabled && localConfig.slayerEnabled();
	}
	public boolean isQuestEnabled()
	{
		return questEnabled && localConfig.questEnabled();
	}
	public boolean isPetEnabled()
	{
		return petEnabled && localConfig.petEnabled();
	}
	public boolean isClueEnabled()
	{
		return clueEnabled && localConfig.clueEnabled();
	}
	public boolean isCombatAchievementEnabled()
	{
		return combatAchievementEnabled && localConfig.combatAchievementEnabled();
	}
	public boolean isDiaryEnabled()
	{
		return diaryEnabled && localConfig.diaryEnabled();
	}
	public boolean isCollectionLogEnabled()
	{
		return collectionLogEnabled && localConfig.collectionLogEnabled();
	}

	public boolean isLoaded()
	{
		return loaded;
	}

	private void doFetch()
	{
		String webhookUrl = localConfig.webhookUrl();
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			log.debug("No webhook URL, skipping config fetch");
			return;
		}

		HttpUrl parsed = HttpUrl.parse(webhookUrl);
		if (parsed == null)
		{
			return;
		}

		String token = parsed.queryParameter("token");
		if (token == null)
		{
			return;
		}

		String configUrl = PluginAuthService.HUB_BASE_URL + "/api/runescape-events/plugin-config?token=" + token;
		HttpUrl url = HttpUrl.parse(configUrl);
		if (url == null)
		{
			return;
		}

		Request request = new Request.Builder()
			.url(url)
			.get()
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Failed to fetch server config: {} {}", response.code(), response.message());
				return;
			}

			String body = response.body().string();
			JsonObject json = gson.fromJson(body, JsonObject.class);
			if (json == null)
			{
				return;
			}

			// Apply server values
			if (json.has("minLootValue")) minLootValue = json.get("minLootValue").getAsInt();
			if (json.has("clueMinValue")) clueMinValue = json.get("clueMinValue").getAsInt();
			if (json.has("lootImageMinValue")) lootImageMinValue = json.get("lootImageMinValue").getAsInt();
			if (json.has("killCountInterval")) killCountInterval = json.get("killCountInterval").getAsInt();
			if (json.has("deathEnabled")) deathEnabled = json.get("deathEnabled").getAsBoolean();
			if (json.has("levelEnabled")) levelEnabled = json.get("levelEnabled").getAsBoolean();
			if (json.has("lootEnabled")) lootEnabled = json.get("lootEnabled").getAsBoolean();
			if (json.has("killCountEnabled")) killCountEnabled = json.get("killCountEnabled").getAsBoolean();
			if (json.has("slayerEnabled")) slayerEnabled = json.get("slayerEnabled").getAsBoolean();
			if (json.has("questEnabled")) questEnabled = json.get("questEnabled").getAsBoolean();
			if (json.has("petEnabled")) petEnabled = json.get("petEnabled").getAsBoolean();
			if (json.has("clueEnabled")) clueEnabled = json.get("clueEnabled").getAsBoolean();
			if (json.has("combatTaskEnabled")) combatAchievementEnabled = json.get("combatTaskEnabled").getAsBoolean();
			if (json.has("diaryEnabled")) diaryEnabled = json.get("diaryEnabled").getAsBoolean();
			if (json.has("collectionLogEnabled")) collectionLogEnabled = json.get("collectionLogEnabled").getAsBoolean();

			loaded = true;
			log.info("Server config loaded: minLoot={}, clueMin={}, kcInterval={}",
				minLootValue, clueMinValue, killCountInterval);
		}
		catch (IOException e)
		{
			log.error("Failed to fetch server config", e);
		}
	}
}
