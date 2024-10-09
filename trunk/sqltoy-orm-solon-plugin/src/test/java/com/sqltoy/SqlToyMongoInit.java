package com.sqltoy;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.noear.solon.core.Props;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * MongoClient 初始化器（用于拆分 XPluginImp 类）
 *
 * @author 夜の孤城
 * @author noear
 * @since 1.8
 */
public class SqlToyMongoInit {
	public static MongoDatabase buildMongoDbClient(Props props) {
		// 封装MongoDB的地址与端口
		ServerAddress address = new ServerAddress(props.get("host", "127.0.0.1"), props.getInt("port", 27017));
		String databaseName = props.get("database", "test");
		MongoClient client = null;
		// 认证
		if (props.contains("username")) {
			MongoCredential credential = MongoCredential.createCredential(props.get("username"), databaseName,
					props.get("password", "").toCharArray());
			MongoClientSettings settings = MongoClientSettings.builder()
					.applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(address))
							.serverSelectionTimeout(props.getInt("connectTimeout", 5000), TimeUnit.MILLISECONDS))
					.credential(credential).build();
			client = MongoClients.create(settings);
		} else {
			MongoClientSettings settings = MongoClientSettings.builder()
					.applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(address))
							.serverSelectionTimeout(props.getInt("connectTimeout", 5000), TimeUnit.MILLISECONDS))
					.build();
			client = MongoClients.create(settings);
		}
		// client
		return client.getDatabase(databaseName);
	}
}
