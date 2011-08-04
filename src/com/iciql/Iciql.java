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

package com.iciql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A class that implements this interface can be used as a database table.
 * <p>
 * You may implement the Table interface on your model object and optionally use
 * IQColumn annotations (which imposes a compile-time and runtime-dependency on
 * iciql), or may choose to use the IQTable and IQColumn annotations only (which
 * imposes a compile-time and runtime-dependency on this file only).
 * <p>
 * If a class is annotated with IQTable and at the same time implements Table,
 * the define() method is not called.
 * <p>
 * Supported data types:
 * <table>
 * <tr>
 * <td>java.lang.String</td>
 * <td>VARCHAR (maxLength > 0) or TEXT (maxLength == 0)</td>
 * </tr>
 * <tr>
 * <td>java.lang.Boolean</td>
 * <td>BIT</td>
 * </tr>
 * <tr>
 * <td>java.lang.Byte</td>
 * <td>TINYINT</td>
 * </tr>
 * <tr>
 * <td>java.lang.Short</td>
 * <td>SMALLINT</td>
 * </tr>
 * <tr>
 * <td>java.lang.Integer</td>
 * <td>INT</td>
 * </tr>
 * <tr>
 * <td>java.lang.Long</td>
 * <td>BIGINT</td>
 * </tr>
 * <tr>
 * <td>java.lang.Float</td>
 * <td>REAL</td>
 * </tr>
 * <tr>
 * <td>java.lang.Double</td>
 * <td>DOUBLE</td>
 * </tr>
 * <tr>
 * <td>java.math.BigDecimal</td>
 * <td>DECIMAL</td>
 * </tr>
 * <tr>
 * <td>java.sql.Date</td>
 * <td>DATE</td>
 * </tr>
 * <tr>
 * <td>java.sql.Time</td>
 * <td>TIME</td>
 * </tr>
 * <tr>
 * <td>java.sql.Timestamp</td>
 * <td>TIMESTAMP</td>
 * </tr>
 * <tr>
 * <td>java.util.Date</td>
 * <td>TIMESTAMP</td>
 * </tr>
 * </table>
 * <p>
 * Unsupported data types: binary types (BLOB, etc), and custom types.
 * <p>
 * Table and field mapping: by default, the mapped table name is the class name
 * and the public fields are reflectively mapped, by their name, to columns. As
 * an alternative, you may specify both the table and column definition by
 * annotations.
 * <p>
 * Table Interface: you may set additional parameters such as table name,
 * primary key, and indexes in the define() method.
 * <p>
 * Annotations: you may use the annotations with or without implementing the
 * Table interface. The annotations allow you to decouple your model completely
 * from iciql other than this file.
 * <p>
 * Automatic model generation: you may automatically generate model classes as
 * strings with the Db and DbInspector objects:
 * 
 * <pre>
 * Db db = Db.open(&quot;jdbc:h2:mem:&quot;, &quot;sa&quot;, &quot;sa&quot;);
 * DbInspector inspector = new DbInspector(db);
 * List&lt;String&gt; models =
 *         inspector.generateModel(schema, table, packageName,
 *         annotateSchema, trimStrings)
 * </pre>
 * 
 * Or you may use the GenerateModels tool to generate and save your classes to
 * the file system:
 * 
 * <pre>
 * java -jar iciql.jar
 *      -url &quot;jdbc:h2:mem:&quot;
 *      -user sa -password sa -schema schemaName -table tableName
 *      -package packageName -folder destination
 *      -annotateSchema false -trimStrings true
 * </pre>
 * 
 * Model validation: you may validate your model class with DbInspector object.
 * The DbInspector will report errors, warnings, and suggestions:
 * 
 * <pre>
 * Db db = Db.open(&quot;jdbc:h2:mem:&quot;, &quot;sa&quot;, &quot;sa&quot;);
 * DbInspector inspector = new DbInspector(db);
 * List&lt;Validation&gt; remarks = inspector.validateModel(new MyModel(), throwOnError);
 * for (Validation remark : remarks) {
 * 	System.out.println(remark);
 * }
 * </pre>
 */
public interface Iciql {

