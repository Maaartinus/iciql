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

import static com.iciql.test.IciqlSuite.assertStartsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.iciql.Db;
import com.iciql.IciqlException;
import com.iciql.test.models.Product;
import com.iciql.test.models.ProductAnnotationOnly;
import com.iciql.test.models.ProductInheritedAnnotation;
import com.iciql.test.models.ProductMixedAnnotation;
import com.iciql.test.models.ProductNoCreateTable;

/**
 * Test annotation processing.
 */
public class AnnotationsTest {

	/**
	 * This object represents a database (actually a connection to the
	 * database).
	 */

	private Db db;

	@Before
	public void setUp() {
		db = IciqlSuite.openNewDb();
		db.insertAll(Product.getList());
		db.insertAll(ProductAnnotationOnly.getList());
		db.insertAll(ProductMixedAnnotation.getList());
	}

	@After
	public void tearDown() {
		db.close();
	}

	@Test
	public void testIndexCreation() throws SQLException {
		// test indexes are created, and columns are in the right order
		DatabaseMetaData meta = db.getConnection().getMetaData();
		boolean isH2 = meta.getDatabaseProductName().equals("H2");
		String schema = IciqlSuite.getDefaultSchema(db);
		ResultSet rs = meta.getIndexInfo(null, schema, "ANNOTATEDPRODUCT", false, true);

		// first index is primary key index
		// H2 gives this a testable name.
		assertTrue(rs.next());
		if (isH2) {
			assertStartsWith(rs.getString("INDEX_NAME").toUpperCase(), "PRIMARY_KEY");
		}
		assertTrue(rs.next());
		assertStartsWith(rs.getString("INDEX_NAME").toUpperCase(), "ANNOTATEDPRODUCT_0");
		assertStartsWith(rs.getString("COLUMN_NAME").toUpperCase(), "NAME");
		assertTrue(rs.next());
		assertStartsWith(rs.getString("INDEX_NAME").toUpperCase(), "ANNOTATEDPRODUCT_0");
		assertStartsWith(rs.getString("COLUMN_NAME").toUpperCase(), "CAT");
		assertTrue(rs.next());
		assertStartsWith(rs.getString("INDEX_NAME").toUpperCase(), "NAMEIDX");
		assertStartsWith(rs.getString("COLUMN_NAME").toUpperCase(), "NAME");
		assertFalse(rs.next());
	}

	@Test
	public void testProductAnnotationOnly() {
		ProductAnnotationOnly p = new ProductAnnotationOnly();
		assertEquals(10, db.from(p).selectCount());

		// test IQColumn.name="cat"
		assertEquals(2, db.from(p).where(p.category).is("Beverages").selectCount());

		// test IQTable.annotationsOnly=true
		// public String unmappedField is ignored by iciql
		try {
			db.from(p).where(p.unmappedField).is("unmapped").selectCount();
			assertTrue("this should never execute", false);
		} catch (IciqlException e) {
			assertEquals(IciqlException.CODE_UNMAPPED_FIELD, e.getIciqlCode());
		}

		// 10 objects, 10 autoIncremented unique values
		assertEquals(10, db.from(p).selectDistinct(p.productName).size());

		// test IQTable.primaryKey=id
		try {
			db.insertAll(ProductAnnotationOnly.getList());
		} catch (IciqlException e) {
			assertEquals(IciqlException.CODE_DUPLICATE_KEY, e.getIciqlCode());
		}
	}

	@Test
	public void testProductMixedAnnotation() {
		ProductMixedAnnotation p = new ProductMixedAnnotation();

		// test IQColumn.name="cat"
		assertEquals(2, db.from(p).where(p.category).is("Beverages").selectCount());

		// test IQTable.annotationsOnly=false
		// public String mappedField is reflectively mapped by iciql
		assertEquals(10, db.from(p).where(p.mappedField).is("mapped").selectCount());

		// test IQColumn.primaryKey=true
		try {
			db.insertAll(ProductMixedAnnotation.getList());
		} catch (IciqlException e) {
			assertEquals(IciqlException.CODE_DUPLICATE_KEY, e.getIciqlCode());
		}
	}

	@Test
	public void testTrimStringAnnotation() {
		ProductAnnotationOnly p = new ProductAnnotationOnly();
		ProductAnnotationOnly prod = db.from(p).selectFirst();
		String oldValue = prod.category;
		String newValue = "01234567890123456789";
		// 2 chars exceeds field max
		prod.category = newValue;
		db.update(prod);

		ProductAnnotationOnly newProd = db.from(p).where(p.productId).is(prod.productId).selectFirst();
		assertEquals(newValue.substring(0, 15), newProd.category);

		newProd.category = oldValue;
		db.update(newProd);
	}

	@Test
	public void testColumnInheritanceAnnotation() {
		ProductInheritedAnnotation table = new ProductInheritedAnnotation();
		List<ProductInheritedAnnotation> inserted = ProductInheritedAnnotation.getData();
		db.insertAll(inserted);

		List<ProductInheritedAnnotation> retrieved = db.from(table).select();

		for (int j = 0; j < retrieved.size(); j++) {
			ProductInheritedAnnotation i = inserted.get(j);
			ProductInheritedAnnotation r = retrieved.get(j);
			assertEquals(i.category, r.category);
			assertEquals(i.mappedField, r.mappedField);
			assertEquals(i.unitsInStock, r.unitsInStock);
			assertEquals(i.unitPrice, r.unitPrice);
			assertEquals(i.name(), r.name());
			assertEquals(i.id(), r.id());
		}
	}

	@Test
	public void testCreateTableIfRequiredAnnotation() {
		// tests IQTable.createTableIfRequired=false
		try {
			db.insertAll(ProductNoCreateTable.getList());
		} catch (IciqlException e) {
			assertEquals(IciqlException.CODE_TABLE_NOT_FOUND, e.getIciqlCode());
		}
	}

}
