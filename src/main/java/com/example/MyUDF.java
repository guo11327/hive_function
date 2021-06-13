package com.example;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;


@Description(
	name="MyUDF",
	value="returns x * 2 ",
	extended="SELECT MyUDF(x);"
	)

public class MyUDF extends GenericUDF {
  
	PrimitiveObjectInspector inputOI;
	PrimitiveObjectInspector outputOI;

	@Override
	public ObjectInspector  initialize(ObjectInspector[] args) throws UDFArgumentException {
		// This UDF accepts one argument
		assert (args.length == 1);
		// The first argument is a primitive type
		assert(args[0].getCategory() == Category.PRIMITIVE);

		inputOI  = (PrimitiveObjectInspector)args[0];
		/* We only support INTEGER type */
		assert(inputOI.getPrimitiveCategory() == PrimitiveCategory.INT);

		/* And we'll return a type int, so let's return the corresponding object inspector */
		outputOI = PrimitiveObjectInspectorFactory.javaIntObjectInspector;
		return outputOI;
	}

	@Override
	public String getDisplayString(String[] args) {
		return "Here, write a udf description";
	}

	@Override
	public Object evaluate(DeferredObject[] args) throws HiveException {
		if (args.length != 1) return null;

		// Access the deferred value. Hive passes the arguments as "deferred" objects 
		// to avoid some computations if we don't actually need some of the values
		Object oin = args[0].get();

		if (oin == null) return null;

		int value = (Integer) inputOI.getPrimitiveJavaObject(oin); 

		int output = value * 2;

		return output;
	}

}