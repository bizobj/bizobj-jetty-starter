package org.bizobj.jetty;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.jasper.servlet.JspServlet;
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
 * The jetty server to start development workspace.<br/>
 * Run this application with following environment variables:
 * <pre>
 *  - HTTP_PORT: http port, default is 8080
 *  - CTX_PATH:  context path
 *  - JDBC_URL:  jdbc url
 * </pre>
 * @author root
 */
public class ContextStarter {
	public static final String VAR_JDBC_URL = "JDBC_URL";
	public static final String VAR_CTX_PATH = "CTX_PATH";
	public static final String VAR_HTTP_PORT = "HTTP_PORT";
	
	public static final String DEFAULT_HTTP_PORT = "8080";
    public static final String DEFAULT_CTX_PATH = "bizobj";
    public static final String DEFAULT_JDBC_URL = "sa@jdbc:hsqldb:mem:bizobj";


    public static void startServer(URL warFolder) throws Exception {
        File fWar = new File(warFolder.toURI());        
        startServer(fWar.getCanonicalPath());
    }
    
    public static void startServer(String warFolder) throws Exception {
        File fWar = new File(warFolder);
        if (fWar.isFile()){
        	fWar = fWar.getParentFile();
        }
        
		File webXml = new File(fWar.getCanonicalPath() + "/WEB-INF/web.xml");
        startServer(fWar.getCanonicalPath(), webXml.getCanonicalPath());
    }
    
    public static void startServer(String warFolder, String webXml) throws Exception {
        File fWar = new File(warFolder);
        if (fWar.isFile()){
        	fWar = fWar.getParentFile();
        }

        Resource r = new FileResource((fWar.toURI().toURL()));
        File w = new File(webXml);
        startServer(r, w);
    }

    public static void startServer(Resource warFolder, File webXml) throws Exception {
        EnvSettings es = readEnv();
        
        //System properties for logging
        System.setProperty("org.eclipse.jetty.LEVEL", "ALL");
        System.setProperty("org.eclipse.jetty.util.log.SOURCE", "false");
        //JSP Compiler setting
        System.setProperty("org.apache.jasper.compiler.disablejsr199", "true");
        
        //Try to stop the previous server instance
        URL stop = new URL("http://127.0.0.1:" + es.httpPort + "/STOP");
        try{ stop.openStream(); }catch(Exception ex){ /*Ignore it*/}
        
        final Server server = new Server(es.httpPort);
        prepareDataSource(server, es);

        //The ROOT web app
        Handler root = delpoyRootCtx();

        //The target web app
        Handler oxApp = deployAppCtx(es, warFolder, webXml);

        //Bind contexts to server
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[] { root, oxApp });
        server.setHandler(contexts);

