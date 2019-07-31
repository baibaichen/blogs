/*
WholeStageCodegenExec
*(2) HashAggregate(keys=[a#19], functions=[max((b#20 + 1))], output=[a#19, max((b + 1))#177, (max((b + 1)) + 1)#178])
+- Exchange hashpartitioning(a#19, 5)
   +- *(1) HashAggregate(keys=[a#19], functions=[partial_max((b#20 + 1))], output=[a#19, max#183])
      +- *(1) SerializeFromObject [assertnotnull(input[0, org.apache.spark.sql.test.SQLTestData$TestData2, true]).a AS a#19, assertnotnull(input[0, org.apache.spark.sql.test.SQLTestData$TestData2, true]).b AS b#20]
         +- Scan[obj#18]
*/


public Object generate(Object[] references) {
  return new GeneratedIteratorForCodegenStage2(references);
}

/*wsc_codegenStageId*/
final class GeneratedIteratorForCodegenStage2 extends org.apache.spark.sql.execution.BufferedRowIterator {
  private Object[] references;
  private scala.collection.Iterator[] inputs;
  private boolean agg_initAgg_0;
  private org.apache.spark.unsafe.KVIterator agg_mapIter_0;
  private org.apache.spark.sql.execution.UnsafeFixedWidthAggregationMap agg_hashMap_0;
  private org.apache.spark.sql.execution.UnsafeKVExternalSorter agg_sorter_0;
  private scala.collection.Iterator inputadapter_input_0;
  private boolean agg_agg_isNull_4_0;
  private org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter[] agg_mutableStateArray_0 = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter[2];

  public GeneratedIteratorForCodegenStage2(Object[] references) {
    this.references = references;
  }

  public void init(int index, scala.collection.Iterator[] inputs) {
    partitionIndex = index;
    this.inputs = inputs;

    agg_hashMap_0 = ((org.apache.spark.sql.execution.aggregate.HashAggregateExec) references[0] /* plan */).createHashMap();
    inputadapter_input_0 = inputs[0];
    agg_mutableStateArray_0[0] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(1, 0);
    agg_mutableStateArray_0[1] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(3, 0);

  }

  private void agg_doAggregateWithKeysOutput_0(UnsafeRow agg_keyTerm_0, UnsafeRow agg_bufferTerm_0)
  throws java.io.IOException {
    ((org.apache.spark.sql.execution.metric.SQLMetric) references[4] /* numOutputRows */).add(1);

    int agg_value_7 = agg_keyTerm_0.getInt(0);
    boolean agg_isNull_8 = agg_bufferTerm_0.isNullAt(0);
    int agg_value_8 = agg_isNull_8 ?
                      -1 : (agg_bufferTerm_0.getInt(0));

    boolean agg_isNull_12 = true;
    int agg_value_12 = -1;

    if (!agg_isNull_8) {
      agg_isNull_12 = false; // resultCode could change nullability.
      agg_value_12 = agg_value_8 + 1;

    }
    agg_mutableStateArray_0[1].reset();

    agg_mutableStateArray_0[1].zeroOutNullBytes();

    agg_mutableStateArray_0[1].write(0, agg_value_7);

    if (agg_isNull_8) {
      agg_mutableStateArray_0[1].setNullAt(1);
    } else {
      agg_mutableStateArray_0[1].write(1, agg_value_8);
    }

    if (agg_isNull_12) {
      agg_mutableStateArray_0[1].setNullAt(2);
    } else {
      agg_mutableStateArray_0[1].write(2, agg_value_12);
    }
    append((agg_mutableStateArray_0[1].getRow()));

  }

