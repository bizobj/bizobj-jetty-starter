package org.bizobj.jetty;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.jasper.servlet.JspServlet;
import org.bizobj.jetty.cfg.Configer;
import org.bizobj.jetty.cfg.backward.EnvConfiger;
import org.bizobj.jetty.cfg.model.EnvSettings;
import org.bizobj.jetty.cfg.model.JdbcSettings;
import org.bizobj.jetty.utils.Misc;
import org.eclipse.jetty.jndi.NamingUtil;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.MetaData;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.Ordering;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;

/**
 * The jetty server to start development workspace.
 * @author thinkbase.net
 */
public class ContextStarter {
	public static final String DEFAULT_HTTP_PORT = "8080";
    public static final String DEFAULT_CTX_PATH = "bizobj";
    public static final String DEFAULT_JDBC_URL = "sa@jdbc:hsqldb:mem:bizobj";

    /**
     * Start server with specified war folder and configurations
     * @param warFolder
     * @param config
     * @throws Exception
     */
    public static void startServer(URL warFolder, Configer config) throws Exception {
        File fWar = new File(warFolder.toURI());        
        startServer(fWar.getCanonicalPath(), config);
    }
    
    /**
     * Start server with specified war folder and configurations
     * @param warFolder
     * @param config
     * @throws Exception
     */
    public static void startServer(String warFolder, Configer config) throws Exception {
        File fWar = new File(warFolder);
        if (fWar.isFile()){
        	fWar = fWar.getParentFile();
        }
        
		File webXml = new File(fWar.getCanonicalPath() + "/WEB-INF/web.xml");
        startServer(fWar.getCanonicalPath(), webXml.getCanonicalPath(), config);
    }
    
    /**
     * Start server with specified war folder, web.xml file and configurations
     * @param warFolder
     * @param webXml
     * @param config
     * @throws Exception
     */
    public static void startServer(String warFolder, String webXml, Configer config) throws Exception {
        File fWar = new File(warFolder);
        if (fWar.isFile()){
        	fWar = fWar.getParentFile();
        }

        Resource r = new FileResource((fWar.toURI().toURL()));
        File w = new File(webXml);
        startServer(r, w, config);
    }

    /**
     * Start server with specified war folder, web.xml file and configurations
     * @param warFolder
     * @param webXml
     * @param config
     * @throws Exception
     */
    public static void startServer(Resource warFolder, File webXml, Configer config) throws Exception {
        EnvSettings es = EnvSettings.readCfg(config);
        
        //System properties for logging
        System.setProperty("org.eclipse.jetty.LEVEL", "ALL");
        System.setProperty("org.eclipse.jetty.util.log.SOURCE", "false");
        //JSP Compiler setting
        //System.setProperty("org.apache.jasper.compiler.disablejsr199", "true");
        
        //Try to stop the previous server instance
        URL stop = new URL("http://127.0.0.1:" + es.getHttpPort() + "/STOP");
        try{ stop.openStream(); }catch(Exception ex){ /*Ignore it*/}
        
        final Server server = new Server(es.getHttpPort());
        prepareDataSource(server, es);

        //The ROOT web app
        Handler root = delpoyRootCtx();

        //The target web app
        Handler app = deployAppCtx(es, warFolder, webXml);

        //Bind contexts to server
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[] { root, app });
        server.setHandler(contexts);

