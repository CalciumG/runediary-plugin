package com.runediary.sync;

import com.runediary.RuneDiaryConfig;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Captures the player's 3D model and uploads it to the hub.
 * Uses player.getModel() to get the actual character mesh,
 * same approach as RuneProfile.
 */
@Slf4j
public class PortraitCapture
{
	private static final MediaType OCTET_TYPE = MediaType.parse("application/octet-stream");

	private final Client client;
	private final RuneDiaryConfig config;
	private final OkHttpClient httpClient;
	private final ScheduledExecutorService executor;
	private final ClientThread clientThread;
	private final String playerName;

	public PortraitCapture(Client client, RuneDiaryConfig config, OkHttpClient httpClient,
						   ScheduledExecutorService executor, ClientThread clientThread,
						   String playerName)
	{
		this.client = client;
		this.config = config;
		this.httpClient = httpClient;
		this.executor = executor;
		this.clientThread = clientThread;
		this.playerName = playerName;
	}

	public void captureAfterDelay()
	{
		executor.schedule(() ->
			clientThread.invokeLater(this::doCapture),
			5, TimeUnit.SECONDS
		);
	}

	private void doCapture()
	{
		try
		{
			Player player = client.getLocalPlayer();
			if (player == null)
			{
				return;
			}

			Model model = player.getModel();
			if (model == null)
			{
				log.debug("Player model is null, skipping portrait");
				return;
			}

			byte[] modelBytes = exportModelToPly(model);
			if (modelBytes == null || modelBytes.length == 0)
			{
				return;
			}

			// Also capture pet model if present
			byte[] petBytes = null;
			NPC follower = findFollower(player);
			if (follower != null)
			{
				Model petModel = follower.getModel();
				if (petModel != null)
				{
					petBytes = exportModelToPly(petModel);
					log.info("Pet model captured: {} ({}KB)", follower.getName(), petBytes != null ? petBytes.length / 1024 : 0);
				}
			}

			final byte[] playerModel = modelBytes;
			final byte[] petModel2 = petBytes;
			executor.submit(() -> {
				uploadModel(playerModel, "player");
				if (petModel2 != null && petModel2.length > 0)
				{
					uploadModel(petModel2, "pet");
				}
			});
			log.info("Player model captured for {} ({}KB PLY)", playerName, modelBytes.length / 1024);
		}
		catch (Exception e)
		{
			log.error("Portrait capture failed", e);
		}
	}

