package ru.georgii.fonar;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.File;
import java.net.UnknownHostException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import ru.georgii.fonar.core.exception.FonarException;
import ru.georgii.fonar.core.exception.NotASupportedFonarServerException;
import ru.georgii.fonar.core.exception.NotEnoughFieldsException;
import ru.georgii.fonar.core.identity.UserIdentity;
import ru.georgii.fonar.core.server.Server;

public class SettingsActivity extends FonarActivity {

    EditText firstnameEditText;
    EditText lastnameEditText;
    EditText bioEditText;
    EditText nicknameEditText;
    EditText serverEditText;
    View profileSettingsView;

    CardView photoCardView;
    ImageView photoImageView;

    Button connectButton;

    SocketService service;

    private void enableControls(boolean f) {
        runOnUiThread(() -> {
            firstnameEditText.setEnabled(f);
            lastnameEditText.setEnabled(f);
            bioEditText.setEnabled(f);
            nicknameEditText.setEnabled(f);
            serverEditText.setEnabled(f);
            photoCardView.setEnabled(f);
            connectButton.setEnabled(f);
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {

            if (data.getData() == null) {
                return;
            }

            Uri uri = data.getData();
            System.out.println(uri);

            File file = new File(uri.getPath());
            RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

            (new Thread() {
                public void run() {

                    try {
                        UserIdentity identity = UserIdentity.getInstance(getApplicationContext());
                        service.getServerManager().requireCurrentServer().getRestClient(identity).updatePhoto(body);

                        runOnUiThread(() -> {
                            photoImageView.setImageURI(uri);
                            Toast.makeText(SettingsActivity.this, getString(R.string.success_avatar_updated), Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(SettingsActivity.this, getString(R.string.error_avatar_upload_failed), Toast.LENGTH_SHORT).show();
                        });
                    }

                }
            }).start();

        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        firstnameEditText = findViewById(R.id.firstnameEditText);
        lastnameEditText = findViewById(R.id.lastnameEditText);
        bioEditText = findViewById(R.id.bioEditText);
        nicknameEditText = findViewById(R.id.nicknameEditText);
        serverEditText = findViewById(R.id.serverEditText);
        profileSettingsView = findViewById(R.id.profileSettingsView);

        connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(this::onConnectButtonClicked);

        photoCardView = findViewById(R.id.photoCardView);
        photoImageView = findViewById(R.id.photoImageView);
        photoImageView.setOnClickListener((view) -> {
            if (service != null) {
                ImagePicker.with(this)
                        .crop()
                        .compress(128)
                        .maxResultSize(128, 128)
                        .start();
            }
        });

        enableControls(false);

    }

    @Override
    void onServiceConnected(SocketService service) {

        (new Thread() {
            public void run() {

                SettingsActivity.this.service = service;
                enableControls(true);

                try {
                    Server server = service.getServerManager().requireCurrentServer();
                    runOnUiThread(() -> {
                        serverEditText.setText(server.getUrl());
                    });

                    try {
                        getUserProfile(server);
                    } catch (FonarException e) {
                        e.printStackTrace();
                    }
                } catch (FonarException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        serverEditText.setText("https://fonar-messenger.herokuapp.com");
                        Toast.makeText(SettingsActivity.this, getString(R.string.error_server_connection_failed), Toast.LENGTH_SHORT).show();
                    });
                    enableControls(true);
                }


            }
        }).start();

    }

    void onConnectButtonClicked(View view) {

        Toast.makeText(SettingsActivity.this, getString(R.string.placeholder_connecting), Toast.LENGTH_SHORT).show();

        enableControls(false);

        (new Thread() {
            public void run() {

                Server s = new Server(serverEditText.getText().toString());
                try {
                    service.getServerManager().setCurrentServer(s, service.getUserIdentity());
                } catch (UnknownHostException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, getString(R.string.error_failed_resolve_host), Toast.LENGTH_SHORT).show();
                    });
                    enableControls(true);
                    return;
                } catch (NotASupportedFonarServerException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, getString(R.string.error_not_supported_fonar_server) + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    enableControls(true);
                    return;
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, getString(R.string.error_swapping_server_failed), Toast.LENGTH_SHORT).show();
                    });
                    enableControls(true);
                    return;
                }

                try {
                    saveUserProfile(service.getServerManager().requireCurrentServer());
                } catch (NotEnoughFieldsException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show();
                    });
                    enableControls(true);
                    return;
                } catch (FonarException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, getString(R.string.error_server_connection_failed), Toast.LENGTH_SHORT).show();
                    });
                    enableControls(true);
                    return;
                }

                enableControls(true);
                finish();

            }
        }).start();
    }

    void getUserProfile(Server server) throws FonarException {

        enableControls(false);

        try {

            UserIdentity me = UserIdentity.getInstance(getApplicationContext());

            runOnUiThread(() -> {
                firstnameEditText.setText(me.getFirstname());
                lastnameEditText.setText(me.getLastname());
                bioEditText.setText(me.getBio());
                nicknameEditText.setText(me.getNickname());
                photoImageView.setImageBitmap(me.getAvatar());
                profileSettingsView.setVisibility(View.VISIBLE);
            });

            enableControls(true);

        } catch (Exception e) {

            enableControls(true);
            e.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(SettingsActivity.this, getString(R.string.error_server_connection_failed), Toast.LENGTH_SHORT).show();
                //profileSettingsView.setVisibility(View.GONE);
            });
            throw new FonarException("Failed to save current profile.", e);
        }

    }

    void saveUserProfile(Server server) throws FonarException {

        try {

            if ((firstnameEditText.getText().toString().length() == 0) || (nicknameEditText.getText().length() == 0)) {
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show();
                });

                throw new NotEnoughFieldsException("Not enough fields filled.");
            }

            UserIdentity me = UserIdentity.getInstance(getApplicationContext());
            me.setFirstname(firstnameEditText.getText().toString());
            me.setLastname(lastnameEditText.getText().toString());
            me.setBio(bioEditText.getText().toString());
            me.setNickname(nicknameEditText.getText().toString());
            me.save(getApplicationContext());

            me.updateOnServer(server);

            enableControls(true);

        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(SettingsActivity.this, getString(R.string.error_failed_save_profile), Toast.LENGTH_SHORT).show();
                //profileSettingsView.setVisibility(View.GONE);
            });
            throw new FonarException("Failed to save current profile.", e);
        }

    }

}