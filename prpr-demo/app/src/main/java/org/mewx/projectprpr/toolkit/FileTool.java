package org.mewx.projectprpr.toolkit;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;

/**
 * Created by MewX on 4/12/2016.
 * Light file tool.
 */
@SuppressWarnings("unused")
public class FileTool {
    private static final String TAG = FileTool.class.getSimpleName();

    public static boolean deleteFile(@NonNull String filePath) {
        Log.v(TAG, "Path: " + filePath);
        File file = new File(filePath);

        if (file.delete()) {
            Log.v(TAG, "Delete successfully.");
            return true;
        } else {
            Log.e(TAG, "Delete failed.");
            return false;
        }
    }

    public static void deleteFolder(@NonNull String folderPath) {
        File fileOrDirectory = new File(folderPath);
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteFolder(child.getAbsolutePath());
        fileOrDirectory.delete();
    }

    public static boolean existFile(@NonNull String path) {
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    public static boolean existFileAggressive(String path) {
        File file = new File(path);
        if(file.exists()) {
            if(file.length() != 0) return true;
            deleteFile(path); // delete if file empty
        }
        return false;
    }

    public static boolean existFolder(@NonNull String path) {
        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public static void copyFile(@NonNull String fromPath, @NonNull String toPath, Boolean forceWrite) {
        File fromFile = new File(fromPath);
        File toFile = new File(toPath);
        if (!fromFile.exists() || !fromFile.isFile() || !fromFile.canRead() || toFile.exists() && toFile.isDirectory())
            return;

        if (!toFile.getParentFile().exists()) toFile.getParentFile().mkdirs();
        if (toFile.exists() && forceWrite) toFile.delete();

        try {
            FileInputStream fosfrom = new FileInputStream(fromFile);
            FileOutputStream fosto = new FileOutputStream(toFile);

            byte[] bt = new byte[1024];
            int c;
            while ((c = fosfrom.read(bt)) > 0) fosto.write(bt, 0, c);
            fosfrom.close();
            fosto.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @NonNull
    public static byte[] loadFile(@NonNull String path) {
        // if file not exist, then return null
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            // load existing file
            int fileSize = (int) file.length(); // get file size
            try {
                FileInputStream in = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(in);

                // read all
                byte[] bs = new byte[fileSize];
                if(dis.read(bs, 0, fileSize) == -1)
                    return new byte[0];

                dis.close();
                in.close();
                return bs;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    public static boolean saveFile(@NonNull String filepath, @NonNull byte[] bs, boolean forceUpdate) {
        // if forceUpdate == true then update the file
        File file = new File(filepath);
        Log.v(TAG, "Path: " + filepath);
        if (!file.exists() || forceUpdate) {
            if (file.exists() && !file.isFile()) {
                Log.v(TAG, "Write failed0");
                return false; // is not a file
            }

            try {
                file.createNewFile(); // create file
                FileOutputStream out = new FileOutputStream(file); // trunc
                DataOutputStream dos = new DataOutputStream(out);

                // write all
                dos.write(bs);

                dos.close();
                out.close();
                Log.v("MewX-File", "Write successfully");
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("MewX-File", "Write failed1");
                return false;
            }
        }
        return true; // say it successful
    }

    public static boolean saveFile(@NonNull String filepath, @NonNull String str, boolean forceUpdate) {
        return FileTool.saveFile(filepath, str.getBytes(), forceUpdate);
    }

    @NonNull
    public static String loadFullFileContent(@NonNull String FileFullPath) {
        String h = "";
        try {
            byte[] b = loadFile(FileFullPath);
            if(b.length == 0) return "";
            h = new String(b, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return h;
    }

    public static boolean writeFullFileContent(@NonNull String FileFullPath,@NonNull String s) {
        return saveFile(FileFullPath, s, true);
    }

    public static String getFileNameFromFullPath(@NonNull String fullPath) {
        char separator = fullPath.contains("/") ? '/' : '\\';
        int start = fullPath.lastIndexOf(separator);
        return start == -1 ? fullPath : fullPath.substring(start + 1);
    }

    public static String[] getFileList(String fullPath) {
        File myDirectory = new File(fullPath);
        File[] directories = myDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile();
            }
        });

        // convert to file list
        String[] list = new String[directories.length];
        for (int i = 0; i < directories.length; i++) {
            list[i] = getFileNameFromFullPath(directories[i].getName());
        }
        return list;
    }

    public static String[] getFolderList(String fullPath) {
        File myDirectory = new File(fullPath);
        File[] directories = myDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        // convert to file list
        String[] list = new String[directories.length];
        for (int i = 0; i < directories.length; i++) {
            list[i] = getFileNameFromFullPath(directories[i].getName());
        }
        return list;
    }

    public static void writeFullSerializable(@NonNull String FileFullPath, @NonNull Serializable serializable) {
        ObjectOutputStream oos = null;
        FileOutputStream fout = null;
        try{
            fout = new FileOutputStream(FileFullPath, true);
            oos = new ObjectOutputStream(fout);
            oos.writeObject(serializable);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if(oos != null) oos.close();
                if(fout != null) fout.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Nullable
    public static Serializable loadFullSerializable(@NonNull String FileFullPath) {
        FileInputStream streamIn = null;
        ObjectInputStream objectinputstream = null;
        Serializable result = null;
        try {
            streamIn = new FileInputStream(FileFullPath);
            objectinputstream = new ObjectInputStream(streamIn);
            result = (Serializable) objectinputstream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(objectinputstream != null) objectinputstream.close();
                if(streamIn != null) streamIn.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return result;
    }
}
