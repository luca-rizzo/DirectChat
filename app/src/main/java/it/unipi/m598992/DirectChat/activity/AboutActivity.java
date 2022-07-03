package it.unipi.m598992.DirectChat.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import it.unipi.m598992.DirectChat.R;
import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //disattivo dark mode altrimenti la libreria sfrutterà le risorse a seconda che sia attivo o meno la modalità
        //dark nel cellulare
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        Element versionElement = new Element().setTitle("Version 1.0");
        Element creatorElement = new Element();
        creatorElement.setTitle(getString(R.string.created_by));
        creatorElement.setGravity(Gravity.CENTER_HORIZONTAL);
        View aboutPage = new AboutPage(this)
                .setImage(R.mipmap.ic_launcher).addItem(versionElement).setDescription(getString(R.string.app_description))
                .addEmail("lucalucasrizzo@gmail.com")
                .addGitHub("luca-rizzo", getString(R.string.github_page))
                .addItem(creatorElement)
                .create();

        setContentView(aboutPage);
    }
}