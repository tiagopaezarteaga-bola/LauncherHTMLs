package com.example.launcherhtmls;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LauncherHTMLs";
    private static final int PORT = 8000;
    private static final int PERM_CODE = 1001;

    private Python py;
    private PyObject serverModule;
    private Thread serverThread;
    private boolean serverRunning = false;

    private File currentDir;
    private String selectedHtmlName = null;
    private File selectedHtmlDir = null;

    private EditText etPath;
    private ListView lvFiles;
    private Button btnLaunch;
    private Button btnGo;
    private Button btnScan;
    private Button btnUp;
    private Button btnRefresh;
    private TextView tvStatus;
    private TextView tvServerStatus;

    private List<FileEntry> currentEntries = new ArrayList<>();

    /* ── Clase para entradas del listado ── */
    static class FileEntry {
        String displayName;
        String fullPath;
        boolean isDirectory;
        File file;
        FileEntry(String dn, String fp, boolean dir, File f) {
            displayName = dn; fullPath = fp; isDirectory = dir; file = f;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Iniciar Chaquopy */
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
        serverModule = py.getModule("server");

        /* Vincular vistas */
        etPath = findViewById(R.id.et_path);
        lvFiles = findViewById(R.id.lv_files);
        btnLaunch = findViewById(R.id.btn_launch);
        btnGo = findViewById(R.id.btn_go);
        btnScan = findViewById(R.id.btn_scan);
        btnUp = findViewById(R.id.btn_up);
        btnRefresh = findViewById(R.id.btn_refresh);
        tvStatus = findViewById(R.id.tv_status);
        tvServerStatus = findViewById(R.id.tv_server_status);

        /* Permisos */
        if (!checkPermissions()) {
            requestPermissions();
        }

        /* Directorio inicial */
        currentDir = findDefaultDir();
        etPath.setText(currentDir.getAbsolutePath());

        /* Botones */
        btnGo.setOnClickListener(v -> {
            File dir = new File(etPath.getText().toString().trim());
            if (dir.isDirectory()) {
                currentDir = dir;
                browseDirectory(currentDir);
            } else {
                toast("Carpeta no valida");
            }
        });

        btnUp.setOnClickListener(v -> {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.canRead()) {
                currentDir = parent;
                etPath.setText(currentDir.getAbsolutePath());
                browseDirectory(currentDir);
            }
        });

        btnRefresh.setOnClickListener(v -> browseDirectory(currentDir));

        btnScan.setOnClickListener(v -> scanRecursive());

        btnLaunch.setOnClickListener(v -> launchWebApp());

        /* Click en lista */
        lvFiles.setOnItemClickListener((parent, view, position, id) -> {
            FileEntry entry = currentEntries.get(position);
            if (entry.isDirectory) {
                currentDir = entry.file;
                etPath.setText(currentDir.getAbsolutePath());
                browseDirectory(currentDir);
            } else {
                /* Seleccionar HTML */
                selectedHtmlName = entry.file.getName();
                selectedHtmlDir = entry.file.getParentFile();
                tvStatus.setText("✅ Seleccionado: " + entry.file.getAbsolutePath());
                btnLaunch.setEnabled(true);
                /* Resaltar en la lista */
                for (int i = 0; i < lvFiles.getChildCount(); i++) {
                    lvFiles.getChildAt(i).setBackgroundColor(0x00000000);
                }
                view.setBackgroundColor(0x44E8A43A);
            }
        });

        /* Navegar al directorio inicial */
        browseDirectory(currentDir);
    }

    /* ══════════════════════════════════════
       NAVEGACIÓN DE ARCHIVOS
       ══════════════════════════════════════ */

    private void browseDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            tvStatus.setText("❌ Carpeta no encontrada: " + dir.getAbsolutePath());
            return;
        }

        currentDir = dir;
        etPath.setText(dir.getAbsolutePath());
        currentEntries.clear();

        File[] files = dir.listFiles();
        if (files == null) {
            tvStatus.setText("❌ Sin permiso para leer: " + dir.getAbsolutePath());
            return;
        }

        /* Ordenar: carpetas primero, luego archivos, alfabético */
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        /* Entrada "subir" */
        if (dir.getParentFile() != null) {
            currentEntries.add(new FileEntry(
                "⬆ ..", dir.getParentFile().getAbsolutePath(), true, dir.getParentFile()));
        }

        /* Carpetas */
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                currentEntries.add(new FileEntry(
                    "📁 " + f.getName(), f.getAbsolutePath(), true, f));
            }
        }

        /* Archivos HTML */
        boolean autoFound = false;
        for (File f : files) {
            if (!f.isDirectory() && isHtmlFile(f.getName())) {
                currentEntries.add(new FileEntry(
                    "🌐 " + f.getName(), f.getAbsolutePath(), false, f));
                /* Auto-seleccionar index.html */
                if (f.getName().equalsIgnoreCase("index.html") && !autoFound) {
                    selectedHtmlName = f.getName();
                    selectedHtmlDir = f.getParentFile();
                    btnLaunch.setEnabled(true);
                    tvStatus.setText("✅ index.html encontrado — listo para lanzar");
                    autoFound = true;
                }
            }
        }

        /* Si no hay HTMLs, mostrar todos los archivos para que se vea el contenido */
        if (currentEntries.stream().noneMatch(e -> !e.isDirectory)) {
            for (File f : files) {
                if (!f.isDirectory() && !f.getName().startsWith(".")) {
                    String icon = getFileIcon(f.getName());
                    currentEntries.add(new FileEntry(
                        icon + " " + f.getName(), f.getAbsolutePath(), false, f));
                }
            }
        }

        /* Adaptador */
        List<String> displayNames = new ArrayList<>();
        for (FileEntry e : currentEntries) {
            if (!e.isDirectory && !isHtmlFile(e.file.getName())) {
                displayNames.add(e.displayName + "  (no HTML)");
            } else {
                displayNames.add(e.displayName);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1, displayNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view.findViewById(android.R.id.text1);
                tv.setTextColor(0xFFE8E8E8);
                tv.setTextSize(14);
                tv.setPadding(8, 12, 8, 12);
                return view;
            }
        };
        lvFiles.setAdapter(adapter);

        if (!autoFound) {
            tvStatus.setText(currentEntries.stream()
                .filter(e -> !e.isDirectory).count() + " archivo(s) HTML en " + dir.getName());
        }
    }

    /* ── Escaneo recursivo ── */
    private void scanRecursive() {
        tvStatus.setText("🔍 Buscando archivos HTML...");
        btnScan.setEnabled(false);

        new Thread(() -> {
            List<FileEntry> results = new ArrayList<>();
            scanDir(currentDir, results, 0);

            runOnUiThread(() -> {
                currentEntries.clear();
                /* Ordenar por path */
                results.sort(Comparator.comparing(e -> e.fullPath.toLowerCase()));
                currentEntries.addAll(results);

                List<String> displayNames = new ArrayList<>();
                for (FileEntry e : currentEntries) {
                    /* Mostrar path relativo */
                    String rel = e.fullPath;
                    String base = currentDir.getAbsolutePath();
                    if (rel.startsWith(base)) {
                        rel = rel.substring(base.length() + 1);
                    }
                    displayNames.add("🌐 " + rel);
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    MainActivity.this, android.R.layout.simple_list_item_1, displayNames) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView tv = (TextView) view.findViewById(android.R.id.text1);
                        tv.setTextColor(0xFFE8E8E8);
                        tv.setTextSize(13);
                        tv.setPadding(8, 10, 8, 10);
                        return view;
                    }
                };
                lvFiles.setAdapter(adapter);
                tvStatus.setText("✅ " + results.size() + " archivo(s) HTML encontrados");
                btnScan.setEnabled(true);

                /* Auto-seleccionar si solo hay uno */
                if (results.size() == 1) {
                    FileEntry e = results.get(0);
                    selectedHtmlName = e.file.getName();
                    selectedHtmlDir = e.file.getParentFile();
                    btnLaunch.setEnabled(true);
                    tvStatus.setText("✅ Auto-seleccionado: " + e.file.getName());
                }
            });
        }).start();
    }

    private void scanDir(File dir, List<FileEntry> results, int depth) {
        if (depth > 6) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                scanDir(f, results, depth + 1);
            } else if (!f.isDirectory() && isHtmlFile(f.getName())) {
                results.add(new FileEntry(f.getName(), f.getAbsolutePath(), false, f));
            }
        }
    }

    /* ══════════════════════════════════════
       SERVIDOR Y LANZAMIENTO
       ══════════════════════════════════════ */

    private void launchWebApp() {
        if (selectedHtmlName == null || selectedHtmlDir == null) {
            toast("Selecciona un archivo HTML primero");
            return;
        }

        /* Detener servidor previo */
        stopServer();

        tvServerStatus.setText("⏳ Iniciando servidor en " + selectedHtmlDir.getAbsolutePath());
        btnLaunch.setEnabled(false);

        /* Iniciar servidor Python en hilo separado */
        serverThread = new Thread(() -> {
            try {
                serverModule.callAttr("main", PORT, selectedHtmlDir.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Server error", e);
                runOnUiThread(() -> {
                    tvServerStatus.setText("❌ Error servidor: " + e.getMessage());
                    btnLaunch.setEnabled(true);
                });
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        /* Esperar a que el servidor esté listo */
        new Thread(() -> {
            boolean ready = false;
            for (int i = 0; i < 40; i++) {
                try {
                    Thread.sleep(300);
                    Socket s = new Socket("localhost", PORT);
                    s.close();
                    ready = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (ready) {
                serverRunning = true;
                runOnUiThread(() -> {
                    tvServerStatus.setText("🟢 Servidor listo en puerto " + PORT);
                    btnLaunch.setEnabled(true);
                    openChromeTab();
                });
            } else {
                runOnUiThread(() -> {
                    tvServerStatus.setText("❌ Servidor no respondió tras 12s");
                    btnLaunch.setEnabled(true);
                });
            }
        }).start();
    }

    private void stopServer() {
        if (serverRunning || serverThread != null) {
            try {
                serverModule.callAttr("stop");
            } catch (Exception ignored) {}
            serverRunning = false;
            serverThread = null;
            /* Dar tiempo al puerto para liberarse */
            try { Thread.sleep(500); } catch (Exception ignored) {}
        }
    }

    private void openChromeTab() {
        String url = "http://localhost:" + PORT + "/" + selectedHtmlName;
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShowTitle(true);
            CustomTabsIntent intent = builder.build();
            intent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            /* Fallback: abrir en navegador normal */
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        }
    }

    /* ══════════════════════════════════════
       PERMISOS
       ══════════════════════════════════════ */

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERM_CODE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERM_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERM_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERM_CODE) {
            if (checkPermissions()) {
                browseDirectory(currentDir);
            } else {
                tvStatus.setText("❌ Permisos de almacenamiento denegados");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == PERM_CODE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                browseDirectory(currentDir);
            } else {
                tvStatus.setText("❌ Permisos de almacenamiento denegados");
            }
        }
    }

    /* ══════════════════════════════════════
       UTILIDADES
       ══════════════════════════════════════ */

    private File findDefaultDir() {
        File d = new File("/sdcard/Download");
        if (d.isDirectory()) return d;
        d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (d.isDirectory()) return d;
        d = Environment.getExternalStorageDirectory();
        if (d.isDirectory()) return d;
        return new File("/");
    }

    private boolean isHtmlFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".html") || n.endsWith(".htm");
    }

    private String getFileIcon(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".js")) return "📜";
        if (n.endsWith(".wasm")) return "⚙";
        if (n.endsWith(".css")) return "🎨";
        if (n.endsWith(".json")) return "📋";
        if (n.endsWith(".py")) return "🐍";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "📝";
        return "📄";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }
}
