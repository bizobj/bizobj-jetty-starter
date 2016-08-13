package org.bizobj.jetty;

import java.net.URL;

import org.bizobj.jetty.cfg.backward.EnvConfiger;

public class TestStarter {

	public static void main(String[] args) throws Exception {
		System.setProperty(EnvConfiger.VAR_CTX_PATH, "test");
		
		URL warUrl = TestStarter.class.getResource("/war_test/default.jsp");
		ContextStarter.startServer(warUrl, new EnvConfiger());
	}

}
