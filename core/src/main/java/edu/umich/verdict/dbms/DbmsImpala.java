package edu.umich.verdict.dbms;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import edu.umich.verdict.VerdictContext;
import edu.umich.verdict.datatypes.SampleParam;
import edu.umich.verdict.datatypes.TableUniqueName;
import edu.umich.verdict.datatypes.VerdictResultSet;
import edu.umich.verdict.exceptions.VerdictException;
import edu.umich.verdict.relation.ExactRelation;
import edu.umich.verdict.relation.Relation;
import edu.umich.verdict.relation.SingleRelation;
import edu.umich.verdict.relation.ApproxRelation;
import edu.umich.verdict.relation.ApproxSingleRelation;
import edu.umich.verdict.relation.expr.ColNameExpr;
import edu.umich.verdict.util.VerdictLogger;

public class DbmsImpala extends Dbms {

	public DbmsImpala(VerdictContext vc, String dbName, String host, String port, String schema, String user,
			String password, String jdbcClassName) throws VerdictException {
		super(vc, dbName, host, port, schema, user, password, jdbcClassName);
		// TODO Auto-generated constructor stub
	}

	// @return temp table name
	protected TableUniqueName createTempTableWithRand(TableUniqueName originalTableName) throws VerdictException {
		TableUniqueName tempTableName = generateTempTableName();
		VerdictLogger.debug(this, "Creating a temp table with random numbers: " + tempTableName);
		executeUpdate(String.format("CREATE TABLE %s AS SELECT *, rand(unix_timestamp()) as verdict_rand FROM %s",
				tempTableName, originalTableName));
		return tempTableName;
	}
	
	protected void createUniformSampleTableFromTempTable(TableUniqueName tempTableName, SampleParam param)
			throws VerdictException {
		VerdictLogger.debug(this, "Creating a sample table of " + tempTableName);
		executeUpdate(String.format("CREATE TABLE %s AS SELECT * FROM %s WHERE verdict_rand <= %f",
				param.sampleTableName(), tempTableName, param.samplingRatio));
	}

	@Override
	public Pair<Long, Long> createUniformRandomSampleTableOf(SampleParam param) throws VerdictException {
		dropTable(param.sampleTableName());
		TableUniqueName tempTableName = createTempTableWithRand(param.originalTable);
		createUniformSampleTableFromTempTable(tempTableName, param);
		dropTable(tempTableName);
		
		return Pair.of(getTableSize(param.sampleTableName()), getTableSize(param.originalTable));
	}
	
	protected TableUniqueName createTempTableExlucdingNameEntry(SampleParam param, TableUniqueName metaNameTableName) throws VerdictException {
		TableUniqueName tempTableName = generateTempTableName();
		TableUniqueName originalTableName = param.originalTable;
		executeUpdate(String.format("CREATE TABLE %s AS SELECT * FROM %s "
				+ "WHERE originalschemaname <> \"%s\" OR originaltablename <> \"%s\" OR sampletype <> \"%s\""
				+ "OR samplingratio <> %s OR columnnames <> \"%s\"",
				tempTableName, metaNameTableName, originalTableName.schemaName, originalTableName.tableName,
				param.sampleType, samplingRatioToString(param.samplingRatio), columnNameListToString(param.columnNames)));
		return tempTableName;
	}
	
	@Override
	public void updateSampleNameEntryIntoDBMS(SampleParam param, TableUniqueName metaNameTableName) throws VerdictException {
		TableUniqueName tempTableName = createTempTableExlucdingNameEntry(param, metaNameTableName);
		insertSampleNameEntryIntoDBMS(param, tempTableName);
		VerdictLogger.debug(this, "Created a temp table with the new sample name info: " + tempTableName);
		
		// copy temp table to the original meta name table after inserting a new entry.
		dropTable(metaNameTableName);
		executeUpdate(String.format("CREATE TABLE %s AS SELECT * FROM %s", metaNameTableName, tempTableName));
		VerdictLogger.debug(this, String.format("Moved the temp table (%s) to the meta name table (%s).", tempTableName, metaNameTableName));
		dropTable(tempTableName);
	}
	
	protected TableUniqueName createTempTableExlucdingSizeEntry(SampleParam param, TableUniqueName metaSizeTableName) throws VerdictException {
		TableUniqueName tempTableName = generateTempTableName();
		TableUniqueName sampleTableName = param.sampleTableName();
		executeUpdate(String.format("CREATE TABLE %s AS SELECT * FROM %s WHERE schemaname <> \"%s\" OR tablename <> \"%s\" ",
				tempTableName, metaSizeTableName, sampleTableName.schemaName, sampleTableName.tableName));
		return tempTableName;
	}
	
