package com.dimtion.shaarlier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;


public class AddAccountActivity extends AppCompatActivity {

    private String urlShaarli;
    private String username;
    private String password;
    private String shortName;
    private ShaarliAccount account;
    private Boolean isDefaultAccount;
    private Boolean isValidateCert;

    private Boolean isEditing = false;


    private class networkHandler extends Handler{
        private final Activity mParent;

        public networkHandler(Activity parent){
            this.mParent = parent;
        }

        /**
         * Handle the arrival of a message coming from the network service.
         * @param msg the message given by the service
         */
        @Override
        public void handleMessage(Message msg){
            findViewById(R.id.tryConfButton).setVisibility(View.VISIBLE);
            findViewById(R.id.tryingConfSpinner).setVisibility(View.GONE);

            // Show the returned error
            switch (msg.arg1) {
                case NetworkService.NO_ERROR:
                    Toast.makeText(getApplicationContext(), R.string.success_test, Toast.LENGTH_LONG).show();
                    saveAccount();
                    finish();
                    break;
                case NetworkService.NETWORK_ERROR:
                    Toast.makeText(getApplicationContext(), R.string.error_connecting, Toast.LENGTH_LONG).show();
                    enableSendReport((Exception) msg.obj);
                    break;
                case NetworkService.TOKEN_ERROR:
                    Toast.makeText(getApplicationContext(), R.string.error_parsing_token, Toast.LENGTH_LONG).show();
                    enableSendReport(new Exception("TOKEN ERROR"));
                    break;
                case NetworkService.LOGIN_ERROR:
                    Toast.makeText(getApplicationContext(), R.string.error_login, Toast.LENGTH_LONG).show();
                    enableSendReport(new Exception("LOGIN ERROR"));
                    break;
                default:
                    Toast.makeText(getApplicationContext(), R.string.error_unknown, Toast.LENGTH_LONG).show();
                    enableSendReport(new Exception("UNKNOWN ERROR"));
            }
        }

        private void enableSendReport(final Exception error) {
            Button reportButton = (Button) findViewById(R.id.sendReportButton);
            reportButton.setVisibility(View.VISIBLE);
            reportButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mParent);

                    builder.setMessage(R.string.report_issue).setTitle("REPORT - Shaarlier");

                    final String extra = "Url Shaarli: " + urlShaarli;

