/*
 *    Copyright 2018 University of Michigan
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.verdictdb.core.sqlobject;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class BaseColumn implements UnnamedColumn, SelectItem, GroupingAttribute {

  private static final long serialVersionUID = -7763524127341519557L;

  String schemaName = "";

  String tableSourceAlias = "";

  String tableName = "";

  String columnName;

  public BaseColumn(String columnName) {
    this.columnName = columnName;
  }

  public BaseColumn(String tableSourceAlias, String columnName) {
    this.tableSourceAlias = tableSourceAlias;
    this.columnName = columnName;
  }

  public BaseColumn(String schemaName, String tableSourceAlias, String columnName) {
    this.schemaName = schemaName;
    this.tableSourceAlias = tableSourceAlias;
    this.columnName = columnName;
  }

  public BaseColumn(
      String schemaName, String tableName, String tableSourceAlias, String columnName) {
    this.schemaName = schemaName;
    this.tableName = tableName;
    this.tableSourceAlias = tableSourceAlias;
    this.columnName = columnName;
  }

  public String getTableSourceAlias() {
    return tableSourceAlias;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  public void setTableSourceAlias(String tableSourceAlias) {
    this.tableSourceAlias = tableSourceAlias;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public static BaseColumn create(String tableSourceAlias, String columnName) {
    return new BaseColumn(tableSourceAlias, columnName);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return EqualsBuilder.reflectionEquals(this, obj);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  @Override
  public boolean isAggregateColumn() {
    return false;
  }

  @Override
  public BaseColumn deepcopy() {
    return new BaseColumn(schemaName, tableName, tableSourceAlias, columnName);
  }
}
