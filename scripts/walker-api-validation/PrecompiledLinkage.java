import org.objectweb.asm.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.*;
import java.util.stream.Stream;

/** Offline symbolic linkage only. Never define or initialize an application class. */
public final class PrecompiledLinkage implements Opcodes {
    static final String ROOT = "net/runelite/client/plugins/microbot/";
    static final List<String> SCOPE = List.of("util/walker/", "api/tileobject/", "util/tileobject/",
        "api/npc/", "util/npc/", "api/actor/");
    static final Set<String> QUERY_TYPES = Set.of(ROOT + "api/AbstractEntityQueryable",
        ROOT + "api/IEntityQueryable", ROOT + "api/IEntity");
    record Entry(Path source, String item, byte[] direct) {}
    record Member(String owner, String name, String desc, int access) {}
    static final class Info {
        String name, parent, nest;
        int access;
        List<String> interfaces = new ArrayList<>(), nestMembers = new ArrayList<>();
        Map<String, Member> methods = new HashMap<>(), fields = new HashMap<>();
        boolean isInterface() { return (access & ACC_INTERFACE) != 0; }
    }
    record Ref(String caller, String site, String owner, String name, String desc,
               int opcode, boolean itf, boolean field, boolean typeOnly) {}
    final Map<String, List<Entry>> index = new HashMap<>();
    final Map<String, Info> infos = new HashMap<>();
    final Map<Path, ZipFile> archives = new LinkedHashMap<>();
    final Set<String> pluginClasses = new TreeSet<>();
    final Map<String, Entry> pluginEntries = new HashMap<>();
    final Set<String> duplicateReports = new HashSet<>();
    final List<Ref> references = new ArrayList<>();
    int checked, errors, skipped;

