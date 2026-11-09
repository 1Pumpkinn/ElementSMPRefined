package net.rose.elementSMPRefined.util.server.pack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The resource pack bundled in the plugin jar under {@code elementsmprefined/}, zipped in memory
 * together with its SHA-1.
 *
 * <p>Entries are written in sorted order with a fixed timestamp, so the same pack contents always
 * produce the same bytes and therefore the same hash. Players only re-download when the pack
 * actually changed.
 */
public final class ResourcePackArchive {
    public static final String JAR_ROOT = "elementsmprefined/";

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, 1, 1, 0, 0);

    private final byte[] bytes;
    private final String sha1;
    private final int fileCount;

    private ResourcePackArchive(byte[] bytes, String sha1, int fileCount) {
        this.bytes = bytes;
        this.sha1 = sha1;
        this.fileCount = fileCount;
    }

    public static ResourcePackArchive fromJar(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            List<String> names =
                    jar.stream()
                            .filter(entry -> !entry.isDirectory())
                            .map(JarEntry::getName)
                            .filter(name -> name.startsWith(JAR_ROOT))
                            .sorted()
                            .toList();

            if (!names.contains(JAR_ROOT + "pack.mcmeta")) {
                throw new IOException(
                        "No pack.mcmeta found under " + JAR_ROOT + " in " + jarFile.getName());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (String name : names) {
                    ZipEntry entry = new ZipEntry(name.substring(JAR_ROOT.length()));
                    entry.setTimeLocal(FIXED_TIME);
                    zip.putNextEntry(entry);
                    try (InputStream in = jar.getInputStream(jar.getEntry(name))) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }

            byte[] zipped = out.toByteArray();
            return new ResourcePackArchive(zipped, sha1Hex(zipped), names.size());
        }
    }

    private static String sha1Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is missing from this JVM", e);
        }
    }

    public byte[] bytes() {
        return bytes;
    }

    public String sha1() {
        return sha1;
    }

    public int fileCount() {
        return fileCount;
    }
}
