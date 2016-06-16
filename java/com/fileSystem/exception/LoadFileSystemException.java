package com.fileSystem.exception;

public class LoadFileSystemException extends Exception {

	/**
	 *  
	 */
	private static final long serialVersionUID = 1L;

	private String exMsg = "file load Exception !";

	public LoadFileSystemException(String exMsg) {
		super(exMsg);

	}

	@Override
	public String getMessage() {
		// TODO Auto-generated method stub
		return super.getMessage();
	}

	@Override
	public void printStackTrace() {
		// TODO Auto-generated method stub
		System.out.println("error:" + exMsg);
		super.printStackTrace();
	}

}
