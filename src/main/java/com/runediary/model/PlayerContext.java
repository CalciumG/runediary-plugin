package com.runediary.model;

/**
 * Thread-safe holder for current player state.
 * Written on the game thread, read from executor threads.
 * Uses volatile fields for safe cross-thread visibility.
 */
public class PlayerContext
{
	private volatile String playerName;
	private volatile String accountType = "NORMAL";
	private volatile String accountHash;
	private volatile int world;
	private volatile int regionId;
	private volatile boolean seasonalWorld;

	public String getPlayerName()
	{
		return playerName;
	}
	public void setPlayerName(String playerName)
	{
		this.playerName = playerName;
	}

	public String getAccountType()
	{
		return accountType;
	}
	public void setAccountType(String accountType)
	{
		this.accountType = accountType;
	}

	public String getAccountHash()
	{
		return accountHash;
	}
	public void setAccountHash(String accountHash)
	{
		this.accountHash = accountHash;
	}

	public int getWorld()
	{
		return world;
	}
	public void setWorld(int world)
	{
		this.world = world;
	}

	public int getRegionId()
	{
		return regionId;
	}
	public void setRegionId(int regionId)
	{
		this.regionId = regionId;
	}

	public boolean isSeasonalWorld()
	{
		return seasonalWorld;
	}
	public void setSeasonalWorld(boolean seasonalWorld)
	{
		this.seasonalWorld = seasonalWorld;
	}

	public boolean isValid()
	{
		return playerName != null && !playerName.isEmpty() && accountHash != null;
	}
}