                    builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            DebugHelper.sendMailDev(mParent, "REPORT - Shaarlier", DebugHelper.generateReport(error, mParent, extra));
                        }
                    });
                    builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_account);

        Intent intent = getIntent();
        long accountId = intent.getLongExtra("_id", -1);

        if (accountId != -1) {
            isEditing = true;
            AccountsSource accountsSource = new AccountsSource(getApplicationContext());
            try {
                account = accountsSource.getShaarliAccountById(accountId);
            } catch (Exception e) {
                account = null;
            }
            fillFields();
        } else {

            AccountsSource source = new AccountsSource(getApplicationContext());
            source.rOpen();
            List<ShaarliAccount> allAccounts = source.getAllAccounts();
            if (allAccounts.isEmpty()) {  // If it is the first account created
                CheckBox defaultCheck = (CheckBox) findViewById(R.id.defaultAccountCheck);
                defaultCheck.setChecked(true);
                defaultCheck.setEnabled(false);
            }
        }
    }


    /**
    * Fill the fields with the selected account when editing a new account
    */
    private void fillFields() {
        // Get the user inputs :
        ((EditText) findViewById(R.id.urlShaarliView)).setText(account.getUrlShaarli());
        ((EditText) findViewById(R.id.usernameView)).setText(account.getUsername());
        ((EditText) findViewById(R.id.passwordView)).setText(account.getPassword());
        ((EditText) findViewById(R.id.shortNameView)).setText(account.getShortName());
        ((CheckBox) findViewById(R.id.disableCertValidation)).setChecked(!account.isValidateCert());

        // Is it the default account ?
        SharedPreferences prefs = getSharedPreferences(getString(R.string.params), MODE_PRIVATE);
        this.isDefaultAccount = (prefs.getLong(getString(R.string.p_default_account), -1) == account.getId());
        ((CheckBox) findViewById(R.id.defaultAccountCheck)).setChecked(this.isDefaultAccount);

        findViewById(R.id.deleteAccountButton).setVisibility(View.VISIBLE);
    }

    /**
     * Handle the action of deletion : show a confirmation dialog then delete (if wanted)
     * @param view : The view needed for handling interface actions
     */
    public void deleteAccountAction(View view) {
        // Show dialog to confirm deletion
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.text_confirm_deletion_account));
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteAccount();
                finish();
            }
        });

        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Just dismiss the dialog
            }
        });
        builder.show();
    }

    /**
     * Delete the selected account from the database
     */
    private void deleteAccount() {
        AccountsSource source = new AccountsSource(getApplicationContext());
        source.wOpen();
        source.deleteAccount(this.account);
        source.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_add_account, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_validate) {
            tryAndSaveAction(findViewById(id));
        }

        return super.onOptionsItemSelected(item);
    }

    /**
    * Obviously hide the keyboard
    * From : http://stackoverflow.com/a/7696791/1582589
    */
    private void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    /**
     * Action which handle the press on the try and save button
     * @param view : needed for binding with interface actions
     */
    public void tryAndSaveAction(View view) {

        hideKeyboard();
        findViewById(R.id.tryingConfSpinner).setVisibility(View.VISIBLE);
        findViewById(R.id.tryConfButton).setVisibility(View.GONE);

        // Get the user inputs :
        final String urlShaarliInput = ((EditText) findViewById(R.id.urlShaarliView)).getText().toString();
        this.username = ((EditText) findViewById(R.id.usernameView)).getText().toString();
        this.password = ((EditText) findViewById(R.id.passwordView)).getText().toString();
        this.shortName = ((EditText) findViewById(R.id.shortNameView)).getText().toString();
        this.isDefaultAccount = ((CheckBox) findViewById(R.id.defaultAccountCheck)).isChecked();
        this.isValidateCert = !((CheckBox) findViewById(R.id.disableCertValidation)).isChecked();

        this.urlShaarli = NetworkManager.toUrl(urlShaarliInput);

        ((EditText) findViewById(R.id.urlShaarliView)).setText(this.urlShaarli);  // Update the view

        // Create a fake account :
        ShaarliAccount accountToTest = new ShaarliAccount();
        accountToTest.setUrlShaarli(this.urlShaarli);
        accountToTest.setUsername(this.username);
        accountToTest.setPassword(this.password);
        accountToTest.setValidateCert(this.isValidateCert);

        // Try the configuration :
        Intent i = new Intent(this, NetworkService.class);
        i.putExtra("action", "checkShaarli");
        i.putExtra("account", accountToTest);
        i.putExtra(NetworkService.EXTRA_MESSENGER, new Messenger(new networkHandler(this)));
        startService(i);
    }

    /**
     * Save a new account into the database,
     * should be called only if the account was verified.
     */
    private void saveAccount() {
        AccountsSource accountsSource = new AccountsSource(getApplicationContext());
        accountsSource.wOpen();
        try {
            if (isEditing) {  // Only update the database
                account.setUrlShaarli(this.urlShaarli);
                account.setUsername(this.username);
                account.setPassword(this.password);
                account.setShortName(this.shortName);
                account.setValidateCert(this.isValidateCert);
                accountsSource.editAccount(account);
            } else {
                this.account = accountsSource.createAccount(this.urlShaarli, this.username, this.password, this.shortName, this.isValidateCert);
            }
        } catch (Exception e) {
            Log.e("ENCRYPTION ERROR", e.getMessage());
        }

        accountsSource.close();

        // Set the default account if needed
        if (this.isDefaultAccount) {
            SharedPreferences prefs = getSharedPreferences(getString(R.string.params), MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putLong(getString(R.string.p_default_account), this.account.getId());
            editor.apply();
        }
    }
}