package com.runediary.sync;

import com.google.gson.Gson;
import com.runediary.RuneDiaryConfig;
import com.runediary.model.PlayerContext;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProfileSyncServiceSeasonalTest
{
	private OkHttpClient httpClient;
	private RuneDiaryConfig config;
	private PlayerContext playerContext;
	private ProfileSyncService service;

	@Before
	public void setUp() throws Exception
	{
		httpClient = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		Response response = new Response.Builder()
			.request(new Request.Builder().url("https://example.com/").build())
			.protocol(Protocol.HTTP_1_1)
			.code(200)
			.message("OK")
			.body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{}"))
			.build();
		when(call.execute()).thenReturn(response);
		when(httpClient.newCall(any(Request.class))).thenReturn(call);
		config = mock(RuneDiaryConfig.class);
		when(config.webhookUrl()).thenReturn(
			"https://www.runediary.com/api/runescape-events?token=test-token"
		);
		playerContext = new PlayerContext();
		playerContext.setPlayerName("TestPlayer");
		playerContext.setAccountHash("deadbeef");
		service = new ProfileSyncService(
			new Gson(),
			mock(Client.class),
			config,
			httpClient,
			mock(java.util.concurrent.ScheduledExecutorService.class),
			mock(ClientThread.class),
			playerContext,
			mock(ItemManager.class)
		);
	}

	@Test
	public void sendSync_doesNotCallHttp_whenSeasonalWorld()
	{
		playerContext.setSeasonalWorld(true);

		service.sendSync(Collections.emptyMap());

		verify(httpClient, never()).newCall(any(Request.class));
	}

	@Test
	public void sendSync_callsHttp_whenNotSeasonal()
	{
		playerContext.setSeasonalWorld(false);

		service.sendSync(Collections.emptyMap());

		verify(httpClient, times(1)).newCall(any(Request.class));
	}

	@Test
	public void sendSync_doesNotCallHttp_whenWebhookUrlMissing()
	{
		playerContext.setSeasonalWorld(false);
		when(config.webhookUrl()).thenReturn("");

		service.sendSync(Collections.emptyMap());

		verify(httpClient, never()).newCall(any(Request.class));
	}
}
