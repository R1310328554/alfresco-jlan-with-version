/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.jlan.server.filesys.db.derby;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.alfresco.jlan.debug.Debug;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.db.DBSearchContext;
import org.alfresco.jlan.util.WildCard;

/**
 * Derby Database Search Context Class
 *
 * @author gkspencer
 */
public class DerbySearchContext extends DBSearchContext {

  /**
	 * Class constructor
	 * 
	 * @param rs ResultSet
	 * @param filter WildCard
   */
  protected DerbySearchContext(ResultSet rs, WildCard filter) {
    super(rs, filter);
  }
  
  /**
   * Return the next file from the search, or return false if there are no more files
   * 
   * @param info FileInfo
   * @return boolean
   */
  public boolean nextFileInfo(FileInfo info) {
    
    //	Get the next file from the search
    
    try {
    	
    	//	Return the next file details or loop until a match is found if a complex wildcard filter
    	//	has been specified
    	
      while ( m_rs != null && m_rs.next()) {
        
        //	Get the file name for the next file
        
        info.setFileId(m_rs.getInt("FileId"));
        info.setFileName(m_rs.getString("FileName"));
        info.setSize(m_rs.getLong("FileSize"));

        Timestamp createDate = m_rs.getTimestamp("CreateDate");
        if ( createDate != null)
        	info.setCreationDateTime(createDate.getTime());
        else
        	info.setCreationDateTime(System.currentTimeMillis());
        	
        Timestamp modifyDate = m_rs.getTimestamp("ModifyDate");
        if ( modifyDate != null)
        	info.setModifyDateTime(modifyDate.getTime());
        else
        	info.setModifyDateTime(System.currentTimeMillis());
        	
				Timestamp accessDate = m_rs.getTimestamp("AccessDate");
				if ( accessDate != null)
					info.setAccessDateTime(accessDate.getTime());

				Timestamp changeDate = m_rs.getTimestamp("ChangeDate");
				if ( changeDate != null)
				  info.setChangeDateTime( changeDate.getTime());
				
        //	Build the file attributes flags
        
        int attr = 0;
        
        if ( m_rs.getBoolean("ReadOnlyFile") == true)
        	attr += FileAttribute.ReadOnly;
        	
        if ( m_rs.getBoolean("SystemFile") == true)
        	attr += FileAttribute.System;
        	
        if ( m_rs.getBoolean("HiddenFile") == true)
        	attr += FileAttribute.Hidden;
        	
        if ( m_rs.getBoolean("DirectoryFile") == true)
        	attr += FileAttribute.Directory;

				if ( m_rs.getBoolean("ArchivedFile") == true)
					attr += FileAttribute.Archive;
        	
        info.setFileAttributes(attr);
        
				//	Get the group/owner id
	    
				info.setGid(m_rs.getInt("OwnerGid"));
				info.setUid(m_rs.getInt("OwnerUid"));
	    
				info.setMode(m_rs.getInt("FileMode"));

        //	Check if there is a complex wildcard filter
        
        if ( m_filter == null || m_filter.matchesPattern(info.getFileName()) == true)
        	return true;
      }
    }
    catch (SQLException ex) {
      Debug.println(ex);
    }
    
		//	No more files, clear the resultset
		
    m_rs = null;
    return false;
  }

  /**
   * Return the file name of the next file in the active search. Returns
   * null if the search is complete.
   *
   * @return String
   */
  public String nextFileName() {

    //	Get the next file from the search
    
    try {

			//	Return the next file details or loop until a match is found if a complex wildcard filter
			//	has been specified

			String fileName = null;
			    	
      while ( m_rs != null && m_rs.next()) {
        
        //	Get the file name for the next file
        
        fileName = m_rs.getString("FileName");
        
				//	Check if there is a complex wildcard filter
		        
				if ( m_filter == null || m_filter.matchesPattern(fileName) == true)
					return fileName;
      }
    }
    catch (SQLException ex) {
    }
    
    //	No more files, clear the resultset

    m_rs = null;
    return null;
  }
}
