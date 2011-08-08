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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.iciql.Iciql.EnumId;
import com.iciql.Iciql.EnumType;
import com.iciql.Iciql.IQColumn;
import com.iciql.Iciql.IQEnum;
import com.iciql.Iciql.IQIndex;
import com.iciql.Iciql.IQIndexes;
import com.iciql.Iciql.IQSchema;
import com.iciql.Iciql.IQTable;
import com.iciql.Iciql.IQVersion;
import com.iciql.Iciql.IndexType;
import com.iciql.util.StatementBuilder;
import com.iciql.util.StatementLogger;
import com.iciql.util.StringUtils;
import com.iciql.util.Utils;

/**
 * A table definition contains the index definitions of a table, the field
 * definitions, the table name, and other meta data.
 * 
 * @param <T>
 *            the table type
 */

class TableDefinition<T> {

	/**
	 * The meta data of an index.
	 */

	static class IndexDefinition {
		IndexType type;
		String indexName;

		List<String> columnNames;
	}

	/**
	 * The meta data of a field.
	 */

	static class FieldDefinition {
		String columnName;
		Field field;
		String dataType;
		int maxLength;
		boolean isPrimaryKey;
		boolean isAutoIncrement;
		boolean trimString;
		boolean allowNull;
		String defaultValue;
		EnumType enumType;

		Object getValue(Object obj) {
			try {
				return field.get(obj);
			} catch (Exception e) {
				throw new IciqlException(e);
			}
		}

		Object initWithNewObject(Object obj) {
			Object o = Utils.newObject(field.getType());
			setValue(obj, o);
			return o;
		}

		void setValue(Object obj, Object o) {
			try {
				if (!field.isAccessible()) {
					field.setAccessible(true);
				}
				Class<?> targetType = field.getType();
				if (targetType.isEnum()) {
					o = Utils.convertEnum(o, targetType, enumType);
				} else {
					o = Utils.convert(o, targetType);
				}
				field.set(obj, o);
			} catch (IciqlException e) {
				throw e;
			} catch (Exception e) {
				throw new IciqlException(e);
			}
		}

		Object read(ResultSet rs, int columnIndex) {
			try {
				return rs.getObject(columnIndex);
			} catch (SQLException e) {
				throw new IciqlException(e);
			}
		}
	}

	String schemaName;
	String tableName;
	int tableVersion;
	private boolean createTableIfRequired = true;
	private Class<T> clazz;
	private ArrayList<FieldDefinition> fields = Utils.newArrayList();
	private IdentityHashMap<Object, FieldDefinition> fieldMap = Utils.newIdentityHashMap();

	private List<String> primaryKeyColumnNames;
	private ArrayList<IndexDefinition> indexes = Utils.newArrayList();
	private boolean memoryTable;

	TableDefinition(Class<T> clazz) {
		this.clazz = clazz;
		schemaName = null;
		tableName = clazz.getSimpleName();
	}

	Class<T> getModelClass() {
		return clazz;
	}

	List<FieldDefinition> getFields() {
		return fields;
	}

	FieldDefinition getField(String name) {
		for (FieldDefinition field : fields) {
			if (field.columnName.equalsIgnoreCase(name)) {
				return field;
			}
		}
		return null;
	}

	void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * Define a primary key by the specified model fields.
	 * 
	 * @param modelFields
	 *            the ordered list of model fields
	 */
	void setPrimaryKey(Object[] modelFields) {
		List<String> columnNames = mapColumnNames(modelFields);
		setPrimaryKey(columnNames);
	}

	/**
	 * Define a primary key by the specified column names.
	 * 
	 * @param columnNames
	 *            the ordered list of column names
	 */
	void setPrimaryKey(List<String> columnNames) {
		primaryKeyColumnNames = Utils.newArrayList(columnNames);
		// set isPrimaryKey flag for all field definitions
		for (FieldDefinition fieldDefinition : fieldMap.values()) {
			fieldDefinition.isPrimaryKey = this.primaryKeyColumnNames.contains(fieldDefinition.columnName);
		}
	}

	<A> String getColumnName(A fieldObject) {
		FieldDefinition def = fieldMap.get(fieldObject);
		return def == null ? null : def.columnName;
	}

	private ArrayList<String> mapColumnNames(Object[] columns) {
		ArrayList<String> columnNames = Utils.newArrayList();
		for (Object column : columns) {
			columnNames.add(getColumnName(column));
		}
		return columnNames;
	}

