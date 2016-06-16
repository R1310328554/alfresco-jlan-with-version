package com.util;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

public class CacheManagerUtil {
	
	private static CacheManager cacheManager;
	
	/**
	 * 初始化
	 */
	static
	{
		if(null == cacheManager)
		{
			cacheManager = CacheManager.create(FileUtil.getClassesPath()+"/ehcacheutil.xml"); 
		}
	}
	
	public static CacheManager getCacheManager() {
		return cacheManager;
	}

	public static void setCacheManager(CacheManager cacheManager) {
		CacheManagerUtil.cacheManager = cacheManager;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			CacheManagerUtil.ehcache();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void ehcache() throws Exception {  
		CacheManager manager = CacheManager.create(FileUtil.getClassesPath()+"/ehcacheutil.xml");  
		manager.addCache("TEST_ID.TEST");  
		Cache cid = manager.getCache("TEST_ID.TEST");  
//		Class.forName(dbDriver);  
//		Connection conn = DriverManager.getConnection(dbURL, user, pass);  
		try {  
				long begin = System.currentTimeMillis();  
//				Statement s = conn.createStatement();  
//				String sql = "SELECT TEST_ID,TEST_NAME,TEST_TIME,TEST_VALUE FROM TEST";  
//				ResultSet querySet = s.executeQuery(sql);  
				for (int i = 1; i<10; i++) {  
//					TEST curr = new TEST();  
//					curr.TEST_ID = querySet.getLong(1);  
//					curr.TEST_NAME = querySet.getString(2);  
//					curr.TEST_TIME = querySet.getTimestamp(3);  
//					curr.TEST_VALUE = querySet.getBigDecimal(4);  
					cid.put(new Element(i, "val_"+i));  
				}  
				long end = System.currentTimeMillis();  
				System.out.printf("Time:%d\n", (end - begin));  
				
				
				for(int i=1;i<10;i++)
				{
					System.out.println(cid.get(i).getObjectValue());
				}
		} catch (Exception ex) {  
			ex.printStackTrace();  
		} finally {  
//			conn.close();  
		}  
	}  
		 

}
