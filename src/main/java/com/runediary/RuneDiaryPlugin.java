package com.runediary;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.runediary.detectors.BankValueDetector;
import com.runediary.detectors.AchievementDiaryDetector;
import com.runediary.detectors.ClueDetector;
import com.runediary.detectors.CollectionLogDetector;
import com.runediary.detectors.CombatAchievementDetector;
import com.runediary.detectors.DeathDetector;
import com.runediary.detectors.KillCountDetector;
import com.runediary.detectors.LevelDetector;
import com.runediary.detectors.LootDetector;
import com.runediary.detectors.PetDetector;
import com.runediary.detectors.QuestDetector;
import com.runediary.detectors.SlayerDetector;
import com.runediary.dispatch.PayloadBuilder;
import com.runediary.dispatch.ScreenshotCapture;
import com.runediary.dispatch.WebhookDispatcher;
import com.runediary.model.PlayerContext;
import com.runediary.sync.BossPBTracker;
import com.runediary.sync.PluginAuthService;
import com.runediary.sync.PortraitCapture;
import com.runediary.sync.ProfileSyncService;
import com.runediary.sync.ServerConfig;
import com.runediary.sync.SessionTracker;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import okhttp3.OkHttpClient;

import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
	name = "RuneDiary",
	description = "Event tracking for RuneScape Events Hub",
	tags = {"runediary", "events", "webhook", "tracking"}
)
public class RuneDiaryPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private RuneDiaryConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	@Getter
	private PlayerContext playerContext;

	@Getter
	private WebhookDispatcher dispatcher;

	@Getter
	private PayloadBuilder payloadBuilder;

	@Getter
	private ScreenshotCapture screenshotCapture;

	private BankValueDetector bankValueDetector;
	private DeathDetector deathDetector;
	private LevelDetector levelDetector;
	private LootDetector lootDetector;
	private KillCountDetector killCountDetector;
	private SlayerDetector slayerDetector;
	private QuestDetector questDetector;
	private PetDetector petDetector;
	private CombatAchievementDetector combatAchievementDetector;
	private AchievementDiaryDetector achievementDiaryDetector;
	private CollectionLogDetector collectionLogDetector;
	private ClueDetector clueDetector;
	private ProfileSyncService profileSyncService;
	private PluginAuthService pluginAuthService;
	private SessionTracker sessionTracker;
	private BossPBTracker bossPBTracker;

	@Getter
	private ServerConfig serverConfig;
	private volatile boolean waitingForLogin = false;

	@Provides
	RuneDiaryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneDiaryConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("RuneDiary plugin started");
		playerContext = new PlayerContext();
		payloadBuilder = new PayloadBuilder();
		screenshotCapture = new ScreenshotCapture(drawManager);
		dispatcher = new WebhookDispatcher(gson, okHttpClient, executor, config);
		serverConfig = new ServerConfig(gson, config, okHttpClient, executor);

		bankValueDetector = new BankValueDetector(client, itemManager, playerContext);
		bankValueDetector.setOnBankUpdated(() -> {
			if (profileSyncService != null)
			{
				profileSyncService.triggerSync();
			}
		});
		deathDetector = new DeathDetector(client, config, dispatcher, payloadBuilder, screenshotCapture, itemManager, playerContext);
		levelDetector = new LevelDetector(client, config, dispatcher, payloadBuilder, screenshotCapture, playerContext);
		lootDetector = new LootDetector(config, dispatcher, payloadBuilder, screenshotCapture, itemManager, playerContext, serverConfig);
		killCountDetector = new KillCountDetector(config, dispatcher, payloadBuilder, screenshotCapture, playerContext, serverConfig);
		slayerDetector = new SlayerDetector(config, dispatcher, payloadBuilder, screenshotCapture, playerContext);
		questDetector = new QuestDetector(client, config, dispatcher, payloadBuilder, screenshotCapture, playerContext);
		petDetector = new PetDetector(config, dispatcher, payloadBuilder, screenshotCapture, playerContext);
		combatAchievementDetector = new CombatAchievementDetector(client, config, dispatcher, payloadBuilder, screenshotCapture, playerContext);
		achievementDiaryDetector = new AchievementDiaryDetector(config, dispatcher, payloadBuilder, screenshotCapture, playerContext);
		collectionLogDetector = new CollectionLogDetector(client, config, dispatcher, payloadBuilder, screenshotCapture, playerContext, itemManager);
		clueDetector = new ClueDetector(client, config, dispatcher, payloadBuilder, screenshotCapture, playerContext, itemManager, serverConfig);
		sessionTracker = new SessionTracker(client, playerContext);
		bossPBTracker = new BossPBTracker();
		profileSyncService = new ProfileSyncService(gson, client, config, okHttpClient, executor, clientThread, playerContext, itemManager);
		profileSyncService.setBankSnapshotSupplier(bankValueDetector::getBankSnapshot);
		profileSyncService.setSessionDataSupplier(sessionTracker::getSessionData);
		profileSyncService.setBossDataSupplier(bossPBTracker::getBossData);
		pluginAuthService = new PluginAuthService(gson, config, okHttpClient, executor, configManager, playerContext);

		eventBus.register(sessionTracker);
		eventBus.register(bossPBTracker);
		eventBus.register(bankValueDetector);
		eventBus.register(deathDetector);
		eventBus.register(levelDetector);
		eventBus.register(lootDetector);
		eventBus.register(killCountDetector);
		eventBus.register(slayerDetector);
		eventBus.register(questDetector);
		eventBus.register(petDetector);
		eventBus.register(combatAchievementDetector);
		eventBus.register(achievementDiaryDetector);
		eventBus.register(collectionLogDetector);
		eventBus.register(clueDetector);
		eventBus.register(profileSyncService);
	}

	@Override
	protected void shutDown()
	{
		log.info("RuneDiary plugin stopped");
		eventBus.unregister(profileSyncService);
		eventBus.unregister(sessionTracker);
		eventBus.unregister(bossPBTracker);
		eventBus.unregister(bankValueDetector);
		eventBus.unregister(deathDetector);
		eventBus.unregister(levelDetector);
		eventBus.unregister(lootDetector);
		eventBus.unregister(killCountDetector);
		eventBus.unregister(slayerDetector);
		eventBus.unregister(questDetector);
		eventBus.unregister(petDetector);
		eventBus.unregister(combatAchievementDetector);
		eventBus.unregister(achievementDiaryDetector);
		eventBus.unregister(collectionLogDetector);
		eventBus.unregister(clueDetector);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			waitingForLogin = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (playerContext.isValid())
			{
				profileSyncService.syncOnLogout();
			}
			waitingForLogin = false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (waitingForLogin && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			waitingForLogin = false;
			updatePlayerContext();
			sessionTracker.startSession();
			pluginAuthService.authenticateIfNeeded();
			serverConfig.fetchFromHub();

			// Capture player 3D model after a delay (model needs to load)
			new PortraitCapture(client, config, okHttpClient, executor, clientThread,
				playerContext.getPlayerName()).captureAfterDelay();
			profileSyncService.loadCollectionLogFromCache();
			profileSyncService.onLoginReady();
			log.info("Player context ready: {}", playerContext.getPlayerName());
		}
	}

	private void updatePlayerContext()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		playerContext.setPlayerName(client.getLocalPlayer().getName());
		playerContext.setWorld(client.getWorld());
		playerContext.setRegionId(client.getLocalPlayer().getWorldLocation().getRegionID());
		playerContext.setAccountHash(Long.toHexString(client.getAccountHash()));
		playerContext.setSeasonalWorld(client.getWorldType().contains(WorldType.SEASONAL));

		playerContext.setAccountType(detectAccountType());

		log.debug("Player context updated: {} (world {}, type {})",
			playerContext.getPlayerName(), playerContext.getWorld(), playerContext.getAccountType());
	}

	private String detectAccountType()
	{
		int accountType = client.getVarbitValue(VarbitID.IRONMAN);
		switch (accountType)
		{
			case 1:
				return "IRONMAN";
			case 2:
				return "ULTIMATE_IRONMAN";
			case 3:
				return "HARDCORE_IRONMAN";
			default:
				return "NORMAL";
		}
		// Note: GIM detection requires additional varbit checks; will be enhanced later
	}
}
