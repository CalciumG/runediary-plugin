package com.runediary.dispatch;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.runelite.client.ui.DrawManager;

public class ScreenshotCapture
{
	private final DrawManager drawManager;

	public ScreenshotCapture(DrawManager drawManager)
	{
		this.drawManager = drawManager;
	}

	public CompletableFuture<BufferedImage> capture()
	{
		CompletableFuture<BufferedImage> future = new CompletableFuture<>();

		Consumer<Image> callback = image ->
		{
			int width = image.getWidth(null);
			int height = image.getHeight(null);
			BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = buffered.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			future.complete(buffered);
		};

		drawManager.requestNextFrameListener(callback);
		return future;
	}
}
