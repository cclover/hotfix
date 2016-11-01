package com.example.chengchao.hotfixexample;

import android.os.Bundle;
import android.os.Process;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import com.example.hotfixlib.HotFix;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    Button btnBug;
    Button btnBugLib;
    Button btnFix;
    TestClass tc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        btnBug = (Button)findViewById(R.id.btnBug);
        btnBugLib = (Button)findViewById(R.id.btnBugLib);
        btnFix = (Button)findViewById(R.id.btnPatch);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        btnBug.setOnClickListener(this);
        btnBugLib.setOnClickListener(this);
        btnFix.setOnClickListener(this);

        tc = new TestClass();
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.btnBug){
            showToast(view, tc.show());
        }else if(view.getId() == R.id.btnBugLib){
            showToast(view, tc.function());
        }else if(view.getId() == R.id.btnPatch){
            getPatch(view);
        }
    }

    private  void getPatch(View view){
        //Get patch file from asset.
        if(HotFix.getPatch(getApplicationContext())) {
            showToast(view,  "Get Patch. Please restart the app!");
        }else{
            showToast(view,  "No patch file");
        }
    }

    private  void showToast(View view, String message){
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            Process.killProcess(Process.myPid());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}
