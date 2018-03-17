package de.earthlingz.oerszebra.components;

import android.support.v4.app.FragmentActivity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {AppModule.class})
public interface NetComponent {
    // void inject(MainActivity activity);
    void inject(FragmentActivity fragment);
    // void inject(MyService service);
}