	@Override
	public void updateSampleSizeEntryIntoDBMS(SampleParam param, long sampleSize, long originalTableSize, TableUniqueName metaSizeTableName) throws VerdictException {
		TableUniqueName tempTableName = createTempTableExlucdingSizeEntry(param, metaSizeTableName);
		insertSampleSizeEntryIntoDBMS(param, sampleSize, originalTableSize, tempTableName);
		VerdictLogger.debug(this, "Created a temp table with the new sample size info: " + tempTableName);
		
		// copy temp table to the original meta size table after inserting a new entry.
		dropTable(metaSizeTableName);
		executeUpdate(String.format("CREATE TABLE %s AS SELECT * FROM %s", metaSizeTableName, tempTableName));
		VerdictLogger.debug(this, String.format("Moved the temp table (%s) to the meta size table (%s).", tempTableName, metaSizeTableName));
		dropTable(tempTableName);
	}
	
	/**
	 * Creates a universe sample table without dropping an old table.
	 * @param originalTableName
	 * @param sampleRatio
	 * @throws VerdictException
	 */
	@Override
	protected TableUniqueName justCreateUniverseSampleTableOf(SampleParam param) throws VerdictException {
		TableUniqueName sampleTableName = param.sampleTableName();
		String sql = String.format("CREATE TABLE %s AS SELECT * FROM %s "
								 + "WHERE abs(fnv_hash(%s)) %% 10000 <= %.4f",
								 sampleTableName, param.originalTable, param.columnNames.get(0), param.samplingRatio*10000);
		VerdictLogger.debug(this, String.format("Create a table: %s", sql));
		this.executeUpdate(sql);
		return sampleTableName;
	}
	
	private String columnNamesInString(TableUniqueName tableName) {
		return columnNamesInString(tableName, tableName.tableName);
	}
	
	private String columnNamesInString(TableUniqueName tableName, String subTableName) {
		List<String> colNames = vc.getMeta().getColumnNames(tableName);
		List<String> colNamesWithTable = new ArrayList<String>();
		for (String c : colNames) {
			colNamesWithTable.add(String.format("%s.%s", subTableName, c));
		}
		return Joiner.on(", ").join(colNamesWithTable);
	}
	
	/**
	 * Creates a temp table that includes
	 * 1. all the columns in the original table.
	 * 2. the size of the group on which this stratified sample is being created.
	 * 3. a random number between 0 and 1.
	 * @param param
	 * @return A pair of the table with random numbers and the table that stores the per-group size.
	 * @throws VerdictException
	 */
	protected Pair<TableUniqueName, TableUniqueName> createTempTableWithGroupCountsAndRand(SampleParam param) throws VerdictException {
		TableUniqueName rnTempTable = generateTempTableName();
		TableUniqueName grpTempTable = generateTempTableName();
		
		TableUniqueName originalTableName = param.originalTable;
		String groupName = Joiner.on(", ").join(param.columnNames);
		
		String sql1 = String.format("CREATE TABLE %s AS SELECT %s, COUNT(*) AS verdict_grp_size FROM %s GROUP BY %s",
									grpTempTable, groupName, originalTableName, groupName);
		VerdictLogger.debug(this, "The query used for the group-size temp table: ");
		VerdictLogger.debugPretty(this, Relation.prettyfySql(sql1), "  ");
		executeUpdate(sql1);
		
		String sql2 = String.format("CREATE TABLE %s AS SELECT %s, verdict_grp_size, rand(unix_timestamp()) as verdict_rand ",
									rnTempTable, columnNamesInString(originalTableName))
				+ String.format("FROM %s, %s", originalTableName, grpTempTable);
		List<String> joinCond = new ArrayList<String>();
		for (String g : param.columnNames) {
			joinCond.add(String.format("%s.%s = %s.%s", originalTableName, g, grpTempTable, g));
		}
		sql2 = sql2 + " WHERE " + Joiner.on(" AND ").join(joinCond);
		
		VerdictLogger.debug(this, "The query used for the temp table with group counts and random numbers.");
		VerdictLogger.debugPretty(this, Relation.prettyfySql(sql2), "  ");
		
		executeUpdate(sql2);
		return Pair.of(rnTempTable, grpTempTable);
	}
	
