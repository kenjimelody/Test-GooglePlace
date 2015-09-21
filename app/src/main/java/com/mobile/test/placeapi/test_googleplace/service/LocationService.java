package com.mobile.test.placeapi.test_googleplace.service;

import java.util.List;
import java.util.Locale;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import java.io.IOException;

import android.app.Service;
import android.os.AsyncTask;
import android.widget.Toast;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.provider.Settings;
import android.content.DialogInterface;
import android.annotation.SuppressLint;
import android.location.LocationManager;
import android.location.LocationListener;

import com.mobile.test.placeapi.test_googleplace.R;

public class LocationService extends Service implements LocationListener {

	private boolean LOGGER = false;
	public static final String TAG = "BroadcastService";
    public static final String BROADCAST_ACTION = "com.infratrans.rescue.service.location_tracker";
    
    Handler mHandler;
    
    private Location location = null;
	private LocationManager lm = null;
	private boolean gpsEnabled = false;
	
	private String speed = "";
	private String latitude = "";
	private String longitude = "";
	private String address_text = "";
	
	private static final int UPDATE_ADDRESS = 1;
	private static final int UPDATE_LATLNG = 2;

	private static final int TEN_SECONDS = 10000;
	private static final int TEN_METERS = 10;
	private static final int TWO_MINUTES = 1000 * 60 * 2;

	private boolean mUseFine;
	private boolean mUseBoth;
	private boolean mGeocoderAvailable;

	private static final String KEY_FINE = "use_fine";
	private static final String KEY_BOTH = "use_both";
    
    @Override
	public void onDestroy() {
    	
    	lm.removeUpdates(this);
    	super.onDestroy();
    }
    
    @Override
	public void onCreate() {
    	
    	super.onCreate();
    	
    	mUseFine = false;
		mUseBoth = false;
		
		prepareingLocation();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	
    	super.onStartCommand(intent, flags, startId);
        
    	LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

		if (!gpsEnabled) {

			Intent intent_broadcast = new Intent(BROADCAST_ACTION);
			intent_broadcast.putExtra("opensetting", true);
			LocationService.this.sendBroadcast(intent_broadcast);
			
		} else {

			useCoarseFineProviders();
		}
    	
        return START_STICKY;
    }
    