	/**
	 * An annotation for an iciql version.
	 * <p>
	 * @IQVersion(1)
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface IQVersion {

		/**
		 * If set to a non-zero value, iciql maintains a "_iq_versions" table
		 * within your database. The version number is used to call to a
		 * registered DbUpgrader implementation to perform relevant ALTER
		 * statements. Default: 0. You must specify a DbUpgrader on your Db
		 * object to use this parameter.
		 */
		int value() default 0;

	}

	/**
	 * An annotation for a schema.
	 * <p>
	 * @IQSchema("PUBLIC")
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface IQSchema {

		/**
		 * The schema may be optionally specified. Default: unspecified.
		 */
		String value() default "";

	}

	/**
	 * Enumeration defining the four index types.
	 */
	public static enum IndexType {
		STANDARD, UNIQUE, HASH, UNIQUE_HASH;
	}

	/**
	 * An index annotation.
	 * <p>
	 * <ul>
	 * <li>@IQIndex("name")
	 * <li>@IQIndex({"street", "city"})
	 * <li>@IQIndex(name="streetidx", value={"street", "city"})
	 * <li>@IQIndex(name="addressidx", type=IndexType.UNIQUE, value={"house_number", "street", "city"})
	 * </ul>
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface IQIndex {

		/**
		 * Index name. If null or empty, iciql will generate one.
		 */
		String name() default "";

		/**
		 * Type of the index.
		 * <ul>
		 * <li>com.iciql.iciql.IndexType.STANDARD
		 * <li>com.iciql.iciql.IndexType.UNIQUE
		 * <li>com.iciql.iciql.IndexType.HASH
		 * <li>com.iciql.iciql.IndexType.UNIQUE_HASH
		 * </ul>
		 * 
		 * HASH indexes may only be valid for single column indexes.
		 * 
		 */
		IndexType type() default IndexType.STANDARD;

		/**
		 * Columns to include in index.
		 * <ul>
		 * <li>single column index: value = "id"
		 * <li>multiple column index: value = { "id", "name", "date" }
		 * </ul>
		 */
		String[] value() default {};
	}

	/**
	 * Annotation to specify multiple indexes.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface IQIndexes {
		IQIndex[] value() default {};
	}

	/**
	 * Annotation to define a table.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface IQTable {

		/**
		 * The table name. If not specified the class name is used as the table
		 * name.
		 * <p>
		 * The table name may still be overridden in the define() method if the
		 * model class is not annotated with IQTable. Default: unspecified.
		 */
		String name() default "";

		/**
		 * The primary key may be optionally specified. If it is not specified,
		 * then no primary key is set by the IQTable annotation. You may specify
		 * a composite primary key.
		 * <ul>
		 * <li>primaryKey = "id, name"
		 * <li>primaryKey = "id name"
		 * </ul>
		 * The primary key may still be overridden in the define() method if the
		 * model class is not annotated with IQTable. Default: unspecified.
		 */
		String primaryKey() default "";

		/**
		 * The inherit columns allows this model class to inherit columns from
		 * its super class. Any IQTable annotation present on the super class is
		 * ignored. Default: false.
		 */
		boolean inheritColumns() default false;

		/**
		 * Whether or not iciql tries to create the table and indexes. Default:
		 * true.
		 */
		boolean createIfRequired() default true;

		/**
		 * Whether only supported types are mapped. If true, unsupported mapped
		 * types will throw an IciqlException. If false, unsupported mapped
		 * types will default to VARCHAR. Default: true.
		 */
		boolean strictTypeMapping() default true;

		/**
		 * If true, only fields that are explicitly annotated as IQColumn are
		 * mapped. Default: true.
		 */
		boolean annotationsOnly() default true;

		/**
		 * If true, this table is created as a memory table where data is
		 * persistent, but index data is kept in main memory. Valid only for H2
		 * databases. Default: false.
		 */
		boolean memoryTable() default false;
	}

	/**
	 * Annotation to define a column. Annotated fields may have any scope
	 * (however, the JVM may raise a SecurityException if the SecurityManager
	 * doesn't allow iciql to access the field.)
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface IQColumn {

		/**
		 * If not specified, the field name is used as the column name. Default:
		 * the field name.
		 */
		String name() default "";

		/**
		 * This column is the primary key. Default: false.
		 */
		boolean primaryKey() default false;

		/**
		 * The column is created with a sequence as the default value. Default:
		 * false.
		 */
		boolean autoIncrement() default false;

		/**
		 * If larger than zero, it is used during the CREATE TABLE phase. It may
		 * also be used to prevent database exceptions on INSERT and UPDATE
		 * statements (see trimString).
		 * <p>
		 * Any maxLength set in define() may override this annotation setting if
		 * the model class is not annotated with IQTable. Default: 0.
		 */
		int maxLength() default 0;

		/**
		 * If true, iciql will automatically trim the string if it exceeds
		 * maxLength (value.substring(0, maxLength)). Default: false.
		 */
		boolean trimString() default false;

		/**
		 * If false, iciql will set the column NOT NULL during the CREATE TABLE
		 * phase. Default: false.
		 */
		boolean allowNull() default false;

		/**
		 * The default value assigned to the column during the CREATE TABLE
		 * phase. This field could contain a literal single-quoted value, or a
		 * function call. Empty strings are considered NULL. Examples:
		 * <ul>
		 * <li>defaultValue="" (null)
		 * <li>defaultValue="CURRENT_TIMESTAMP"
		 * <li>defaultValue="''" (empty string)
		 * <li>defaultValue="'0'"
		 * <li>defaultValue="'1970-01-01 00:00:01'"
		 * </ul>
		 * if the default value is specified, and auto increment is disabled,
		 * and primary key is disabled, then this value is included in the
		 * "DEFAULT ..." phrase of a column during the CREATE TABLE process.
		 * Default: unspecified (null).
		 */
		String defaultValue() default "";

	}

	/**
	 * This method is called to let the table define the primary key, indexes,
	 * and the table name.
	 */
	@Deprecated
	void defineIQ();
}