    static boolean scoped(String name) {
        return name != null && (SCOPE.stream().anyMatch(s -> name.startsWith(ROOT + s)) ||
            QUERY_TYPES.stream().anyMatch(s -> name.equals(s) || name.startsWith(s + "$")));
    }
    static String key(String name, String desc) { return name + desc; }
    static String pkg(String name) { int i = name.lastIndexOf('/'); return i < 0 ? "" : name.substring(0, i); }
    static String quote(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '\\' || c == '"') b.append('\\').append(c);
            else if (c < 32) b.append(String.format("\\u%04x", (int)c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
    static void fail(String message) { throw new IllegalStateException(message); }
    void add(String name, Entry entry, boolean plugin) {
        if (name.equals("module-info")) return;
        index.computeIfAbsent(name, n -> new ArrayList<>()).add(entry);
        if (plugin) { pluginClasses.add(name); pluginEntries.put(name, entry); }
    }
    void addPath(Path path, boolean plugin) throws IOException {
        path = path.toRealPath();
        if (Files.isDirectory(path)) {
            if (!plugin) fail("External dependencies must be exact JAR files: " + path);
            try (Stream<Path> paths = Files.walk(path)) {
                for (Path f : paths.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                    String item = path.relativize(f).toString().replace('\\', '/');
                    add(item.substring(0, item.length() - 6), new Entry(f, null, null), true);
                }
            }
            return;
        }
        ZipFile zip = new ZipFile(path.toFile());
        archives.put(path, zip);
        boolean multi = false;
        ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
        if (manifest != null) try (InputStream in = zip.getInputStream(manifest)) {
            multi = "true".equalsIgnoreCase(new Manifest(in).getMainAttributes().getValue("Multi-Release"));
        }
        Map<String, String> chosen = new TreeMap<>();
        Map<String, Integer> versions = new HashMap<>();
        for (ZipEntry e : Collections.list(zip.entries())) {
            String item = e.getName(), logical = item;
            if (!item.endsWith(".class")) continue;
            int version = 0;
            if (item.startsWith("META-INF/versions/")) {
                if (!multi) continue;
                String tail = item.substring(18);
                int slash = tail.indexOf('/');
                if (slash < 0) continue;
                try { version = Integer.parseInt(tail.substring(0, slash)); }
                catch (NumberFormatException ex) { continue; }
                if (version < 9 || version > 17) continue;
                logical = tail.substring(slash + 1);
            } else if (item.startsWith("META-INF/")) continue;
            if (version > versions.getOrDefault(logical, -1)) {
                versions.put(logical, version); chosen.put(logical, item);
            } else if (version == versions.getOrDefault(logical, -1)) {
                fail("Duplicate class entry in " + path + ": " + logical);
            }
        }
        for (var e : chosen.entrySet()) add(e.getKey().substring(0, e.getKey().length() - 6),
            new Entry(path, e.getValue(), null), plugin);
    }
    void addJdk() throws IOException {
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path modules = jrt.getPath("/modules");
        try (Stream<Path> paths = Files.walk(modules)) {
            for (Path f : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String item = modules.relativize(f).toString().replace('\\', '/');
                item = item.substring(item.indexOf('/') + 1);
                add(item.substring(0, item.length() - 6), new Entry(f, null, null), false);
            }
        }
    }
    byte[] read(Entry e) throws IOException {
        if (e.direct != null) return e.direct;
        if (e.item == null) return Files.readAllBytes(e.source);
        ZipFile zip = archives.get(e.source);
        try (InputStream in = zip.getInputStream(zip.getEntry(e.item))) { return in.readAllBytes(); }
    }
    byte[] bytes(String name) throws IOException {
        List<Entry> matches = index.get(name);
        if (matches == null) fail("Unavailable exact dependency/class: " + name);
        if (matches.size() > 1) {
            if (scoped(name) || name.startsWith("java/")) fail("Duplicate inspected API/JDK class " + name + " in " + matches);
            if (duplicateReports.add(name)) System.out.println("{\"classSelection\":" + quote(name) +
                ",\"selected\":" + quote(matches.get(0).source.toString()) + ",\"policy\":\"candidate, old plugin, exact deps, JRT; non-scope duplicate\"}");
        }
        Entry e = matches.get(0);
        return read(e);
    }
    Info info(String name) throws IOException {
        Info cached = infos.get(name);
        if (cached != null) return cached;
        Info i = new Info();
        new ClassReader(bytes(name)).accept(new ClassVisitor(ASM9) {
            public void visit(int v, int access, String n, String sig, String parent, String[] interfaces) {
                i.name = n; i.access = access; i.parent = parent;
                i.interfaces.addAll(Arrays.asList(interfaces)); i.nest = n;
            }
            public void visitNestHost(String host) { i.nest = host; }
            public void visitNestMember(String member) { i.nestMembers.add(member); }
            public FieldVisitor visitField(int a, String n, String d, String s, Object value) {
                i.fields.put(key(n, d), new Member(i.name, n, d, a)); return null;
            }
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                i.methods.put(key(n, d), new Member(i.name, n, d, a)); return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!name.equals(i.name)) fail("Class path/name mismatch: " + name + " / " + i.name);
        infos.put(name, i);
        return i;
    }
    boolean subtype(String child, String parent) throws IOException { return subtype(child, parent, new HashSet<>()); }
    boolean subtype(String child, String parent, Set<String> seen) throws IOException {
        if (child.equals(parent)) return true;
        if (!seen.add(child)) return false;
        Info i = info(child);
        if (i.parent != null && subtype(i.parent, parent, seen)) return true;
        for (String n : i.interfaces) if (subtype(n, parent, seen)) return true;
        return false;
    }
    boolean scopedHierarchy(String name, Set<String> seen) throws IOException {
        if (scoped(name)) return true;
        if (!seen.add(name)) return false;
        Info i = info(name);
        if (i.parent != null && scopedHierarchy(i.parent, seen)) return true;
        for (String n : i.interfaces) if (scopedHierarchy(n, seen)) return true;
        return false;
    }
    List<String> types(String descriptor) {
        List<String> out = new ArrayList<>();
        Type t = Type.getType(descriptor);
        collectType(t, out); return out;
    }
    void collectType(Type t, List<String> out) {
        switch (t.getSort()) {
            case Type.OBJECT -> out.add(t.getInternalName());
            case Type.ARRAY -> collectType(t.getElementType(), out);
            case Type.METHOD -> {
                for (Type a : t.getArgumentTypes()) collectType(a, out);
                collectType(t.getReturnType(), out);
            }
            default -> { }
        }
    }
    void typeRef(String caller, String site, String name) {
        if (name == null) return;
        if (name.startsWith("[")) {
            for (String n : types(name)) typeRef(caller, site, n);
        } else if (scoped(name)) references.add(new Ref(caller, site, name, "", "", 0, false, false, true));
    }
    void descriptor(String caller, String site, String desc) {
        for (String name : types(desc)) typeRef(caller, site, name);
    }
    void handle(String caller, String site, Handle h) {
        int opcode = switch (h.getTag()) {
            case H_GETFIELD -> GETFIELD; case H_GETSTATIC -> GETSTATIC;
            case H_PUTFIELD -> PUTFIELD; case H_PUTSTATIC -> PUTSTATIC;
            case H_INVOKESTATIC -> INVOKESTATIC; case H_INVOKEINTERFACE -> INVOKEINTERFACE;
            case H_INVOKEVIRTUAL -> INVOKEVIRTUAL;
            case H_INVOKESPECIAL, H_NEWINVOKESPECIAL -> INVOKESPECIAL;
            default -> throw new IllegalArgumentException("Unsupported handle tag " + h.getTag());
        };
        if (h.getTag() == H_NEWINVOKESPECIAL && !h.getName().equals("<init>")) fail("Invalid constructor handle");
        references.add(new Ref(caller, site + "/handle:" + h.getTag(), h.getOwner(), h.getName(),
            h.getDesc(), opcode, h.isInterface(), h.getTag() <= H_PUTSTATIC, false));
    }
    void constant(String caller, String site, Object value) {
        if (value instanceof Handle h) handle(caller, site, h);
        else if (value instanceof Type t) descriptor(caller, site, t.getDescriptor());
        else if (value instanceof ConstantDynamic c) {
            descriptor(caller, site, c.getDescriptor()); handle(caller, site + "/condy-bootstrap", c.getBootstrapMethod());
            for (int n = 0; n < c.getBootstrapMethodArgumentCount(); n++) constant(caller, site + "/condy:" + n, c.getBootstrapMethodArgument(n));
        }
    }
    void scan(String caller) throws IOException {
        // Always scan the ORIGINAL old plugin bytes, even for shaded library copies.
        ClassReader reader = new ClassReader(pluginEntries.containsKey(caller) ? read(pluginEntries.get(caller)) : bytes(caller));
        if (!reader.getClassName().equals(caller)) fail("Class directory root is wrong: " + caller + " contains " + reader.getClassName());
        reader.accept(new ClassVisitor(ASM9) {
            public void visit(int v, int a, String n, String s, String parent, String[] interfaces) {
                typeRef(caller, "super", parent);
                for (String itf : interfaces) typeRef(caller, "interface", itf);
            }
            public FieldVisitor visitField(int a, String n, String d, String s, Object value) {
                descriptor(caller, "field:" + n, d); constant(caller, "field:" + n, value); return null;
            }
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] exceptions) {
                descriptor(caller, n + d, d);
                if (exceptions != null) for (String ex : exceptions) typeRef(caller, n + "/throws", ex);
                return new MethodVisitor(ASM9) {
                    int insn, line = -1;
                    String site() { return n + d + "@" + (++insn) + ":line" + line; }
                    public void visitLineNumber(int number, Label start) { line = number; }
                    public void visitTypeInsn(int op, String type) { typeRef(caller, site(), type); }
                    public void visitMultiANewArrayInsn(String desc, int dimensions) { descriptor(caller, site(), desc); }
                    public void visitTryCatchBlock(Label a, Label b, Label c, String type) { typeRef(caller, n + "/catch", type); }
                    public void visitFieldInsn(int op, String owner, String name, String desc) {
                        references.add(new Ref(caller, site(), owner, name, desc, op, false, true, false));
                    }
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        references.add(new Ref(caller, site(), owner, name, desc, op, itf, false, false));
                    }
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
                        String at = site(); descriptor(caller, at, desc); handle(caller, at + "/indy-bootstrap", bootstrap);
                        for (Object arg : args) constant(caller, at + "/indy-arg", arg);
                    }
                    public void visitLdcInsn(Object value) { constant(caller, site(), value); }
                };
            }
        }, ClassReader.SKIP_FRAMES);
    }
    Member field(String owner, String name, String desc, Set<String> seen) throws IOException {
        if (!seen.add(owner)) return null;
        Info i = info(owner);
        Member m = i.fields.get(key(name, desc));
        if (m != null) return m;
        for (String n : i.interfaces) { m = field(n, name, desc, seen); if (m != null) return m; }
        return i.parent == null ? null : field(i.parent, name, desc, seen);
    }
    void interfaceMethods(String owner, String name, String desc, Set<String> seen, Map<String, Member> found) throws IOException {
        if (!seen.add(owner)) return;
        Info i = info(owner);
        if (i.isInterface()) {
            Member m = i.methods.get(key(name, desc));
            if (m != null && (m.access & (ACC_STATIC | ACC_PRIVATE)) == 0) found.put(owner, m);
        }
        for (String n : i.interfaces) interfaceMethods(n, name, desc, seen, found);
        if (!i.isInterface() && i.parent != null) interfaceMethods(i.parent, name, desc, seen, found);
    }
    Member method(String owner, String name, String desc) throws IOException {
        Info i = info(owner);
        Member own = i.methods.get(key(name, desc));
        if (own != null || name.equals("<init>")) return own;
        // JVMS signature-polymorphic calls legitimately use a call-site-specific descriptor.
        if (owner.equals("java/lang/invoke/MethodHandle") || owner.equals("java/lang/invoke/VarHandle")) {
            Member polymorphic = i.methods.values().stream().filter(m -> m.name.equals(name) &&
                (m.access & (ACC_NATIVE | ACC_VARARGS)) == (ACC_NATIVE | ACC_VARARGS)).findFirst().orElse(null);
            if (polymorphic != null) return polymorphic;
        }
        if (!i.isInterface()) {
            Set<String> seen = new HashSet<>();
            String parent = i.parent;
            while (parent != null) {
                if (!seen.add(parent)) fail("Cyclic superclass: " + parent);
                Info p = info(parent); Member m = p.methods.get(key(name, desc));
                if (m != null) return m;
                parent = p.parent;
            }
        } else {
            Member object = info("java/lang/Object").methods.get(key(name, desc));
            if (object != null && (object.access & ACC_PUBLIC) != 0 && (object.access & ACC_STATIC) == 0) return object;
        }
        Map<String, Member> found = new TreeMap<>();
        interfaceMethods(owner, name, desc, new HashSet<>(), found);
        Set<String> dominated = new HashSet<>();
        for (String a : found.keySet()) for (String b : found.keySet()) {
            if (!a.equals(b) && subtype(b, a)) dominated.add(a);
        }
        dominated.forEach(found::remove);
        List<Member> concrete = found.values().stream().filter(m -> (m.access & ACC_ABSTRACT) == 0).toList();
        if (concrete.size() > 1) fail("Conflicting maximally-specific defaults: " + found.keySet());
        return concrete.size() == 1 ? concrete.get(0) : found.values().stream().findFirst().orElse(null);
    }
    boolean nestmates(String a, String b) throws IOException {
        Info x = info(a), y = info(b);
        if (!x.nest.equals(y.nest) || !pkg(a).equals(pkg(b))) return false;
        Info host = info(x.nest);
        return (a.equals(host.name) || host.nestMembers.contains(a)) && (b.equals(host.name) || host.nestMembers.contains(b));
    }
    void classAccess(String caller, String target) throws IOException {
        Info i = info(target);
        if ((i.access & ACC_PUBLIC) == 0 && !pkg(caller).equals(pkg(target))) fail("Inaccessible class " + target);
    }
    void loadHierarchy(String target, Set<String> seen) throws IOException {
        if (!seen.add(target)) return;
        Info i = info(target);
        if (i.parent != null) loadHierarchy(i.parent, seen);
        for (String n : i.interfaces) loadHierarchy(n, seen);
    }
    boolean relevant(Ref r) throws IOException {
        if (r.typeOnly || scoped(r.owner) || types(r.desc).stream().anyMatch(PrecompiledLinkage::scoped)) return true;
        // javac can encode an inherited API call with a plugin subclass as owner.
        return pluginClasses.contains(r.owner) && scopedHierarchy(r.owner, new HashSet<>());
    }
    Member verify(Ref r) throws IOException {
        classAccess(r.caller, r.owner); loadHierarchy(r.owner, new HashSet<>());
        if (r.typeOnly) return null;
        for (String type : types(r.desc)) { classAccess(r.caller, type); loadHierarchy(type, new HashSet<>()); }
        Info owner = info(r.owner);
        if (!r.field && owner.isInterface() != r.itf) fail("Methodref/InterfaceMethodref owner kind changed");
        if (!r.field && r.opcode == INVOKEINTERFACE && !owner.isInterface()) fail("invokeinterface owner is not interface");
        if (!r.field && r.opcode == INVOKEVIRTUAL && owner.isInterface()) fail("invokevirtual owner is interface");
        Member m = r.field ? field(r.owner, r.name, r.desc, new HashSet<>()) : method(r.owner, r.name, r.desc);
        if (m == null) fail("Unresolved " + (r.field ? "field " : "method ") + r.owner + "." + r.name + r.desc);
        boolean staticOp = r.opcode == GETSTATIC || r.opcode == PUTSTATIC || r.opcode == INVOKESTATIC;
        if (((m.access & ACC_STATIC) != 0) != staticOp) fail("Static/instance mismatch: resolved " + m);
        if (r.name.equals("<init>") && (r.opcode != INVOKESPECIAL || !m.owner.equals(r.owner) || owner.isInterface())) {
            fail("Invalid constructor resolution/invocation");
        }
        if (r.name.equals("<clinit>")) fail("Explicit class initializer reference");
        if ((m.access & ACC_PUBLIC) == 0) {
            if ((m.access & ACC_PRIVATE) != 0) {
                if (!nestmates(r.caller, m.owner)) fail("Private member access denied: " + m);
            } else if (!pkg(r.caller).equals(pkg(m.owner))) {
                if ((m.access & ACC_PROTECTED) == 0 || !subtype(r.caller, m.owner)) fail("Member access denied: " + m);
            }
        }
        if (!r.field && r.opcode == INVOKESPECIAL && !r.name.equals("<init>") && (m.access & ACC_PRIVATE) == 0) {
            if (owner.isInterface()) {
                if (!info(r.caller).interfaces.contains(r.owner)) fail("invokespecial interface is not a direct superinterface");
            } else if (!subtype(r.caller, r.owner)) fail("invokespecial owner is not caller/superclass");
        }
        if (r.field && (r.opcode == PUTFIELD || r.opcode == PUTSTATIC) && (m.access & ACC_FINAL) != 0) {
            String required = r.opcode == PUTSTATIC ? "<clinit>" : "<init>";
            if (r.site.contains("/handle:") || !r.caller.equals(m.owner) || !r.site.startsWith(required + "(")) {
                fail("Write to final field outside declaring initializer or via setter handle");
            }
        }
        return m;
    }
    void run(Path candidate, Path plugin, List<Path> deps) throws IOException {
        addPath(candidate, false); addPath(plugin, true);
        for (Path dep : deps) addPath(dep, false);
        addJdk();
        if (pluginClasses.isEmpty()) fail("No old plugin classes found");
        for (String name : pluginClasses) scan(name);
        for (Ref r : references) {
            try {
                if (!relevant(r)) { skipped++; continue; }
                checked++;
                Member resolved = verify(r);
                System.out.println("{\"status\":\"OK\",\"caller\":" + quote(r.caller) + ",\"site\":" + quote(r.site) +
                    ",\"owner\":" + quote(r.owner) + ",\"name\":" + quote(r.name) + ",\"descriptor\":" + quote(r.desc) +
                    ",\"resolvedOwner\":" + quote(resolved == null ? r.owner : resolved.owner) + "}");
            } catch (Exception ex) {
                errors++;
                System.out.println("{\"status\":\"FAIL\",\"caller\":" + quote(r.caller) + ",\"site\":" + quote(r.site) +
                    ",\"owner\":" + quote(r.owner) + ",\"name\":" + quote(r.name) + ",\"descriptor\":" + quote(r.desc) +
                    ",\"error\":" + quote(ex.toString()) + "}");
            }
        }
        if (checked == 0) { errors++; System.out.println("{\"status\":\"FAIL\",\"error\":\"No scoped references checked; not a representative binary\"}"); }
        System.out.println("{\"summary\":true,\"checked\":" + checked + ",\"errors\":" + errors + ",\"skippedOutsideScope\":" + skipped +
            ",\"pluginClasses\":" + pluginClasses.size() + ",\"jdk\":" + quote(System.getProperty("java.runtime.version")) +
            ",\"scope\":" + quote(SCOPE + " + " + QUERY_TYPES) + ",\"claim\":\"scoped symbolic linkage only; no initialization or gameplay\"}");
    }
    static Info fixture(String name, String parent, int access, String... interfaces) {
        Info i = new Info(); i.name = name; i.parent = parent; i.nest = name; i.access = access;
        i.interfaces.addAll(Arrays.asList(interfaces)); return i;
    }
    static void putMethod(Info i, String name, String desc, int access) {
        i.methods.put(key(name, desc), new Member(i.name, name, desc, access));
    }
    static Ref call(String caller, String owner, String name, String desc, int op, boolean itf, boolean field) {
        return new Ref(caller, "test()V@1", owner, name, desc, op, itf, field, false);
    }
    interface Checked { void run() throws Exception; }
    static int tests;
    static void check(String label, Checked action, String expectedFailure) throws Exception {
        try {
            action.run();
            if (expectedFailure != null) throw new AssertionError("Expected failure: " + label);
        } catch (IllegalStateException ex) {
            if (expectedFailure == null || !ex.getMessage().contains(expectedFailure)) throw ex;
        }
        tests++;
        System.out.println("{\"selfTest\":" + quote(label) + ",\"status\":\"OK\"}");
    }
    static void selfTest() throws Exception {
        PrecompiledLinkage t = new PrecompiledLinkage(); t.addJdk();
        String base = ROOT + "util/walker/FixtureBase", child = ROOT + "util/walker/FixtureChild";
        String itf = ROOT + "util/walker/FixtureInterface", caller = "fixture/Caller";
        Info b = fixture(base, "java/lang/Object", ACC_PUBLIC);
        putMethod(b, "<init>", "()V", ACC_PUBLIC);
        putMethod(b, "hop", "()I", ACC_PUBLIC);
        putMethod(b, "staticHop", "()I", ACC_PUBLIC | ACC_STATIC);
        putMethod(b, "secret", "()V", ACC_PRIVATE);
        b.fields.put(key("state", "I"), new Member(base, "state", "I", ACC_PUBLIC));
        b.fields.put(key("STATIC", "I"), new Member(base, "STATIC", "I", ACC_PUBLIC | ACC_STATIC));
        Info i = fixture(itf, "java/lang/Object", ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
        putMethod(i, "ready", "()Z", ACC_PUBLIC | ACC_ABSTRACT);
        putMethod(i, "defaultHop", "()I", ACC_PUBLIC);
        putMethod(i, "staticInterface", "()I", ACC_PUBLIC | ACC_STATIC);
        i.fields.put(key("CONSTANT", "I"), new Member(itf, "CONSTANT", "I", ACC_PUBLIC | ACC_STATIC | ACC_FINAL));
        Info c = fixture(child, base, ACC_PUBLIC, itf);
        Info p = fixture(caller, child, ACC_PUBLIC);
        for (Info x : List.of(b, i, c, p)) t.infos.put(x.name, x);
        t.pluginClasses.add(caller);
        check("inherited exact return descriptor", () -> t.verify(call(caller, child, "hop", "()I", INVOKEVIRTUAL, false, false)), null);
        check("return-type-only change rejected", () -> t.verify(call(caller, child, "hop", "()J", INVOKEVIRTUAL, false, false)), "Unresolved method");
        check("inherited field", () -> t.verify(call(caller, child, "state", "I", GETFIELD, false, true)), null);
        check("static field", () -> t.verify(call(caller, base, "STATIC", "I", GETSTATIC, false, true)), null);
        check("interface field lookup", () -> t.verify(call(caller, child, "CONSTANT", "I", GETSTATIC, false, true)), null);
        check("removed field rejected", () -> t.verify(call(caller, child, "absent", "I", GETFIELD, false, true)), "Unresolved field");
        check("static method", () -> t.verify(call(caller, base, "staticHop", "()I", INVOKESTATIC, false, false)), null);
        check("method staticness rejected", () -> t.verify(call(caller, base, "hop", "()I", INVOKESTATIC, false, false)), "Static/instance mismatch");
        check("field staticness rejected", () -> t.verify(call(caller, base, "state", "I", GETSTATIC, false, true)), "Static/instance mismatch");
        check("constructor exact owner", () -> t.verify(call(caller, base, "<init>", "()V", INVOKESPECIAL, false, false)), null);
        check("constructor is not inherited", () -> t.verify(call(caller, child, "<init>", "()V", INVOKESPECIAL, false, false)), "Unresolved method");
        check("constructor overload rejected", () -> t.verify(call(caller, base, "<init>", "(I)V", INVOKESPECIAL, false, false)), "Unresolved method");
        check("invokeinterface", () -> t.verify(call(caller, itf, "ready", "()Z", INVOKEINTERFACE, true, false)), null);
        check("wrong interface bit rejected", () -> t.verify(call(caller, itf, "ready", "()Z", INVOKEINTERFACE, false, false)), "owner kind changed");
        check("inherited default", () -> t.verify(call(caller, child, "defaultHop", "()I", INVOKEVIRTUAL, false, false)), null);
        check("static interface", () -> t.verify(call(caller, itf, "staticInterface", "()I", INVOKESTATIC, true, false)), null);
        check("interface statics not inherited", () -> t.verify(call(caller, child, "staticInterface", "()I", INVOKESTATIC, false, false)), "Unresolved method");
        check("private access rejected", () -> t.verify(call(caller, base, "secret", "()V", INVOKEVIRTUAL, false, false)), "Private member access denied");
        check("missing exact dependency rejected", () -> t.verify(call(caller, ROOT + "util/walker/Unavailable", "x", "()V", INVOKESTATIC, false, false)), "Unavailable exact dependency");
        check("missing descriptor type rejected", () -> {
            putMethod(b, "typed", "(Lmissing/Dependency;)V", ACC_PUBLIC);
            t.verify(call(caller, base, "typed", "(Lmissing/Dependency;)V", INVOKEVIRTUAL, false, false));
        }, "Unavailable exact dependency");
        String second = ROOT + "util/walker/OtherDefault";
        Info other = fixture(second, "java/lang/Object", ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
        putMethod(other, "defaultHop", "()I", ACC_PUBLIC); t.infos.put(second, other);
        c.interfaces.add(second);
        check("conflicting default methods rejected", () -> t.verify(call(caller, child, "defaultHop", "()I", INVOKEVIRTUAL, false, false)), "Conflicting maximally-specific");
        c.interfaces.remove(second);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V17, ACC_PUBLIC, caller, null, child, null);
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "bootstrapFixture", "()V", null, null);
        mv.visitCode();
        Handle target = new Handle(H_INVOKEVIRTUAL, base, "hop", "()I", false);
        Handle fieldHandle = new Handle(H_GETSTATIC, base, "STATIC", "I", false);
        Handle bootstrap = new Handle(H_INVOKESTATIC, "fixture/Bootstrap", "bootstrap", "()Ljava/lang/Object;", false);
        mv.visitInvokeDynamicInsn("test", "()Ljava/lang/Runnable;", bootstrap, target, fieldHandle);
        mv.visitLdcInsn(new ConstantDynamic("test", "Ljava/lang/Object;", bootstrap, target));
        mv.visitLdcInsn(new Handle(H_NEWINVOKESPECIAL, base, "<init>", "()V", false));
        mv.visitInsn(RETURN); mv.visitMaxs(4, 0); mv.visitEnd(); writer.visitEnd();
        t.index.put(caller, List.of(new Entry(null, null, writer.toByteArray())));
        t.scan(caller);
        check("bootstrap indy/condy and constructor handle extraction", () -> {
            // The scoped superclass is a type reference, not a fifth API handle.
            List<Ref> handles = t.references.stream().filter(r -> !r.typeOnly && r.site.contains("/handle:")).toList();
            String at = "bootstrapFixture()V@";
            Set<Ref> expected = Set.of(
                new Ref(caller, at + "1:line-1/indy-bootstrap/handle:6", "fixture/Bootstrap", "bootstrap", "()Ljava/lang/Object;", INVOKESTATIC, false, false, false),
                new Ref(caller, at + "1:line-1/indy-arg/handle:5", base, "hop", "()I", INVOKEVIRTUAL, false, false, false),
                new Ref(caller, at + "1:line-1/indy-arg/handle:2", base, "STATIC", "I", GETSTATIC, false, true, false),
                new Ref(caller, at + "2:line-1/condy-bootstrap/handle:6", "fixture/Bootstrap", "bootstrap", "()Ljava/lang/Object;", INVOKESTATIC, false, false, false),
                new Ref(caller, at + "2:line-1/condy:0/handle:5", base, "hop", "()I", INVOKEVIRTUAL, false, false, false),
                new Ref(caller, at + "3:line-1/handle:8", base, "<init>", "()V", INVOKESPECIAL, false, false, false));
            if (handles.size() != expected.size() || !new HashSet<>(handles).equals(expected)) {
                fail("Bootstrap/constant handle records differ: expected " + expected + ", actual " + handles);
            }
            if (t.references.stream().noneMatch(r -> r.typeOnly && r.site.equals("super") && r.owner.equals(child))) {
                fail("Scoped superclass reference missing from fixture");
            }
            for (Ref r : handles) if (scoped(r.owner)) t.verify(r);
        }, null);
        String invalidCaller = "fixture/InvalidHandleCaller";
        t.infos.put(invalidCaller, fixture(invalidCaller, child, ACC_PUBLIC));
        ClassWriter invalid = new ClassWriter(0);
        invalid.visit(V17, ACC_PUBLIC, invalidCaller, null, child, null);
        MethodVisitor bad = invalid.visitMethod(ACC_PUBLIC | ACC_STATIC, "badHandle", "()V", null, null);
        bad.visitCode();
        bad.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, base, "hop", "()J", false));
        bad.visitInsn(POP); bad.visitInsn(RETURN); bad.visitMaxs(1, 0); bad.visitEnd(); invalid.visitEnd();
        t.index.put(invalidCaller, List.of(new Entry(null, null, invalid.toByteArray())));
        t.scan(invalidCaller);
        List<Ref> invalidHandles = t.references.stream().filter(r -> r.caller.equals(invalidCaller) && !r.typeOnly).toList();
        check("scanned return-type-changed handle retained exactly", () -> {
            Ref expected = new Ref(invalidCaller, "badHandle()V@1:line-1/handle:5", base, "hop", "()J", INVOKEVIRTUAL, false, false, false);
            if (!invalidHandles.equals(List.of(expected))) fail("Changed handle descriptor lost: " + invalidHandles);
        }, null);
        check("scanned return-type-changed handle rejected", () -> t.verify(invalidHandles.get(0)), "Unresolved method");
        System.out.println("{\"selfTestSummary\":true,\"passed\":" + tests + ",\"applicationClassesInitialized\":false}");
    }
    public static void main(String[] args) {
        try {
            Path actual = Path.of(ClassReader.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            String expected = System.getProperty("walker.audit.asm");
            if (expected == null || !Files.isSameFile(actual, Path.of(expected))) fail("Unexpected ASM CodeSource: " + actual);
            int api = Opcodes.class.getField("ASM9").getInt(null);
            int version = Opcodes.class.getField("V17").getInt(null);
            if (api != ASM9 || version != 61) fail("ASM9 / Java17 class API unavailable");
            ClassWriter probe = new ClassWriter(0);
            probe.visit(V17, ACC_PUBLIC, "fixture/AsmCapability", null, "java/lang/Object", null); probe.visitEnd();
            new ClassReader(probe.toByteArray()).accept(new ClassVisitor(ASM9) {}, ClassReader.SKIP_CODE);
            System.out.println("{\"asmCodeSource\":" + quote(actual.toString()) + ",\"asmImplementationVersion\":" +
                quote(ClassReader.class.getPackage().getImplementationVersion()) + ",\"api\":\"ASM9\",\"java17ClassRead\":true}");
        } catch (Exception | LinkageError ex) {
            System.err.println("INCOMPLETE ASM TOOL DEPENDENCY: " + ex); System.exit(2);
        }
        if (Runtime.version().feature() == 17 && args.length == 1 && args[0].equals("--self-test")) {
            try { selfTest(); return; }
            catch (Exception | AssertionError ex) { ex.printStackTrace(System.err); System.exit(2); }
        }
        if (Runtime.version().feature() != 17 || args.length < 2) {
            System.err.println("Requires JDK 17. Usage: PrecompiledLinkage exact-client.jar old-plugin.jar-or-classdir [exact-dependency.jar ...]");
            System.exit(2);
        }
        PrecompiledLinkage tool = new PrecompiledLinkage();
        int exit = 2;
        try {
            tool.run(Path.of(args[0]), Path.of(args[1]), Arrays.stream(args).skip(2).map(Path::of).toList());
            exit = tool.errors == 0 ? 0 : 1;
        } catch (Exception ex) {
            System.err.println("INCOMPLETE: " + ex); ex.printStackTrace(System.err);
        } finally {
            for (ZipFile zip : tool.archives.values()) try { zip.close(); } catch (IOException ignored) { }
        }
        System.exit(exit);
    }
}
