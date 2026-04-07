package com.runediary.dispatch;

import com.runediary.model.EventPayload;
import com.runediary.model.EventType;
import com.runediary.model.PlayerContext;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public class PayloadBuilder
{
	public EventPayload build(PlayerContext ctx, EventType type, String content, Map<String, Object> extra)
	{
		String timestamp = Instant.now().toString();

		EventPayload.Embed embed = EventPayload.Embed.builder()
			.title(content)
			.timestamp(timestamp)
			.build();

		return EventPayload.builder()
			.content(content)
			.type(type.name())
			.playerName(ctx.getPlayerName())
			.accountType(ctx.getAccountType())
			.dinkAccountHash(ctx.getAccountHash())
			.world(ctx.getWorld())
			.regionId(ctx.getRegionId())
			.seasonalWorld(ctx.isSeasonalWorld())
			.extra(extra)
			.embeds(Collections.singletonList(embed))
			.build();
	}
}