	/**
	 * Defines an index with the specified model fields.
	 * 
	 * @param type
	 *            the index type (STANDARD, HASH, UNIQUE, UNIQUE_HASH)
	 * @param modelFields
	 *            the ordered list of model fields
	 */
	void addIndex(IndexType type, Object[] modelFields) {
		List<String> columnNames = mapColumnNames(modelFields);
		addIndex(null, type, columnNames);
	}

	/**
	 * Defines an index with the specified column names.
	 * 
	 * @param type
	 *            the index type (STANDARD, HASH, UNIQUE, UNIQUE_HASH)
	 * @param columnNames
	 *            the ordered list of column names
	 */
	void addIndex(String name, IndexType type, List<String> columnNames) {
		IndexDefinition index = new IndexDefinition();
		if (StringUtils.isNullOrEmpty(name)) {
			index.indexName = tableName + "_" + indexes.size();
		} else {
			index.indexName = name;
		}
		index.columnNames = Utils.newArrayList(columnNames);
		index.type = type;
		indexes.add(index);
	}

	public void setColumnName(Object column, String columnName) {
		FieldDefinition def = fieldMap.get(column);
		if (def != null) {
			def.columnName = columnName;
		}
	}

	public void setMaxLength(Object column, int maxLength) {
		FieldDefinition def = fieldMap.get(column);
		if (def != null) {
			def.maxLength = maxLength;
		}
	}

	void mapFields() {
		boolean byAnnotationsOnly = false;
		boolean inheritColumns = false;
		boolean strictTypeMapping = false;
		if (clazz.isAnnotationPresent(IQTable.class)) {
			IQTable tableAnnotation = clazz.getAnnotation(IQTable.class);
			byAnnotationsOnly = tableAnnotation.annotationsOnly();
			inheritColumns = tableAnnotation.inheritColumns();
			strictTypeMapping = tableAnnotation.strictTypeMapping();
		}

		List<Field> classFields = Utils.newArrayList();
		classFields.addAll(Arrays.asList(clazz.getDeclaredFields()));
		if (inheritColumns) {
			Class<?> superClass = clazz.getSuperclass();
			classFields.addAll(Arrays.asList(superClass.getDeclaredFields()));
		}

		for (Field f : classFields) {
			// default to field name
			String columnName = f.getName();
			boolean isAutoIncrement = false;
			boolean isPrimaryKey = false;
			int maxLength = 0;
			boolean trimString = false;
			boolean allowNull = true;
			EnumType enumType = null;
			String defaultValue = "";
			boolean hasAnnotation = f.isAnnotationPresent(IQColumn.class);
			if (hasAnnotation) {
				IQColumn col = f.getAnnotation(IQColumn.class);
				if (!StringUtils.isNullOrEmpty(col.name())) {
					columnName = col.name();
				}
				isAutoIncrement = col.autoIncrement();
				isPrimaryKey = col.primaryKey();
				maxLength = col.length();
				trimString = col.trim();
				allowNull = col.allowNull();
				defaultValue = col.defaultValue();
			}

			// configure Java -> SQL enum mapping
			if (f.getType().isEnum()) {
				enumType = EnumType.DEFAULT_TYPE;
				if (f.getType().isAnnotationPresent(IQEnum.class)) {
					// enum definition is annotated for all instances
					IQEnum iqenum = f.getType().getAnnotation(IQEnum.class);
					enumType = iqenum.value();
				}
				if (f.isAnnotationPresent(IQEnum.class)) {
					// this instance of the enum is annotated
					IQEnum iqenum = f.getAnnotation(IQEnum.class);
					enumType = iqenum.value();
				}
			}

			boolean isPublic = Modifier.isPublic(f.getModifiers());
			boolean reflectiveMatch = isPublic && !byAnnotationsOnly;
			if (reflectiveMatch || hasAnnotation) {
				FieldDefinition fieldDef = new FieldDefinition();
				fieldDef.field = f;
				fieldDef.columnName = columnName;
				fieldDef.isAutoIncrement = isAutoIncrement;
				fieldDef.isPrimaryKey = isPrimaryKey;
				fieldDef.maxLength = maxLength;
				fieldDef.trimString = trimString;
				fieldDef.allowNull = allowNull;
				fieldDef.defaultValue = defaultValue;
				fieldDef.enumType = enumType;
				fieldDef.dataType = ModelUtils.getDataType(fieldDef, strictTypeMapping);
				fields.add(fieldDef);
			}
		}
		List<String> primaryKey = Utils.newArrayList();
		for (FieldDefinition fieldDef : fields) {
			if (fieldDef.isPrimaryKey) {
				primaryKey.add(fieldDef.columnName);
			}
		}
		if (primaryKey.size() > 0) {
			setPrimaryKey(primaryKey);
		}
	}

