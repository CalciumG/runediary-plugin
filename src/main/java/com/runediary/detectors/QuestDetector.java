package com.runediary.detectors;

import com.runediary.RuneDiaryConfig;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.PlayerContext;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class QuestDetector
{
	// Widget group for the quest completion scroll
	private static final int QUEST_COMPLETED_GROUP = 153;
	private static final int QUEST_COMPLETED_TITLE_CHILD = 3;

	// Varbits for quest data (from net.runelite.api.gameval.VarbitID)
	private static final int VARBIT_QP_MAX = 1781;
	private static final int VARBIT_QUESTS_COMPLETED = 6338;
	private static final int VARBIT_QUESTS_TOTAL = 11876;
	// VarPlayer for quest points
	private static final int VARPLAYER_QP = 43;

	private final Client client;
	private final RuneDiaryConfig config;
	private final WebhookDispatcher dispatcher;
	private final PayloadBuilder payloadBuilder;
	private final ScreenshotCapture screenshotCapture;
	private final PlayerContext playerContext;

	private boolean questCompleted = false;

	public QuestDetector(Client client, RuneDiaryConfig config, WebhookDispatcher dispatcher,
						 PayloadBuilder payloadBuilder, ScreenshotCapture screenshotCapture,
						 PlayerContext playerContext)
	{
		this.client = client;
		this.config = config;
		this.dispatcher = dispatcher;
		this.payloadBuilder = payloadBuilder;
		this.screenshotCapture = screenshotCapture;
		this.playerContext = playerContext;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == QUEST_COMPLETED_GROUP)
		{
			// Quest scroll widget loaded — wait 1 tick for varbits to update
			questCompleted = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!questCompleted || !config.questEnabled() || !playerContext.isValid())
		{
			return;
		}
		questCompleted = false;

		// Read quest name from widget
		Widget titleWidget = client.getWidget(QUEST_COMPLETED_GROUP, QUEST_COMPLETED_TITLE_CHILD);
		if (titleWidget == null)
		{
			return;
		}

		String questName = titleWidget.getText();
		if (questName == null || questName.isEmpty())
		{
			return;
		}
		// Clean HTML tags
		questName = questName.replaceAll("<[^>]+>", "").trim();

		// Read accurate quest data from varbits
		int questPoints = client.getVarpValue(VARPLAYER_QP);
		int totalQuestPoints;
		int completedQuests;
		int totalQuests;
		try
		{
			totalQuestPoints = client.getVarbitValue(VARBIT_QP_MAX);
			completedQuests = client.getVarbitValue(VARBIT_QUESTS_COMPLETED);
			totalQuests = client.getVarbitValue(VARBIT_QUESTS_TOTAL);
		}
		catch (Exception e)
		{
			totalQuestPoints = 0;
			completedQuests = 0;
			totalQuests = 0;
		}

		Map<String, Object> extra = new HashMap<>();
		extra.put("questName", questName);
		extra.put("completedQuests", completedQuests);
		extra.put("totalQuests", totalQuests);
		extra.put("questPoints", questPoints);
		extra.put("totalQuestPoints", totalQuestPoints);

		String content = playerContext.getPlayerName() + " has completed a quest: " + questName;

		EventPayload payload = payloadBuilder.build(playerContext, EventType.QUEST, content, extra);

		if (config.sendScreenshots() && config.sendQuestScreenshots())
		{
			screenshotCapture.capture().thenAccept(image -> dispatcher.dispatch(payload, image));
		}
		else
		{
			dispatcher.dispatch(payload, null);
		}
	}
}
