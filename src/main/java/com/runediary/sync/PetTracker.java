package com.runediary.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class PetTracker
{
	private static final String PET_MESSAGE = "You have a funny feeling like you're being followed";
	private static final String PET_INVENTORY = "You feel something weird sneaking into your backpack";
	private static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

	// Known pet item IDs from the collection log
	private static final Set<Integer> PET_ITEM_IDS = new HashSet<>();
	static
	{
		// Skilling pets
		PET_ITEM_IDS.add(13320); // Heron
		PET_ITEM_IDS.add(13321); // Rock golem
		PET_ITEM_IDS.add(13322); // Beaver
		PET_ITEM_IDS.add(13324); // Baby chinchompa
		PET_ITEM_IDS.add(20659); // Giant squirrel
		PET_ITEM_IDS.add(20661); // Tangleroot
		PET_ITEM_IDS.add(20663); // Rocky
		PET_ITEM_IDS.add(20665); // Rift guardian
		PET_ITEM_IDS.add(20693); // Phoenix
		PET_ITEM_IDS.add(21509); // Herbi

		// Boss pets
		PET_ITEM_IDS.add(11995); // Pet chaos elemental
		PET_ITEM_IDS.add(12643); // Pet dagannoth supreme
		PET_ITEM_IDS.add(12644); // Pet dagannoth prime
		PET_ITEM_IDS.add(12645); // Pet dagannoth rex
		PET_ITEM_IDS.add(12646); // Baby mole
		PET_ITEM_IDS.add(12647); // Kalphite princess
		PET_ITEM_IDS.add(12648); // Pet smoke devil
		PET_ITEM_IDS.add(12649); // Pet kree'arra
		PET_ITEM_IDS.add(12650); // Pet general graardor
		PET_ITEM_IDS.add(12651); // Pet zilyana
		PET_ITEM_IDS.add(12652); // Pet k'ril tsutsaroth
		PET_ITEM_IDS.add(12653); // Prince black dragon
		PET_ITEM_IDS.add(12655); // Pet kraken
		PET_ITEM_IDS.add(12816); // Pet dark core
		PET_ITEM_IDS.add(12921); // Pet snakeling (Zulrah)
		PET_ITEM_IDS.add(13071); // Chompy chick
		PET_ITEM_IDS.add(13177); // Venenatis spiderling
		PET_ITEM_IDS.add(13178); // Callisto cub
		PET_ITEM_IDS.add(13179); // Vet'ion jr.
		PET_ITEM_IDS.add(13181); // Scorpia's offspring
		PET_ITEM_IDS.add(13225); // Tzrek-jad
		PET_ITEM_IDS.add(13247); // Hellpuppy
		PET_ITEM_IDS.add(13262); // Abyssal orphan
		PET_ITEM_IDS.add(20851); // Olmlet
		PET_ITEM_IDS.add(21273); // Skotos
		PET_ITEM_IDS.add(21291); // Jal-nib-rek
		PET_ITEM_IDS.add(21748); // Noon (Grotesque Guardians)
		PET_ITEM_IDS.add(21992); // Vorki
		PET_ITEM_IDS.add(22473); // Lil' zik
		PET_ITEM_IDS.add(22746); // Ikkle hydra
		PET_ITEM_IDS.add(23495); // Sraracha
		PET_ITEM_IDS.add(23757); // Youngllef
		PET_ITEM_IDS.add(23760); // Smolcano
		PET_ITEM_IDS.add(24491); // Little nightmare
		PET_ITEM_IDS.add(25348); // Lil' creator
		PET_ITEM_IDS.add(25602); // Tiny tempor
		PET_ITEM_IDS.add(26348); // Nexling
		PET_ITEM_IDS.add(26901); // Abyssal protector
		PET_ITEM_IDS.add(27352); // Tumeken's guardian
		PET_ITEM_IDS.add(27590); // Muphin
		PET_ITEM_IDS.add(28246); // Wisp
		PET_ITEM_IDS.add(28248); // Butch
		PET_ITEM_IDS.add(28250); // Baron
		PET_ITEM_IDS.add(28252); // Lil'viathan
		PET_ITEM_IDS.add(28801); // Scurry
		PET_ITEM_IDS.add(28960); // Smol heredit
		PET_ITEM_IDS.add(28962); // Quetzin
		PET_ITEM_IDS.add(29836); // Nid
		PET_ITEM_IDS.add(30152); // Huberte
		PET_ITEM_IDS.add(30154); // Moxi
		PET_ITEM_IDS.add(30622); // Bran
		PET_ITEM_IDS.add(30888); // Yami
		PET_ITEM_IDS.add(31130); // Dom
		PET_ITEM_IDS.add(31283); // Soup
		PET_ITEM_IDS.add(31285); // Gull
		PET_ITEM_IDS.add(33101); // Mooleta
		PET_ITEM_IDS.add(33124); // Beef
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]+>", "");

		if (message.contains(PET_MESSAGE) || message.contains(PET_INVENTORY))
		{
			// Chat detection only — actual pet identity captured via collection log sync
			log.debug("Pet drop detected from chat");
		}
	}

	/**
	 * Check collection log items for owned pets.
	 * Called with the full collection log item map from ProfileSyncService.
	 */
	public Map<String, Object> getPetData(Map<Integer, Integer> collectionLogItems,
										  Map<Integer, String> collectionLogNames)
	{
		Map<String, Object> pets = new HashMap<>();
		int owned = 0;
		int total = PET_ITEM_IDS.size();

		for (int petId : PET_ITEM_IDS)
		{
			Integer quantity = collectionLogItems.get(petId);
			String name = collectionLogNames.get(petId);

			if (quantity != null && quantity > 0)
			{
				Map<String, Object> petInfo = new HashMap<>();
				petInfo.put("name", name != null ? name : "Unknown");
				petInfo.put("obtained", true);
				pets.put(String.valueOf(petId), petInfo);
				owned++;
			}
		}

		Map<String, Object> result = new HashMap<>();
		result.put("owned", owned);
		result.put("total", total);
		result.put("pets", pets);
		return result;
	}

	public static boolean isPetItem(int itemId)
	{
		return PET_ITEM_IDS.contains(itemId);
	}
}
