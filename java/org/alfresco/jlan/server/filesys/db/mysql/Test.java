package org.alfresco.jlan.server.filesys.db.mysql;

import java.io.File;

import com.util.AESUtil;

public class Test {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
//		File rollFile = new File("/usr/linkapp/data/linkapp/archive/db/2015/01/30/1013260903_3f82ac6c83ad1d489fa48f4e88f9d7fd.LFS");
//		File uFile = new File("/usr/linkapp/data/linkapp/archive/view/LK/");
//		File rollFile = new File("C:\\Users\\lk\\Documents\\temp\\1013260903_3f82ac6c83ad1d489fa48f4e88f9d7fd.LFS");
//		File uFile = new File("C:\\Users\\lk\\Documents\\temp");
		File rollFile = new File("C:\\temp\\1013260903_3f82ac6c83ad1d489fa48f4e88f9d7fd.LFS");
		rollFile = new File("C:\\temp\\0959490475_152f708220ea01ca317eb8b7cd0c964b.LFS");
		File uFile = new File("C:\\temp\\ww224.docx");
		// TODO Auto-generated method stub
		AESUtil.decryptFile(rollFile, uFile);
		
		System.out.println("Test.enclosing_method()");
	}

}
