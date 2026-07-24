package com.ziqi.codesim.region.dynamic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Dynamic equivalence check by I/O sampling: reflectively runs two compiled methods on the SAME
 * random inputs and compares outputs. Catches behaviourally-equivalent, structurally different
 * (Type-4) methods that SMT cannot prove (loops, recursion, nonlinear arithmetic), at the cost of
 * being EVIDENCE, not proof: {@link DynamicVerdict#LIKELY_EQUIVALENT} means "agreed on every sampled
 * input", never "equal for all inputs".
 *
 * <p>Supported signatures: parameters and return of primitive types (int/long/short/byte/char/
 * boolean/double/float), {@link String}, and 1-D arrays of those. Anything else -&gt;
 * {@link DynamicVerdict#UNSUPPORTED}. Each invocation runs under a timeout so an input-dependent long
 * loop cannot hang the caller (it becomes {@link DynamicVerdict#UNKNOWN}). Array arguments are copied
 * before each call so a mutating method (e.g. an in-place sort) cannot corrupt the shared inputs.
 */
public final class DynamicEquivalenceChecker {

    private static final Object FAILED = new Object(); // invocation failure sentinel (distinct from a null return)

    private final int samples;
    private final int inputBound;
    private final int maxArrayLength;
    private final long timeoutMillis;

    public DynamicEquivalenceChecker() {
        this(64, 128, 8, 500);
    }

    public DynamicEquivalenceChecker(int samples, int inputBound, int maxArrayLength, long timeoutMillis) {
        this.samples = samples;
        this.inputBound = inputBound;
        this.maxArrayLength = maxArrayLength;
        this.timeoutMillis = timeoutMillis;
    }

    public DynamicVerdict check(Path leftClasses, String leftClass, String leftMethod,
                                Path rightClasses, String rightClass, String rightMethod) {
        return check(leftClasses, List.of(), leftClass, leftMethod, null,
                rightClasses, List.of(), rightClass, rightMethod, null);
    }

    /**
     * Descriptor-exact variant used by the production pipeline. The raw WALA signatures preserve
     * overload identity; support classpaths let project-context classes link at runtime.
     */
    public DynamicVerdict check(Path leftClasses, List<Path> leftSupportClasspath,
                                String leftClass, String leftMethod, String leftRawSignature,
                                Path rightClasses, List<Path> rightSupportClasspath,
                                String rightClass, String rightMethod, String rightRawSignature) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (URLClassLoader leftLoader = loader(leftClasses, leftSupportClasspath);
             URLClassLoader rightLoader = loader(rightClasses, rightSupportClasspath)) {
            Method left = supportedMethod(leftLoader, leftClass, leftMethod, leftRawSignature);
            Method right = supportedMethod(rightLoader, rightClass, rightMethod, rightRawSignature);
            if (left == null || right == null || !sameSignature(left, right)) {
                return DynamicVerdict.UNSUPPORTED;
            }
            if (!hasObservableOutput(left)) {
                // void return AND no array argument: the method's only effect is on fields/state we
                // cannot observe, so we cannot tell two such methods apart -- do not guess.
                return DynamicVerdict.UNSUPPORTED;
            }
            Object leftInstance = instance(left);
            Object rightInstance = instance(right);
            if (leftInstance == NO_INSTANCE || rightInstance == NO_INSTANCE) {
                return DynamicVerdict.UNKNOWN;
            }

            Random random = new Random(0x5EED);
            Class<?>[] parameterTypes = left.getParameterTypes();
            for (int s = 0; s < samples; s++) {
                Object[] base = randomArguments(parameterTypes, random);
                Object[] leftArgs = copyArguments(base);
                Object[] rightArgs = copyArguments(base);
                Object leftResult = invoke(executor, left, leftInstance, leftArgs);
                Object rightResult = invoke(executor, right, rightInstance, rightArgs);
                if (leftResult == FAILED || rightResult == FAILED) {
                    return DynamicVerdict.UNKNOWN;
                }
                // Behaviour = the return value AND the final state of the (copied) arguments, so a
                // void method whose effect is mutating an array (e.g. an in-place sort) is judged too.
                if (!Objects.deepEquals(leftResult, rightResult)
                        || !argumentsEqual(leftArgs, rightArgs)) {
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

    private static URLClassLoader loader(Path classesDir, List<Path> supportClasspath) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(classesDir.toUri().toURL());
        for (Path entry : supportClasspath) {
            urls.add(entry.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    /** Exact overload when a raw signature is provided; legacy name-only callers retain first-match behavior. */
    private static Method supportedMethod(URLClassLoader loader, String className, String methodName,
                                          String rawSignature) {
        try {
            Class<?> clazz = loader.loadClass(className);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)
                        || (rawSignature != null && !rawSignature.isBlank()
                        && !rawMethodKey(method).equals(rawMethodKey(rawSignature)))
                        || !(method.getReturnType() == void.class || isSupported(method.getReturnType()))) {
                    continue;
                }
                boolean allSupported = true;
                for (Class<?> parameterType : method.getParameterTypes()) {
                    if (!isSupported(parameterType)) {
                        allSupported = false;
                        break;
                    }
                }
                if (allSupported) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable ignored) {
            // Class not loadable / linkage error -> unsupported.
        }
        return null;
    }

    private static String rawMethodKey(Method method) {
        StringBuilder key = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) {
            key.append(descriptor(parameter));
        }
        return key.append(')').append(descriptor(method.getReturnType())).toString();
    }

    private static String rawMethodKey(String rawSignature) {
        int paren = rawSignature.indexOf('(');
        if (paren < 0) {
            return rawSignature;
        }
        int dot = rawSignature.lastIndexOf('.', paren);
        return rawSignature.substring(dot < 0 ? 0 : dot + 1);
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (!type.isPrimitive()) {
            return "L" + type.getName().replace('.', '/') + ";";
        }
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        throw new IllegalArgumentException("Unknown primitive: " + type);
    }

    /** True when the method produces something we can compare: a non-void return, or an array we can inspect after the call. */
    private static boolean hasObservableOutput(Method method) {
        if (method.getReturnType() != void.class) {
            return true;
        }
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (parameterType.isArray()) {
                return true;
            }
        }
        return false;
    }

    /** Compare the two sides' arguments after the call (side effects), element-wise with deep equality. */
    private static boolean argumentsEqual(Object[] left, Object[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!Objects.deepEquals(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSignature(Method left, Method right) {
        if (left.getReturnType() != right.getReturnType()
                || left.getParameterCount() != right.getParameterCount()) {
            return false;
        }
        Class<?>[] a = left.getParameterTypes();
        Class<?>[] b = right.getParameterTypes();
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSupported(Class<?> type) {
        return type == int.class || type == long.class || type == short.class || type == byte.class
                || type == char.class || type == boolean.class || type == double.class || type == float.class
                || type == String.class
                || type == int[].class || type == long[].class || type == short[].class || type == byte[].class
                || type == char[].class || type == boolean[].class || type == double[].class
                || type == float[].class || type == String[].class;
    }

    private Object[] randomArguments(Class<?>[] parameterTypes, Random random) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = randomValue(parameterTypes[i], random);
        }
        return args;
    }

    private Object randomValue(Class<?> type, Random random) {
        if (type == int.class) {
            return randomInt(random);
        }
        if (type == long.class) {
            return (long) randomInt(random);
        }
        if (type == short.class) {
            return (short) randomInt(random);
        }
        if (type == byte.class) {
            return (byte) (random.nextInt(256) - 128);
        }
        if (type == char.class) {
            return (char) ('a' + random.nextInt(26));
        }
        if (type == boolean.class) {
            return random.nextBoolean();
        }
        if (type == double.class) {
            return (double) randomInt(random);
        }
        if (type == float.class) {
            return (float) randomInt(random);
        }
        if (type == String.class) {
            return randomString(random);
        }
        if (type == int[].class) {
            int[] a = new int[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = randomInt(random);
            }
            return a;
        }
        if (type == long[].class) {
            long[] a = new long[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = randomInt(random);
            }
            return a;
        }
        if (type == short[].class) {
            short[] a = new short[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = (short) randomInt(random);
            }
            return a;
        }
        if (type == byte[].class) {
            byte[] a = new byte[random.nextInt(maxArrayLength + 1)];
            random.nextBytes(a);
            return a;
        }
        if (type == char[].class) {
            char[] a = new char[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = (char) ('a' + random.nextInt(26));
            }
            return a;
        }
        if (type == boolean[].class) {
            boolean[] a = new boolean[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = random.nextBoolean();
            }
            return a;
        }
        if (type == double[].class) {
            double[] a = new double[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = randomInt(random);
            }
            return a;
        }
        if (type == float[].class) {
            float[] a = new float[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = randomInt(random);
            }
            return a;
        }
        if (type == String[].class) {
            String[] a = new String[random.nextInt(maxArrayLength + 1)];
            for (int i = 0; i < a.length; i++) {
                a[i] = randomString(random);
            }
            return a;
        }
        return null; // unreachable: isSupported gated the signature
    }

    private int randomInt(Random random) {
        return random.nextInt(2 * inputBound + 1) - inputBound;
    }

    private String randomString(Random random) {
        int length = random.nextInt(maxArrayLength + 1);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        return builder.toString();
    }

    /** Fresh copies of any array arguments, so an in-place-mutating method cannot corrupt shared inputs. */
    private static Object[] copyArguments(Object[] args) {
        Object[] copy = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            copy[i] = copyValue(args[i]);
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof int[] a) {
            return a.clone();
        }
        if (value instanceof long[] a) {
            return a.clone();
        }
        if (value instanceof short[] a) {
            return a.clone();
        }
        if (value instanceof byte[] a) {
            return a.clone();
        }
        if (value instanceof char[] a) {
            return a.clone();
        }
        if (value instanceof boolean[] a) {
            return a.clone();
        }
        if (value instanceof double[] a) {
            return a.clone();
        }
        if (value instanceof float[] a) {
            return a.clone();
        }
        if (value instanceof String[] a) {
            return a.clone();
        }
        return value; // primitives (boxed) and String are immutable
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

    private Object invoke(ExecutorService executor, Method method, Object instance, Object[] args) {
        Future<Object> future = executor.submit(() -> method.invoke(instance, args));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Throwable ex) {
            future.cancel(true);
            return FAILED;
        }
    }
}
