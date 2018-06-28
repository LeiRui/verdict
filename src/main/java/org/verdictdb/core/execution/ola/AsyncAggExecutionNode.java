/*
 * Copyright 2018 University of Michigan
 * 
 * You must contact Barzan Mozafari (mozafari@umich.edu) or Yongjoo Park (pyongjoo@umich.edu) to discuss
 * how you could use, modify, or distribute this code. By default, this code is not open-sourced and we do
 * not license this code.
 */

package org.verdictdb.core.execution.ola;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.core.execution.CreateTableAsSelectExecutionNode;
import org.verdictdb.core.execution.ExecutionInfoToken;
import org.verdictdb.core.execution.ExecutionTokenQueue;
import org.verdictdb.core.execution.QueryExecutionNode;
import org.verdictdb.core.execution.QueryExecutionPlan;
import org.verdictdb.core.query.AbstractRelation;
import org.verdictdb.core.query.AliasedColumn;
import org.verdictdb.core.query.ColumnOp;
import org.verdictdb.core.query.SelectItem;
import org.verdictdb.core.query.SelectQuery;
import org.verdictdb.core.query.UnnamedColumn;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.core.rewriter.aggresult.AggNameAndType;
import org.verdictdb.core.rewriter.query.AggQueryRewriter;
import org.verdictdb.core.rewriter.query.AggblockMeta;
import org.verdictdb.exception.VerdictDBTypeException;
import org.verdictdb.exception.VerdictDBValueException;
import org.verdictdb.exception.VerdictDBException;

/**
 * Represents an execution of a single aggregate query (without nested components).
 * 
 * Steps:
 * 1. identify agg and nonagg columns of a given select agg query.
 * 2. convert the query into multiple block-agg queries.
 * 3. issue those block-agg queries one by one.
 * 4. combine the results of those block-agg queries as the answers to those queries arrive.
 * 5. depending on the interface, call an appropriate result handler.
 * 
 * @author Yongjoo Park
 *
 */
public class AsyncAggExecutionNode extends QueryExecutionNode {

  ScrambleMeta scrambleMeta;

  // group-by columns
  List<String> nonaggColumns;
  //  
  // agg columns. pairs of their column names and their types (i.e., sum, avg, count)
  List<AggNameAndType> aggColumns;

//  SelectQuery originalQuery;

//  List<AsyncAggExecutionNode> children = new ArrayList<>();

//  int tableNum = 1;

//  String getNextTempTableName(String tableNamePrefix) {
//    return tableNamePrefix + tableNum++;
//  }

  private AsyncAggExecutionNode(QueryExecutionPlan plan) {
    super(plan);
  }
  
  public static AsyncAggExecutionNode create(
      QueryExecutionPlan plan,
      List<QueryExecutionNode> individualAggs,
      List<QueryExecutionNode> combiners) {
    
    AsyncAggExecutionNode node = new AsyncAggExecutionNode(plan);
    ExecutionTokenQueue queue = node.generateListeningQueue();
    individualAggs.get(0).addBroadcastingQueue(queue);
    node.addDependency(individualAggs.get(0));
    for (QueryExecutionNode c : combiners) {
      c.addBroadcastingQueue(queue);
      node.addDependency(c);
    }
    return node;
  }

  @Override
  public ExecutionInfoToken executeNode(DbmsConnection conn, List<ExecutionInfoToken> downstreamResults) 
      throws VerdictDBException {
//    ExecutionInfoToken token = super.executeNode(conn, downstreamResults);
//    System.out.println("AsyncNode execution " + getSelectQuery());
//    try {
//      TimeUnit.SECONDS.sleep(1);
//      this.print();
////      for (QueryExecutionNode n : getDependents()) {
////        System.out.println(n + " " + n.getStatus());
////      }
//    } catch (InterruptedException e) {
//      // TODO Auto-generated catch block
//      e.printStackTrace();
//    }
    return downstreamResults.get(0);
  }

  @Override
  public QueryExecutionNode deepcopy() {
    AsyncAggExecutionNode copy = new AsyncAggExecutionNode(getPlan());
    copyFields(this, copy);
    return copy;
  }
  
  void copyFields(AsyncAggExecutionNode from, AsyncAggExecutionNode to) {
    to.scrambleMeta = from.scrambleMeta;
    to.nonaggColumns = from.nonaggColumns;
    to.aggColumns = from.aggColumns;
  }

}
