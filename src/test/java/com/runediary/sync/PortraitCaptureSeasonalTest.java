package com.runediary.sync;

import com.runediary.RuneDiaryConfig;
import com.runediary.model.PlayerContext;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
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

public class PortraitCaptureSeasonalTest
{
	private OkHttpClient httpClient;
	private RuneDiaryConfig config;
	private PlayerContext playerContext;
	private PortraitCapture capture;

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
		capture = new PortraitCapture(
			mock(Client.class),
			config,
			httpClient,
			mock(java.util.concurrent.ScheduledExecutorService.class),
			mock(ClientThread.class),
			playerContext,
			"TestPlayer"
		);
	}

	@Test
	public void uploadModel_doesNotCallHttp_whenSeasonalWorld()
	{
		playerContext.setSeasonalWorld(true);

		capture.uploadModel(new byte[]{1, 2, 3}, "player");

		verify(httpClient, never()).newCall(any(Request.class));
	}

	@Test
	public void uploadModel_callsHttp_whenNotSeasonal()
	{
		playerContext.setSeasonalWorld(false);

		capture.uploadModel(new byte[]{1, 2, 3}, "player");

		verify(httpClient, times(1)).newCall(any(Request.class));
	}

	@Test
	public void uploadModel_doesNotCallHttp_whenWebhookUrlMissing()
	{
		playerContext.setSeasonalWorld(false);
		when(config.webhookUrl()).thenReturn("");

		capture.uploadModel(new byte[]{1, 2, 3}, "player");

		verify(httpClient, never()).newCall(any(Request.class));
	}
}
