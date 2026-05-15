package org.sagacity.sqltoy.config.model;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * 资源描述对象，封装资源的路径、URL、类型等信息。
 * <p>
 * 对于本地文件系统资源，还提供文件路径和最后修改时间， 便于实现文件变更监听功能。
 */
public class Resource {
	/** 资源的相对路径（相对于classpath根目录） */
	private final String path;
	/** 资源的URL */
	private final URL url;
	/** 资源来源类型 */
	private final ResourceType type;
	/** 本地文件路径（仅FILE类型有效） */
	private final Path filePath;

	/**
	 * 构造资源对象，自动解析本地文件路径。
	 * 
	 * @param path 资源的相对路径
	 * @param url  资源的URL
	 * @param type 资源来源类型
	 */
	public Resource(String path, URL url, ResourceType type) {
		this.path = path;
		this.url = url;
		this.type = type;
		Path tempPath = null;
		if (type == ResourceType.FILE) {
			try {
				tempPath = Paths.get(url.toURI().normalize());
			} catch (Exception e) {
				// ignore
			}
		}
		this.filePath = tempPath;
	}

	/**
	 * 构造资源对象，显式指定本地文件路径。
	 * 
	 * @param path     资源的相对路径
	 * @param url      资源的URL
	 * @param type     资源来源类型
	 * @param filePath 本地文件路径
	 */
	public Resource(String path, URL url, ResourceType type, Path filePath) {
		this.path = path;
		this.url = url;
		this.type = type;
		this.filePath = filePath;
	}

	/**
	 * 获取资源的相对路径。
	 * 
	 * @return 相对于classpath根目录的路径
	 */
	public String getPath() {
		return path;
	}

	/**
	 * 获取资源的URL。
	 * 
	 * @return 资源URL
	 */
	public URL getUrl() {
		return url;
	}

	/**
	 * 获取资源来源类型。
	 * 
	 * @return 资源类型（FILE或JAR）
	 */
	public ResourceType getType() {
		return type;
	}

	/**
	 * 获取本地文件路径。
	 * 
	 * @return 本地文件Path对象，仅FILE类型有效；JAR类型返回null
	 */
	public Path getFilePath() {
		return filePath;
	}

	/**
	 * 判断是否为本地文件。
	 * 
	 * @return 是本地文件返回true，可监听文件变更
	 */
	public boolean isLocalFile() {
		return type == ResourceType.FILE;
	}

	/**
	 * 读取资源内容。
	 * 
	 * @return 资源文件内容字符串
	 * @throws IOException 读取失败时抛出
	 */
	public String readContent() throws IOException {
		try (InputStream is = url.openStream(); Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
			scanner.useDelimiter("\\A");
			String content = scanner.hasNext() ? scanner.next() : "";
			return content.isBlank() ? "" : content;
		}
	}

	/**
	 * 获取最后修改时间。
	 * 
	 * @return 最后修改时间戳（毫秒），仅FILE类型有效；JAR类型返回0
	 */
	public long getLastModified() {
		if (type == ResourceType.FILE && filePath != null) {
			try {
				return Files.getLastModifiedTime(filePath).toMillis();
			} catch (IOException e) {
				return 0;
			}
		}
		return 0;
	}
}