	private NPC findFollower(Player player)
	{
		try
		{
			// Find the player's follower (pet) from nearby NPCs
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc != null && npc.getInteracting() == player && npc.getComposition() != null
					&& npc.getComposition().isFollower())
				{
					return npc;
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Error finding follower: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * Export a RuneLite Model to PLY binary format.
	 * PLY stores vertices (x,y,z,r,g,b) and triangular faces.
	 */
	private byte[] exportModelToPly(Model model)
	{
		try
		{
			int faceCount = model.getFaceCount();
			int vertexCount = faceCount * 3;

			float[] verticesX = model.getVerticesX();
			float[] verticesY = model.getVerticesY();
			float[] verticesZ = model.getVerticesZ();
			int[] indices1 = model.getFaceIndices1();
			int[] indices2 = model.getFaceIndices2();
			int[] indices3 = model.getFaceIndices3();
			int[] faceColors1 = model.getFaceColors1();

			if (verticesX == null || indices1 == null)
			{
				return null;
			}

			// Build PLY header
			String header = "ply\n"
				+ "format binary_little_endian 1.0\n"
				+ "element vertex " + vertexCount + "\n"
				+ "property float x\n"
				+ "property float y\n"
				+ "property float z\n"
				+ "property uchar red\n"
				+ "property uchar green\n"
				+ "property uchar blue\n"
				+ "element face " + faceCount + "\n"
				+ "property list uchar int vertex_indices\n"
				+ "end_header\n";

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(header.getBytes());

			DataOutputStream dos = new DataOutputStream(baos);

			// Write vertices
			for (int face = 0; face < faceCount; face++)
			{
				int[] faceIndices = { indices1[face], indices2[face], indices3[face] };

				// Get color for this face (convert from OSRS HSL)
				int rgb = 0x808080; // default grey
				if (faceColors1 != null && face < faceColors1.length)
				{
					rgb = rs2hslToRgb(faceColors1[face]);
				}
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;

				for (int idx : faceIndices)
				{
					if (idx >= 0 && idx < verticesX.length)
					{
						writeFloatLE(dos, verticesX[idx]);
						writeFloatLE(dos, -verticesY[idx]); // Y is inverted in OSRS
						writeFloatLE(dos, verticesZ[idx]);
						dos.writeByte(r);
						dos.writeByte(g);
						dos.writeByte(b);
					}
				}
			}

			// Write faces
			for (int face = 0; face < faceCount; face++)
			{
				dos.writeByte(3); // triangle
				writeIntLE(dos, face * 3);
				writeIntLE(dos, face * 3 + 1);
				writeIntLE(dos, face * 3 + 2);
			}

			dos.flush();
			return baos.toByteArray();
		}
		catch (Exception e)
		{
			log.error("Failed to export model to PLY", e);
			return null;
		}
	}

	/**
	 * Convert OSRS RS2HSL color to RGB.
	 * OSRS uses a custom HSL format: 16 bits packed as HHHHHHSSSLLLLLLL
	 * where H=hue(6 bits), S=saturation(3 bits), L=lightness(7 bits)
	 */
	private static int rs2hslToRgb(int rs2hsl)
	{
		// Handle special values
		if (rs2hsl < 0 || rs2hsl > 65535)
		{
			return 0x808080;
		}

		int h = (rs2hsl >> 10) & 0x3F;
		int s = (rs2hsl >> 7) & 0x07;
		int l = rs2hsl & 0x7F;

		// Convert to 0-1 range with OSRS-specific scaling
		float hue = h / 64.0f;
		float sat = (s + 1) / 8.0f; // +1 to avoid zero saturation
		float lit = l / 128.0f;

		// OSRS applies a brightness curve to lightness
		// This makes the colors much brighter than raw values suggest
		if (l < 64)
		{
			lit = lit * 2.0f; // Boost dark values
		}
		else
		{
			lit = 0.5f + (lit - 0.5f) * 1.5f; // Stretch light values
		}
		lit = Math.min(1.0f, Math.max(0.0f, lit));

		return java.awt.Color.HSBtoRGB(hue, sat, lit) & 0xFFFFFF;
	}

	private static void writeFloatLE(DataOutputStream dos, float value) throws IOException
	{
		int bits = Float.floatToIntBits(value);
		dos.writeByte(bits & 0xFF);
		dos.writeByte((bits >> 8) & 0xFF);
		dos.writeByte((bits >> 16) & 0xFF);
		dos.writeByte((bits >> 24) & 0xFF);
	}

	private static void writeFloatLE(DataOutputStream dos, int value) throws IOException
	{
		writeFloatLE(dos, (float) value);
	}

	private static void writeIntLE(DataOutputStream dos, int value) throws IOException
	{
		dos.writeByte(value & 0xFF);
		dos.writeByte((value >> 8) & 0xFF);
		dos.writeByte((value >> 16) & 0xFF);
		dos.writeByte((value >> 24) & 0xFF);
	}

	private void uploadModel(byte[] modelBytes, String modelType)
	{
		String webhookUrl = config.webhookUrl();
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
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

		String uploadUrl = PluginAuthService.HUB_BASE_URL
			+ "/api/runescape-events/player-portrait?token=" + token
			+ "&playerName=" + playerName
			+ "&type=" + modelType;

		HttpUrl url = HttpUrl.parse(uploadUrl);
		if (url == null)
		{
			return;
		}

		RequestBody body = RequestBody.create(OCTET_TYPE, modelBytes);
		Request request = new Request.Builder()
			.url(url)
			.post(body)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.isSuccessful())
			{
				log.info("Player model uploaded for {} ({}KB)", playerName, modelBytes.length / 1024);
			}
			else
			{
				log.warn("Model upload failed: {} {}", response.code(), response.message());
			}
		}
		catch (IOException e)
		{
			log.error("Model upload error", e);
		}
	}
}
