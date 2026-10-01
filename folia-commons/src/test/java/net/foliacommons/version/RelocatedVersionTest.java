package net.foliacommons.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RelocatedVersionTest {
    @TempDir
    Path directory;

    @Test
    void independentRelocatedCopiesReadTheirOwnIdenticallyNamedResources() throws Exception {
        Path first = jar("first.jar", Map.of("consumer/first", "version=1.2.3"));
        Path second = jar("second.jar", Map.of("consumer/second", "version=4.5.6"));
        try (URLClassLoader a = loader(first); URLClassLoader b = loader(second)) {
            Class<?> firstClass = a.loadClass("consumer.first.version.LibraryVersion");
            Class<?> secondClass = b.loadClass("consumer.second.version.LibraryVersion");
            assertNotSame(firstClass.getClassLoader(), secondClass.getClassLoader());
            assertEquals("1.2.3", read(firstClass, "/shared-version.properties"));
            assertEquals("4.5.6", read(secondClass, "/shared-version.properties"));
            assertEquals("unknown", read(firstClass, "/consumer-second-version.properties"));
            assertEquals("unknown", read(secondClass, "/consumer-first-version.properties"));
        }
    }

    @Test
    void multipleRelocationsInOnePluginKeepDistinctResourcesAndFallbacks() throws Exception {
        Path merged = jar("merged.jar", Map.of("consumer/board", "version=1.2.0", "consumer/npc", "version=1.3.0",
                "consumer/unfiltered", "version=${project.version}", "consumer/empty", "version=  "));
        try (URLClassLoader loader = loader(merged)) {
            assertEquals("1.2.0", read(loader.loadClass("consumer.board.version.LibraryVersion"), "/consumer-board-version.properties"));
            assertEquals("1.3.0", read(loader.loadClass("consumer.npc.version.LibraryVersion"), "/consumer-npc-version.properties"));
            assertEquals("unknown", read(loader.loadClass("consumer.unfiltered.version.LibraryVersion"), "/consumer-unfiltered-version.properties"));
            assertEquals("unknown", read(loader.loadClass("consumer.empty.version.LibraryVersion"), "/consumer-empty-version.properties"));
        }
    }

    private Path jar(String name, Map<String, String> copies) throws Exception {
        byte[] original;
        try (var input = LibraryVersion.class.getResourceAsStream("LibraryVersion.class")) {
            original = input.readAllBytes();
        }
        Path path = directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            if (copies.size() == 1) {
                output.putNextEntry(new JarEntry("shared-version.properties"));
                output.write(copies.values().iterator().next().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            for (Map.Entry<String, String> copy : copies.entrySet()) {
                output.putNextEntry(new JarEntry(copy.getKey() + "/version/LibraryVersion.class"));
                output.write(relocate(original, copy.getKey()));
                output.closeEntry();
                output.putNextEntry(new JarEntry(copy.getKey().replace('/', '-') + "-version.properties"));
                output.write(copy.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private static byte[] relocate(byte[] original, String prefix) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(original));
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(input.readInt());
            output.writeShort(input.readUnsignedShort());
            output.writeShort(input.readUnsignedShort());
            int count = input.readUnsignedShort();
            output.writeShort(count);
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                output.writeByte(tag);
                switch (tag) {
                    case 1 -> output.writeUTF(input.readUTF().replace("net/foliacommons", prefix));
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> output.writeInt(input.readInt());
                    case 5, 6 -> { output.writeLong(input.readLong()); index++; }
                    case 7, 8, 16, 19, 20 -> output.writeShort(input.readUnsignedShort());
                    case 15 -> { output.writeByte(input.readUnsignedByte()); output.writeShort(input.readUnsignedShort()); }
                    default -> throw new IllegalStateException("Unexpected constant pool tag " + tag);
                }
            }
            output.write(input.readAllBytes());
        }
        return bytes.toByteArray();
    }

    private static URLClassLoader loader(Path jar) throws Exception {
        return new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static String read(Class<?> relocated, String resource) throws Exception {
        return (String) relocated.getMethod("read", Class.class, String.class).invoke(null, relocated, resource);
    }
}
