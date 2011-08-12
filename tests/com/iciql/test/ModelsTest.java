/*
 * Copyright 2004-2011 H2 Group.
 * Copyright 2011 James Moger.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iciql.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import com.iciql.Db;
import com.iciql.DbInspector;
import com.iciql.DbUpgrader;
import com.iciql.DbVersion;
import com.iciql.Iciql.IQVersion;
import com.iciql.ValidationRemark;
import com.iciql.test.models.Product;
import com.iciql.test.models.ProductAnnotationOnly;
import com.iciql.test.models.ProductMixedAnnotation;
import com.iciql.test.models.SupportedTypes;
import com.iciql.test.models.SupportedTypes.SupportedTypes2;

/**
 * Test that the mapping between classes and tables is done correctly.
 */
public class ModelsTest {

	/*
	 * The ErrorCollector Rule allows execution of a test to continue after the
	 * first problem is found and report them all at once
	 */
	@Rule
	public ErrorCollector errorCollector = new ErrorCollector();

	private Db db;

	@Before
	public void setUp() {
		db = IciqlSuite.openDb();
		db.insertAll(Product.getList());
		db.insertAll(ProductAnnotationOnly.getList());
		db.insertAll(ProductMixedAnnotation.getList());
	}

	@After
	public void tearDown() {
		db.close();
	}

	@Test
	public void testValidateModels() {
		boolean isH2 = IciqlSuite.getDatabaseName(db).equals("H2");
		DbInspector inspector = new DbInspector(db);
		validateModel(inspector, new Product(), 3);
		validateModel(inspector, new ProductAnnotationOnly(), isH2 ? 2 : 3);
		validateModel(inspector, new ProductMixedAnnotation(), isH2 ? 3 : 4);
	}

	private void validateModel(DbInspector inspector, Object o, int expected) {
		List<ValidationRemark> remarks = inspector.validateModel(o, false);
		assertTrue("validation remarks are null for " + o.getClass().getName(), remarks != null);
		StringBuilder sb = new StringBuilder();
		sb.append("validation remarks for " + o.getClass().getName());
		sb.append('\n');
		for (ValidationRemark remark : remarks) {
			sb.append(remark.toString());
			sb.append('\n');
			if (remark.isError()) {
				errorCollector.addError(new SQLException(remark.toString()));
			}
		}
		assertTrue(remarks.get(0).message.equals("@IQSchema(name=PUBLIC)"));
		assertEquals(sb.toString(), expected, remarks.size());
	}

	@Test
	public void testSupportedTypes() {
		List<SupportedTypes> original = SupportedTypes.createList();
		db.insertAll(original);
		List<SupportedTypes> retrieved = db.from(SupportedTypes.SAMPLE).select();
		assertEquals(original.size(), retrieved.size());
		for (int i = 0; i < original.size(); i++) {
			SupportedTypes o = original.get(i);
			SupportedTypes r = retrieved.get(i);
			assertTrue(o.equivalentTo(r));
		}
	}

	@Test
	public void testModelGeneration() {
		List<SupportedTypes> original = SupportedTypes.createList();
		db.insertAll(original);
		DbInspector inspector = new DbInspector(db);
		List<String> models = inspector.generateModel(null, "SupportedTypes", "com.iciql.test.models", true,
				true);
		assertEquals(1, models.size());
		// a poor test, but a start
		String dbName = IciqlSuite.getDatabaseName(db);
		if (dbName.equals("H2")) {
			assertEquals(1478, models.get(0).length());
		} else if (dbName.startsWith("HSQL")) {
			// HSQL uses Double instead of Float
			assertEquals(1479, models.get(0).length());
		}
	}

	@Test
	public void testDatabaseUpgrade() {
		// insert a database version record
		db.insert(new DbVersion(1));

		TestDbUpgrader dbUpgrader = new TestDbUpgrader();
		db.setDbUpgrader(dbUpgrader);

		List<SupportedTypes> original = SupportedTypes.createList();
		db.insertAll(original);

		assertEquals(1, dbUpgrader.oldVersion.get());
		assertEquals(2, dbUpgrader.newVersion.get());
	}

	@Test
	public void testTableUpgrade() {
		Db db = IciqlSuite.openDb();

		// insert first, this will create version record automatically
		List<SupportedTypes> original = SupportedTypes.createList();
		db.insertAll(original);

		// reset the dbUpgrader (clears the update check cache)
		TestDbUpgrader dbUpgrader = new TestDbUpgrader();
		db.setDbUpgrader(dbUpgrader);

		SupportedTypes2 s2 = new SupportedTypes2();

		List<SupportedTypes2> types = db.from(s2).select();
		assertEquals(10, types.size());
		assertEquals(1, dbUpgrader.oldVersion.get());
		assertEquals(2, dbUpgrader.newVersion.get());
		db.close();
	}

	/**
	 * A sample database upgrader class.
	 */
	@IQVersion(2)
	class TestDbUpgrader implements DbUpgrader {
		final AtomicInteger oldVersion = new AtomicInteger(0);
		final AtomicInteger newVersion = new AtomicInteger(0);

		public boolean upgradeTable(Db db, String schema, String table, int fromVersion, int toVersion) {
			// just claims success on upgrade request
			oldVersion.set(fromVersion);
			newVersion.set(toVersion);
			return true;
		}

		public boolean upgradeDatabase(Db db, int fromVersion, int toVersion) {
			// just claims success on upgrade request
			oldVersion.set(fromVersion);
			newVersion.set(toVersion);
			return true;
		}

	}

}
