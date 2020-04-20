package com.journear.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.journear.app.R;
import com.journear.app.core.IsValid;
import com.journear.app.core.PersistentStore;
import com.journear.app.core.ServerFunctions;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    EditText email, password;
    Button login;
    final String logTag = "LoginActivity";

    Response.ErrorListener responseErrorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.i(logTag, "Server communication while trying to log in.");
        }
    };

    Response.Listener responseListener = new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            try {
                Log.d(logTag, "Received server response.");
                //Process os success response
                if (response.get("Message").toString().equals("Success")) {
                    afterLoginSuccess();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_login);
        email = findViewById(R.id.edit_email);
        password = findViewById(R.id.edit_password);
        login = findViewById(R.id.btn_login);

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                validateAndLogin();

            }
        });


    }

    private void validateAndLogin() {
        String emailString = email.getText().toString();
        String passwordString = password.getText().toString();

        if(validateInputs(emailString, passwordString))
            ServerFunctions.getInstance(LoginActivity.this).authenticate(emailString, passwordString,
                responseListener, responseErrorListener);
    }

    private boolean validateInputs(String email, String password) {
        return IsValid.email(email) & IsValid.password(password);
    }

    private void afterLoginSuccess() {
        // TODO : Move PersistenceStore call to LocalFunctions
        PersistentStore.getInstance(LoginActivity.this).setItem("currentUser", email.getText().toString(), true);


        Intent loginSuccessIntent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(loginSuccessIntent);
        finish();
    }
}




