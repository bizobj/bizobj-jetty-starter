package org.bizobj.jetty;

import java.net.URL;

public class TestStarter {

	public static void main(String[] args) throws Exception {
		System.setProperty(ContextStarter.VAR_CTX_PATH, "test");
		
		URL warUrl = TestStarter.class.getResource("/war_test/default.jsp");
		ContextStarter.startServer(warUrl);
	}

}
