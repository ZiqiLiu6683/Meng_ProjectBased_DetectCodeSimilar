package com.ziqi.codesim.region.dynamic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Dynamic equivalence check by I/O sampling: reflectively runs two compiled methods on the SAME
 * random integer inputs and compares outputs. This catches behaviourally-equivalent, structurally
 * different (Type-4) methods that SMT cannot prove -- loops, nonlinear arithmetic -- at the cost of
 * being EVIDENCE, not proof: {@link DynamicVerdict#LIKELY_EQUIVALENT} means "agreed on every sampled
 * input", never "equal for all inputs".
 *
 * <p>First slice: methods whose parameters and return are all {@code int}. Each invocation runs
 * under a timeout so an input-dependent long/infinite loop cannot hang the caller (it becomes
 * {@link DynamicVerdict#UNKNOWN}).
 */
public final class DynamicEquivalenceChecker {

    private final int samples;
    private final int inputBound;
    private final long timeoutMillis;

    public DynamicEquivalenceChecker() {
        this(64, 128, 500);
    }

    public DynamicEquivalenceChecker(int samples, int inputBound, long timeoutMillis) {
        this.samples = samples;
        this.inputBound = inputBound;
        this.timeoutMillis = timeoutMillis;
    }

    public DynamicVerdict check(Path leftClasses, String leftClass, String leftMethod,
                                Path rightClasses, String rightClass, String rightMethod) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (URLClassLoader leftLoader = loader(leftClasses);
             URLClassLoader rightLoader = loader(rightClasses)) {
            Method left = intMethod(leftLoader, leftClass, leftMethod);
            Method right = intMethod(rightLoader, rightClass, rightMethod);
            if (left == null || right == null
                    || left.getParameterCount() != right.getParameterCount()) {
                return DynamicVerdict.UNSUPPORTED;
            }
            Object leftInstance = instance(left);
            Object rightInstance = instance(right);
            if (leftInstance == NO_INSTANCE || rightInstance == NO_INSTANCE) {
                return DynamicVerdict.UNKNOWN;
            }

            Random random = new Random(0x5EED);
            int arity = left.getParameterCount();
            for (int s = 0; s < samples; s++) {
                Object[] args = randomIntArgs(arity, random);
                Integer leftResult = invoke(executor, left, leftInstance, args);
                Integer rightResult = invoke(executor, right, rightInstance, args);
                if (leftResult == null || rightResult == null) {
                    return DynamicVerdict.UNKNOWN;
                }
                if (!leftResult.equals(rightResult)) {
                    return DynamicVerdict.DIFFERENT;
                }
            }
            return DynamicVerdict.LIKELY_EQUIVALENT;
        } catch (Exception ex) {
            return DynamicVerdict.UNKNOWN;
        } finally {
            executor.shutdownNow();
        }
    }

    private static URLClassLoader loader(Path classesDir) throws Exception {
        URL url = classesDir.toUri().toURL();
        return new URLClassLoader(new URL[]{url}, ClassLoader.getPlatformClassLoader());
    }

    private static Method intMethod(URLClassLoader loader, String className, String methodName) {
        try {
            Class<?> clazz = loader.loadClass(className);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getReturnType() != int.class) {
                    continue;
                }
                boolean allInt = true;
                for (Class<?> parameterType : method.getParameterTypes()) {
                    if (parameterType != int.class) {
                        allInt = false;
                        break;
                    }
                }
                if (allInt) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable ignored) {
            // Class not loadable / linkage error -> unsupported.
        }
        return null;
    }

    private static final Object NO_INSTANCE = new Object();

    private static Object instance(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        try {
            var constructor = method.getDeclaringClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable ex) {
            return NO_INSTANCE;
        }
    }

    private Object[] randomIntArgs(int arity, Random random) {
        Object[] args = new Object[arity];
        for (int i = 0; i < arity; i++) {
            args[i] = random.nextInt(2 * inputBound + 1) - inputBound;
        }
        return args;
    }

    private Integer invoke(ExecutorService executor, Method method, Object instance, Object[] args) {
        Future<Object> future = executor.submit(() -> method.invoke(instance, args));
        try {
            return (Integer) future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Throwable ex) {
            future.cancel(true);
            return null;
        }
    }
}
