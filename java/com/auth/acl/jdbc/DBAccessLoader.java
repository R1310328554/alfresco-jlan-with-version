package com.auth.acl.jdbc;

import java.util.List;

import com.auth.acl.jdbc.Exception.LoadAccessException;

/**
 *  
 * @author caosai
 *
 */

public interface DBAccessLoader {
   /**
    *  加载 所有权限规则 
    * @return
    * @throws LoadAccessException
    */
	public List<AccessDTO> loadAllAccessForDevice() throws LoadAccessException;
	
	
 /**
  *   根据共享名查询权限规则 
  * @param shareName
  * @return
  * @throws LoadAccessException
  */

	public  String  LoadAccessByName(String Username,String shareName)
			throws LoadAccessException;
	
	/**
	 *  是否 拥有权限记录
	 * @param username
	 * @return
	 */
	
    public   boolean   hasAccess(String username,String share)  throws LoadAccessException;
    
    
	
	

}