	/**
	 * Optionally truncates strings to the maximum length and converts
	 * java.lang.Enum types to Strings or Integers.
	 */
	private Object getValue(Object obj, FieldDefinition field) {
		Object value = field.getValue(obj);
		if (field.enumType != null) {
			// convert enumeration to INT or STRING
			Enum<?> iqenum = (Enum<?>) value;
			switch (field.enumType) {
			case NAME:
				if (field.trimString && field.maxLength > 0) {
					if (iqenum.name().length() > field.maxLength) {
						return iqenum.name().substring(0, field.maxLength);
					}
				}
				return iqenum.name();
			case ORDINAL:
				return iqenum.ordinal();			
			case ENUMID:
				if (!EnumId.class.isAssignableFrom(value.getClass())) {
					throw new IciqlException(field.field.getName() + " does not implement EnumId!");
				}
				EnumId enumid = (EnumId) value;
				return enumid.enumId();			
			}
		}
		if (field.trimString && field.maxLength > 0) {
			if (value instanceof String) {
				// clip strings
				String s = (String) value;
				if (s.length() > field.maxLength) {
					return s.substring(0, field.maxLength);
				}
				return s;
			}
			return value;
		}
		// standard behavior
		return value;
	}

	long insert(Db db, Object obj, boolean returnKey) {
		SQLStatement stat = new SQLStatement(db);
		StatementBuilder buff = new StatementBuilder("INSERT INTO ");
		buff.append(db.getDialect().prepareTableName(schemaName, tableName)).append('(');
		for (FieldDefinition field : fields) {
			buff.appendExceptFirst(", ");
			buff.append(db.getDialect().prepareColumnName(field.columnName));
		}
		buff.append(") VALUES(");
		buff.resetCount();
		for (FieldDefinition field : fields) {
			buff.appendExceptFirst(", ");
			buff.append('?');
			Object value = getValue(obj, field);
			stat.addParameter(value);
		}
		buff.append(')');
		stat.setSQL(buff.toString());
		StatementLogger.insert(stat.getSQL());
		if (returnKey) {
			return stat.executeInsert();
		}
		return stat.executeUpdate();
	}

	void merge(Db db, Object obj) {
		if (primaryKeyColumnNames == null || primaryKeyColumnNames.size() == 0) {
			throw new IllegalStateException("No primary key columns defined " + "for table " + obj.getClass()
					+ " - no update possible");
		}
		SQLStatement stat = new SQLStatement(db);
		StatementBuilder buff = new StatementBuilder("MERGE INTO ");
		buff.append(db.getDialect().prepareTableName(schemaName, tableName)).append(" (");
		buff.resetCount();
		for (FieldDefinition field : fields) {
			buff.appendExceptFirst(", ");
			buff.append(db.getDialect().prepareColumnName(field.columnName));
		}
		buff.append(") KEY(");
		buff.resetCount();
		for (FieldDefinition field : fields) {
			if (field.isPrimaryKey) {
				buff.appendExceptFirst(", ");
				buff.append(db.getDialect().prepareColumnName(field.columnName));
			}
		}
		buff.append(") ");
		buff.resetCount();
		buff.append("VALUES (");
		for (FieldDefinition field : fields) {
			buff.appendExceptFirst(", ");
			buff.append('?');
			Object value = getValue(obj, field);
			stat.addParameter(value);
		}
		buff.append(')');
		stat.setSQL(buff.toString());

		StatementLogger.merge(stat.getSQL());
		stat.executeUpdate();
	}

