package com.khader.mlfati;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.*;
import java.util.HashSet;
import java.util.zip.*;
import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

@CapacitorPlugin(name = "FM")
public class FMPlugin extends Plugin {
    File f(String p) { if (p != null && p.startsWith("/")) return new File(p); return new File(Environment.getExternalStorageDirectory(), p == null ? "" : p); }

    @PluginMethod
    public void check(PluginCall c) {
        JSObject o = new JSObject();
        o.put("granted", Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager());
        c.resolve(o);
    }

    @PluginMethod
    public void ask(PluginCall c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        }
        c.resolve();
    }

    @PluginMethod
    public void list(PluginCall c) {
        File[] l = f(c.getString("path")).listFiles();
        if (l == null) { c.reject("لا يمكن قراءة المجلد (الصلاحية؟)"); return; }
        JSArray a = new JSArray();
        for (File x : l) {
            JSObject o = new JSObject();
            o.put("name", x.getName());
            o.put("dir", x.isDirectory());
            o.put("size", x.length());
            o.put("mtime", x.lastModified());
            a.put(o);
        }
        JSObject r = new JSObject();
        r.put("files", a);
        c.resolve(r);
    }

    @PluginMethod
    public void mkdir(PluginCall c) { f(c.getString("path")).mkdirs(); c.resolve(); }

    @PluginMethod
    public void del(PluginCall c) { rm(f(c.getString("path"))); c.resolve(); }

    @PluginMethod
    public void mv(PluginCall c) {
        File a = f(c.getString("from")), b = f(c.getString("to"));
        try { b.getParentFile().mkdirs(); if (!a.renameTo(b)) { copy(a, b); rm(a); } c.resolve(); } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void cp(PluginCall c) {
        try { copy(f(c.getString("from")), f(c.getString("to"))); c.resolve(); } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void write(PluginCall c) {
        try {
            File t = f(c.getString("path"));
            t.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(t);
            o.write(Base64.decode(c.getString("data"), Base64.DEFAULT));
            o.close();
            c.resolve();
        } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void thumb(PluginCall c) {
        try {
            String path = f(c.getString("path")).getPath();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int s = 1;
            int mx = c.getInt("max", 320);
            while (o.outWidth / s > mx || o.outHeight / s > mx) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(path, o);
            if (b == null) { c.reject("no bitmap"); return; }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 70, bo);
            JSObject r = new JSObject();
            r.put("data", "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            c.resolve(r);
        } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void roots(PluginCall c) {
        try {
            JSArray a = new JSArray();
            File prim = Environment.getExternalStorageDirectory();
            JSObject p0 = new JSObject();
            p0.put("path", "");
            p0.put("name", "الذاكرة الداخلية");
            p0.put("total", prim.getTotalSpace());
            p0.put("free", prim.getFreeSpace());
            a.put(p0);
            HashSet<String> seen = new HashSet<>();
            seen.add(prim.getAbsolutePath());
            File[] ds = getContext().getExternalFilesDirs(null);
            if (ds != null) for (File d : ds) {
                if (d == null) continue;
                String s = d.getAbsolutePath();
                int i = s.indexOf("/Android/data");
                if (i < 0) continue;
                addRoot(a, new File(s.substring(0, i)), seen);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                StorageManager sm = (StorageManager) getContext().getSystemService(Context.STORAGE_SERVICE);
                for (StorageVolume v : sm.getStorageVolumes()) {
                    File d = v.getDirectory();
                    if (d == null || v.isPrimary()) continue;
                    addRoot(a, d, seen);
                }
            }
            JSObject r = new JSObject();
            r.put("roots", a);
            c.resolve(r);
        } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    void addRoot(JSArray a, File d, HashSet<String> seen) throws Exception {
        String p = d.getAbsolutePath();
        if (seen.contains(p) || p.startsWith("/storage/emulated") || !d.exists()) return;
        seen.add(p);
        JSObject o = new JSObject();
        o.put("path", p);
        o.put("name", "بطاقة SD");
        o.put("total", d.getTotalSpace());
        o.put("free", d.getFreeSpace());
        a.put(o);
    }

    @PluginMethod
    public void zip(PluginCall c) {
        try {
            JSArray ps = c.getArray("paths");
            File out = f(c.getString("to"));
            out.getParentFile().mkdirs();
            ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
            for (int i = 0; i < ps.length(); i++) { File s = f(ps.getString(i)); addZip(z, s, s.getName()); }
            z.close();
            c.resolve();
        } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void unzip(PluginCall c) {
        try {
            File d = f(c.getString("to"));
            d.mkdirs();
            ZipInputStream z = new ZipInputStream(new FileInputStream(f(c.getString("path"))));
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                File t = new File(d, e.getName());
                if (!t.getCanonicalPath().startsWith(d.getCanonicalPath())) continue;
                if (e.isDirectory()) { t.mkdirs(); continue; }
                t.getParentFile().mkdirs();
                FileOutputStream o = new FileOutputStream(t);
                byte[] b = new byte[65536];
                int k;
                while ((k = z.read(b)) > 0) o.write(b, 0, k);
                o.close();
            }
            z.close();
            c.resolve();
        } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    void addZip(ZipOutputStream z, File x, String n) throws IOException {
        if (x.isDirectory()) {
            File[] l = x.listFiles();
            if (l != null) for (File y : l) addZip(z, y, n + "/" + y.getName());
        } else {
            z.putNextEntry(new ZipEntry(n));
            FileInputStream i = new FileInputStream(x);
            byte[] b = new byte[65536];
            int k;
            while ((k = i.read(b)) > 0) z.write(b, 0, k);
            i.close();
            z.closeEntry();
        }
    }

    void rm(File x) {
        if (x.isDirectory()) { File[] l = x.listFiles(); if (l != null) for (File y : l) rm(y); }
        x.delete();
    }

    void copy(File a, File b) throws IOException {
        if (a.isDirectory()) {
            b.mkdirs();
            File[] l = a.listFiles();
            if (l != null) for (File y : l) copy(y, new File(b, y.getName()));
        } else {
            b.getParentFile().mkdirs();
            InputStream i = new FileInputStream(a);
            OutputStream o = new FileOutputStream(b);
            byte[] buf = new byte[65536];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
            i.close();
            o.close();
        }
    }
}