        server.start();
        System.out.println("********************************************************************************");
        System.out.println("Embeded Jetty("+Server.getVersion()+") Server started at port ["+es.httpPort+"].");
        System.out.println("********************************************************************************");
        server.join();
    }

    @SuppressWarnings("serial")
    private static Handler delpoyRootCtx() throws IOException, URISyntaxException {
        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
        root.setContextPath("/");
        root.setBaseResource(buildFolderResource("/war_root/place-holder-war-root"));
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

    private static WebAppContext deployAppCtx(EnvSettings es, Resource res, File descriptor) throws Exception {
        WebAppContext web = new WebAppContext();
        web.setDescriptor(descriptor.getCanonicalPath());
        web.setBaseResource(res);
        web.setContextPath("/" + es.ctxPath);
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
     * See {@link MetaInfConfiguration#preConfigure(WebAppContext)}, {@link WebInfConfiguration#configure(WebAppContext)},
     * {@link MetaData#setDefaults(Resource)}, {@link MetaData#orderFragments()} for detail.
     * @param ctx
     * @throws Exception 
     * @throws IOException 
     * @throws MalformedURLException 
     */
    private static void applyMetaInfResourcesAndFragmentXml(WebAppContext ctx) throws MalformedURLException, IOException, Exception{
    	String[] metaInfResources = Misc.findClasspathResources("META-INF/resources");
    	Resource[] collection=new Resource[]{
        		ctx.getBaseResource(),
        		new ResourceCollection(metaInfResources)
        };
        ctx.setBaseResource(new ResourceCollection(collection));

    	String[] fragmentXmls = Misc.findClasspathResources("META-INF/web-fragment.xml");
    	//FIXME We need sort here, because by default, web-fragment.xml files should be loaded by the order of jar's name
    	Arrays.sort(fragmentXmls);
        ctx.getMetaData().setOrdering(new Ordering.RelativeOrdering(ctx.getMetaData()));
    	//Yes, they're always like "jar:/path/file.jar!/META-INF/web-fragment.xml"
    	//FIXME Can't support web-fragment.xml in class folder
    	for (int i = 0; i < fragmentXmls.length; i++) {
			String xmlRes = fragmentXmls[i];
			String jarRes = xmlRes.substring("jar:".length(), xmlRes.length()-"!/META-INF/web-fragment.xml".length());
			ctx.getMetaData().addWebInfJar(Resource.newResource(jarRes));	//Jetty should auto-search web-fragment in WEB-INF jars
		}
    	ctx.getMetaData().orderFragments();
    }
    
    /**
     * Get the resource of specified web content folder
     * @param resourceFileName
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    private static final Resource buildFolderResource(String resourceFileName) throws IOException, URISyntaxException {
        URL resFileUrl = ContextStarter.class.getResource(resourceFileName);
        File resFile = new File(resFileUrl.toURI());
        File warDir = resFile.getParentFile();
        Resource r = new FileResource(warDir.toURI().toURL());
        return r;
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

        if (null != envContext){
            Properties p = new Properties();
            p.put("driverClassName", es.getJdbcDriver());
            p.put("url", es.jdbcUrl);
            p.put("username", es.dbUser);
            p.put("password", es.dbPass);
            
            //DBCP Properties for testing
            p.put("maxActive", "5");
            p.put("maxWait", "60000");
            p.put("minIdle", "0");
            p.put("maxIdle", "1");
            
            p.put("validationQuery", es.getValidationQuery());
            DataSource ds = BasicDataSourceFactory.createDataSource(p);
            
            NamingUtil.bind(envContext, es.getJndiName(), ds);
        }
        
        System.out.println(">>> DataSource ["+es.getJndiName()+"] created:");
        System.out.println(">>> \t url: " + es.jdbcUrl);
    }
    
    private static EnvSettings readEnv(){
        EnvSettings es = new EnvSettings();
        es.httpPort = Integer.valueOf(_readEnv(VAR_HTTP_PORT, DEFAULT_HTTP_PORT));
		es.ctxPath = _readEnv(VAR_CTX_PATH, DEFAULT_CTX_PATH);
        
        //Analysis JDBC URL String: user/pass@...
        String rawUrl = _readEnv(VAR_JDBC_URL, DEFAULT_JDBC_URL);
        int firstAt = rawUrl.indexOf('@');
        if (firstAt<0){
        	es.jdbcUrl = rawUrl;
        	es.dbPass = "";
        	es.dbUser = "";
        }else{
        	String userpass = rawUrl.substring(0, firstAt);
        	String url = rawUrl.substring(firstAt+1);
        	String[] tmp = userpass.split("\\/");
        	String user = tmp.length>0?tmp[0]:"";
        	String pass = tmp.length>1?tmp[1]:"";
        	es.jdbcUrl = url;
        	es.dbUser = user;
        	es.dbPass = pass;
        }
        
        return es;
    }
    private static String _readEnv(String var, String defVal){
        String v = System.getProperty(var);
        if (null==v){
            v=System.getenv(var);
        }
        if (null==v){
            v=defVal;
        }
        if (null!=v){
            System.setProperty(var, v);        //Remember the real variable value into System Properties
        }
        return v;
    }
    /**
     * Store the settings defined by environment variables
     * @author root
     *
     */
    private static class EnvSettings{
        private int httpPort;
        private String ctxPath;
        private String jdbcUrl;
        private String dbUser;
        private String dbPass;
        
        private String _jdbcUrl(){
            return (null==this.jdbcUrl)?"":this.jdbcUrl;
        }
        /** jdbc:oracle:thin:@localhost:1521:XE */
        private boolean isOracle(){
            return _jdbcUrl().startsWith("jdbc:oracle:thin:");
        }
        /** jdbc:sqlserver://localhost:1433;databaseName=orderMgr */
        private boolean isMSSQL(){
            return _jdbcUrl().startsWith("jdbc:sqlserver://");
        }
        /** jdbc:jtds:sqlserver://localhost:1433/orderMgr */
        private boolean isMSSQL_JTDS(){
            return _jdbcUrl().startsWith("jdbc:jtds:sqlserver://");
        }
        /** jdbc:mysql://localhost:3306/orderMgr?useUnicode=true&amp;characterEncoding=UTF-8 */
        private boolean isMySQL(){
            return _jdbcUrl().startsWith("jdbc:mysql://");
        }
        /** jdbc:hsqldb:hsql://localhost/TestHSQLDB */
        private boolean isHSQL(){
            return _jdbcUrl().startsWith("jdbc:hsqldb:");
        }
        
        private String getJndiName(){
            return "jdbc/"+this.ctxPath+"DS";
        }
        private String getJdbcDriver(){
            if (isOracle()){
                return "oracle.jdbc.driver.OracleDriver";
            }else if (isMSSQL()){
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            }else if (isMSSQL_JTDS()){
                return "net.sourceforge.jtds.jdbc.Driver";
            }else if (isMySQL()){
                return "com.mysql.jdbc.Driver";
            }else if (isHSQL()){
                return "org.hsqldb.jdbcDriver";
            }else{
                throw new RuntimeException("Unknown database type ["+this.jdbcUrl+"]");
            }
        }
        private String getValidationQuery(){
            if (isOracle()){
                return "SELECT 1 From dual";
            }else if (isMSSQL()){
                return "Select 1";
            }else if (isMSSQL_JTDS()){
                return "Select 1";
            }else if (isMySQL()){
                return "Select 1";
            }else if (isHSQL()){
                return "Select COUNT(*) As X From INFORMATION_SCHEMA.SYSTEM_USERS Where 1=0";
            }else{
                throw new RuntimeException("Unknown database type ["+this.jdbcUrl+"]");
            }
        }
    }
}
