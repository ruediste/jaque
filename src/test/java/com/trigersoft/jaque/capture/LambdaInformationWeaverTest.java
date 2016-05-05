package com.trigersoft.jaque.capture;

import static org.junit.Assert.assertEquals;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.trigersoft.jaque.expression.LambdaExpression;

@RunWith(LambdaRunner.class)
public class LambdaInformationWeaverTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	public interface CaptureSupplier<T> extends Supplier<T>, CapturingLambda {

	}

	@SuppressWarnings("unchecked")
	<T> T load(Class<? extends T> cls) throws Exception {
		return cls.newInstance();
	}

	public static class A implements Supplier<CaptureSupplier<String>> {

		@Override
		public CaptureSupplier<String> get() {
			return () -> "Hello World";
		}

	}

	@Test
	public void testSimple() throws Exception {
		CaptureSupplier<String> lambda = () -> "Hello World";
		assertEquals("Hello World", lambda.get());
		LambdaExpression<?> exp = LambdaInformationWeaver.getLambdaExpression(lambda);
		assertEquals("()->{Hello World}", exp.toString());
	}

	static String method() {
		return "Hello World";
	}

	@Test
	public void testMethodReference() throws Exception {
		CaptureSupplier<String> lambda = LambdaInformationWeaverTest::method;
		assertEquals("Hello World", lambda.get());
		LambdaExpression<?> exp = LambdaInformationWeaver.getLambdaExpression(lambda);
		assertEquals("()->{" + LambdaInformationWeaverTest.class.getName() + ".method()}", exp.toString());
	}

	public interface CaptureFunction<T, R> extends Function<T, R>, CapturingLambda {
	}

	@Test
	public void testWithParameters() throws Exception {
		String t = "7";
		CaptureFunction<String, String> lambda = arg -> "hello " + t + " " + arg;
		assertEquals("hello 7 2", lambda.apply("2"));
		LambdaExpression<?> exp = LambdaInformationWeaver.getLambdaExpression(lambda);
		assertEquals(
				"(java.lang.Object P0)->{java.lang.StringBuilder.<new>(hello ).append(A0).append( ).append(P0).toString()}",
				exp.toString());
	}

	String field = "fieldStr";

	@Test
	public void testWiththisReference() throws Exception {
		String t = "7";
		CaptureFunction<String, String> lambda = arg -> "hello " + t + " " + arg + " " + field;
		assertEquals("hello 7 2 fieldStr", lambda.apply("2"));
		LambdaExpression<?> exp = LambdaInformationWeaver.getLambdaExpression(lambda);
		assertEquals(
				"(java.lang.Object P0)->{java.lang.StringBuilder.<new>(hello ).append(A0).append( ).append(A1).append( ).append(this.field()).toString()}",
				exp.toString());
	}

	public static class Api {
		private Supplier<String> supplier;

		public void invoke(@Capture Supplier<String> supplier) {
			this.supplier = supplier;

		}
	}

	public static class C implements Consumer<Api> {
		@Override
		public void accept(Api api) {
			api.invoke(() -> "Hello World");
		}
	}

	@Test
	public void testCaptureSupplier() throws Exception {
		Api api = new Api();
		Consumer<Api> c = load(C.class);
		c.accept(api);
		LambdaExpression<?> lamda = LambdaInformationWeaver.getLambdaExpression(api.supplier);
		assertEquals("()->{Hello World}", lamda.toString());
	}

	public static class D implements Consumer<Api> {
		@Override
		public void accept(Api api) {
			int i = 1;
			api.invoke(() -> "Hello World " + i);
		}
	}

	@Test
	public void testCaptureSupplierWithCaptured() throws Exception {
		Api api = new Api();
		Consumer<Api> c = load(D.class);
		c.accept(api);
		LambdaExpression<?> lamda = LambdaInformationWeaver.getLambdaExpression(api.supplier);
		assertEquals("()->{java.lang.StringBuilder.<new>(Hello World ).append(A0).toString()}", lamda.toString());
	}

}