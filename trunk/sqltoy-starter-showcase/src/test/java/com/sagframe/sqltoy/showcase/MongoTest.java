package com.sagframe.sqltoy.showcase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sagframe.sqltoy.SqlToyApplication;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class MongoTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	@Test
	public void testSearch() {

	}

	@Test
	public void testFindPage() {

	}
}
