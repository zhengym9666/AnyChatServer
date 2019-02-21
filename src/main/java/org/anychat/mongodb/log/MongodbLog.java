package org.anychat.mongodb.log;

import org.grain.log.ILog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongodbLog implements ILog {
	private Logger log;

	public MongodbLog() {
		this.log = LoggerFactory.getLogger("mongodbLog");
	}

	@Override
	public void warn(String warn) {
		this.log.warn(warn);

	}

	@Override
	public void error(String error, Throwable e) {
		this.log.error(error, e);

	}

	@Override
	public void info(String info) {
		this.log.info(info);

	}

}
