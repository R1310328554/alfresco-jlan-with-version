package com.dao;

public interface BaseDao {
	public   void update(String sql);
	
	public boolean query(String sql);
	
	public long querlong(String sql);
}
