/*
WholeStageCodegenExec
*(1) HashAggregate(keys=[a#19], functions=[partial_max((b#20 + 1))], output=[a#19, max#183])
+- *(1) SerializeFromObject [assertnotnull(input[0, org.apache.spark.sql.test.SQLTestData$TestData2, true]).a AS a#19, assertnotnull(input[0, org.apache.spark.sql.test.SQLTestData$TestData2, true]).b AS b#20]
   +- Scan[obj#18]
*/

public Object generate(Object[] references) {
  return new GeneratedIteratorForCodegenStage1(references);
}

/*wsc_codegenStageId*/
final class GeneratedIteratorForCodegenStage1 extends org.apache.spark.sql.execution.BufferedRowIterator {
  private Object[] references;
  private scala.collection.Iterator[] inputs;
  private boolean agg_initAgg_0;
  private boolean agg_bufIsNull_0;
  private int agg_bufValue_0;
  private agg_FastHashMap_0 agg_fastHashMap_0;
  private org.apache.spark.unsafe.KVIterator<UnsafeRow, UnsafeRow> agg_fastHashMapIter_0;
  private org.apache.spark.unsafe.KVIterator agg_mapIter_0;
  private org.apache.spark.sql.execution.UnsafeFixedWidthAggregationMap agg_hashMap_0;
  private org.apache.spark.sql.execution.UnsafeKVExternalSorter agg_sorter_0;
  private scala.collection.Iterator inputadapter_input_0;
  private boolean agg_agg_isNull_7_0;
  private boolean agg_agg_isNull_12_0;
  private org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter[] serializefromobject_mutableStateArray_0 = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter[4];

  public GeneratedIteratorForCodegenStage1(Object[] references) {
    this.references = references;
  }

  public void init(int index, scala.collection.Iterator[] inputs) {
    partitionIndex = index;
    this.inputs = inputs;

    agg_fastHashMap_0 = new agg_FastHashMap_0(((org.apache.spark.sql.execution.aggregate.HashAggregateExec) references[0] /* plan */).getTaskMemoryManager(), ((org.apache.spark.sql.execution.aggregate.HashAggregateExec) references[0] /* plan */).getEmptyAggregationBuffer());
    agg_hashMap_0 = ((org.apache.spark.sql.execution.aggregate.HashAggregateExec) references[0] /* plan */).createHashMap();
    inputadapter_input_0 = inputs[0];
    serializefromobject_mutableStateArray_0[0] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(2, 0);
    serializefromobject_mutableStateArray_0[1] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(2, 0);
    serializefromobject_mutableStateArray_0[2] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(1, 0);
    serializefromobject_mutableStateArray_0[3] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(2, 0);

  }

  public class agg_FastHashMap_0 {
    private org.apache.spark.sql.catalyst.expressions.RowBasedKeyValueBatch batch;
    private int[] buckets;
    private int capacity = 1 << 16;
    private double loadFactor = 0.5;
    private int numBuckets = (int) (capacity / loadFactor);
    private int maxSteps = 2;
    private int numRows = 0;
    private Object emptyVBase;
    private long emptyVOff;
    private int emptyVLen;
    private boolean isBatchFull = false;

    public agg_FastHashMap_0(
      org.apache.spark.memory.TaskMemoryManager taskMemoryManager,
      InternalRow emptyAggregationBuffer) {
      batch = org.apache.spark.sql.catalyst.expressions.RowBasedKeyValueBatch
              .allocate(((org.apache.spark.sql.types.StructType) references[1] /* keySchemaTerm */), ((org.apache.spark.sql.types.StructType) references[2] /* valueSchemaTerm */), taskMemoryManager, capacity);

      final UnsafeProjection valueProjection = UnsafeProjection.create(((org.apache.spark.sql.types.StructType) references[2] /* valueSchemaTerm */));
      final byte[] emptyBuffer = valueProjection.apply(emptyAggregationBuffer).getBytes();

      emptyVBase = emptyBuffer;
      emptyVOff = Platform.BYTE_ARRAY_OFFSET;
      emptyVLen = emptyBuffer.length;

      buckets = new int[numBuckets];
      java.util.Arrays.fill(buckets, -1);
    }

