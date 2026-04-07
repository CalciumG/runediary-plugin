package com.runediary.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EventPayload
{
	String content;
	String type;
	String playerName;
	String accountType;
	String dinkAccountHash;
	int world;
	int regionId;
	boolean seasonalWorld;
	Map<String, Object> extra;
	List<Embed> embeds;

	@Value
	@Builder
	public static class Embed
	{
		String title;
		String description;
		String timestamp;
	}
}
