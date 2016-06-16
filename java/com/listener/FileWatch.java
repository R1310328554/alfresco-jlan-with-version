package com.listener;

import java.io.File;

import org.apache.log4j.Logger;

import com.dao.BaseDao;
import com.util.SpringUtil;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

/*
 * 
 * 采用jnotify来对文件进行监控，， 可以检测出文件的增删 
 *  
 */
public class FileWatch implements Runnable {
	/*
	 * 路径 和共享名字 当前池路径 当前池共享名字
	 */
	private String path;
	private String sharingname;
	private Logger log4j = Logger.getLogger(this.getClass());
	public FileWatch(String path, String sharingname) {
		this.path = path;
		this.sharingname = sharingname;
	}

	public void sampleTest() throws Exception {
		int mask = JNotify.FILE_CREATED | JNotify.FILE_DELETED
				| JNotify.FILE_MODIFIED | JNotify.FILE_RENAMED;
		boolean watchSubtree = true;

		int watchID = JNotify
				.addWatch(path, mask, watchSubtree, new Listener());

		Thread.sleep(1000000000000000000l); // 多长时间后停止监听

		boolean res = JNotify.removeWatch(watchID);
		if (res) {
		}
	}

	class Listener implements JNotifyListener {
		public void fileRenamed(int wd, String rootPath, String oldName,
				String newName) {
			// print("renamed " + rootPath + " : " + oldName + " -> " +
			// newName);
		}

		public void fileModified(int wd, String rootPath, String name) {
			// print("modified " + rootPath + " : " + name);
		}

		public void fileDeleted(int wd, String rootPath, String name) {
			// print("deleted " + rootPath + " : " + name);
		}

		public synchronized void fileCreated(int wd, String rootPath,
				String name) {
			BaseDao baseDao = (BaseDao) SpringUtil.getBean("baseDao");
			int jie = name.indexOf("/",1);
			String name1 = name.substring(0, jie);
			File file = new File(rootPath + "/" + name);
			long fileSize = file.length();
			if (fileSize <= 0) {
				int k = 0;
				while (k <= 10) {
					k++;
					try {
						Thread.sleep(2000);
						fileSize = file.length();

						if (fileSize > 0) {
							break;
						}
					} catch (InterruptedException e) {
						log4j.error("fileCreated ERROR: rootPath:"+rootPath+",name="+name+"; \n"+e.getMessage());
					}
				}
			}
			if (fileSize > 0) {
				StringBuffer qStr = new StringBuffer();
				qStr.append("update jweb_backupmediaroll r set r.hasSpace=r.hasSpace+"
										+ fileSize + " where r.name=" + "'"
										+ name1 + "'")
						.append("and r.pool_id ="
										+ "("
										+ "select pool_id from jweb_pool_sharing where name="
										+ "'" + sharingname + "'" + ")");
				log4j.debug("****"+qStr.toString()+"****");
				baseDao.update(qStr.toString());
				log4j.debug("file.exists()" + file.exists()+ "*************path:" + rootPath + name + "****fileSize****" + fileSize);
			}
		}

	}

	public void run() {
		try {
			sampleTest();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