	void update(Db db, Object obj) {
		if (primaryKeyColumnNames == null || primaryKeyColumnNames.size() == 0) {
			throw new IllegalStateException("No primary key columns defined " + "for table " + obj.getClass()
					+ " - no update possible");
		}
		SQLStatement stat = new SQLStatement(db);
		StatementBuilder buff = new StatementBuilder("UPDATE ");
		buff.append(db.getDialect().prepareTableName(schemaName, tableName)).append(" SET ");
		buff.resetCount();

		for (FieldDefinition field : fields) {
			if (!field.isPrimaryKey) {
				buff.appendExceptFirst(", ");
				buff.append(db.getDialect().prepareColumnName(field.columnName));
				buff.append(" = ?");
				Object value = getValue(obj, field);
				stat.addParameter(value);
			}
		}
		Object alias = Utils.newObject(obj.getClass());
		Query<Object> query = Query.from(db, alias);
		boolean firstCondition = true;
		for (FieldDefinition field : fields) {
			if (field.isPrimaryKey) {
				Object aliasValue = field.getValue(alias);
				Object value = field.getValue(obj);
				if (!firstCondition) {
					query.addConditionToken(ConditionAndOr.AND);
				}
				firstCondition = false;
				query.addConditionToken(new Condition<Object>(aliasValue, value, CompareType.EQUAL));
			}
		}
		stat.setSQL(buff.toString());
		query.appendWhere(stat);
		StatementLogger.update(stat.getSQL());
		stat.executeUpdate();
	}

	void delete(Db db, Object obj) {
		if (primaryKeyColumnNames == null || primaryKeyColumnNames.size() == 0) {
			throw new IllegalStateException("No primary key columns defined " + "for table " + obj.getClass()
					+ " - no update possible");
		}
		SQLStatement stat = new SQLStatement(db);
		StatementBuilder buff = new StatementBuilder("DELETE FROM ");
		buff.append(db.getDialect().prepareTableName(schemaName, tableName));
		buff.resetCount();
		Object alias = Utils.newObject(obj.getClass());
		Query<Object> query = Query.from(db, alias);
		boolean firstCondition = true;
		for (FieldDefinition field : fields) {
			if (field.isPrimaryKey) {
				Object aliasValue = field.getValue(alias);
				Object value = field.getValue(obj);
				if (!firstCondition) {
					query.addConditionToken(ConditionAndOr.AND);
				}
				firstCondition = false;
				query.addConditionToken(new Condition<Object>(aliasValue, value, CompareType.EQUAL));
			}
		}
		stat.setSQL(buff.toString());
		query.appendWhere(stat);
		StatementLogger.delete(stat.getSQL());
		stat.executeUpdate();
	}

	TableDefinition<T> createTableIfRequired(Db db) {
		if (!createTableIfRequired) {
			// skip table and index creation
			// but still check for upgrades
			db.upgradeTable(this);
			return this;
		}
		SQLDialect dialect = db.getDialect();
		SQLStatement stat = new SQLStatement(db);
		StatementBuilder buff;
		if (memoryTable && dialect.supportsMemoryTables()) {
			buff = new StatementBuilder("CREATE MEMORY TABLE IF NOT EXISTS ");
		} else {
			buff = new StatementBuilder("CREATE TABLE IF NOT EXISTS ");
		}

		buff.append(dialect.prepareTableName(schemaName, tableName)).append('(');

		for (FieldDefinition field : fields) {
			buff.appendExceptFirst(", ");
			buff.append(dialect.prepareColumnName(field.columnName)).append(' ').append(field.dataType);
			if (field.maxLength > 0) {
				buff.append('(').append(field.maxLength).append(')');
			}

			if (field.isAutoIncrement) {
				buff.append(" AUTO_INCREMENT");
			}

			if (!field.allowNull) {
				buff.append(" NOT NULL");
			}

			// default values
			if (!field.isAutoIncrement && !field.isPrimaryKey) {
				String dv = field.defaultValue;
				if (!StringUtils.isNullOrEmpty(dv)) {
					if (ModelUtils.isProperlyFormattedDefaultValue(dv)
							&& ModelUtils.isValidDefaultValue(field.field.getType(), dv)) {
						buff.append(" DEFAULT " + dv);
					}
				}
			}
		}

		// primary key
		if (primaryKeyColumnNames != null && primaryKeyColumnNames.size() > 0) {
			buff.append(", PRIMARY KEY(");
			buff.resetCount();
			for (String n : primaryKeyColumnNames) {
				buff.appendExceptFirst(", ");
				buff.append(n);
			}
			buff.append(')');
		}
		buff.append(')');
		stat.setSQL(buff.toString());
		StatementLogger.create(stat.getSQL());
		stat.executeUpdate();

		// create indexes
		for (IndexDefinition index : indexes) {
			String sql = db.getDialect().prepareCreateIndex(schemaName, tableName, index);
			stat.setSQL(sql);
			StatementLogger.create(stat.getSQL());
			stat.executeUpdate();
		}

		// tables are created using IF NOT EXISTS
		// but we may still need to upgrade
		db.upgradeTable(this);
		return this;
	}

