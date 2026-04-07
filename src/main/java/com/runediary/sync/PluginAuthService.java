package com.runediary.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.runediary.RuneDiaryConfig;
import com.runediary.model.PlayerContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class PluginAuthService
{
	private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
	private final Gson gson;
	static final String HUB_BASE_URL = System.getProperty("runediary.hubUrl", "https://www.runediary.com");

	private final RuneDiaryConfig config;
	private final OkHttpClient httpClient;
	private final ScheduledExecutorService executor;
	private final ConfigManager configManager;
	private final PlayerContext playerContext;

	public PluginAuthService(Gson gson, RuneDiaryConfig config, OkHttpClient httpClient,
							 ScheduledExecutorService executor, ConfigManager configManager,
							 PlayerContext playerContext)
	{
		this.gson = gson;
		this.config = config;
		this.httpClient = httpClient;
		this.executor = executor;
		this.configManager = configManager;
		this.playerContext = playerContext;
	}

	public void authenticateIfNeeded()
	{
		// Already has a webhook URL configured — skip
		String existingUrl = config.webhookUrl();
		if (existingUrl != null && !existingUrl.isEmpty())
		{
			log.debug("Webhook URL already configured, skipping auto-auth");
			return;
		}

		if (!playerContext.isValid())
		{
			log.debug("Player context not valid, skipping auto-auth");
			return;
		}

		executor.submit(this::doAuth);
	}

	private void doAuth()
	{
		String baseUrl = HUB_BASE_URL;

		String authUrl = baseUrl + "/api/runescape-events/plugin-auth";
		HttpUrl url = HttpUrl.parse(authUrl);
		if (url == null)
		{
			log.warn("Invalid auth URL: {}", authUrl);
			return;
		}

		Map<String, String> payload = new HashMap<>();
		payload.put("playerName", playerContext.getPlayerName());
		payload.put("dinkAccountHash", playerContext.getAccountHash());
		payload.put("accountType", playerContext.getAccountType());

		String json = gson.toJson(payload);
		RequestBody body = RequestBody.create(JSON_TYPE, json);
		Request request = new Request.Builder()
			.url(url)
			.post(body)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				log.warn("Plugin auth failed: {} {}", response.code(), response.message());
				return;
			}

			if (response.body() == null)
			{
				log.warn("Plugin auth returned empty body");
				return;
			}

			String responseBody = response.body().string();
			JsonObject result = gson.fromJson(responseBody, JsonObject.class);

			if (result != null && result.has("webhookUrl"))
			{
				String webhookUrl = result.get("webhookUrl").getAsString();
				configManager.setConfiguration("runediary", "webhookUrl", webhookUrl);
				log.info("Auto-configured webhook URL for {}", playerContext.getPlayerName());
			}
			else
			{
				log.warn("Plugin auth response missing webhookUrl: {}", responseBody);
			}
		}
		catch (IOException e)
		{
			log.error("Plugin auth error", e);
		}
	}
}