    @SuppressLint("NewApi")
	private void prepareingLocation() {
 		
		mGeocoderAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Geocoder.isPresent();

		mHandler = new Handler() {
			
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case UPDATE_ADDRESS:
					//Global.printLog(LOGGER, "mAddress", (String) msg.obj);
					sendBroadcast(latitude, longitude, speed);
					break;
				case UPDATE_LATLNG:
					//Global.printLog(LOGGER, "mLatLng", (String) msg.obj);
					break;
				}
			}
		};

		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
 	}

   	private void enableLocationSettings() {

   		Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
   		startActivity(settingsIntent);
   	}
    
	@Override
	public IBinder onBind(Intent intent) {

		return null;
	}

	@Override
	public void onLocationChanged(Location location) {

		updateUILocation(location);
	}

	@Override
	public void onProviderDisabled(String provider) { }

	@Override
	public void onProviderEnabled(String provider) { }

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) { }
	
	private void settingupLocation() {

 		Location gpsLocation = null;
 		Location networkLocation = null;
 		
 		if(lm != null)
 			lm.removeUpdates(this);
 		
 		
 		if (mUseFine) {

 			gpsLocation = requestUpdatesFromProvider(LocationManager.GPS_PROVIDER, R.string.not_support_gps);
 			
 			if (gpsLocation != null)
 				updateUILocation(gpsLocation);

 		} else if (mUseBoth) {

 			gpsLocation = requestUpdatesFromProvider(LocationManager.GPS_PROVIDER, R.string.not_support_gps);
 			networkLocation = requestUpdatesFromProvider(LocationManager.NETWORK_PROVIDER, R.string.not_support_network);

 			if (gpsLocation != null && networkLocation != null) {
 				updateUILocation(getBetterLocation(gpsLocation, networkLocation));
 			} else if (gpsLocation != null) {
 				updateUILocation(gpsLocation);
 			} else if (networkLocation != null) {
 				updateUILocation(networkLocation);
 			}
 		}
 		
 	}
	
	private Location requestUpdatesFromProvider(final String provider, final int errorResId) {

 		Location location = null;
 		if (lm.isProviderEnabled(provider)) {

 			lm.requestLocationUpdates(provider, TEN_SECONDS, TEN_METERS, this);
 			location = lm.getLastKnownLocation(provider);
 		} else {

 			Toast.makeText(this, errorResId, Toast.LENGTH_LONG).show();
 		}
 		return location;
 	}

 	public void useFineProvider() {
 		mUseFine = true;
 		mUseBoth = false;
 		settingupLocation();
 	}

 	public void useCoarseFineProviders() {
 		mUseFine = false;
 		mUseBoth = true;
 		settingupLocation();
 	}
 	
 	private void doReverseGeocoding(Location location) {

 		(new ReverseGeocodingTask(this)).execute(new Location[] { location });
 	}

 	private void updateUILocation(Location location) {

 		speed = String.valueOf(location.getSpeed());
 		latitude = String.valueOf(location.getLatitude());
 		longitude = String.valueOf(location.getLongitude());
 		sendBroadcast(latitude, longitude, speed);
 		
 		Message.obtain(mHandler, UPDATE_LATLNG, location.getLatitude() + ", " + location.getLongitude()).sendToTarget();

 		if (mGeocoderAvailable)
 			doReverseGeocoding(location);
 	}
 	
 	protected Location getBetterLocation(Location newLocation, Location currentBestLocation) {

 		if (currentBestLocation == null) {

 			return newLocation;
 		}

 		long timeDelta = newLocation.getTime() - currentBestLocation.getTime();
 		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
 		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
 		boolean isNewer = timeDelta > 0;


 		if (isSignificantlyNewer) {
 			return newLocation;
 		} else if (isSignificantlyOlder) {
 			return currentBestLocation;
 		}

 		int accuracyDelta = (int) (newLocation.getAccuracy() - currentBestLocation.getAccuracy());
 		boolean isLessAccurate = accuracyDelta > 0;
 		boolean isMoreAccurate = accuracyDelta < 0;
 		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

 		boolean isFromSameProvider = isSameProvider(newLocation.getProvider(), currentBestLocation.getProvider());

 		if (isMoreAccurate) {
 			return newLocation;
 		} else if (isNewer && !isLessAccurate) {
 			return newLocation;
 		} else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
 			return newLocation;
 		}
 		return currentBestLocation;
 	}

 	/** Checks whether two providers are the same */
 	private boolean isSameProvider(String provider1, String provider2) {

 		if (provider1 == null) {
 			return provider2 == null;
 		}
 		return provider1.equals(provider2);
 	}

 	private class ReverseGeocodingTask extends AsyncTask<Location, Void, Void> {

 		Context mContext;

 		public ReverseGeocodingTask(Context context) {

 			super();
 			mContext = context;
 		}

 		@Override
 		protected Void doInBackground(Location... params) {

 			Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());

 			Location loc = params[0];
 			List<Address> addresses = null;
 			try {

 				addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);

 			} catch (IOException e) {

 				e.printStackTrace();
 				Message.obtain(mHandler, UPDATE_ADDRESS, e.toString()).sendToTarget();
 			}
 			if (addresses != null && addresses.size() > 0) {

 				Address address = addresses.get(0);
 				String addressText = String.format("%s, %s, %s", address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "", address.getLocality(), address.getCountryName());
 				address_text = addressText;

 				Message.obtain(mHandler, UPDATE_ADDRESS, addressText).sendToTarget();
 			}
 			return null;
 		}
 	}
 	
 	private void sendBroadcast(String latitude, String longitude, String speed) {
 		
 		Intent intent = new Intent(BROADCAST_ACTION);
		intent.putExtra("latitude", latitude);
		intent.putExtra("longitude", longitude);
		intent.putExtra("speed", speed);
		LocationService.this.sendBroadcast(intent);
 	}
	
}
