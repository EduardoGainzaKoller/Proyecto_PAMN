package com.ulpgc.walljumper.android;

import android.os.Bundle;
import android.content.pm.ActivityInfo;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;


import com.ulpgc.walljumper.WallJumperGame;
import com.ulpgc.walljumper.android.AndroidDatabaseService;
import com.ulpgc.walljumper.db.DatabaseService;


public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        DatabaseService firebaseService = new AndroidDatabaseService();
        WallJumperGame game = new WallJumperGame(firebaseService);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;

        // Forzar orientación vertical
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);


        initialize(game, configuration);

    }
}
