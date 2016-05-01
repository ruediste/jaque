package com.trigersoft.jaque.capture;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.trigersoft.jaque.expression.LambdaExpression;

public class LambdaInformationWeaver extends ClassVisitor {

	public LambdaInformationWeaver(ClassVisitor cv) {
		super(Opcodes.ASM5, cv);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
			@Override
			public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
				if (bsm.getTag() == Opcodes.H_INVOKESTATIC
						&& "java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())
						&& "metafactory".equals(bsm.getName())) {
					// we are processing a lamda creation, replace with our own
					// metafactory
					super.visitInvokeDynamicInsn(name, desc,
							new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(LambdaInformationWeaver.class),
									"metafactory",
									"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"),
							bsmArgs);

				} else
					super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
			}

		};
	}

	/**
	 * Custom metafactory to capture lambda information
	 */
	public static CallSite metafactory(MethodHandles.Lookup caller, String invokedName, MethodType invokedType,
			/* argarray: */MethodType samMethodType, MethodHandle implMethod, MethodType instantiatedMethodType)
					throws LambdaConversionException, NoSuchMethodException, IllegalAccessException {

		CallSite result = LambdaMetafactory.metafactory(caller, invokedName, invokedType, samMethodType, implMethod,
				instantiatedMethodType);
		if (CapturingLambda.class.isAssignableFrom(invokedType.returnType())) {
			MethodHandle lambdaHandle = result.dynamicInvoker();
			Member calledMethod = MethodHandles.reflectAs(Member.class, implMethod);
			Class<?> lambdaInterface = lambdaHandle.type().returnType();

			MethodHandle wrapHandle = MethodHandles.publicLookup()
					.findStatic(LambdaInformationWeaver.class, "wrap",
							MethodType.methodType(Object.class, Member.class, Class.class, MethodHandle.class,
									Object[].class))
					.bindTo(calledMethod).bindTo(lambdaInterface).bindTo(lambdaHandle)
					.asCollector(Object[].class, lambdaHandle.type().parameterCount()).asType(lambdaHandle.type());

			result = new ConstantCallSite(wrapHandle);
		}
		return result;
	}

	private static class InfoInvocationHandler implements InvocationHandler {

		private final Member member;
		private final Object lambda;
		private final Object[] capturedArgs;

		public InfoInvocationHandler(Member member, Object lambda, Object[] capturedArgs) {
			this.member = member;
			this.lambda = lambda;
			this.capturedArgs = capturedArgs;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return method.invoke(lambda, args);
		}

		public Member getMember() {
			return member;
		}

		public Object[] getCapturedArgs() {
			return capturedArgs;
		}

	}

	public static Member getMember(CapturingLambda lambda) {
		return ((InfoInvocationHandler) Proxy.getInvocationHandler(lambda)).getMember();
	}

	public static LambdaExpression<?> getLambdaExpression(CapturingLambda lambda) {
		InfoInvocationHandler handler = (InfoInvocationHandler) Proxy.getInvocationHandler(lambda);
		return LambdaExpression.parseLambdaMethod(handler.member, lambda, handler.capturedArgs);
	}

	public static Object wrap(Member member, Class<?> lambdaInterface, MethodHandle lambdaHandle, Object[] args)
			throws Throwable {
		Object lambda = lambdaHandle.asSpreader(Object[].class, args.length).invoke(args);
		return Proxy.newProxyInstance(lambdaInterface.getClassLoader(), new Class<?>[] { lambdaInterface },
				new InfoInvocationHandler(member, lambda, args));
	}
}
