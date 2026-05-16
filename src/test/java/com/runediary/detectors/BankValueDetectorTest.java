package com.runediary.detectors;

import com.runediary.model.PlayerContext;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BankValueDetectorTest
{
	private static final int BANK_GROUP_ID = 12;
	private static final int OTHER_GROUP_ID = 999;

	private ItemManager itemManager;
	private PlayerContext playerContext;
	private BankValueDetector detector;
	private AtomicInteger callbackCount;

	@Before
	public void setUp()
	{
		Client client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		when(itemManager.getItemPrice(anyInt())).thenReturn(100);
		playerContext = new PlayerContext();
		playerContext.setPlayerName("TestPlayer");
		playerContext.setAccountHash("deadbeef");

		detector = new BankValueDetector(client, itemManager, playerContext);

		callbackCount = new AtomicInteger(0);
		detector.setOnBankUpdated(callbackCount::incrementAndGet);
	}

	private ItemContainerChanged bankContainerChange()
	{
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(995, 10)});
		return new ItemContainerChanged(InventoryID.BANK, bank);
	}

	@Test
	public void containerChange_doesNotFireCallback()
	{
		detector.onItemContainerChanged(bankContainerChange());
		detector.onItemContainerChanged(bankContainerChange());
		detector.onItemContainerChanged(bankContainerChange());

		assertEquals("Container changes should not trigger the sync callback", 0, callbackCount.get());
	}

	@Test
	public void containerChange_updatesInMemorySnapshot()
	{
		detector.onItemContainerChanged(bankContainerChange());

		assertEquals(1000L, detector.getLastBankValue()); // 10 * 100
		assertEquals(1, detector.getLastBankItemCount());
	}

	@Test
	public void widgetClosed_firesCallback_whenBankClosesAndDataExists()
	{
		detector.onItemContainerChanged(bankContainerChange());

		detector.onWidgetClosed(new WidgetClosed(BANK_GROUP_ID, 0, false));

		assertEquals(1, callbackCount.get());
	}

	@Test
	public void widgetClosed_ignored_forNonBankWidgets()
	{
		detector.onItemContainerChanged(bankContainerChange());

		detector.onWidgetClosed(new WidgetClosed(OTHER_GROUP_ID, 0, false));

		assertEquals(0, callbackCount.get());
	}

	@Test
	public void widgetClosed_ignored_whenNoBankDataYet()
	{
		detector.onWidgetClosed(new WidgetClosed(BANK_GROUP_ID, 0, false));

		assertEquals(0, callbackCount.get());
	}
}
