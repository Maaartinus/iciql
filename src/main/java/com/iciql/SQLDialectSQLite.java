/*
 * Copyright 2014 James Moger.
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

import com.iciql.TableDefinition.FieldDefinition;
import com.iciql.TableDefinition.IndexDefinition;
import com.iciql.util.IciqlLogger;
import com.iciql.util.StatementBuilder;


/**
 * SQLite database dialect.
 */
public class SQLDialectSQLite extends SQLDialectDefault {

	@Override
	public boolean supportsSavePoints() {
		return false;
	}

	@Override
	public void configureDialect(Db db) {
		super.configureDialect(db);
		// enable foreign key constraint enforcement
		db.executeUpdate("PRAGMA foreign_keys = ON;");
	}

	@Override
	protected <T> String prepareCreateTable(TableDefinition<T> def) {
		return "CREATE TABLE IF NOT EXISTS";
	}

	@Override
	protected <T> String prepareCreateView(TableDefinition<T> def) {
		return "CREATE VIEW IF NOT EXISTS";
	}

	@Override
	public <T> void prepareDropView(SQLStatement stat, TableDefinition<T> def) {
		StatementBuilder buff = new StatementBuilder("DROP VIEW IF EXISTS "
				+ prepareTableName(def.schemaName, def.tableName));
		stat.setSQL(buff.toString());
		return;
	}

	@Override
	public void prepareCreateIndex(SQLStatement stat, String schemaName, String tableName,
			IndexDefinition index) {
		StatementBuilder buff = new StatementBuilder();
		buff.append("CREATE ");
		switch (index.type) {
		case UNIQUE:
			buff.append("UNIQUE ");
			break;
		case UNIQUE_HASH:
			buff.append("UNIQUE ");
			break;
		default:
			IciqlLogger.warn("{0} does not support hash indexes", getClass().getSimpleName());
		}
		buff.append("INDEX IF NOT EXISTS ");
		buff.append(index.indexName);
		buff.append(" ON ");
		// FIXME maybe we can use schemaName ?
		// buff.append(prepareTableName(schemaName, tableName));
		buff.append(tableName);
		buff.append("(");
		for (String col : index.columnNames) {
			buff.appendExceptFirst(", ");
			buff.append(prepareColumnName(col));
		}
		buff.append(") ");

		stat.setSQL(buff.toString().trim());
	}

	@Override
	public <T> void prepareMerge(SQLStatement stat, String schemaName, String tableName,
			TableDefinition<T> def, Object obj) {
		StatementBuilder buff = new StatementBuilder("INSERT OR REPLACE INTO ");
		buff.append(prepareTableName(schemaName, tableName)).append(" (");
		buff.resetCount();
		for (FieldDefinition field : def.fields) {
			buff.appendExceptFirst(", ");
			buff.append(field.columnName);
		}
		buff.append(") ");
		buff.resetCount();
		buff.append("VALUES (");
		for (FieldDefinition field : def.fields) {
			buff.appendExceptFirst(", ");
			buff.append('?');
			Object value = def.getValue(obj, field);
			Object parameter = serialize(value, field.typeAdapter);
			stat.addParameter(parameter);
		}
		buff.append(')');
		stat.setSQL(buff.toString());
	}

}