package com.seedlessds.app.filesystem;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Keep;

import com.seedlessds.app.emu.EmuAssets;

import java.io.File;
import java.io.IOException;

@Keep
public class PathCache {
    public static final String SYS_PREFIX = "SeedlessDS" + File.separator;
    public static final String USER_PREFIX = "User" + File.separator;

    private static volatile Context ctx;
    private static volatile File dataRoot;

    public static void setDataRoot(File root) {
        dataRoot = root;
    }

    private static final class RomLocation {
        final String key;
        final Uri uri;
        final String realPath;
        final String name;

        RomLocation(String key, Uri uri, String realPath, String name) {
            this.key = key;
            this.uri = uri;
            this.realPath = realPath;
            this.name = name;
        }
    }

    private static volatile RomLocation activeRom = new RomLocation(null, null, null, null);
    // Snapshot transactions serialize with each other, while cartridge backup
    // access and ROM reads remain independent of a slow snapshot provider.
    private static final Object[] pathLocks = { new Object(), new Object(), new Object() };

    private static int pathGroup(String path) {
        String normalized = sanitize(path);
        if (normalized.startsWith(USER_PREFIX + "savestates/")) return 0;
        if (normalized.startsWith(USER_PREFIX + "backup/")) return 1;
        return 2;
    }

    public static void setAppContext(Context c) {
        ctx = c.getApplicationContext();
    }

    private static File systemDir() {
        File d = dataRoot != null ? dataRoot : new File(ctx.getFilesDir(), EmuAssets.DATA_DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static volatile String activeUserSub = "";

    private static File userDir() {
        File base = new File(systemDir(), "user");
        File d = activeUserSub.isEmpty() ? base : new File(base, activeUserSub);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File resolve(String path) {
        if (path.startsWith("/")) return new File(path);
        String p = sanitize(path);
        // The cheat database is shared state, not per-profile data: the core
        // opens "User/usrcheat.dat" through the active profile directory, so
        // pin it to the base user dir instead of duplicating 13MB per profile.
        if (p.equals(USER_PREFIX + "usrcheat.dat")) return cheatDbFile();
        if (p.startsWith(SYS_PREFIX)) return new File(systemDir(), p.substring(SYS_PREFIX.length()));
        if (p.startsWith(USER_PREFIX)) return new File(userDir(), p.substring(USER_PREFIX.length()));
        throw new RuntimeException("Bad path prefix: " + path);
    }

    private static File cheatDbFile() {
        File d = new File(systemDir(), "user");
        if (!d.exists()) d.mkdirs();
        return new File(d, "usrcheat.dat");
    }

    public static void changeRom(String key, Uri uri, String realPath, String name) {
        activeRom = new RomLocation(key, uri, realPath, name);
    }

    public static NativePathHandle open(String path, String mode) {
        RomLocation rom = activeRom;
        if (rom.key != null && rom.key.equals(path)) {
            if (rom.uri != null) {
                try {
                    ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(rom.uri, "r");
                    if (pfd != null) {
                        String name = rom.name != null ? rom.name : path;
                        return new NativePathHandle(name, pfd.detachFd(), name);
                    }
                } catch (Exception e) {
                }
            }
            if (rom.realPath != null) return new NativePathHandle(rom.realPath,
                rom.name != null ? rom.name : new File(rom.realPath).getName());
        }
        synchronized (pathLocks[pathGroup(path)]) {
            return openData(path, mode, rom);
        }
    }

    private static NativePathHandle openData(String path, String mode, RomLocation rom) {
        if (dataRoot == null && path.startsWith(USER_PREFIX)) {
            NativePathHandle h = com.seedlessds.app.emu.SaveLocation.openHandle(
                ctx, rom.uri, rom.realPath, sanitize(path).substring(USER_PREFIX.length()), mode, activeUserSub, resolve(path));
            if (h != null) return h;
        }
        File f = resolve(path);
        String m = mode.replace("b", "");
        boolean write = m.contains("w") || m.contains("+") || m.contains("a");
        if (write) {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!f.exists()) {
                try { f.createNewFile(); } catch (IOException ignored) { }
            }
        }
        return new NativePathHandle(f);
    }

    public static boolean remove(String path) {
        synchronized (pathLocks[pathGroup(path)]) {
            return removeData(path, activeRom);
        }
    }

    private static boolean removeData(String path, RomLocation rom) {
        if (dataRoot == null && path.startsWith(USER_PREFIX)) {
            Boolean r = com.seedlessds.app.emu.SaveLocation.remove(
                ctx, rom.uri, rom.realPath, sanitize(path).substring(USER_PREFIX.length()), activeUserSub);
            if (r != null) { File i = resolve(path); if (i.exists()) i.delete(); return r; }
        }
        File f = resolve(path);
        return f.exists() && f.delete();
    }

    public static boolean rename(String from, String to) {
        int fromGroup = pathGroup(from);
        int toGroup = pathGroup(to);
        // Acquire cross-directory locks in one order, including uncommon moves
        // between backup and snapshot directories.
        synchronized (pathLocks[Math.min(fromGroup, toGroup)]) {
            synchronized (pathLocks[Math.max(fromGroup, toGroup)]) {
                return renameData(from, to, activeRom);
            }
        }
    }

    private static boolean renameData(String from, String to, RomLocation rom) {
        if (dataRoot == null && from.startsWith(USER_PREFIX) && to.startsWith(USER_PREFIX)) {
            Boolean r = com.seedlessds.app.emu.SaveLocation.rename(
                ctx, rom.uri, rom.realPath, sanitize(from).substring(USER_PREFIX.length()),
                sanitize(to).substring(USER_PREFIX.length()), activeUserSub);
            if (r != null) return r;
        }
        File a = resolve(from);
        if (!a.exists()) return false;
        File b = resolve(to);
        File parent = b.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try { android.system.Os.rename(a.getAbsolutePath(), b.getAbsolutePath()); return true; }
        catch (Exception e) { return false; }
    }

    private static String sanitize(String s) {
        String sep = File.separator;
        while (s.contains(sep + sep)) s = s.replace(sep + sep, sep);
        return s.startsWith(sep) ? s.substring(sep.length()) : s;
    }
}