    public org.apache.spark.sql.catalyst.expressions.UnsafeRow findOrInsert(int agg_key_0) {
      long h = hash(agg_key_0);
      int step = 0;
      int idx = (int) h & (numBuckets - 1);
      while (step < maxSteps) {
        // Return bucket index if it's either an empty slot or already contains the key
        if (buckets[idx] == -1) {
          if (numRows < capacity && !isBatchFull) {
            // creating the unsafe for new entry
            org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter agg_rowWriter
              = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(
              1, 0);
            agg_rowWriter.reset(); //TODO: investigate if reset or zeroout are actually needed
            agg_rowWriter.zeroOutNullBytes();
            agg_rowWriter.write(0, agg_key_0);
            org.apache.spark.sql.catalyst.expressions.UnsafeRow agg_result
              = agg_rowWriter.getRow();
            Object kbase = agg_result.getBaseObject();
            long koff = agg_result.getBaseOffset();
            int klen = agg_result.getSizeInBytes();

            UnsafeRow vRow
              = batch.appendRow(kbase, koff, klen, emptyVBase, emptyVOff, emptyVLen);
            if (vRow == null) {
              isBatchFull = true;
            } else {
              buckets[idx] = numRows++;
            }
            return vRow;
          } else {
            // No more space
            return null;
          }
        } else if (equals(idx, agg_key_0)) {
          return batch.getValueRow(buckets[idx]);
        }
        idx = (idx + 1) & (numBuckets - 1);
        step++;
      }
      // Didn't find it
      return null;
    }

    private boolean equals(int idx, int agg_key_0) {
      UnsafeRow row = batch.getKeyRow(buckets[idx]);
      return (row.getInt(0) == agg_key_0);
    }

    private long hash(int agg_key_0) {
      long agg_hash_0 = 0;

      int agg_result_0 = agg_key_0;
      agg_hash_0 = (agg_hash_0 ^ (0x9e3779b9)) + agg_result_0 + (agg_hash_0 << 6) + (agg_hash_0 >>> 2);

      return agg_hash_0;
    }

    public org.apache.spark.unsafe.KVIterator<UnsafeRow, UnsafeRow> rowIterator() {
      return batch.rowIterator();
    }

    public void close() {
      batch.close();
    }

  }

  private void agg_doAggregateWithKeysOutput_0(UnsafeRow agg_keyTerm_0, UnsafeRow agg_bufferTerm_0)
  throws java.io.IOException {
    ((org.apache.spark.sql.execution.metric.SQLMetric) references[8] /* numOutputRows */).add(1);

    int agg_value_18 = agg_keyTerm_0.getInt(0);
    boolean agg_isNull_18 = agg_bufferTerm_0.isNullAt(0);
    int agg_value_19 = agg_isNull_18 ?
                       -1 : (agg_bufferTerm_0.getInt(0));

    serializefromobject_mutableStateArray_0[3].reset();

    serializefromobject_mutableStateArray_0[3].zeroOutNullBytes();

    serializefromobject_mutableStateArray_0[3].write(0, agg_value_18);

    if (agg_isNull_18) {
      serializefromobject_mutableStateArray_0[3].setNullAt(1);
    } else {
      serializefromobject_mutableStateArray_0[3].write(1, agg_value_19);
    }
    append((serializefromobject_mutableStateArray_0[3].getRow()));

  }

  private void serializefromobject_doConsume_0(InternalRow inputadapter_row_0, org.apache.spark.sql.test.SQLTestData$TestData2 serializefromobject_expr_0_0, boolean serializefromobject_exprIsNull_0_0) throws java.io.IOException {
    if (serializefromobject_exprIsNull_0_0) {
      throw new NullPointerException(((java.lang.String) references[6] /* errMsg */));
    }
    boolean serializefromobject_isNull_0 = true;
    int serializefromobject_value_0 = -1;
    if (!false) {
      serializefromobject_isNull_0 = false;
      if (!serializefromobject_isNull_0) {
        serializefromobject_value_0 = serializefromobject_expr_0_0.a();
      }
    }
    if (serializefromobject_exprIsNull_0_0) {
      throw new NullPointerException(((java.lang.String) references[7] /* errMsg */));
    }
    boolean serializefromobject_isNull_3 = true;
    int serializefromobject_value_3 = -1;
    if (!false) {
      serializefromobject_isNull_3 = false;
      if (!serializefromobject_isNull_3) {
        serializefromobject_value_3 = serializefromobject_expr_0_0.b();
      }
    }

    agg_doConsume_0(serializefromobject_value_0, serializefromobject_value_3);

  }

  private void agg_doConsume_0(int agg_expr_0_0, int agg_expr_1_0) throws java.io.IOException {
    UnsafeRow agg_unsafeRowAggBuffer_0 = null;
    UnsafeRow agg_fastAggBuffer_0 = null;

    if (true) {
      if (!false) {
        agg_fastAggBuffer_0 = agg_fastHashMap_0.findOrInsert(
                                agg_expr_0_0);
      }
    }
    // Cannot find the key in fast hash map, try regular hash map.
    if (agg_fastAggBuffer_0 == null) {
      // generate grouping key
      serializefromobject_mutableStateArray_0[2].reset();

      serializefromobject_mutableStateArray_0[2].write(0, agg_expr_0_0);
      int agg_value_6 = 48;

      agg_value_6 = org.apache.spark.unsafe.hash.Murmur3_x86_32.hashInt(agg_expr_0_0, agg_value_6);
      if (true) {
        // try to get the buffer from hash map
        agg_unsafeRowAggBuffer_0 =
          agg_hashMap_0.getAggregationBufferFromUnsafeRow((serializefromobject_mutableStateArray_0[2].getRow()), agg_value_6);
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
                                     (serializefromobject_mutableStateArray_0[2].getRow()), agg_value_6);
        if (agg_unsafeRowAggBuffer_0 == null) {
          // failed to allocate the first page
          throw new OutOfMemoryError("No enough memory for aggregation");
        }
      }

    }

