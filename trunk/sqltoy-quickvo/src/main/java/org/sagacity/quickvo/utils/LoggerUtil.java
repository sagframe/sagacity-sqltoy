package org.sagacity.quickvo.utils;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.sagacity.quickvo.QuickVOConstants;

public class LoggerUtil {
	private static Logger logger = null;

	public static Logger getLogger() {
		if (logger == null) {
			logger = Logger.getLogger("sagacity.quickvo");
			logger.setLevel(Level.ALL);
			Handler handler;
			try {
				handler = new FileHandler(FileUtil.linkPath(QuickVOConstants.BASE_LOCATE, "quickvo.log"));
				handler.setFormatter(new SimpleFormatter());
				logger.addHandler(handler);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return logger;
	}
}
