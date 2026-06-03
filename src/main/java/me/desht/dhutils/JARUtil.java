package me.desht.dhutils;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class JARUtil {
    public enum ExtractWhen {
        ALWAYS, IF_NOT_EXISTS, IF_NEWER
    }

    public static final Charset TARGET_ENCODING = StandardCharsets.UTF_8;
    public static final Charset SOURCE_ENCODING = StandardCharsets.UTF_8;

    private final Plugin plugin;

    public JARUtil(Plugin plugin) {
        this.plugin = plugin;
    }

    public void extractResource(String from, File to) {
        extractResource(from, to, ExtractWhen.IF_NEWER);
    }

    public void extractResource(String from, File to, ExtractWhen when) {
        File of = to;
        if (to.isDirectory()) {
            String fname = new File(from).getName();
            of = new File(to, fname);
        } else if (!of.isFile()) {
            LogUtils.warning("not a file: " + of);
            return;
        }

        File jarFile = getJarFile();

        Debugger.getInstance().debug(
                2,
                "extractResource: file=" + of + ", file-last-mod=" + of.lastModified()
                        + ", file-exists=" + of.exists() + ", jar-last-mod="
                        + (jarFile != null ? jarFile.lastModified() : "n/a") + ", when=" + when);

        // if the file exists and we don't need to overwrite, leave it alone
        if (of.exists() && when != ExtractWhen.ALWAYS) {
            if (when == ExtractWhen.IF_NOT_EXISTS) {
                return;
            }
            // IF_NEWER: preserve the file unless we can positively prove the JAR is newer.
            // When jarFile == null (e.g. tests, OSGi loaders) we have no JAR timestamp, so
            // we conservatively keep the existing file rather than clobbering user edits.
            if (jarFile == null || of.lastModified() >= jarFile.lastModified()) {
                return;
            }
        }

        if (!from.startsWith("/")) {
            from = "/" + from;
        }

        Debugger.getInstance().debug(
                String.format("extracting resource: %s (%s) -> %s (%s)", from,
                        SOURCE_ENCODING.name(), to, TARGET_ENCODING.name()));

        final char[] cbuf = new char[1024];
        int read;
        try {
            final Reader in = new BufferedReader(new InputStreamReader(
                    openResourceNoCache(from), SOURCE_ENCODING));
            final Writer out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(of), TARGET_ENCODING));
            while ((read = in.read(cbuf)) > 0) {
                out.write(cbuf, 0, read);
            }
            out.close();
            in.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public File getJarFile() {
        CodeSource codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        URL url = codeSource.getLocation();
        if (url == null) {
            return null;
        }
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public InputStream openResourceNoCache(String resource) throws IOException {
        URL res = plugin.getClass().getResource(resource);
        if (res == null) {
            LogUtils.warning("can't find " + resource + " in plugin JAR file"); //$NON-NLS-1$
            return null;
        }
        URLConnection resConn = res.openConnection();
        resConn.setUseCaches(false);
        return resConn.getInputStream();
    }
}
