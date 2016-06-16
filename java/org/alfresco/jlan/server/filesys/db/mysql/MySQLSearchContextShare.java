/*
 * Copyright (c) LinkApp Team All rights reserved.
 * 版权归LinkApp研发团队所有
 * 任何的侵权、盗版行为均将追究其法律责任
 * 
 * The LinkApp Project
 * http://www.linkapp.cn
 */
package org.alfresco.jlan.server.filesys.db.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileType;
import org.alfresco.jlan.server.filesys.db.DBSearchContext;
import org.alfresco.jlan.util.WildCard;
import org.apache.log4j.Logger;

import com.util.DBUtil;

/**
 * MySQL Database Search Context Class
 * 
 */
public class MySQLSearchContextShare extends DBSearchContext {

	private Logger log4j = Logger.getLogger(this.getClass());
	/**
	 * Class constructor
	 * 
	 * @param rs
	 *            ResultSet
	 * @param filter
	 *            WildCard
	 */
	private String shareName;
	private int userId;//当前用户id
	private String baseUrl;//网址

	protected MySQLSearchContextShare(ResultSet rs, WildCard filter, String shareName,int userId,String baseUrl) {
		super(rs, filter);
		this.userId = userId;
		this.shareName = shareName;
		this.baseUrl = baseUrl;
	}
	

	/**
	 * Return the next file from the search, or return false if there are no
	 * more files
	 * 
	 * @param info
	 *            FileInfo
	 * @return boolean
	 */
	public boolean nextFileInfo(FileInfo info) {

		// Get the next file from the search

		try {

			// Return the next file details or loop until a match is found if a
			// complex wildcard filter
			// has been specified
			while (m_rs.next()) {
				String name = m_rs.getString("name");
				// Get the file name for the next file
				info.setFileId(m_rs.getInt("id"));
				info.setFileName(name);
				info.setSize(m_rs.getLong("size"));
				if(name.equalsIgnoreCase(DBUtil.CLOUDURL))
				{
					try{
					//重设置大小
					info.setFileId(-99);
//					info.setFileId(-userId);
					info.setSize(DBUtil.getURLMYFILE_TXT(this.getBaseUrl(),userId).getBytes().length);
					}catch(Exception e)
					{
					}
				}
				long modifyDate = m_rs.getLong("lastModified");
				if (modifyDate != 0L)
					info.setModifyDateTime(modifyDate);
				else
					info.setModifyDateTime(System.currentTimeMillis());
				
				Timestamp ts = m_rs.getTimestamp("add_time");
				if (ts !=null && ts.getTime()>0)
				{
					info.setCreationDateTime(ts.getTime());
					info.setChangeDateTime(info.getModifyDateTime());
				}
				else
				{
					info.setCreationDateTime(info.getModifyDateTime());
					info.setChangeDateTime(info.getModifyDateTime());
				}				
				int attr = 0;
				if (m_rs.getBoolean("isFile") == true) {
					info.setFileType(FileType.RegularFile);					
					attr += FileAttribute.ReadOnly;// 只读权限
				} else
					attr += FileAttribute.Directory;

				if (m_rs.getBoolean("isHidden") == true) 
				{
					attr += FileAttribute.Hidden;//隐藏
				}
				info.setFileType(FileType.Directory);
								
				if (hasMarkAsOffline()) {
					if (getOfflineFileSize() == 0 || info.getSize() >= getOfflineFileSize())
						attr += FileAttribute.NTOffline;
				}
				info.setFileAttributes(attr);
				info.setUid(userId);

				if (m_filter == null || m_filter.matchesPattern(info.getFileName()) == true)
					return true;
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		// No more files
		closeSearch();
		return false;
	}

	/**
	 * Return the file name of the next file in the active search. Returns null
	 * if the search is complete.
	 * 
	 * @return String
	 */
	public String nextFileName() {

		// Get the next file from the search

		try {
			String fileName = null;
			while (m_rs.next()) {
				fileName = m_rs.getString("name");
				if (m_filter == null || m_filter.matchesPattern(fileName) == true)
					return fileName;
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * Close the search
	 */
	public void closeSearch() {
		// Check if the resultset is valid, if so then close it
		if (m_rs != null) {
			try {
				Statement stmt = m_rs.getStatement();
				if (stmt != null)
					stmt.close();
				m_rs.close();
			} catch (Exception ex) {
			}
			m_rs = null;
		}
		// Call the base class
		super.closeSearch();
	}

	public String getShareName() {
		return shareName;
	}

	public void setShareName(String shareName) {
		this.shareName = shareName;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}


	public String getBaseUrl() {
		return baseUrl;
	}


	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}
	
	
}