  private void agg_doConsume_0(InternalRow inputadapter_row_0, int agg_expr_0_0, int agg_expr_1_0, boolean agg_exprIsNull_1_0) throws java.io.IOException {
    UnsafeRow agg_unsafeRowAggBuffer_0 = null;

    // generate grouping key
    agg_mutableStateArray_0[0].reset();

    agg_mutableStateArray_0[0].write(0, agg_expr_0_0);
    int agg_value_2 = 48;

    agg_value_2 = org.apache.spark.unsafe.hash.Murmur3_x86_32.hashInt(agg_expr_0_0, agg_value_2);
    if (true) {
      // try to get the buffer from hash map
      agg_unsafeRowAggBuffer_0 =
        agg_hashMap_0.getAggregationBufferFromUnsafeRow((agg_mutableStateArray_0[0].getRow()), agg_value_2);
    }
    // Can't allocate buffer from the hash map. Spill the map and fallback to sort-based
    // aggregation after processing all input rows.
    if (agg_unsafeRowAggBuffer_0 == null) {
      if (agg_sorter_0 == null) {
        agg_sorter_0 = agg_hashMap_0.destructAndCreateExternalSorter();
      } else {
        agg_sorter_0.merge(agg_hashMap_0.destructAndCreateExternalSorter());
      }

      // the hash map had be spilled, it should have enough memory now,
      // try to allocate buffer again.
      agg_unsafeRowAggBuffer_0 = agg_hashMap_0.getAggregationBufferFromUnsafeRow(
                                   (agg_mutableStateArray_0[0].getRow()), agg_value_2);
      if (agg_unsafeRowAggBuffer_0 == null) {
        // failed to allocate the first page
        throw new OutOfMemoryError("No enough memory for aggregation");
      }
    }

    // common sub-expressions

    // evaluate aggregate function
    agg_agg_isNull_4_0 = true;
    int agg_value_4 = -1;

    boolean agg_isNull_5 = agg_unsafeRowAggBuffer_0.isNullAt(0);
    int agg_value_5 = agg_isNull_5 ?
                      -1 : (agg_unsafeRowAggBuffer_0.getInt(0));

    if (!agg_isNull_5 && (agg_agg_isNull_4_0 ||
                          agg_value_5 > agg_value_4)) {
      agg_agg_isNull_4_0 = false;
      agg_value_4 = agg_value_5;
    }

    if (!agg_exprIsNull_1_0 && (agg_agg_isNull_4_0 ||
                                agg_expr_1_0 > agg_value_4)) {
      agg_agg_isNull_4_0 = false;
      agg_value_4 = agg_expr_1_0;
    }
    // update unsafe row buffer
    if (!agg_agg_isNull_4_0) {
      agg_unsafeRowAggBuffer_0.setInt(0, agg_value_4);
    } else {
      agg_unsafeRowAggBuffer_0.setNullAt(0);
    }

  }

  private void agg_doAggregateWithKeys_0() throws java.io.IOException {
    while (inputadapter_input_0.hasNext() && !stopEarly()) {
      InternalRow inputadapter_row_0 = (InternalRow) inputadapter_input_0.next();
      int inputadapter_value_0 = inputadapter_row_0.getInt(0);
      boolean inputadapter_isNull_1 = inputadapter_row_0.isNullAt(1);
      int inputadapter_value_1 = inputadapter_isNull_1 ?
                                 -1 : (inputadapter_row_0.getInt(1));

      agg_doConsume_0(inputadapter_row_0, inputadapter_value_0, inputadapter_value_1, inputadapter_isNull_1);
      if (shouldStop()) return;
    }

    agg_mapIter_0 = ((org.apache.spark.sql.execution.aggregate.HashAggregateExec) references[0] /* plan */).finishAggregate(agg_hashMap_0, agg_sorter_0, ((org.apache.spark.sql.execution.metric.SQLMetric) references[1] /* peakMemory */), ((org.apache.spark.sql.execution.metric.SQLMetric) references[2] /* spillSize */), ((org.apache.spark.sql.execution.metric.SQLMetric) references[3] /* avgHashProbe */));
  }

  protected void processNext() throws java.io.IOException {
    if (!agg_initAgg_0) {
      agg_initAgg_0 = true;
      long wholestagecodegen_beforeAgg_0 = System.nanoTime();
      agg_doAggregateWithKeys_0();
      ((org.apache.spark.sql.execution.metric.SQLMetric) references[5] /* aggTime */).add((System.nanoTime() - wholestagecodegen_beforeAgg_0) / 1000000);
    }

    // output the result

    while (agg_mapIter_0.next()) {
      UnsafeRow agg_aggKey_0 = (UnsafeRow) agg_mapIter_0.getKey();
      UnsafeRow agg_aggBuffer_0 = (UnsafeRow) agg_mapIter_0.getValue();
      agg_doAggregateWithKeysOutput_0(agg_aggKey_0, agg_aggBuffer_0);

      if (shouldStop()) return;
    }

    agg_mapIter_0.close();
    if (agg_sorter_0 == null) {
      agg_hashMap_0.free();
    }
  }

}