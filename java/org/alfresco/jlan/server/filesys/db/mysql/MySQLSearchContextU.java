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
public class MySQLSearchContextU extends DBSearchContext {
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
	private String userName;

	protected MySQLSearchContextU(ResultSet rs, WildCard filter,
			String shareName, String userName) {
		
		super(rs, filter);
		this.shareName = shareName;
		this.userName = userName;
		log4j.debug("MySQLSearchContextU shareName:"+shareName+" ,userName:"+userName);
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
		log4j.debug("nextFileInfo ,info:"+info);
		try {
			while (m_rs.next()) {
				info.setFileId(0);
//				info.setFileName(m_rs.getString("name")+ DBUtil.SPECIAL_CHAR);
				info.setFileName(m_rs.getString("name"));
				info.setSize(0);
				Timestamp createDate = m_rs.getTimestamp("regdate");
				Timestamp modifyDate = m_rs.getTimestamp("lastvisit");
				if (null != createDate && createDate.getTime()>0)
					info.setCreationDateTime(createDate.getTime());
				else
					info.setCreationDateTime(System.currentTimeMillis());
				if (null !=modifyDate && modifyDate.getTime()>0)
					info.setModifyDateTime(modifyDate.getTime());
				else
					info.setModifyDateTime(System.currentTimeMillis());
				int attr = 0;
				attr += FileAttribute.Directory;
				attr += FileAttribute.ReadOnly;//用户层目录不允许操作
				info.setFileType(FileType.Directory);
				if (hasMarkAsOffline()) {
					if (getOfflineFileSize() == 0 || info.getSize() >= getOfflineFileSize())
						attr += FileAttribute.NTOffline;
				}
				info.setFileAttributes(attr);
				info.setUid(m_rs.getInt("id"));
				if (m_filter == null || m_filter.matchesPattern(info.getFileName()) == true)
					return true;
			}
		} catch (SQLException ex) {
		}
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
				// fileName = m_rs.getString("name");
//				fileName = m_rs.getString("name") + DBUtil.SPECIAL_CHAR;
				fileName = m_rs.getString("name");
				if (m_filter == null || m_filter.matchesPattern(fileName) == true)
					return fileName;
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		// No more files
		return null;
	}

	/**
	 * Close the search
	 */
	public void closeSearch() {
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
		super.closeSearch();
	}

	public String getShareName() {
		return shareName;
	}

	public void setShareName(String shareName) {
		this.shareName = shareName;
	}
}
