package com.ziqi.codesim.next.semantic.compilation;

import com.ziqi.codesim.next.semantic.SourceAnalysisInput;
import com.ziqi.codesim.semantic.backend.AnalysisException;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles one source file under a frozen Java-17 contract, with immutable content-addressed reuse.
 *
 * <p>Resolution order: real project/classpath context, plain standalone javac, diagnostic-driven
 * dependency stubs, then an {@link AnalysisException} for the caller's source-only fallback. Stubs
 * are kept in a separate class directory so WALA can load them as context rather than Application
 * clone candidates.
 */
public final class JavaCompilationCoordinator {
    public static final int JAVA_RELEASE = 17;
    private static final int MAX_STUB_ROUNDS = 5;
    private static final String CACHE_VERSION = "4";
    private static final String IMPLEMENTATION_SHA256 = implementationFingerprint();
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|strictfp\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final ConcurrentMap<String, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Path, String> CLASSPATH_FINGERPRINTS = new ConcurrentHashMap<>();

    private final Path cacheRoot;

    public JavaCompilationCoordinator() {
        this(defaultCacheRoot());
    }

    public JavaCompilationCoordinator(Path cacheRoot) {
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
    }

    public CompilationArtifact compile(SourceAnalysisInput input) throws AnalysisException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AnalysisException("WALA next pipeline requires a JDK compiler");
        }
        List<Path> context = resolveContext(input);
        String cacheKey = cacheKey(input, context);
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        jvmLock.lock();
        try {
            Files.createDirectories(cacheRoot.resolve("entries"));
            Files.createDirectories(cacheRoot.resolve("locks"));
            Files.createDirectories(cacheRoot.resolve("work"));
            Path lockPath = cacheRoot.resolve("locks").resolve(cacheKey + ".lock");
            try (FileChannel channel = FileChannel.open(lockPath,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                CompilationArtifact cached = readCached(cacheKey, context);
                if (cached != null) {
                    return cached;
                }
                return compileAndPublish(compiler, input, context, cacheKey);
            }
        } catch (IOException ex) {
            throw new AnalysisException("Failed to manage Java compilation cache", ex);
        } finally {
            jvmLock.unlock();
            if (!jvmLock.hasQueuedThreads()) {
                JVM_LOCKS.remove(cacheKey, jvmLock);
            }
        }
    }

    private CompilationArtifact compileAndPublish(JavaCompiler compiler, SourceAnalysisInput input,
                                                  List<Path> context, String cacheKey)
            throws AnalysisException {
        Path work = cacheRoot.resolve("work").resolve(cacheKey + "-" + UUID.randomUUID());
        try {
            Path sourceDirectory = work.resolve("source");
            Path classes = work.resolve("classes");
            Files.createDirectories(sourceDirectory);
            Path sourceFile = sourceDirectory.resolve(publicTypeFileName(input.source(), input.fileName()));
            Files.writeString(sourceFile, input.source(), StandardCharsets.UTF_8);

            CompileAttempt exact = compile(compiler, List.of(sourceFile), classes, context);
            CompilationArtifact.CompilationMode mode = !context.isEmpty()
                    ? CompilationArtifact.CompilationMode.PROJECT_CONTEXT
                    : CompilationArtifact.CompilationMode.STANDALONE;
            int stubCount = 0;
            String initialDiagnostics = summarize(exact.diagnostics());
            String diagnostics = initialDiagnostics;
            List<Path> support = new ArrayList<>(context);

            if (!exact.success()) {
                if (!input.allowStubs()) {
                    AnalysisException failure = compilationFailure(sourceFile, exact.diagnostics());
                    writeFailureManifest(work, diagnostics);
                    publish(work, cacheKey);
                    throw failure;
                }
                StubGenerator generator = new StubGenerator();
                CompileAttempt current = exact;
                boolean compiled = false;
                Path stubSources = work.resolve("stub-sources");
                Path stubClasses = work.resolve("stub-classes");
                for (int round = 0; round < MAX_STUB_ROUNDS; round++) {
                    int changes = generator.update(input.source(), current.diagnostics());
                    if (changes == 0) {
                        break;
                    }
                    recreateDirectory(stubSources);
                    recreateDirectory(stubClasses);
                    stubCount = generator.writeSources(stubSources);
                    List<Path> generatedSources = generator.sourceFiles(stubSources);
                    CompileAttempt stubs = compile(compiler, generatedSources, stubClasses, context);
                    if (!stubs.success()) {
                        diagnostics = summarize(stubs.diagnostics());
                        break;
                    }
                    recreateDirectory(classes);
                    List<Path> targetClasspath = new ArrayList<>(context);
                    targetClasspath.add(stubClasses);
                    current = compile(compiler, List.of(sourceFile), classes, targetClasspath);
                    if (current.success()) {
                        support.add(stubClasses);
                        mode = CompilationArtifact.CompilationMode.STUBBED;
                        // A successful retry has no errors of its own. Preserve the exact compile
                        // failure that justified generated context for paper provenance.
                        diagnostics = initialDiagnostics;
                        compiled = true;
                        break;
                    }
                    diagnostics = summarize(current.diagnostics());
                }
                if (!compiled) {
                    AnalysisException failure = compilationFailure(sourceFile, current.diagnostics());
                    writeFailureManifest(work, diagnostics);
                    publish(work, cacheKey);
                    throw failure;
                }
            }

            writeManifest(work, mode, stubCount, diagnostics);
            Path entry = publish(work, cacheKey);
            List<Path> publishedSupport = new ArrayList<>(context);
            if (mode == CompilationArtifact.CompilationMode.STUBBED) {
                publishedSupport.add(entry.resolve("stub-classes"));
            }
            return new CompilationArtifact(entry.resolve("classes"), publishedSupport, mode,
                    cacheKey, false, stubCount, diagnostics);
        } catch (IOException ex) {
            throw new AnalysisException("Failed to compile source for WALA next pipeline", ex);
        } finally {
            if (Files.exists(work)) {
                try {
                    deleteTree(work);
                } catch (IOException ignored) {
                    // A temporary cleanup failure must not replace the compilation result.
                }
            }
        }
    }

    private CompilationArtifact readCached(String cacheKey, List<Path> context)
            throws IOException, AnalysisException {
        Path entry = cacheRoot.resolve("entries").resolve(cacheKey);
        Path manifest = entry.resolve("compilation.properties");
        Path classes = entry.resolve("classes");
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!CACHE_VERSION.equals(properties.getProperty("cacheVersion"))) {
            return null;
        }
        if (!IMPLEMENTATION_SHA256.equals(
                properties.getProperty("compilerImplementationSha256"))) {
            return null;
        }
        if ("failed".equals(properties.getProperty("status"))) {
            throw new AnalysisException("Cached Java 17 compilation failure ["
                    + properties.getProperty("diagnosticSummary", "") + "]");
        }
        if (!containsClassFile(classes)) {
            return null;
        }
        CompilationArtifact.CompilationMode mode = CompilationArtifact.CompilationMode.valueOf(
                properties.getProperty("mode"));
        List<Path> support = new ArrayList<>(context);
        if (mode == CompilationArtifact.CompilationMode.STUBBED) {
            Path stubs = entry.resolve("stub-classes");
            if (!containsClassFile(stubs)) {
                return null;
            }
            support.add(stubs);
        }
        return new CompilationArtifact(classes, support, mode, cacheKey, true,
                Integer.parseInt(properties.getProperty("stubCount", "0")),
                properties.getProperty("diagnosticSummary", ""));
    }

    private static CompileAttempt compile(JavaCompiler compiler, List<Path> sourceFiles,
                                          Path output, List<Path> classpath) throws IOException {
        Files.createDirectories(output);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                collector, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            List<String> options = new ArrayList<>(List.of(
                    "--release", String.valueOf(JAVA_RELEASE),
                    "-g",
                    "-proc:none",
                    "-d", output.toString()));
            if (!classpath.isEmpty()) {
                options.add("-classpath");
                options.add(String.join(java.io.File.pathSeparator,
                        classpath.stream().map(Path::toString).toList()));
            }
            boolean success = Boolean.TRUE.equals(compiler.getTask(
                    null, fileManager, collector, options, null, units).call());
            return new CompileAttempt(success, collector.getDiagnostics().stream()
                    .map(CompilerDiagnostic::from)
                    .toList());
        }
    }

    private List<Path> resolveContext(SourceAnalysisInput input) throws AnalysisException {
        LinkedHashSet<Path> entries = new LinkedHashSet<>();
        for (Path path : input.classpath()) {
            if (!Files.exists(path)) {
                throw new AnalysisException("Classpath entry does not exist: " + path);
            }
            entries.add(path);
        }
        Path root = input.projectRoot();
        if (root != null) {
            if (!Files.isDirectory(root)) {
                throw new AnalysisException("Project root is not a directory: " + root);
            }
            for (String relative : List.of(
                    "target/classes", "build/classes/java/main", "build/resources/main",
                    "out/production", "bin")) {
                Path candidate = root.resolve(relative);
                if (Files.isDirectory(candidate)) {
                    entries.add(candidate.toAbsolutePath().normalize());
                }
            }
            for (String relative : List.of("lib", "libs", "target/dependency", "build/libs")) {
                Path jars = root.resolve(relative);
                if (Files.isDirectory(jars)) {
                    try (var stream = Files.walk(jars, 3)) {
                        stream.filter(Files::isRegularFile)
                                .filter(path -> path.toString().endsWith(".jar"))
                                .sorted()
                                .map(path -> path.toAbsolutePath().normalize())
                                .forEach(entries::add);
                    } catch (IOException ex) {
                        throw new AnalysisException("Failed to discover project classpath: " + root, ex);
                    }
                }
            }
        }
        return List.copyOf(entries);
    }

    private String cacheKey(SourceAnalysisInput input, List<Path> context) throws AnalysisException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "cache=" + CACHE_VERSION);
            update(digest, "stubGenerator=" + StubGenerator.VERSION);
            update(digest, "compilerImplementation=" + IMPLEMENTATION_SHA256);
            update(digest, "release=" + JAVA_RELEASE);
            update(digest, "runtime=" + Runtime.version());
            update(digest, "file=" + input.fileName());
            update(digest, "allowStubs=" + input.allowStubs());
            update(digest, "requestedContext=" + input.hasRequestedContext());
            update(digest, "source=" + input.source());
            for (Path path : context) {
                update(digest, "classpath=" + classpathFingerprint(path));
            }
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new AnalysisException("Failed to fingerprint Java compilation input", ex);
        }
    }

    private static String classpathFingerprint(Path path) {
        return CLASSPATH_FINGERPRINTS.computeIfAbsent(path, JavaCompilationCoordinator::scanFingerprint);
    }

    private static String scanFingerprint(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, path.toAbsolutePath().normalize().toString());
            if (Files.isRegularFile(path)) {
                update(digest, Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis());
            } else {
                try (var stream = Files.walk(path)) {
                    for (Path file : stream.filter(Files::isRegularFile)
                            .filter(candidate -> candidate.toString().endsWith(".class")
                                    || candidate.toString().endsWith(".jar"))
                            .sorted(Comparator.comparing(Path::toString)).toList()) {
                        update(digest, path.relativize(file) + ":" + Files.size(file) + ":"
                                + Files.getLastModifiedTime(file).toMillis());
                    }
                }
            }
            return path + "#" + hex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fingerprint classpath entry: " + path, ex);
        }
    }

    private static void writeManifest(Path work, CompilationArtifact.CompilationMode mode,
                                      int stubCount, String diagnostics) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("cacheVersion", CACHE_VERSION);
        properties.setProperty("compilerImplementationSha256", IMPLEMENTATION_SHA256);
        properties.setProperty("status", "success");
        properties.setProperty("stubGeneratorVersion", StubGenerator.VERSION);
        properties.setProperty("javaRelease", String.valueOf(JAVA_RELEASE));
        properties.setProperty("runtimeVersion", Runtime.version().toString());
        properties.setProperty("mode", mode.name());
        properties.setProperty("stubCount", String.valueOf(stubCount));
        properties.setProperty("diagnosticSummary", diagnostics);
        try (var writer = Files.newBufferedWriter(work.resolve("compilation.properties"),
                StandardCharsets.UTF_8)) {
            properties.store(writer, "CodeSim immutable compilation artifact");
        }
    }

    private static void writeFailureManifest(Path work, String diagnostics) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("cacheVersion", CACHE_VERSION);
        properties.setProperty("compilerImplementationSha256", IMPLEMENTATION_SHA256);
        properties.setProperty("status", "failed");
        properties.setProperty("stubGeneratorVersion", StubGenerator.VERSION);
        properties.setProperty("javaRelease", String.valueOf(JAVA_RELEASE));
        properties.setProperty("runtimeVersion", Runtime.version().toString());
        properties.setProperty("diagnosticSummary", diagnostics);
        try (var writer = Files.newBufferedWriter(work.resolve("compilation.properties"),
                StandardCharsets.UTF_8)) {
            properties.store(writer, "CodeSim immutable negative compilation artifact");
        }
    }

    private Path publish(Path work, String cacheKey) throws IOException {
        Path entry = cacheRoot.resolve("entries").resolve(cacheKey);
        if (Files.exists(entry)) {
            deleteTree(entry);
        }
        moveDirectory(work, entry);
        return entry;
    }

    private static AnalysisException compilationFailure(Path source,
                                                        List<CompilerDiagnostic> diagnostics) {
        return new AnalysisException("Failed to compile source for WALA next pipeline: " + source
                + (diagnostics.isEmpty() ? "" : " [" + summarize(diagnostics) + "]"));
    }

    private static String summarize(List<CompilerDiagnostic> diagnostics) {
        String summary = diagnostics.stream()
                .filter(diagnostic -> diagnostic.kind() == javax.tools.Diagnostic.Kind.ERROR)
                .limit(5)
                .map(diagnostic -> "L" + diagnostic.line() + " " + diagnostic.code() + ": "
                        + diagnostic.message().replaceAll("\\s+", " ").strip())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
        return summary.length() <= 1000 ? summary : summary.substring(0, 1000);
    }

    private static String publicTypeFileName(String source, String fallback) {
        Matcher matcher = PUBLIC_TYPE.matcher(source);
        return matcher.find() ? matcher.group(1) + ".java" : Path.of(fallback).getFileName().toString();
    }

    private static Path defaultCacheRoot() {
        String configured = System.getProperty("codesim.compileCache", "").strip();
        return configured.isEmpty()
                ? Path.of(System.getProperty("java.io.tmpdir"), "codesim-compile-cache-v4")
                : Path.of(configured);
    }

    /**
     * Hash the actual compiler/stub bytecode rather than trusting a manually maintained version.
     * This prevents a changed implementation from accepting immutable positive or negative cache
     * entries produced by older class files.
     */
    static String implementationFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Class<?> type : List.of(JavaCompilationCoordinator.class, StubGenerator.class)) {
                String resource = "/" + type.getName().replace('.', '/') + ".class";
                update(digest, resource);
                try (InputStream stream = type.getResourceAsStream(resource)) {
                    if (stream == null) {
                        throw new IOException("Missing runtime class resource: " + resource);
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = stream.read(buffer)) >= 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static boolean containsClassFile(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var stream = Files.walk(directory)) {
            return stream.anyMatch(path -> path.toString().endsWith(".class"));
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            deleteTree(directory);
        }
        Files.createDirectories(directory);
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    private record CompileAttempt(boolean success, List<CompilerDiagnostic> diagnostics) {
    }
}
