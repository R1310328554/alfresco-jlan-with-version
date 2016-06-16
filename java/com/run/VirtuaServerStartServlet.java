package com.run;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

public class VirtuaServerStartServlet extends HttpServlet {

	private static final long serialVersionUID = -5532731354927539614L;
	private static VirtuaServiceRun cifsService = null;
	
	public void init() throws ServletException {
		System.out.println("******** 启动调用init ********");
		if(null == cifsService){
			cifsService = new VirtuaServiceRun();
		}
		cifsService.start();
	}

	public void destroy() {
		System.out.println("******** 停止调用destroy ********");
		cifsService.shutDownServer();
	}


}
