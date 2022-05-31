package com.example.aplicatiesenzori2021;

import static android.graphics.Color.*;
import static java.lang.System.currentTimeMillis;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.PrimitiveIterator;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {
    private Sensor sensorG, sensorNeproc;
    private Button salvareButton, alarmaButton;

    //indicator stare pornire monitorizare
    boolean monitorizarePornita =false, oldMonitorizarePornita=false;
    boolean alarmaActiva =false;

    //BarChart initializare
    BarChart chart;
    int dimLista =50;
    float groupSpace = 0.1f;
    float barSpace = 0.5f; // x2 dataset
    float barWidth = 1f; // x2 dataset
    ArrayList<BarEntry> entriesX = new ArrayList<>();
    ArrayList<BarEntry> rawentries = new ArrayList<>();
    ArrayList<BarEntry> rawentriesZ = new ArrayList<>();
    //endregion BarChart

    TextView textMax;//declarare fereastra afisare texxt
    StringBuilder sb = new StringBuilder();//aici se coleteaza textul care va fi salvat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ascundere initiala butoane in afara de start
        salvareButton = (Button) findViewById(R.id.stopbutton);
        salvareButton.setVisibility(View.INVISIBLE);
        alarmaButton = (Button) findViewById(R.id.button);
        alarmaButton.setVisibility(View.INVISIBLE);

        //BarChart identificare
        chart = (BarChart) findViewById(R.id.bargraph); //getApplicationContext()
        //endbarchart

        textMax = (TextView) findViewById(R.id.max);//mesaje text ecran

        SensorManager sensorManager;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        //The gravity sensor provides a three dimensional vector indicating the direction and magnitude of gravity.
        sensorG = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (sensorG != null) {//verificare ca senzorul exista
            Log.d("listasenzori","sensorG");
            //pornirea se face la apasare buton
        }

        //senzor acceleratie !! in Android 12 (API level 31) or higher, this sensor is rate-limited.
        sensorNeproc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);//
        if (sensorNeproc != null) {//verifcare ca senzorul exista
            Log.d("listasenzori","sensorNeproc");
            //pornirea se face dupa apasare buton...
        }
    }

    public long lastUpdate=0;
    float gx,gy,gz,x,y,z,oldXYZ, oldG;
    //date de la senzor hardware de acceleratie
    SensorEventListener accSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            try {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    //valori brute citite
                    x = event.values[0];
                    y = event.values[1];
                    z = event.values[2];

                    Date now = new Date();//inregistrare moment
                    if(monitorizarePornita!=oldMonitorizarePornita){
                        //butonul a fost apasat dupa o oprire

                        Log.d("listasenzori", "primaacc");
                        oldXYZ=x+y+z;
                        oldMonitorizarePornita=monitorizarePornita;//salvare stare
                    }

                    //detectie impact
                    float newXYZ=(float) Math.sqrt(x*x + y*y + z*z);
                    float detectie;
                    if(oldXYZ==0)  detectie=1;
                    else detectie=newXYZ / oldXYZ;//previne impartire la 0

                    Log.d("listasenzori", String.valueOf(detectie));
                    if(detectie > 5) {
                        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_NOTIFICATION));
                        mp.start();
                        //activeaza butonul de oprire alarma
                        alarmaButton = (Button) findViewById(R.id.button);
                        alarmaButton.setVisibility(View.VISIBLE);
                        Log.d("listasenzori", "pornire functie");
                        //seteaza variabila pentru verificare apasare buton
                        alarmaActiva = true;
                        //executa functia Verificare1 dupa 20 de secunde
                        (new Handler()).postDelayed(this::Verificare1, 5000);
                    }
                    oldXYZ=newXYZ;//pt iteratia urmatoare

                    sb.append("||" + x + "|" + y + "|" + z + "|" + gx + "|" + gy + "|" + gz + "|" + String.valueOf(now.getTime()) + "||" + "\r\n");

                    //region MPAndroidChart
                    rawentries.add(new BarEntry(rawentries.size(), (int) y));
                    rawentriesZ.add(new BarEntry(rawentriesZ.size(), (int) z));
                    entriesX.add(new BarEntry(entriesX.size(), (int) x));//todo: handle double
                    //truncare lista pt performanta...
                    int k = entriesX.size();

                    if (k > dimLista) {
                        entriesX.subList(0, 1).clear();
                        rawentries.subList(0, 1).clear();
                        rawentriesZ.subList(0, 1).clear();
                    }

                    BarDataSet dataset = new BarDataSet(entriesX, "X");
                    dataset.setColor(BLUE);
                    BarDataSet rawdataset = new BarDataSet(rawentries, "Y");
                    rawdataset.setColor(RED);
                    BarDataSet rawdatasetZ = new BarDataSet(rawentriesZ, "Y");
                    rawdatasetZ.setColor(GREEN);

                    BarData data = new BarData(dataset, rawdataset, rawdatasetZ);
                    data.setBarWidth(barWidth);
                    YAxis rightAxis = chart.getAxisRight();
                    rightAxis.setDrawGridLines(false);

                    chart.setData(data);//chart.setData(rawdata);
                    chart.groupBars(1f, groupSpace, barSpace);

                    chart.notifyDataSetChanged();//
                    //chart.setVisibleXRange(30,30); //
                    //go to last entries
                    chart.moveViewToX(((entriesX.size() - dimLista) > 0) ? (entriesX.size() - dimLista) : 0);
                    //endregion MPAndroidChart
                }

            } catch (Throwable ex) {
                Log.e("Exception", ex.toString());
            }
        }

        private void Verificare1() {
            if(alarmaActiva)
            {
                Log.d("listasenzori", "verificare 20secunde");
                //sunet
                MediaPlayer mp = MediaPlayer.create(getApplicationContext(), RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_NOTIFICATION));
                mp.start();
                //asteapta inca 10 secunde, verifica daca nu e apasat butonul de anulare si trimite email
                (new Handler()).postDelayed(this::trimiteEmail, 5000);
            }
        }
        public void trimiteEmail() {
            if(alarmaActiva)
            {
                //trimite email catre adresa prestabilita (tb testata inainte pentru a nu porni selectia de aplicatii de email!)
                //trebuie setata aplicatia de email pe Android
                Log.i("email", "verificare 10secunde mesaj trimis");
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("message/rfc822");
                i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"recipient@example.com"});//destinatie
                i.putExtra(Intent.EXTRA_SUBJECT, "Detectie cadere");
                i.putExtra(Intent.EXTRA_TEXT   , "data: " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                try {
                    startActivity(Intent.createChooser(i, "Trimite mail..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(getApplicationContext(), "Nu sunt instalati clienti de email.", Toast.LENGTH_SHORT).show();
                }
            }
            alarmaActiva=false;
            //ascunde buton alarma
            alarmaButton = (Button) findViewById(R.id.button);
            alarmaButton.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //TODO
        }
    };

    //date senzor acceleratie gravitationala
    SensorEventListener sensorGSensorEventListener = new SensorEventListener(){
       @Override
        public void onSensorChanged(SensorEvent event) {
           try {
               gx = event.values[0];
               gy = event.values[1];
               gz = event.values[2];
               Date now = new Date();//inregistrare moment
               if (monitorizarePornita != oldMonitorizarePornita) {
                   Log.d("listasenzori", "primaG");
                   //butonul a fost apasat dupa o oprire
                   oldG = gx + gy + gz;
                   oldMonitorizarePornita = monitorizarePornita;//salvare stare
               }

               //detectie impact
               float newG = (float) Math.sqrt(gx*gx + gy*gy + gz*gz);
               float detectie;
               if(oldG==0)  detectie=1;
               else detectie=newG / oldG;//previne impartire la 0

               Log.d("listasenzori", String.valueOf(detectie));

               if (detectie > 5) {
                   MediaPlayer mp = MediaPlayer.create(getApplicationContext(), RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_NOTIFICATION));
                   mp.start();
                   //activeaza butonul de oprire alarma
                   alarmaButton = (Button) findViewById(R.id.button);
                   alarmaButton.setVisibility(View.VISIBLE);
                   Log.d("listasenzori", "pornire functie");
                   //seteaza variabila pentru verificare apasare buton
                   alarmaActiva = true;
                   //executa functia Verificare1 dupa 20 de secunde
                   (new Handler()).postDelayed(this::Verificare1, 5000);
               }
               oldG = newG;//pt iteratia urmatoare
               sb.append("||" + x + "|" + y + "|" + z + "|" + gx + "|" + gy + "|" + gz + "|" + String.valueOf(now.getTime()) + "||" + "\r\n");


               //region MPAndroidChart
               rawentries.add(new BarEntry(rawentries.size(), (int) gx));
               rawentriesZ.add(new BarEntry(rawentriesZ.size(), (int) gy));
               entriesX.add(new BarEntry(entriesX.size(), (int) gz));//todo: handle double
               //truncate lists for performance...
               int k = entriesX.size();

               if (k > dimLista) {
                   entriesX.subList(0, 1).clear();
                   rawentries.subList(0, 1).clear();
                   rawentriesZ.subList(0, 1).clear();
               }

               BarDataSet dataset = new BarDataSet(entriesX, "X");
               dataset.setColor(BLUE);
               BarDataSet rawdataset = new BarDataSet(rawentries, "Y");
               rawdataset.setColor(RED);
               BarDataSet rawdatasetZ = new BarDataSet(rawentriesZ, "Y");
               rawdatasetZ.setColor(GREEN);

               BarData data = new BarData(dataset, rawdataset, rawdatasetZ);
               data.setBarWidth(barWidth);
               YAxis rightAxis = chart.getAxisRight();
               rightAxis.setDrawGridLines(false);

               chart.setData(data);//chart.setData(rawdata);
               chart.groupBars(1f, groupSpace, barSpace);

               chart.notifyDataSetChanged();//
               //chart.setVisibleXRange(30,30); //
               //go to last entries
               chart.moveViewToX(((entriesX.size() - dimLista) > 0) ? (entriesX.size() - dimLista) : 0);
               //endregion MPAndroidChart

           } catch (Throwable ex) {
               Log.e("Exception", ex.toString());
           }
       }

        private void Verificare1() {
            if(alarmaActiva)
            {
                Log.d("listasenzori", "verificare 20secunde");
                //sunet
                MediaPlayer mp = MediaPlayer.create(getApplicationContext(), RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_NOTIFICATION));
                mp.start();
                //asteapta inca 10 secunde, verifica daca nu e apasat butonul de anulare si trimite email
                (new Handler()).postDelayed(this::trimiteEmail, 5000);
            }
        }
        public void trimiteEmail() {
            if(alarmaActiva)
            {
                //trimite email catre adresa prestabilita (tb testata inainte pentru a nu porni selectia de aplicatii de email!)
                //trebuie setata aplicatia de email pe Android
                Log.d("listasenzori", "verificare 10secunde mesaj trimis");
              /*  Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("message/rfc822");
                i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"recipient@example.com"});//destinatie
                i.putExtra(Intent.EXTRA_SUBJECT, "Detectie cadere");
                i.putExtra(Intent.EXTRA_TEXT   , "data: " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                try {
                    startActivity(Intent.createChooser(i, "Trimite mail..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(getApplicationContext(), "Nu sunt instalati clienti de email.", Toast.LENGTH_SHORT).show();
                }*/
            }
            alarmaActiva=false;
            //ascunde buton alarma
            alarmaButton = (Button) findViewById(R.id.button);
            alarmaButton.setVisibility(View.INVISIBLE);
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private Switch swButton;//PT SELECRIE SENZOR G SAU ACC

    public void StopStartButon(View view) {
        swButton = (Switch) findViewById(R.id.switch2);
        Boolean switchState = swButton.isChecked();
        SensorManager sensorManager;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);


        if(monitorizarePornita){
            if(switchState) {
                Log.d("listasenzori", "Folosire G");
                sensorManager.unregisterListener(sensorGSensorEventListener);
                //ARATA SWITCH
                swButton = (Switch) findViewById(R.id.switch2);
                swButton.setVisibility(View.VISIBLE);
                //modifica text buton start
                salvareButton = (Button) findViewById(R.id.startbutton);
                salvareButton.setText("Start");
                monitorizarePornita=false;
            }
            else {
                Log.d("listasenzori", "Folosire Acc");
                sensorManager.unregisterListener(accSensorEventListener);
                //ARATA SWITCH
                swButton = (Switch) findViewById(R.id.switch2);
                swButton.setVisibility(View.VISIBLE);
                //modifica text buton start
                salvareButton = (Button) findViewById(R.id.startbutton);
                salvareButton.setText("Start");
                monitorizarePornita=false;
            }
        }
        else{
            if(switchState) {
                sensorManager.registerListener(sensorGSensorEventListener, sensorG, SensorManager.SENSOR_DELAY_NORMAL);//SENSOR_DELAY_NORMAL
                //arata buton salvare
                salvareButton = (Button) findViewById(R.id.stopbutton);//buton salvare
                salvareButton.setVisibility(View.VISIBLE);
                //ascunde SWITCH
                swButton = (Switch) findViewById(R.id.switch2);
                swButton.setVisibility(View.INVISIBLE);
                //modifica text buton start sa devina stop
                salvareButton = (Button) findViewById(R.id.startbutton);
                salvareButton.setText("Stop");
                //pentru a sti starea curenta apasare buton
                monitorizarePornita = true;
            }
            else {
                sensorManager.registerListener(accSensorEventListener, sensorNeproc, SensorManager.SENSOR_DELAY_FASTEST);//SENSOR_DELAY_NORMAL

                //arata buton salvare
                salvareButton = (Button) findViewById(R.id.stopbutton);//buton salvare
                salvareButton.setVisibility(View.VISIBLE);
                //ascunde SWITCH
                swButton = (Switch) findViewById(R.id.switch2);
                swButton.setVisibility(View.INVISIBLE);
                //modifica text buton start sa devina stop
                salvareButton = (Button) findViewById(R.id.startbutton);
                salvareButton.setText("Stop");
                //pentru a sti starea curenta apasare buton
                monitorizarePornita=true;
            }
        }
    }

    public void SaveButon(View view) {
        Log.i("Acc",sb.toString());//scriere date senzor in log informatii

        String wheresaved="";

        //salvare date in clipboard
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {//pt. Android < vers 7
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(sb.toString());
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", sb.toString());
                clipboard.setPrimaryClip(clip);
            }
            wheresaved+="Clipboard ";//success
        } catch(Throwable ex) {
            Log.e("Exception", ex.toString());
        }

        //incercare salvare in fisier
        try {
            //denumire fisier sa includa oraminutseconda
            //File file = new File(String.format("%s/datesalvate%d.csv", path, currentTimeMillis() / 1000L));
            Save2File();

            wheresaved+=" File ";//success
        } catch(Throwable ex) {
            Log.e("Exception", ex.toString());
        }

        //afisare mesaj pe ecran
        Toast.makeText(getApplicationContext(), (wheresaved.equals("")?"Error Saving":"Saved: "+wheresaved), Toast.LENGTH_LONG).show();
    }

    public  void Save2File() {
        String path2 = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();


        // Create the folder.
        File folder = new File(path2);
        folder.mkdirs();

        // Create the file.
        File file = new File(folder, String.format("data%d.csv", currentTimeMillis() / 1000L));
        try {
            try (FileWriter writer = new FileWriter(file)) {
                writer.append(sb.toString());
                writer.flush();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        sb = new StringBuilder();//initializare nou StringBuilder pt plasare noi date
    }

    public void anulareAlarma(View view) {
        alarmaActiva=false;
        //ascunde buton
        alarmaButton = (Button) findViewById(R.id.button);
        alarmaButton.setVisibility(View.INVISIBLE);
    }
}

