/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at
         http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */


package org.apache.cordova.geolocation;

import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.HashMap;

import javax.security.auth.callback.Callback;

public class Geolocation extends CordovaPlugin {
    
    private static final String TAG = "GeolocationPlugin";
    private static final int REQUEST_CHECK_SETTINGS = 1;

    private static final String ACTION_GET_PERMISSION = "getPermission";
    private static final String ACTION_BEGIN_NATIVE_WATCH = "beginNativeWatch";
    private static final String ACTION_CLEAR_NATIVE_WATCH = "clearNativeWatch";
    private static final String WATCH_OPTION_FREQUENCY = "frequency";
    private static final String WATCH_OPTION_ENABLE_HIGH_ACCURACY = "enableHighAccuracy";

    private static final String[] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION };

    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;
    private String mLastUpdateTime;

    private CallbackContext mPermissionRequestContext;
    private CallbackContext mWatcherContext;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {

        Activity activity = cordova.getActivity();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        mSettingsClient = LocationServices.getSettingsClient(activity);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mCurrentLocation = locationResult.getLastLocation();
                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                sendCurrentPositionToWatcher();
            }
        };
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        LOG.d(TAG, "We are entering execute");

        if (ACTION_GET_PERMISSION.equals(action))
        {
            return handlePermissionRequest(callbackContext);
        }

        if (ACTION_CLEAR_NATIVE_WATCH.equals(action)) {
            return handleClearNativeWatch(callbackContext);
        }

        if (ACTION_BEGIN_NATIVE_WATCH.equals(action) && args != null) {
            return handleBeginNativeWatch(callbackContext, args.optJSONObject(1));
        }

        return false;
    }

    @Override
    private void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if (mPermissionRequestContext != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    mPermissionRequestContext.sendPluginResult(result);
                    return;
                }

            }
            result = new PluginResult(PluginResult.Status.OK);
            mPermissionRequestContext.sendPluginResult(result);
        }
    }

    @Override
    public boolean hasPermisssion() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
                return false;
            }
        }
        return true;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */
    @Override
    public void requestPermissions(int requestCode)
    {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode != REQUEST_CHECK_SETTINGS) {
            return;
        }

        switch (resultCode) {
            case Activity.RESULT_OK:
                Log.i(TAG, "User agreed to make required location settings changes.");
                // Nothing to do. startLocationupdates() gets called in onResume again.
                break;
            case Activity.RESULT_CANCELED:
            default:
                rejectCurrentWatcherContext("User chose not to make required location settings changes.");
                break;
        }
    }

    private boolean handlePermissionRequest(CallbackContext callbackContext) {
        mPermissionRequestContext = callbackContext;
        if (hasPermisssion()) {
            PluginResult r = new PluginResult(PluginResult.Status.OK);
            mPermissionRequestContext.sendPluginResult(r);
        } else {
            PermissionHelper.requestPermissions(this, 0, permissions);
        }
        return true;
    }

    private boolean handleBeginNativeWatch(final CallbackContext callbackContext, final JSONObject watchOptions) {

        if (watchOptions != null) {
            replaceWatcherContext(callbackContext);
            startLocationUpdates(
                watchOptions.optBoolean(WATCH_OPTION_ENABLE_HIGH_ACCURACY, false),
                watchOptions.optLong(WATCH_OPTION_FREQUENCY, 5000)
            );

        } else {
            callbackContext.error("Missing required parameter watchOptions");
        }

        return true;
    }

    private boolean handleClearNativeWatch(final CallbackContext callbackContext) {
        replaceWatcherContext(null);
        callbackContext.success("watcher ID cleared");
        return true;
    }

    private void replaceWatcherContext(final CallbackContext callbackContext) {
        rejectCurrentWatcherContext("cleared");
        mWatcherContext = callbackContext;
    }

    private void rejectCurrentWatcherContext(String errorMessage) {
        if (mWatcherContext != null) {
            Log.e(TAG, "rejectCurrentWatcherContext(): " + errorMessage);
            mWatcherContext.error(errorMessage);
            mWatcherContext = null;
        }
    }

    private void sendCurrentPositionToWatcher() {

        if (mWatcherContext == null) {
            return;
        }

        JSONObject positionResult = createPositionResultJSON(mCurrentLocation, mLastUpdateTime);
        PluginResult r = new PluginResult(PluginResult.Status.OK, positionResult);
        r.setKeepCallback(true);
        mWatcherContext.sendPluginResult(r);
    }

    private static JSONObject createPositionResultJSON(Location location, String timestamp) {
        final JSONObject result = new JSONObject();
        result.put("coords", createCoordinatesObjectFromLocation(location));
        result.put("timestamp", timestamp);
        return result;
    }

    private static JSONObject createCoordinatesObjectFromLocation(Location location) {
        final JSONObject result = new JSONObject();
        if (location != null) {
            result.put("latitude", location.getLatitude());
            result.put("longitude", location.getLongitude());
            result.put("accuracy", location.getAccuracy());
            result.put("altitude", location.getAltitude());
            result.put("heading", location.getBearing());
            result.put("speed", location.getSpeed());
            result.put("altitudeAccuracy", location.getVerticalAccuracyMeters());
        }
        return result;
    }

    private void handleLocationSettingsCheckFailure(@NonNull Exception e) {

        int statusCode = ((ApiException) e).getStatusCode();
        Log.i(TAG, "handleLocationSettingsCheckFailure() statusCode = " + statusCode);

        switch (statusCode) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i(TAG,
                        "Location settings are not satisfied. Attempting to upgrade location settings");
                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().
                    ResolvableApiException rae = (ResolvableApiException) e;
                    rae.startResolutionForResult(cordova.getActivity(), REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException sie) {
                    rejectCurrentWatcherContext("PendingIntent unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                rejectCurrentWatcherContext("Location settings are inadequate, and cannot be fixed here. Fix in Settings.");
                break;
            default:
                rejectCurrentWatcherContext("Unknown error occurred while attempting to start location updates: " + statusCode);
                break;
        }
    }

    private void startLocationUpdates(boolean enableHighAccuracy, long frequency) {

        Log.i(TAG, "startLocationUpdates() enableHighAccuracy = " + enableHighAccuracy + ", frequency = " + frequency);

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setFastestInterval(frequency);
        locationRequest.setPriority(
            enableHighAccuracy 
            ? LocationRequest.PRIORITY_HIGH_ACCURACY 
            : LocationRequest.PRIORITY_LOW_POWER
        );

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        Activity activity = cordova.getActivity();

        OnSuccessListener<LocationSettingsResponse> onSuccess = new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                Log.i(TAG, "All location settings are satisfied.");
                mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper());
            }
        };

        OnFailureListener onFailure = new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                handleLocationSettingsCheckFailure(e);
            }
        };

        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener(activity, onSuccess)
            .addOnFailureListener(activity, onFailure);
    }
}