    if (agg_fastAggBuffer_0 != null) {
      // common sub-expressions

      // evaluate aggregate function
      agg_agg_isNull_12_0 = true;
      int agg_value_13 = -1;

      boolean agg_isNull_13 = agg_fastAggBuffer_0.isNullAt(0);
      int agg_value_14 = agg_isNull_13 ?
                         -1 : (agg_fastAggBuffer_0.getInt(0));

      if (!agg_isNull_13 && (agg_agg_isNull_12_0 ||
                             agg_value_14 > agg_value_13)) {
        agg_agg_isNull_12_0 = false;
        agg_value_13 = agg_value_14;
      }

      int agg_value_15 = -1;
      agg_value_15 = agg_expr_1_0 + 1;

      if (!false && (agg_agg_isNull_12_0 ||
                     agg_value_15 > agg_value_13)) {
        agg_agg_isNull_12_0 = false;
        agg_value_13 = agg_value_15;
      }
      // update fast row
      agg_fastAggBuffer_0.setInt(0, agg_value_13);
    } else {
      // common sub-expressions

      // evaluate aggregate function
      agg_agg_isNull_7_0 = true;
      int agg_value_8 = -1;

      boolean agg_isNull_8 = agg_unsafeRowAggBuffer_0.isNullAt(0);
      int agg_value_9 = agg_isNull_8 ?
                        -1 : (agg_unsafeRowAggBuffer_0.getInt(0));

      if (!agg_isNull_8 && (agg_agg_isNull_7_0 ||
                            agg_value_9 > agg_value_8)) {
        agg_agg_isNull_7_0 = false;
        agg_value_8 = agg_value_9;
      }

      int agg_value_10 = -1;
      agg_value_10 = agg_expr_1_0 + 1;

      if (!false && (agg_agg_isNull_7_0 ||
                     agg_value_10 > agg_value_8)) {
        agg_agg_isNull_7_0 = false;
        agg_value_8 = agg_value_10;
      }
      // update unsafe row buffer
      agg_unsafeRowAggBuffer_0.setInt(0, agg_value_8);

    }

  }

  private void agg_doAggregateWithKeys_0() throws java.io.IOException {
    while (inputadapter_input_0.hasNext() && !stopEarly()) {
      InternalRow inputadapter_row_0 = (InternalRow) inputadapter_input_0.next();
      boolean inputadapter_isNull_0 = inputadapter_row_0.isNullAt(0);
      org.apache.spark.sql.test.SQLTestData$TestData2 inputadapter_value_0 = inputadapter_isNull_0 ?
          null : ((org.apache.spark.sql.test.SQLTestData$TestData2)inputadapter_row_0.get(0, null));

      serializefromobject_doConsume_0(inputadapter_row_0, inputadapter_value_0, inputadapter_isNull_0);
      if (shouldStop()) return;
    }

    agg_fastHashMapIter_0 = agg_fastHashMap_0.rowIterator();
    agg_mapIter_0 = ((org.apache.spark.sql.execution.aggregate.HashAggregateExec) references[0] /* plan */).finishAggregate(agg_hashMap_0, agg_sorter_0, ((org.apache.spark.sql.execution.metric.SQLMetric) references[3] /* peakMemory */), ((org.apache.spark.sql.execution.metric.SQLMetric) references[4] /* spillSize */), ((org.apache.spark.sql.execution.metric.SQLMetric) references[5] /* avgHashProbe */));

  }

  protected void processNext() throws java.io.IOException {
    if (!agg_initAgg_0) {
      agg_initAgg_0 = true;
      long wholestagecodegen_beforeAgg_0 = System.nanoTime();
      agg_doAggregateWithKeys_0();
      ((org.apache.spark.sql.execution.metric.SQLMetric) references[9] /* aggTime */).add((System.nanoTime() - wholestagecodegen_beforeAgg_0) / 1000000);
    }

    // output the result

    while (agg_fastHashMapIter_0.next()) {
      UnsafeRow agg_aggKey_0 = (UnsafeRow) agg_fastHashMapIter_0.getKey();
      UnsafeRow agg_aggBuffer_0 = (UnsafeRow) agg_fastHashMapIter_0.getValue();
      agg_doAggregateWithKeysOutput_0(agg_aggKey_0, agg_aggBuffer_0);

      if (shouldStop()) return;
    }
    agg_fastHashMap_0.close();

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