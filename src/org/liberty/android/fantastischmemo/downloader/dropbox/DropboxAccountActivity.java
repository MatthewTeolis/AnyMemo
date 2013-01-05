package org.liberty.android.fantastischmemo.downloader.dropbox;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.liberty.android.fantastischmemo.AMActivity;
import org.liberty.android.fantastischmemo.AMPrefKeys;
import org.liberty.android.fantastischmemo.R;
import org.liberty.android.fantastischmemo.downloader.dropbox.DropboxOAuthTokenRetrievalDialogFragment;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class DropboxAccountActivity extends AMActivity {
	private SharedPreferences settings;
	private SharedPreferences.Editor editor;

	private String oatuhAccessToken;
	private String oauthAccessTokenSecret;
	private static final String ACCESS_TOKEN_URL = "https://api.dropbox.com/1/oauth/access_token";

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		editor = settings.edit();

		oatuhAccessToken = settings.getString(AMPrefKeys.DROPBOX_AUTH_TOKEN, null);
		oauthAccessTokenSecret = settings.getString(AMPrefKeys.DROPBOX_AUTH_TOKEN_SECRET, null);

		 if (oatuhAccessToken == null || oauthAccessTokenSecret == null) {
			 showGetTokenDialog();
		 } else {
		     onAuthenticated(oatuhAccessToken, oauthAccessTokenSecret);
		 }
		 
		 setContentView(R.layout.spreadsheet_list_screen);
	}

	private void showGetTokenDialog() {
		DropboxOAuthTokenRetrievalDialogFragment tokenFetchAuthDialog = new DropboxOAuthTokenRetrievalDialogFragment();
		tokenFetchAuthDialog.setAuthCodeReceiveListener(authCodeReceiveListener);
		tokenFetchAuthDialog.show(getSupportFragmentManager(), TAG);
	}

	private DropboxOAuthTokenRetrievalDialogFragment.AuthCodeReceiveListener authCodeReceiveListener = new DropboxOAuthTokenRetrievalDialogFragment.AuthCodeReceiveListener() {
		public void onAuthCodeReceived(String uid) {
			GetAccessTokenTask task = new GetAccessTokenTask();
			task.execute(uid);
		}

		public void onAuthCodeError(String error) {
			 showAuthErrorDialog(error);
		}

		public void onCancelled() {
			finish();
		}
	};

	private class GetAccessTokenTask extends AsyncTask<String, Void, Boolean> {
		private ProgressDialog progressDialog;

		@Override
		public void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(DropboxAccountActivity.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setTitle(getString(R.string.loading_please_wait));
			progressDialog.setMessage(getString(R.string.loading_auth_text));
			progressDialog.setCancelable(false);
			progressDialog.show();
		}

		@Override
		public Boolean doInBackground(String... accessCodes) {
			BufferedReader reader = null;
			try {

				HttpClient httpClient = new DefaultHttpClient();
				HttpPost httpPost = new HttpPost(ACCESS_TOKEN_URL);
				httpPost.setHeader("Authorization", DropboxUtils.buildOAuthAccessHeader());
				
				HttpResponse response = null;
				response = httpClient.execute(httpPost);
				HttpEntity entity = response.getEntity();
				
				InputStream instream = entity.getContent();
				reader = new BufferedReader(new InputStreamReader(instream));
				String result = reader.readLine();
				String[] parsedResult = result.split("&");
				oauthAccessTokenSecret = parsedResult[0].split("=")[1];
				oatuhAccessToken = parsedResult[1].split("=")[1];

				return true;
			} catch (Exception e) {
				Log.e(TAG, "Error redeeming access token", e);
			}
			return false;
		}

		@Override
		public void onPostExecute(Boolean tokenObtained) {
			progressDialog.dismiss();
			if (tokenObtained) {
			    editor.putString("dropbox_auth_token", oatuhAccessToken);
			    editor.putString("dropbox_auth_token_secret", oauthAccessTokenSecret);
			    editor.commit();
		        onAuthenticated(oatuhAccessToken, oauthAccessTokenSecret);
			} else {
			    showAuthErrorDialog(getString(R.string.dropbox_token_failure_text));
			}
		}
	}
	
	
    private void showAuthErrorDialog(String error) {
        String errorMessage = getString(R.string.auth_error_text);
        if (error != null) {
            errorMessage += " " + error;
        }
        new AlertDialog.Builder(DropboxAccountActivity.this)
            .setTitle(R.string.error_text)
            .setMessage(errorMessage)
            .setPositiveButton(R.string.back_menu_text, new DialogInterface.OnClickListener() { 
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
        .show();
    }
    
    protected void onAuthenticated(final String authToken, final String authTokenSecret) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment newFragment = new DownloadDBFileListFragment(authToken, authTokenSecret);
        ft.add(R.id.spreadsheet_list, newFragment);
        ft.commit();
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dropbox_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.upload:{
                startActivity(new Intent(this, UploadDropboxScreen.class));
                return true;
            }
            case R.id.logout:{
                invalidateSavedToken();
                finish();
                return true;
            }
        }
        return false;
    }
    
    
    private void invalidateSavedToken() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(AMPrefKeys.DROPBOX_AUTH_TOKEN, null);
        editor.putString(AMPrefKeys.DROPBOX_AUTH_TOKEN_SECRET, null);
        editor.commit();
    }
}