	/**
	 * 
	 */
	@Override
	protected void justCreateStratifiedSampleTableof(SampleParam param) throws VerdictException {
		Pair<TableUniqueName, TableUniqueName> tempTables = createTempTableWithGroupCountsAndRand(param);
		TableUniqueName rnTempTable = tempTables.getLeft();
		TableUniqueName grpTempTable = tempTables.getRight();
		createStratifiedSampleFromTempTable(rnTempTable, grpTempTable, param);
//		dropTable(rnTempTable);
//		dropTable(grpTempTable);
	}
	
	/**
	 * Creates a stratified sample from a temp table, which is created by
	 *  {@link DbmsImpala#createTempTableWithGroupCountsAndRand createTempTableWithGroupCountsAndRand}.
	 * The created stratified sample includes a sampling probability for every tuple (in column name "verdict_sampling_prob")
	 * so that it can be used for computing the final answer.
	 * 
	 * The sampling probability for each tuple is determined as:
	 *   min( 1.0, (original table size) * (sampling ratio param) / (number of groups) / (size of the group) )
	 * 
	 * @param tempTableName
	 * @param param
	 * @throws VerdictException
	 */
	protected void createStratifiedSampleFromTempTable(TableUniqueName rnTempTable, TableUniqueName grpTempTable, SampleParam param)
			throws VerdictException
	{
		TableUniqueName originalTableName = param.originalTable;
		TableUniqueName sampleTempTable = generateTempTableName();
		String samplingProbColName = vc.samplingProbColName();
		
		VerdictLogger.debug(this, "Creating a sample table using " + rnTempTable + " and " + grpTempTable);
		ApproxRelation r = ApproxSingleRelation.from(vc, new SampleParam(param.originalTable, "uniform", null, new ArrayList<String>()));
		long originalTableSize = r.countValue();
		
		long groupCount = SingleRelation.from(vc, grpTempTable).countValue();
		String tmpCol1 = Relation.genColumnAlias();
		
		// create a sample table without the sampling probability
		String sql1 = String.format("CREATE TABLE %s AS ", sampleTempTable) 
		   		    + String.format("SELECT %s FROM ", columnNamesInString(originalTableName, "t1"))
				    + String.format("(SELECT *, %d*%f/%d/verdict_grp_size AS %s FROM %s) t1 ",
				    				originalTableSize, param.samplingRatio, groupCount, tmpCol1, rnTempTable)
				                  + "WHERE verdict_rand <= " + tmpCol1;
		VerdictLogger.debug(this, "The query used for sample creation without sampling probabilities: ");
		VerdictLogger.debugPretty(this, Relation.prettyfySql(sql1), "  ");
		executeUpdate(sql1);
		
		// attach sampling probability
		List<String> joinCond = new ArrayList<String>();
		for (String g : param.columnNames) {
			joinCond.add(String.format("%s = %s", g, g));
		}
		
		ExactRelation grpRatioBase = SingleRelation.from(vc, sampleTempTable).groupby(param.columnNames).agg("count(*) AS sample_grp_size")
								 	   		      .join(SingleRelation.from(vc, grpTempTable),
								 					    Joiner.on(" AND ").join(joinCond));
		List<String> groupNamesWithTabName = new ArrayList<String>();
		for (String col : param.columnNames) {
			groupNamesWithTabName.add(grpTempTable + "." + col);
		}
		ExactRelation grpRatioRel = grpRatioBase.select(
				Joiner.on(", ").join(groupNamesWithTabName) + String.format(", sample_grp_size / verdict_grp_size AS %s", samplingProbColName)); 
		ExactRelation stSampleRel = SingleRelation.from(vc, sampleTempTable).join(grpRatioRel, Joiner.on(" AND ").join(joinCond))
				    							  .select(columnNamesInString(originalTableName, sampleTempTable.tableName)
				    									  + String.format(", %s", samplingProbColName));
		String sql2 = String.format("CREATE TABLE %s AS ", param.sampleTableName()) + stSampleRel.toSql();
		VerdictLogger.debug(this, "The query used for sample creation with sampling probabilities: ");
		VerdictLogger.debugPretty(this, Relation.prettyfySql(sql2), "  ");
		executeUpdate(sql2);
		
//		dropTable(sampleTempTable);
	}
	
	@Override
	public void deleteSampleNameEntryFromDBMS(SampleParam param, TableUniqueName metaNameTableName) throws VerdictException {
		TableUniqueName tempTable = createTempTableExlucdingNameEntry(param, metaNameTableName);
		dropTable(metaNameTableName);
		moveTable(tempTable, metaNameTableName);
		dropTable(tempTable);
	}
	
