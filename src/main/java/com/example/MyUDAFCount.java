package com.example;

import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedUDAFs;
import org.apache.hadoop.hive.ql.exec.vector.expressions.aggregates.*;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFResolver2;
import org.apache.hadoop.hive.ql.util.JavaDataModel;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils.ObjectInspectorCopyOption;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils.ObjectInspectorObject;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.LongObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.io.LongWritable;

/**
 * This class implements the COUNT aggregation function as in SQL.
 */
@Description(name = "count", value = "_FUNC_(*) - Returns the total number of retrieved rows, including "
		+ "rows containing NULL values.\n"

		+ "_FUNC_(expr) - Returns the number of rows for which the supplied " + "expression is non-NULL.\n"

		+ "_FUNC_(DISTINCT expr[, expr...]) - Returns the number of rows for "
		+ "which the supplied expression(s) are unique and non-NULL.")

public class MyUDAFCount implements GenericUDAFResolver2 {

	@Override
	public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
		// This method implementation is preserved for backward compatibility.
		return new GenericUDAFCountEvaluator();
	}

	@Override
	public GenericUDAFEvaluator getEvaluator(GenericUDAFParameterInfo paramInfo) throws SemanticException {

		TypeInfo[] parameters = paramInfo.getParameters();

		if (parameters.length == 0) {
			if (!paramInfo.isAllColumns()) {
				throw new UDFArgumentException("Argument expected");
			}
			assert !paramInfo.isDistinct() : "DISTINCT not supported with *";
		} else {
			if (parameters.length > 1 && !paramInfo.isDistinct()) {
				throw new UDFArgumentException("DISTINCT keyword must be specified");
			}
			assert !paramInfo.isAllColumns() : "* not supported in expression list";
		}

		GenericUDAFCountEvaluator countEvaluator = new GenericUDAFCountEvaluator();
		countEvaluator.setWindowing(paramInfo.isWindowing());
		countEvaluator.setCountAllColumns(paramInfo.isAllColumns());
		countEvaluator.setCountDistinct(paramInfo.isDistinct());

		return countEvaluator;
	}

	/**
	 * GenericUDAFCountEvaluator.
	 *
	 */
	@VectorizedUDAFs({ VectorUDAFCount.class, VectorUDAFCountMerge.class, VectorUDAFCountStar.class })
	public static class GenericUDAFCountEvaluator extends GenericUDAFEvaluator {
		private boolean isWindowing = false;
		private boolean countAllColumns = false;
		private boolean countDistinct = false;
		private LongObjectInspector partialCountAggOI;
		private ObjectInspector[] inputOI, outputOI;
		private LongWritable result;

		@Override
		public ObjectInspector init(Mode mode, ObjectInspector[] parameters) throws HiveException {
			super.init(mode, parameters);
			if (mode == Mode.PARTIAL2 || mode == Mode.FINAL) {
				partialCountAggOI = (LongObjectInspector) parameters[0];
			} else {
				inputOI = parameters;
				outputOI = ObjectInspectorUtils.getStandardObjectInspector(inputOI, ObjectInspectorCopyOption.JAVA);
			}
			result = new LongWritable(0);

			return PrimitiveObjectInspectorFactory.writableLongObjectInspector;
		}

		public void setWindowing(boolean isWindowing) {
			this.isWindowing = isWindowing;
		}

		public void setCountAllColumns(boolean countAllCols) {
			countAllColumns = countAllCols;
		}

		public boolean getCountAllColumns() {
			return countAllColumns;
		}

		private void setCountDistinct(boolean countDistinct) {
			this.countDistinct = countDistinct;
		}

		private boolean isWindowingDistinct() {
			return isWindowing && countDistinct;
		}

		/** class for storing count value. */
		@AggregationType(estimable = true)
		static class CountAgg extends AbstractAggregationBuffer {
			HashSet<ObjectInspectorObject> uniqueObjects; // Unique rows
			long value;

			@Override
			public int estimate() {
				return JavaDataModel.PRIMITIVES2;
			}
		}

		@Override
		public AggregationBuffer getNewAggregationBuffer() throws HiveException {
			CountAgg buffer = new CountAgg();
			reset(buffer);
			return buffer;
		}

		@Override
		public void reset(AggregationBuffer agg) throws HiveException {
			((CountAgg) agg).value = 0;
			((CountAgg) agg).uniqueObjects = null;
		}

		@Override
		public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
			// parameters == null means the input table/split is empty
			if (parameters == null) {
				return;
			}
			if (countAllColumns) {
				assert parameters.length == 0;
				((CountAgg) agg).value++;
			} else {
				boolean countThisRow = true;
				for (Object nextParam : parameters) {
					if (nextParam == null) {
						countThisRow = false;
						break;
					}
				}

				// Skip the counting if the values are the same for windowing COUNT(DISTINCT)
				// case
				if (countThisRow && isWindowingDistinct()) {
					if (((CountAgg) agg).uniqueObjects == null) {
						((CountAgg) agg).uniqueObjects = new HashSet<ObjectInspectorObject>();
					}
					Set<ObjectInspectorObject> uniqueObjs = ((CountAgg) agg).uniqueObjects;

					ObjectInspectorObject obj = new ObjectInspectorObject(ObjectInspectorUtils
							.copyToStandardObject(parameters, inputOI, ObjectInspectorCopyOption.JAVA), outputOI);
					boolean inserted = uniqueObjs.add(obj);
					if (!inserted) {
						countThisRow = false;
					}
				}

				if (countThisRow) {
					((CountAgg) agg).value++;
				}
			}
		}

		@Override
		public void merge(AggregationBuffer agg, Object partial) throws HiveException {
			if (partial != null) {
				CountAgg countAgg = (CountAgg) agg;

				if (isWindowingDistinct()) {
					throw new HiveException("Distinct windowing UDAF doesn't support merge and terminatePartial");
				} else {
					long p = partialCountAggOI.get(partial);
					countAgg.value += p;
				}
			}
		}

		@Override
		public Object terminate(AggregationBuffer agg) throws HiveException {
			result.set(((CountAgg) agg).value);
			return result;
		}

		@Override
		public Object terminatePartial(AggregationBuffer agg) throws HiveException {
			if (isWindowingDistinct()) {
				throw new HiveException("Distinct windowing UDAF doesn't support merge and terminatePartial");
			} else {
				return terminate(agg);
			}
		}
	}
}