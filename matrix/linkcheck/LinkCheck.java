import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Phase 12C static link check (matrix/matrix.gradle, task linkCheck). Resolves everything the mod jar and the GameTest
 * jar use from Minecraft, Fabric, YACL and Mod Menu against another game version's runtime classpath: classes, fields
 * and methods in bytecode, {@code @Mixin} targets, injector {@code method =} names and {@code @At} targets. Seconds,
 * where a GameTest run takes minutes and a test that fails in its own setup hides every mod break behind it.
 *
 * <p>It proves linkage only. Behaviour changes, data formats and a mixin whose target still exists but means something
 * else are the GameTests' job. Mixin classes no {@code *.mixins.json} in the checked jars lists are skipped: they are
 * gated to the other band ({@code <% if (band == 'A') %>}) and never apply.
 *
 * <pre>LinkCheck &lt;classpath file&gt; &lt;report file&gt; &lt;mod jar&gt; &lt;gametest jar&gt;</pre>
 * Exit 0 = every reference resolves; 1 = problems (listed in the report); 2 = bad arguments.
 */
public final class LinkCheck {
    private record Info(String superName, String[] interfaces, Set<String> fields, Set<String> methods) {}

    private static final Pattern PACKAGE = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MIXIN_LISTS = Pattern.compile("\"(?:mixins|client|server)\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    private final Map<String, Info> index = new HashMap<>();
    private final Set<String> ours = new HashSet<>();
    private final Set<String> listedMixins = new HashSet<>();
    private final Map<String, Set<String>> problems = new TreeMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("usage: LinkCheck <classpath file> <report file> <mod jar> <gametest jar>");
            System.exit(2);
        }
        LinkCheck check = new LinkCheck();
        Path modJar = Path.of(args[2]).toAbsolutePath().normalize();
        Path testJar = Path.of(args[3]).toAbsolutePath().normalize();
        int indexed = 0;
        for (String raw : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String entry = raw.trim();
            if (!entry.endsWith(".jar")) continue;
            Path jar = Path.of(entry).toAbsolutePath().normalize();
            if (jar.equals(modJar) || jar.equals(testJar)) continue;
            check.index(jar);
            indexed++;
        }
        if (indexed == 0) {
            System.err.println("LinkCheck: the classpath file " + args[0] + " lists no jars to check against");
            System.exit(2);
        }
        Map<String, byte[]> mod = check.read(modJar);
        Map<String, byte[]> tests = check.read(testJar);
        int skipped = check.run("MOD", mod) + check.run("TEST", tests);

