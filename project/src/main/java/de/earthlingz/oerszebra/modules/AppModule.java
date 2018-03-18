package de.earthlingz.oerszebra.modules;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import de.earthlingz.oerszebra.BoardState;
import de.earthlingz.oerszebra.parser.Gameparser;

@Module
public class AppModule {

    Application mApplication;

    public AppModule(Application application) {
        mApplication = application;
    }

    @Provides
    @Singleton
    Application providesApplication() {
        return mApplication;
    }

    @Provides
    @Singleton
    BoardState providesBoardState() {
        return new BoardState();
    }

    @Provides
    @Singleton
    Gameparser providesGameparser() {
        return new Gameparser();
    }
}