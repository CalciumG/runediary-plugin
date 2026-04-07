package com.runediary.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ItemData
{
	int id;
	String name;
	int quantity;
	int priceEach;

	public Map<String, Object> toMap()
	{
		Map<String, Object> map = new HashMap<>();
		map.put("id", id);
		map.put("name", name);
		map.put("quantity", quantity);
		map.put("priceEach", priceEach);
		return map;
	}

	public static List<Map<String, Object>> toMaps(List<ItemData> items)
	{
		return items.stream().map(ItemData::toMap).collect(Collectors.toList());
	}
}
