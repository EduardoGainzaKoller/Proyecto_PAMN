package com.ulpgc.walljumper.android;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.ulpgc.walljumper.WallJumperGame;
import com.ulpgc.walljumper.db.DatabaseService;
import com.ulpgc.walljumper.db.AuthService;
import com.badlogic.gdx.Gdx;


public class AndroidLauncher extends AndroidApplication {

    private static final int RC_LOGIN = 1001;

    private WallJumperGame wallJumperGame;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        DatabaseService dbService = new AndroidDatabaseService();
        authService = new FirebaseAuthService();


        wallJumperGame = new WallJumperGame(dbService, authService);


        if (authService.getCurrentUserId() == null) {

            startLoginActivity();
        } else {

            startGame();
        }
    }


    private void startGame() {
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;

        // Forzar orientación vertical
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initialize(wallJumperGame, configuration);
    }


    private void startLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        // Esperamos un resultado de vuelta
        startActivityForResult(intent, RC_LOGIN);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_LOGIN) {
            if (resultCode == RESULT_OK && data != null) {
                // Login exitoso
                final String userId = data.getStringExtra(LoginActivity.EXTRA_USER_ID);

                if (userId != null) {


                    startGame();


                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            wallJumperGame.onUserLoggedIn(userId);
                        }
                    });
                }
            } else {
                finish();
            }
        }
    }
}
