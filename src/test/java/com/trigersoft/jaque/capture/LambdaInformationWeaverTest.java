package com.trigersoft.jaque.capture;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Member;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.trigersoft.jaque.expression.LambdaExpression;

public class LambdaInformationWeaverTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	public interface CaptureSupplier<T> extends Supplier<T>, CapturingLambda {

	}

	private static class WeavingClassLoader extends ClassLoader {
		private Class<?> cls;

		public WeavingClassLoader(Class<?> cls, ClassLoader parent) {
			super(parent);
			this.cls = cls;
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			if (cls.getName().equals(name)) {
				Class<?> result = findLoadedClass(name);
				if (result == null) {
					try (InputStream is = getResourceAsStream(name.replace('.', '/') + ".class")) {
						ClassReader reader = new ClassReader(is);
						ClassWriter cw = new ClassWriter(reader, 0);
						LambdaInformationWeaver weaver = new LambdaInformationWeaver(cw);
						reader.accept(weaver, 0);
						byte[] bb = cw.toByteArray();
						result = defineClass(name, bb, 0, bb.length);
					} catch (IOException e) {
						throw new ClassNotFoundException("error", e);
					}
				}
				return result;
			}
			return super.loadClass(name);
		}
	}

	@SuppressWarnings("unchecked")
	<T> T load(Class<? extends T> cls) throws Exception {
		return (T) new WeavingClassLoader(cls, getClass().getClassLoader()).loadClass(cls.getName()).newInstance();
	}

	public static class A implements Supplier<CaptureSupplier<String>> {

		@Override
		public CaptureSupplier<String> get() {
			return () -> "Hello World";
		}

	}

	@Test
	public void testSimple() throws Exception {
		Supplier<CaptureSupplier<String>> supplier = load(A.class);
		CaptureSupplier<String> lambda = supplier.get();
		assertEquals("Hello World", lambda.get());
		Member member = LambdaInformationWeaver.getMember(lambda);
		LambdaExpression<?> exp = LambdaInformationWeaver.getLambdaExpression(lambda);
		assertEquals(A.class.getName(), member.getDeclaringClass().getName());
	}

	public interface CaptureFunction<T, R> extends Function<T, R>, CapturingLambda {
	}

	public static class B implements Function<String, CaptureFunction<String, String>> {

		@Override
		public CaptureFunction<String, String> apply(String t) {
			return arg -> "hello " + t + " " + arg;
		}

	}

	@Test
	public void testWithParameters() throws Exception {
		Function<String, CaptureFunction<String, String>> function = load(B.class);
		CaptureFunction<String, String> lambda = function.apply("7");
		assertEquals("hello 7 2", lambda.apply("2"));
		Member member = LambdaInformationWeaver.getMember(lambda);
		LambdaExpression<?> exp = LambdaInformationWeaver.getLambdaExpression(lambda);
		assertEquals(B.class.getName(), member.getDeclaringClass().getName());
	}

}