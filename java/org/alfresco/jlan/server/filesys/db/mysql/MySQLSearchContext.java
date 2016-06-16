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
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.util.DBUtil;

/**
 * MySQL Database Search Context Class
 * 
 */
public class MySQLSearchContext extends DBSearchContext {

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
	private String readonlyIds;//只读文件id
	private String noRightIds;//无权文件id
	private int userId;//当前用户id

	protected MySQLSearchContext(ResultSet rs, WildCard filter, String shareName,int userId) {
		super(rs, filter);
		this.userId = userId;
		this.shareName = shareName;
	}
	
	protected MySQLSearchContext(ResultSet rs, WildCard filter, String shareName,int userId,String readonlyIds,String noRightIds) {
		super(rs, filter);
		this.userId = userId;
		this.shareName = shareName;
		this.readonlyIds = readonlyIds;
		this.noRightIds = noRightIds;
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

				// Get the file name for the next file

				info.setFileId(m_rs.getInt("id"));
				info.setFileName(m_rs.getString("name"));
				info.setSize(m_rs.getLong("size"));
				long modifyDate = m_rs.getLong("lastModified");
				if (modifyDate != 0L)
					info.setModifyDateTime(modifyDate);
				else
					info.setModifyDateTime(System.currentTimeMillis());
				
				Timestamp ts = m_rs.getTimestamp("add_time");
				if (ts !=null && ts.getTime()>0)
				{
					info.setCreationDateTime(ts.getTime());
//					info.setModifyDateTime(ts.getTime());
//					info.setChangeDateTime(ts.getTime());
					info.setChangeDateTime(info.getModifyDateTime());
				}
				else
				{
					info.setCreationDateTime(info.getModifyDateTime());
					info.setChangeDateTime(info.getModifyDateTime());
				}				
				int attr = 0;
				if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_RECIVEFILE)) 
				{
					// 共享文件权限
					String permissions = m_rs.getString("permissions");
					if (StringUtils.isNotEmpty(permissions) && permissions.indexOf("w") > -1) 
					{
						// 可读写
					} else {
						// 只读权限
						attr += FileAttribute.ReadOnly;
					}
				} else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_COMMFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_COMMFILE_ALIAS)) 
				{
					// 公共资料库权限设值
					if(null != noRightIds && noRightIds.length()>-1)
					{
						if(noRightIds.contains(","+info.getFileId()+","))
						{
							log4j.debug("无权限：fileId:"+info.getFileId()+" , fname:"+info.getFileName());
							// 无权限
							attr += FileAttribute.Hidden;//隐藏
							attr += FileAttribute.Archive;//归档
						}
					}
					else if(null !=readonlyIds && readonlyIds.length()>1)
					{
						if(readonlyIds.contains(","+info.getFileId()+","))
						{
							log4j.debug("只读权限：fileId:"+info.getFileId()+" , fname:"+info.getFileName());
							// 只读权限
							attr += FileAttribute.ReadOnly;
						}
					}
					boolean islock = m_rs.getBoolean("islock");
					int lockedByUser = m_rs.getInt("lockedByUser");
					if(islock && lockedByUser!=userId)
					{
						//被锁定（且非自己锁定）的文件只读
						attr += FileAttribute.ReadOnly;
					}
				}

				if (m_rs.getBoolean("isFile") == true) {
					info.setFileType(FileType.RegularFile);
				} else
					attr += FileAttribute.Directory;

				info.setFileType(FileType.Directory);

				if (hasMarkAsOffline()) {
					if (getOfflineFileSize() == 0 || info.getSize() >= getOfflineFileSize())
						attr += FileAttribute.NTOffline;
				}
				info.setFileAttributes(attr);
				info.setUid(m_rs.getInt("userId"));

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

	public String getReadonlyIds() {
		return readonlyIds;
	}

	public void setReadonlyIds(String readonlyIds) {
		this.readonlyIds = readonlyIds;
	}

	public String getNoRightIds() {
		return noRightIds;
	}

	public void setNoRightIds(String noRightIds) {
		this.noRightIds = noRightIds;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}
	
}
