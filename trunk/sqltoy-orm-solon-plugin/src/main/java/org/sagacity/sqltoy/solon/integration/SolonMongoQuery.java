package org.sagacity.sqltoy.solon.integration;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.Document;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.integration.MongoQuery;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

public class SolonMongoQuery implements MongoQuery {
    private MongoDatabase db;

    @Override
    public MongoCollection<Document> getCollection(String collectionName) {
        return db.getCollection(collectionName);
    }

    @Override
    public <T> List<T> find(String query, Class<T> entityClass, String collectionName, Long skip, Integer limit) {
        MongoCollection<Document> collection = getCollection(collectionName);
        FindIterable<T> findIterable = collection.find(BsonDocument.parse(query), entityClass);

        if (skip != null) {
            findIterable.skip(skip.intValue());
        }
        if (limit != null) {
            findIterable.limit(limit);
        }

		List<T> data = new ArrayList<>();

		try (MongoCursor<T> cur = findIterable.iterator()) {
			while (cur.hasNext()) {
				data.add(cur.next());
			}
		}

		return data;
	}

	@Override
	public long count(String query, String collectionName) {
		MongoCollection<Document> collection = getCollection(collectionName);
		return collection.countDocuments(BsonDocument.parse(query));
	}

    @Override
    public void initialize(SqlToyContext sqlToyContext) {
        db = sqlToyContext.getAppContext().getBean(MongoDatabase.class);
    }
}