package com.runediary.dispatch;

import com.google.gson.Gson;
import com.runediary.RuneDiaryConfig;
import com.runediary.model.EventPayload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class WebhookDispatcher
{
	private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType IMAGE_TYPE = MediaType.parse("image/png");
	private final Gson gson;
	private final OkHttpClient httpClient;
	private final ScheduledExecutorService executor;
	private final RuneDiaryConfig config;

	public WebhookDispatcher(Gson gson, OkHttpClient httpClient, ScheduledExecutorService executor, RuneDiaryConfig config)
	{
		this.gson = gson;
		this.httpClient = httpClient;
		this.executor = executor;
		this.config = config;
	}

	public void dispatch(EventPayload payload, @Nullable BufferedImage screenshot)
	{
		String webhookUrl = config.webhookUrl();
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			log.debug("No webhook URL configured, skipping dispatch for {} event", payload.getType());
			return;
		}

		HttpUrl url = HttpUrl.parse(webhookUrl);
		if (url == null)
		{
			log.warn("Invalid webhook URL: {}", webhookUrl);
			return;
		}

		executor.submit(() -> send(payload, screenshot, url));
	}

	private void send(EventPayload payload, @Nullable BufferedImage screenshot, HttpUrl url)
	{
		try
		{
			String json = gson.toJson(payload);
			Request request;

			if (screenshot != null)
			{
				byte[] imageBytes = toImageBytes(screenshot);
				if (imageBytes == null)
				{
					log.warn("Failed to encode screenshot, sending without image");
					request = buildJsonRequest(url, json);
				}
				else
				{
					request = buildMultipartRequest(url, json, imageBytes);
				}
			}
			else
			{
				request = buildJsonRequest(url, json);
			}

			try (Response response = httpClient.newCall(request).execute())
			{
				if (response.isSuccessful())
				{
					log.debug("Successfully sent {} event for {}", payload.getType(), payload.getPlayerName());
				}
				else
				{
					log.warn("Failed to send {} event: {} {}", payload.getType(), response.code(), response.message());
				}
			}
		}
		catch (IOException e)
		{
			log.error("Error sending {} event", payload.getType(), e);
		}
	}

	private Request buildJsonRequest(HttpUrl url, String json)
	{
		RequestBody body = RequestBody.create(JSON_TYPE, json);
		return new Request.Builder()
			.url(url)
			.post(body)
			.build();
	}

	private Request buildMultipartRequest(HttpUrl url, String json, byte[] imageBytes)
	{
		MultipartBody body = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("payload_json", json)
			.addFormDataPart("file", "screenshot.png", RequestBody.create(IMAGE_TYPE, imageBytes))
			.build();

		return new Request.Builder()
			.url(url)
			.post(body)
			.build();
	}

	@Nullable
	private byte[] toImageBytes(BufferedImage image)
	{
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "png", baos);
			return baos.toByteArray();
		}
		catch (IOException e)
		{
			log.error("Failed to encode image", e);
			return null;
		}
	}
}
