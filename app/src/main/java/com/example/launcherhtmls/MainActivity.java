package com.example.launcherhtmls;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.chaquo.python.Python;
import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static boolean isServerRunning = false;
    // Carpeta fácil de encontrar en el teléfono
    private static final String APP_DIR = "/sdcard/Download/LocalWebApp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Crear UI simple
        TextView tv = new TextView(this);
        tv.setId(View.generateViewId());
        tv.setTextSize(18);
        tv.setPadding(40, 100, 40, 20);
        tv.setText("Buscando archivos en:\\n" + APP_DIR);

        Button btn = new Button(this);
        btn.setText("🚀 Lanzar App Web");
        btn.setVisibility(View.GONE);
        btn.setOnClickListener(v -> abrirJuego());

        androidx.appcompat.widget.LinearLayoutCompat layout = new androidx.appcompat.widget.LinearLayoutCompat(this);
        layout.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        layout.setGravity(View.CENTER_HORIZONTAL);
        layout.addView(tv);
        layout.addView(btn);
        setContentView(layout);

        // Verificar si existe el index.html
        File indexFile = new File(APP_DIR, "index.html");
        if (indexFile.exists()) {
            tv.setText(tv.getText() + "\\n\\n✅ index.html encontrado.\\nPresiona el botón para lanzar.");
            btn.setVisibility(View.VISIBLE);
            iniciarServidor();
        } else {
            tv.setText(tv.getText() + "\\n\\n❌ No se encontró index.html.\\nPor favor, crea la carpeta y pon tus archivos ahí.");
F.");
        }
    }

    private void iniciarServidor() {
        if (!isServerRunning) {
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(this));
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error Python: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            new Thread(() -> {
                try {
                    Python py = Python.getInstance();
                    PyObject serverModule = py.getModule("server");
                    serverModule.callAttr("start_server", APP_DIR);
                    isServerRunning = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "Error servidor: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private void abrirJuego() {
        String url+url = "http://localhost:8000";
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setShowTitle(true);
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
    }
}