	@Override
	public void deleteSampleSizeEntryFromDBMS(SampleParam param, TableUniqueName metaSizeTableName) throws VerdictException {
		TableUniqueName tempTable = createTempTableExlucdingSizeEntry(param, metaSizeTableName);
		dropTable(metaSizeTableName);
		moveTable(tempTable, metaSizeTableName);
		dropTable(tempTable);
	}
	
	@Override
	public ResultSet getDatabaseNames() throws VerdictException {
		try {
			ResultSet rs = conn.getMetaData().getSchemas(null, "%");
			Map<Integer, Integer> colMap = new HashMap<Integer, Integer>();
			colMap.put(1, 1);
			return new VerdictResultSet(rs, null, colMap);
		} catch (SQLException e) {
			throw new VerdictException(e);
		}
	}
	
	@Override
	public ResultSet getAllTableAndColumns(String schemaName) throws VerdictException {
		try {
			ResultSet rs = conn.getMetaData().getColumns(null, schemaName, "%", "%");
			Map<Integer, Integer> columnMap = new HashMap<Integer, Integer>();
			columnMap.put(1, 3);	// table name
			columnMap.put(2, 4);	// column name
			return new VerdictResultSet(rs, null, columnMap);
		} catch (SQLException e) {
			throw new VerdictException(e);
		}
	}
	
	@Override
	public ResultSet getTableNames(String schemaName) throws VerdictException {
		String[] types = {"TABLE"};
		ResultSet rs;
		try {
			rs = conn.getMetaData().getTables(null, schemaName, "%", types);
			Map<Integer, Integer> columnMap = new HashMap<Integer, Integer>();
			columnMap.put(1, 3);	// table name
			return new VerdictResultSet(rs, null, columnMap);
		} catch (SQLException e) {
			throw new VerdictException(e);
		}
	}
	
	@Override
	public ResultSet describeTable(TableUniqueName tableUniqueName)  throws VerdictException {
		try {
			ResultSet rs = conn.getMetaData().getColumns(
					null, tableUniqueName.schemaName, tableUniqueName.tableName, "%");
			Map<Integer, Integer> columnMap = new HashMap<Integer, Integer>();
			columnMap.put(1, 4);	// column name
			columnMap.put(2, 6); 	// data type name
			columnMap.put(3, 12); 	// remarks
			return new VerdictResultSet(rs, null, columnMap);
		} catch (SQLException e) {
			throw new VerdictException(e);
		}
	}
	
	/**
	 * Impala does not support the standard JDBC protocol {@link java.sql.Connection#setCatalog(String) setCatalog}
	 * function for changing the current database. This is a workaround.
	 */
	@Override
	public void changeDatabase(String schemaName) throws VerdictException {
		execute(String.format("use %s", schemaName));
		currentSchema = Optional.fromNullable(schemaName);
		VerdictLogger.info("Database changed to: " + schemaName);
	}
	
	@Override
	public void createMetaTablesInDMBS(
			TableUniqueName originalTableName,
			TableUniqueName sizeTableName,
			TableUniqueName nameTableName) throws VerdictException {
		VerdictLogger.debug(this, "Creating meta tables if not exist.");
		String sql = String.format("CREATE TABLE IF NOT EXISTS %s", sizeTableName)
				+ " (schemaname STRING, "
				+ " tablename STRING, "
				+ " samplesize BIGINT, "
				+ " originaltablesize BIGINT)";
		executeUpdate(sql);

		sql = String.format("CREATE TABLE IF NOT EXISTS %s", nameTableName)
				+ " (originalschemaname STRING, "
				+ " originaltablename STRING, "
				+ " sampleschemaaname STRING, "
				+ " sampletablename STRING, "
				+ " sampletype STRING, "
				+ " samplingratio DOUBLE, "
				+ " columnnames STRING)";
		executeUpdate(sql);
	}
	
	@Override
	public boolean doesMetaTablesExist(String schemaName) throws VerdictException {
		String[] types = {"TABLE"};
		try {
			ResultSet rs = vc.getDbms().getDbmsConnection().getMetaData().getTables(
					null, schemaName, vc.getMeta().getMetaNameTableName(currentSchema.get()).tableName, types);
			if (!rs.next()) return false;
			else return true;
		} catch (SQLException e) {
			throw new VerdictException(e);
		}
	}
}
