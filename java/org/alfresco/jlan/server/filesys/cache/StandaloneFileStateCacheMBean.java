package org.alfresco.jlan.server.filesys.cache;

import java.util.Map;

public interface StandaloneFileStateCacheMBean {


	public Map<String, FileState> getM_stateCache();

	public void setM_stateCache(Map<String, FileState> m_stateCache);
	
}