	/**
	 * Retrieve list of columns from primary key definition.
	 * 
	 * @param index
	 *            the primary key columns, separated by space
	 * @return the column list
	 */
	private List<String> getColumns(String index) {
		List<String> cols = Utils.newArrayList();
		if (index == null || index.length() == 0) {
			return null;
		}
		String[] cs = index.split("(,|\\s)");
		for (String c : cs) {
			if (c != null && c.trim().length() > 0) {
				cols.add(c.trim());
			}
		}
		if (cols.size() == 0) {
			return null;
		}
		return cols;
	}

	void mapObject(Object obj) {
		fieldMap.clear();
		initObject(obj, fieldMap);

		if (clazz.isAnnotationPresent(IQSchema.class)) {
			IQSchema schemaAnnotation = clazz.getAnnotation(IQSchema.class);
			// setup schema name mapping, if properly annotated
			if (!StringUtils.isNullOrEmpty(schemaAnnotation.value())) {
				schemaName = schemaAnnotation.value();
			}
		}

		if (clazz.isAnnotationPresent(IQTable.class)) {
			IQTable tableAnnotation = clazz.getAnnotation(IQTable.class);

			// setup table name mapping, if properly annotated
			if (!StringUtils.isNullOrEmpty(tableAnnotation.name())) {
				tableName = tableAnnotation.name();
			}

			// allow control over createTableIfRequired()
			createTableIfRequired = tableAnnotation.createIfRequired();

			// model version
			if (clazz.isAnnotationPresent(IQVersion.class)) {
				IQVersion versionAnnotation = clazz.getAnnotation(IQVersion.class);
				if (versionAnnotation.value() > 0) {
					tableVersion = versionAnnotation.value();
				}
			}

			// setup the primary index, if properly annotated
			List<String> primaryKey = getColumns(tableAnnotation.primaryKey());
			if (primaryKey != null) {
				setPrimaryKey(primaryKey);
			}
		}

		if (clazz.isAnnotationPresent(IQIndex.class)) {
			// single table index
			IQIndex index = clazz.getAnnotation(IQIndex.class);
			addIndex(index);
		}

		if (clazz.isAnnotationPresent(IQIndexes.class)) {
			// multiple table indexes
			IQIndexes indexes = clazz.getAnnotation(IQIndexes.class);
			for (IQIndex index : indexes.value()) {
				addIndex(index);
			}
		}
	}

	void addIndex(IQIndex index) {
		List<String> columns = Arrays.asList(index.value());
		addIndex(index.name(), index.type(), columns);
	}

	List<IndexDefinition> getIndexes() {
		return indexes;
	}

	void initObject(Object obj, Map<Object, FieldDefinition> map) {
		for (FieldDefinition def : fields) {
			Object newValue = def.initWithNewObject(obj);
			map.put(newValue, def);
		}
	}

	void initSelectObject(SelectTable<T> table, Object obj, Map<Object, SelectColumn<T>> map) {
		for (FieldDefinition def : fields) {
			Object newValue = def.initWithNewObject(obj);
			SelectColumn<T> column = new SelectColumn<T>(table, def);
			map.put(newValue, column);
		}
	}

	void readRow(Object item, ResultSet rs) {
		for (int i = 0; i < fields.size(); i++) {
			FieldDefinition def = fields.get(i);
			Object o = def.read(rs, i + 1);
			def.setValue(item, o);
		}
	}

	void appendSelectList(SQLStatement stat) {
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				stat.appendSQL(", ");
			}
			FieldDefinition def = fields.get(i);
			stat.appendColumn(def.columnName);
		}
	}

	<Y, X> void appendSelectList(SQLStatement stat, Query<Y> query, X x) {
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				stat.appendSQL(", ");
			}
			FieldDefinition def = fields.get(i);
			Object obj = def.getValue(x);
			query.appendSQL(stat, x, obj);
		}
	}

	<Y, X> void copyAttributeValues(Query<Y> query, X to, X map) {
		for (FieldDefinition def : fields) {
			Object obj = def.getValue(map);
			SelectColumn<Y> col = query.getSelectColumn(obj);
			Object value = col.getCurrentValue();
			def.setValue(to, value);
		}
	}

}
