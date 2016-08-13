package org.bizobj.jetty.cfg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bizobj.jetty.cfg.Configer;

/**
 * Store the settings defined by configer
 * @author root
 *
 */
public class EnvSettings{
    private int httpPort;
    private String ctxPath;
    private List<JdbcSettings> jdbcList = new ArrayList<JdbcSettings>();
    
    public static EnvSettings readCfg(Configer config){
        EnvSettings es = new EnvSettings();
        es.httpPort = config.getHttpPort();
		es.ctxPath = config.getContextPath();
        
		Map<String, String> rawUrls = config.getJdbcUrls();
		if (null!=rawUrls){
			for (Map.Entry<String, String> rawUrl: rawUrls.entrySet()) {
				JdbcSettings jdbc = JdbcSettings.build(rawUrl.getKey(), rawUrl.getValue());
				es.jdbcList.add(jdbc);
			}
		}
        return es;
    }

	public int getHttpPort() {
		return httpPort;
	}

	public String getCtxPath() {
		return ctxPath;
	}

	public List<JdbcSettings> getJdbcList() {
		return jdbcList;
	}
    
}