        List<String> report = new ArrayList<>();
        check.problems.forEach((problem, users) -> {
            report.add(problem);
            report.add("    <- " + String.join(", ", users));
        });
        report.add(check.problems.size() + " problem(s); " + (mod.size() + tests.size()) + " classes checked against "
                + indexed + " jars; " + skipped + " unlisted mixin(s) skipped");
        Files.write(Path.of(args[1]), report, StandardCharsets.UTF_8);
        report.forEach(System.out::println);
        System.exit(check.problems.isEmpty() ? 0 : 1);
    }

    private static boolean external(String owner) {
        return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/") || owner.startsWith("net/fabricmc/")
                || owner.startsWith("dev/isxander/") || owner.startsWith("com/terraformersmc/");
    }

    private interface Sink {
        void accept(String name, byte[] bytes) throws IOException;
    }

    private static void eachClass(Path jar, Sink sink) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String name = e.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                    sink.accept(name.substring(0, name.length() - ".class".length()), zip.readAllBytes());
                }
            }
        }
    }

    private void index(Path jar) throws IOException {
        eachClass(jar, (name, bytes) -> index.putIfAbsent(name, info(bytes)));
    }

    /** Our classes, plus the mixin classes each {@code *.mixins.json} in the jar lists. */
    private Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> classes = new TreeMap<>();
        eachClass(jar, (name, bytes) -> {
            classes.put(name, bytes);
            ours.add(name);
        });
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (!e.getName().endsWith(".mixins.json")) continue;
                String json = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                Matcher pkg = PACKAGE.matcher(json);
                if (!pkg.find()) continue;
                String prefix = pkg.group(1).replace('.', '/') + "/";
                Matcher lists = MIXIN_LISTS.matcher(json);
                while (lists.find()) {
                    Matcher names = QUOTED.matcher(lists.group(1));
                    while (names.find()) listedMixins.add(prefix + names.group(1).replace('.', '/'));
                }
            }
        }
        return classes;
    }

    private int run(String tag, Map<String, byte[]> classes) {
        int skipped = 0;
        for (var e : classes.entrySet()) {
            if (isMixin(e.getValue()) && !listedMixins.contains(e.getKey())) {
                System.out.println("skipped, in no *.mixins.json of these jars: " + e.getKey());
                skipped++;
                continue;
            }
            check(tag, e.getValue());
        }
        return skipped;
    }

    private static boolean isMixin(byte[] bytes) {
        boolean[] found = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (MIXIN.equals(descriptor)) found[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return found[0];
    }

    private static Info info(byte[] bytes) {
        Set<String> fields = new HashSet<>();
        Set<String> methods = new HashSet<>();
        String[] superName = new String[1];
        String[][] interfaces = new String[1][];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String sig, String sup, String[] itfs) {
                superName[0] = sup;
                interfaces[0] = itfs == null ? new String[0] : itfs;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String sig, Object value) {
                fields.add(name + ":" + descriptor);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] ex) {
                methods.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return new Info(superName[0], interfaces[0], fields, methods);
    }

    /** From the classpath, else the running JDK (a Minecraft class may extend or implement a java.* type). */
    private Info lookup(String name) {
        Info known = index.get(name);
        if (known != null) return known;
        try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (in == null) return null;
            Info jdk = info(in.readAllBytes());
            index.put(name, jdk);
            return jdk;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read JDK class " + name, e);
        }
    }

    private boolean hasMember(String owner, String member, boolean field, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        Info info = lookup(owner);
        if (info == null) return false;
        if ((field ? info.fields() : info.methods()).contains(member)) return true;
        if (hasMember(info.superName(), member, field, seen)) return true;
        for (String itf : info.interfaces()) {
            if (hasMember(itf, member, field, seen)) return true;
        }
        return false;
    }

    private boolean hasMethodNamed(String owner, String name) {
        Info info = lookup(owner);
        return info != null && info.methods().stream().anyMatch(m -> m.startsWith(name + "("));
    }

    private void report(String problem, String user) {
        problems.computeIfAbsent(problem, k -> new TreeSet<>()).add(user);
    }

    private void type(String internal, String user) {
        if (internal == null) return;
        String name = internal;
        while (name.startsWith("[")) name = name.substring(1);
        if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1, name.length() - 1);
        if (name.length() <= 1 || ours.contains(name) || !external(name)) return;
        if (lookup(name) == null) report("missing class " + name.replace('/', '.'), user);
    }

    private void descriptor(String descriptor, String user) {
        Type t = Type.getType(descriptor);
        switch (t.getSort()) {
            case Type.METHOD -> {
                for (Type arg : t.getArgumentTypes()) descriptor(arg.getDescriptor(), user);
                descriptor(t.getReturnType().getDescriptor(), user);
            }
            case Type.ARRAY -> type(t.getElementType().getInternalName(), user);
            case Type.OBJECT -> type(t.getInternalName(), user);
            default -> { }
        }
    }

    private void member(String owner, String name, String descriptor, boolean field, String user) {
        descriptor(descriptor, user);
        if (owner.startsWith("[") || ours.contains(owner) || !external(owner)) return;
        if (lookup(owner) == null) {
            type(owner, user);
            return;
        }
        String member = field ? name + ":" + descriptor : name + descriptor;
        if (!hasMember(owner, member, field, new HashSet<>())) {
            report("missing " + (field ? "field " : "method ") + owner.replace('/', '.') + "." + member, user);
        }
    }

    /** An {@code @At} target: {@code Lowner;name(args)ret} or {@code Lowner;name:type}. */
    private void atTarget(String target, String user) {
        int semicolon = target.indexOf(';');
        if (!target.startsWith("L") || semicolon < 0) return;
        String owner = target.substring(1, semicolon);
        String rest = target.substring(semicolon + 1);
        int colon = rest.indexOf(':');
        int paren = rest.indexOf('(');
        if (colon >= 0) member(owner, rest.substring(0, colon), rest.substring(colon + 1), true, user);
        else if (paren >= 0) member(owner, rest.substring(0, paren), rest.substring(paren), false, user);
    }

    /** An injector's {@code method =} selector must name a method declared by one of the mixin's targets. */
    private void methodSelector(String selector, String user, List<String> targets) {
        if (selector.contains("*") || targets.isEmpty()) return;
        int paren = selector.indexOf('(');
        String name = paren >= 0 ? selector.substring(0, paren) : selector;
        for (String target : targets) {
            if (lookup(target) == null) return; // already reported as a missing @Mixin target
            boolean found = paren >= 0
                    ? hasMember(target, name + selector.substring(paren), false, new HashSet<>())
                    : hasMethodNamed(target, name);
            if (found) return;
        }
        report("missing mixin method " + targets.get(0).replace('/', '.') + "." + selector, user);
    }

    private void check(String tag, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        String self = reader.getClassName();
        String user = tag + " " + self.substring(self.lastIndexOf('/') + 1);
        List<String> mixinTargets = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String sig, String sup, String[] itfs) {
                type(sup, user);
                if (itfs != null) for (String itf : itfs) type(itf, user);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN.equals(descriptor)) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String n, Object value) {
                                String target = value instanceof Type t
                                        ? t.getInternalName() : value.toString().replace('.', '/');
                                mixinTargets.add(target);
                                type(target, user + " @Mixin");
                            }
                        };
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String sig, Object value) {
                descriptor(descriptor, user);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] ex) {
                descriptor(descriptor, user);
                String where = user + "#" + name;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        boolean mixinApi = annotation.startsWith("Lorg/spongepowered/")
                                || annotation.startsWith("Lcom/llamalad7/");
                        return mixinApi ? injector(where, mixinTargets) : null;
                    }

                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        member(owner, n, d, false, where);
                    }

                    @Override
                    public void visitFieldInsn(int op, String owner, String n, String d) {
                        member(owner, n, d, true, where);
                    }

                    @Override
                    public void visitTypeInsn(int op, String type) {
                        type(type, where);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type t && t.getSort() != Type.METHOD) descriptor(t.getDescriptor(), where);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String n, String d, Handle bootstrap, Object... args) {
                        descriptor(d, where);
                        for (Object arg : args) {
                            if (arg instanceof Handle h) {
                                member(h.getOwner(), h.getName(), h.getDesc(), h.getTag() <= Opcodes.H_PUTSTATIC, where);
                            }
                        }
                    }

                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        type(type, where);
                    }
                };
            }
        }, 0);
    }

    /** Mixin and MixinExtras annotations, nested ones (@At, @Slice) included. */
    private AnnotationVisitor injector(String where, List<String> mixinTargets) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if ("target".equals(name) && value instanceof String s) atTarget(s, where);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                return injector(where, mixinTargets);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String n, Object value) {
                        if (!(value instanceof String s)) return;
                        if ("method".equals(name)) methodSelector(s, where, mixinTargets);
                        else if ("target".equals(name)) atTarget(s, where);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String n, String descriptor) {
                        return injector(where, mixinTargets);
                    }
                };
            }
        };
    }
}
