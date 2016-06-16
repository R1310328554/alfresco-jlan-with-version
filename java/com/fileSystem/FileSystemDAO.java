package com.fileSystem;

import java.sql.SQLException;
import java.util.List;

import com.base.config.ShareConfig;
import com.base.config.UserShareConfig;

public interface FileSystemDAO {

	/**
	 * 根据共享名查询指定共享信息 
	 * 
	 * @return
	 */

	public ShareConfig findFileByName(String shareName) throws SQLException;

	/**
	 * 按共享名，找存储池对应的卷信息
	 * @param shareName
	 * @return
	 * @throws SQLException
	 */
	public String[] findPathListByShareName(String shareName)throws SQLException;
	
	/**
	 * 查询所有共享信息
	 * 
	 * @return
	 */

	public List<ShareConfig> findAllShares() throws SQLException;
	
	/**
	 * 查询所有用户共享盘
	 * 
	 * @return
	 */
	public List<UserShareConfig> findUserShares() throws SQLException;
	
	/**
	 * 查询指定名称的用户共享盘
	 * 
	 * @return
	 */
	public UserShareConfig findUserShareByName(String shareName) throws SQLException;
	
	public List findDiskSpace(int poolId,String path) throws SQLException;

	
}