        server.start();
        System.out.println("********************************************************************************");
        System.out.println("Embeded Jetty("+Server.getVersion()+") Server started at port ["+es.getHttpPort()+"].");
        System.out.println("********************************************************************************");
        server.join();
    }

    /**
     * Deploy a "root" context, to receive the "STOP" http request
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    @SuppressWarnings("serial")
    private static Handler delpoyRootCtx() throws IOException, URISyntaxException {
        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
        root.setContextPath("/");
        root.setBaseResource(Resource.newClassPathResource("/war_root"));
        root.addServlet(DefaultServlet.class, "/");    //Default servlet
        //FIXME: org.eclipse.jetty.servlet.ServletHolder.initJspServlet() need it - InitParameter "com.sun.appserv.jsp.classpath"
        root.setClassLoader(ContextStarter.class.getClassLoader());
        //JSP Servlet
        root.addServlet(JspServlet.class, "*.jsp");
        //The STOP Servlet
        root.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            public void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
                System.err.println(">>> Stop server request from /STOP ...");
                System.exit(0);
            }
        }), "/STOP");
        
        return root;
    }

    /**
     * Deploy Web app context
     * @param es
     * @param res
     * @param descriptor
     * @return
     * @throws Exception
     */
    private static WebAppContext deployAppCtx(EnvSettings es, Resource res, File descriptor) throws Exception {
        WebAppContext web = new WebAppContext();
        web.setDescriptor(descriptor.getCanonicalPath());
        web.setBaseResource(res);
        web.setContextPath("/" + es.getCtxPath());
        web.setParentLoaderPriority(true);
        //Apply resources in jars' META-INF/resources, and web-fragment.xml in jars
        applyMetaInfResourcesAndFragmentXml(web);
        //Default servlet
        web.addServlet(DefaultServlet.class, "/");
        //JSP servlet
        web.addServlet(JspServlet.class, "*.jsp");
        
        return web;
    }

    /**
     * Apply resources in jars' META-INF/resources, and web-fragment.xml in jars;
     * @see {@link MetaInfConfiguration#preConfigure(WebAppContext)},
     *      {@link WebInfConfiguration#configure(WebAppContext)},
     *      {@link MetaData#setDefaults(Resource)},
     *      {@link MetaData#orderFragments()},
     *      {@link MetaInfConfiguration#scanForFragment(WebAppContext, Resource, ConcurrentHashMap)}
     * @param ctx
     * @throws IOException
     */
    private static void applyMetaInfResourcesAndFragmentXml(WebAppContext ctx) throws IOException{
    	String[] metaInfResources = Misc.findClasspathResources("META-INF/resources");
    	Resource[] collection=new Resource[]{
        		ctx.getBaseResource(),
        		new ResourceCollection(metaInfResources)
        };
        ctx.setBaseResource(new ResourceCollection(collection));

    	String[] fragmentXmls = Misc.findClasspathResources("META-INF/web-fragment.xml");
    	//We need sort here, because by default, web-fragment.xml files should be loaded by the order of jar's name;
    	//  - IF web-fragment.xml in a folder, it should has the path like 'file:/path/classes/META-INF/web-fragment.xml'
    	//  - IF web-fragment.xml in a jar, it shoule has the path like 'jar:file:/path/file.jar!/META-INF/web-fragment.xml'
    	// so sort should make classes before jars.
    	Arrays.sort(fragmentXmls);
        final MetaData metaData = ctx.getMetaData();
        metaData.setOrdering(new Ordering.RelativeOrdering(metaData));
        List<Resource> jarResources = new ArrayList<Resource>();
        List<Resource> dirResources = new ArrayList<Resource>();
    	for (int i = 0; i < fragmentXmls.length; i++) {
			String xmlRes = fragmentXmls[i];
			if (xmlRes.startsWith("jar:")){
				String jarRes = xmlRes.substring("jar:".length(), xmlRes.length()-"!/META-INF/web-fragment.xml".length());
				jarResources.add(Resource.newResource(jarRes));
			}else{
				String dirRes = xmlRes.substring(0, xmlRes.length()-"/META-INF/web-fragment.xml".length());
				dirResources.add(Resource.newResource(dirRes));
			}
		}
    	//FIXME: NOT support auto-search web-fragment in classpath
    	metaData.setWebInfClassesDirs(dirResources);
    	//Auto-search web-fragment in WEB-INF jars
    	for (Resource r: jarResources){
    		metaData.addWebInfJar(r);
    	}
    	//Final ...
    	metaData.orderFragments();
    }
    
    /**
     * ref: http://www.junlu.com/list/96/481920.html - setting up JNDI in embedded Jetty
     * @param server
     * @throws Exception
     */
    private static void prepareDataSource(Server server, EnvSettings es) throws Exception {
        Context envContext = null;
        
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(server.getClass().getClassLoader());
        try {
            Context context = new InitialContext();
            Context compContext = (Context) context.lookup ("java:comp");
            envContext = compContext.createSubcontext("env");
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }

        List<JdbcSettings> jdbcs = es.getJdbcList();
        if (null != envContext){
        	for(JdbcSettings jdbc: jdbcs){
                Properties p = new Properties();
                p.put("driverClassName", jdbc.getJdbcDriver());
                p.put("url", jdbc.getJdbcUrl());
                p.put("username", jdbc.getDbUser());
                p.put("password", jdbc.getDbPass());
                
                //FIXME: DBCP Properties were UNCHANGEABLE
                p.put("maxActive", "5");
                p.put("maxWait", "60000");
                p.put("minIdle", "0");
                p.put("maxIdle", "1");
                
                p.put("validationQuery", jdbc.getValidationQuery());
                DataSource ds = BasicDataSourceFactory.createDataSource(p);
                NamingUtil.bind(envContext, jdbc.getJndiName(), ds);
                
                System.out.println(">>> DataSource ["+jdbc.getJndiName()+"] created:");
                System.out.println(">>> \t url: " + jdbc.getJdbcUrl());
        	}
        }
    }
    
    /**
     * @deprecated {@link #startServer(URL, Configer)}
     * @param warFolder
     * @throws Exception
     */
    public static void startServer(URL warFolder) throws Exception {
    	startServer(warFolder, new EnvConfiger());
    }
    
    /**
     * @deprecated {@link #startServer(String, Configer)}
     * @param warFolder
     * @throws Exception
     */
    public static void startServer(String warFolder) throws Exception {
    	startServer(warFolder, new EnvConfiger());
    }
    
    /**
     * @deprecated {@link #startServer(String, String, Configer)}
     * @param warFolder
     * @param webXml
     * @throws Exception
     */
    public static void startServer(String warFolder, String webXml) throws Exception {
    	startServer(warFolder, webXml, new EnvConfiger());
    }

    /**
     * @deprecated {@link #startServer(Resource, File, Configer)}
     * @param warFolder
     * @param webXml
     * @throws Exception
     */
    public static void startServer(Resource warFolder, File webXml) throws Exception {
    	startServer(warFolder, webXml, new EnvConfiger());
    }

}
