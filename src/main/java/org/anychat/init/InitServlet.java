package org.anychat.init;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.TimeZone;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.anychat.config.CommonConfigChat;
import org.anychat.log.HttpclientLog;
import org.anychat.log.MariadbLog;
import org.anychat.log.ThreadmsgLog;
import org.anychat.log.WebSocketLog;
import org.anychat.mongodb.log.MongodbLog;
import org.anychat.mongodb.service.LoginChatServiceMongodb;
import org.anychat.mongodb.service.MessageServiceMongodb;
import org.anychat.msg.MsgOpCodeChat;
import org.anychat.service.LoginChatService;
import org.anychat.service.MessageService;
import org.anychat.ws.WsOpCodeChat;
import org.grain.httpclient.HttpUtil;
import org.grain.mariadb.MybatisManager;
import org.grain.mongo.MongodbManager;
import org.grain.msg.MsgManager;
import org.grain.thread.AsyncThreadManager;
import org.grain.websokcetlib.WSManager;

public class InitServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void init() throws ServletException {
		// TODO Auto-generated method stub
		super.init();
		WebSocketLog log = null;
		try {

			log = new WebSocketLog();
			WSManager.init(log);
			ThreadmsgLog threadmsgLog = new ThreadmsgLog();
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
			ServletContext servletContext = this.getServletContext();
			String configFileName = servletContext.getInitParameter("configFileName");
			Properties properties = loadConfig(configFileName);
			MybatisManager.init(properties.getProperty("config_dir"), "mybatis-config.xml", new MariadbLog());
			HttpUtil.init("UTF-8", new HttpclientLog());
			// 初始化线程消息
			AsyncThreadManager.init(100, 10, 3, 0, threadmsgLog);
			AsyncThreadManager.start();
			MsgManager.init(true, threadmsgLog);

			WsOpCodeChat.init();
			CommonConfigChat.init(properties);
			MsgOpCodeChat.init();
			if (CommonConfigChat.IS_USE_MONGODB) {
				MongodbManager.init(CommonConfigChat.MONGODB_URL, CommonConfigChat.MONGODB_PORT, CommonConfigChat.MONGODB_USERNAME, CommonConfigChat.MONGODB_PASSWORD, CommonConfigChat.MONGODB_DBNAME, new MongodbLog());
				LoginChatServiceMongodb loginChatServiceMongodb = new LoginChatServiceMongodb();
				MsgManager.addMsgListener(loginChatServiceMongodb);
				WSManager.addWSListener(loginChatServiceMongodb);
				WSManager.addWSListener(new MessageServiceMongodb());
			} else {
				LoginChatService loginChatService = new LoginChatService();
				MsgManager.addMsgListener(loginChatService);
				WSManager.addWSListener(loginChatService);
				WSManager.addWSListener(new MessageService());
			}

		} catch (Exception e) {
			if (log != null) {
				log.error("启动失败", e);
			}
		}

	}

	private Properties loadConfig(String configFileName) throws Exception {
		WSManager.log.info("初始化基础配置文件");
		InputStream inputStream = null;
		URL url = this.getClass().getClassLoader().getResource(configFileName);
		if (url != null) {
			WSManager.log.info("Init.class.getClassLoader().getResource找到配置文件，路径为：" + url.getPath());
			inputStream = this.getClass().getClassLoader().getResourceAsStream(configFileName);
		} else {
			WSManager.log.info("Init.class.getClassLoader().getResource：" + this.getClass().getClassLoader().getResource("").getPath() + "，未找到配置文件：" + configFileName);
		}
		if (inputStream == null) {
			File file = new File(System.getProperty("catalina.base") + "/" + configFileName);
			if (file.exists()) {
				WSManager.log.info("System.getProperty(\"catalina.base\")找到配置文件，路径为" + System.getProperty("catalina.base") + "/" + configFileName);
				inputStream = new FileInputStream(file);
			} else {
				WSManager.log.info("System.getProperty(\"catalina.base\")：" + System.getProperty("catalina.base") + "，未找到配置文件：" + configFileName);
			}
		}
		if (inputStream == null) {
			File file = new File(configFileName);
			if (file.exists()) {
				WSManager.log.info("找到配置文件，路径为" + file.getAbsolutePath());
				inputStream = new FileInputStream(file);
			} else {
				WSManager.log.info("未找到配置文件：" + configFileName);
			}
		}
		if (inputStream != null) {
			Properties properties = new Properties();
			properties.load(inputStream);
			WSManager.log.info("初始化基础配置文件完成");
			inputStream.close();
			return properties;
		} else {
			WSManager.log.warn("未找到配置文件：" + configFileName);
			throw new Exception("未找到配置文件" + configFileName);
		}
	}

    public static void main(String[] args) throws ServletException {
        new InitServlet().init();
    }
}
