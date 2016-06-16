package com.base.config;

public class ShareConfig {

	private long id;
	private String name;
	private int status;
	private int roll_id;
	private String path;
	private String creatTime;
	private long totalSpace;
	private int pool_id;
	private long freeSpace;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getCreatTime() {
		return creatTime;
	}

	public void setCreatTime(String creatTime) {
		this.creatTime = creatTime;
	}

	public int getRoll_id() {
		return roll_id;
	}

	public void setRoll_id(int rollId) {
		roll_id = rollId;
	}

	public long getTotalSpace() {
		return totalSpace;
	}

	public void setTotalSpace(long totalSpace) {
		this.totalSpace = totalSpace;
	}

	public int getPool_id() {
		return pool_id;
	}

	public void setPool_id(int poolId) {
		pool_id = poolId;
	}

	public long getFreeSpace() {
		return freeSpace;
	}

	public void setFreeSpace(long freeSpace) {
		this.freeSpace = freeSpace;
	}
}
