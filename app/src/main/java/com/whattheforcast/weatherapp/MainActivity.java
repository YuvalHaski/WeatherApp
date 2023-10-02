package com.whattheforcast.weatherapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RelativeLayout homeRL;
    private ProgressBar loadingPB;
    private ImageView backIV, iconIV;
    private TextView cityNameTV, temperatureTV, conditionTV;
    private TextInputEditText
            cityEdt;
    private RecyclerView weatherRV;
    private ArrayList<WeatherRVModel> weatherRVModelArrayList;
    private WeatherRVAdapter weatherRVAdapter;
    private LocationManager locationManager;
    private ListView suggestionsLV;
    private final int PERMISSION_CODE = 1; // permission for location view

    private String cityName;


    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            do{
                cityName = getCityName(latitude, longitude);
            }while (cityName.equals("Not Found"));

            getWeatherInfo(cityName);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_main);

        homeRL = findViewById(R.id.idRLHome);
        loadingPB = findViewById(R.id.idPBLoading);
        backIV = findViewById(R.id.idIVBackground);
        iconIV = findViewById(R.id.idIVIcon);
        cityNameTV = findViewById(R.id.idTVCityName);
        temperatureTV = findViewById(R.id.idTVTemperature);
        conditionTV = findViewById(R.id.idTVCondition);
        cityEdt = findViewById(R.id.idEdtCity);
        weatherRV = findViewById(R.id.idRVWeather);
        suggestionsLV = findViewById(R.id.idLVSuggestions);

        weatherRVModelArrayList = new ArrayList<>();
        weatherRVAdapter = new WeatherRVAdapter(this, weatherRVModelArrayList);
        weatherRV.setAdapter(weatherRVAdapter); // set adapter to RecyclerView



        // Check location permissions granted
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            // if location permissions granted
//            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//            if (lastKnownLocation != null && (System.currentTimeMillis() - lastKnownLocation.getTime()) < 10 * 10 * 1000) {
//                cityName = getCityName(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
//                getWeatherInfo(cityName);
//            }
//            else{
//                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
//            }
//        } else { // Ask for permissions and if granted look for current location
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_CODE);
//            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
//        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, request it
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_CODE);
        }
        else{
            getLocationAndWeatherInfo();
        }

        cityEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                suggestionsLV.setVisibility(View.VISIBLE);
                fetchCitySuggestions(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        suggestionsLV.setOnItemClickListener((parent, view, position, id) -> {
            // Handle item selection from the suggestions list
            String selectedSuggestion = (String) parent.getItemAtPosition(position);
            cityEdt.setText(selectedSuggestion);

            // Clear the suggestions list
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) suggestionsLV.getAdapter();
            adapter.clear();
            adapter.notifyDataSetChanged();

            // Hide the ListView
            suggestionsLV.setVisibility(View.GONE);

            // Hide the keyboard
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(cityEdt.getWindowToken(), 0);

            // Update the UI with the selected suggestion
            cityNameTV.setText(selectedSuggestion);
            getWeatherInfo(selectedSuggestion);
        });
    }

    private void fetchCitySuggestions(@NonNull String query) {
        String url = "https://api.weatherapi.com/v1/search.json?key=f3fa2bc7e57a491e8cd145818232408&q=" + query;

        RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET,url,null,new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                List<String> suggestions = new ArrayList<>();
                int lastAddedSuggestion = 0;
                for (int i = 0; i < response.length(); i++) {
                    try {
                        String city = response.getJSONObject(i).getString("name");
                        String country = response.getJSONObject(i).getString("country");
                        String cityAndCountry = city + ", " + country;
                        if(i>0){
                            if(!(cityAndCountry.equals(suggestions.get(lastAddedSuggestion)))) {
                                lastAddedSuggestion = i;
                                suggestions.add(cityAndCountry);
                            }
                        }
                        else {
                            suggestions.add(cityAndCountry);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e("AutoComplete", "Error parsing JSON: " + e.getMessage());
                    }
                }
                // Update the ListView with the fetched suggestions
                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, suggestions);
                suggestionsLV.setAdapter(adapter);
            }
        },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });
        requestQueue.add(request);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Permission granted", Toast.LENGTH_SHORT).show();
                getLocationAndWeatherInfo();
            } else {
                Toast.makeText(MainActivity.this, "Please provide the permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void getLocationAndWeatherInfo(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null && (System.currentTimeMillis() - lastKnownLocation.getTime()) < 10 * 10 * 1000) {
                cityName = getCityName(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                getWeatherInfo(cityName);
            } else {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
            }
        } else {
            // Permissions are not granted
            Toast.makeText(MainActivity.this, "Please provide location permission", Toast.LENGTH_SHORT).show();
            // Request permissions again
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_CODE);
        }
    }



    private String getCityName(double latitude, double longitude) {
        String cityName = "Not Found";
        boolean foundCity = false;
        Geocoder gcd = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = gcd.getFromLocation(latitude, longitude, 10);

            for (Address adr : addresses) {
                if (adr != null) {
                    String city = adr.getLocality();
                    if (city != null && !(city.equals(""))) {
                        cityName = city;
                        foundCity = true;
                    }
                }
            }
            if (!foundCity) {
                Log.d("TAG", "CITY NOT FOUND");
                Toast.makeText(this, "User City Not Found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cityName;
    }




    private void getWeatherInfo(String cityName) {
        String url = "https://api.weatherapi.com/v1/forecast.json?key=f3fa2bc7e57a491e8cd145818232408&q=" + cityName + "&days=1&aqi=yes&alerts=yes";
        cityNameTV.setText(cityName);

        RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                loadingPB.setVisibility(View.GONE);
                homeRL.setVisibility(View.VISIBLE);
                weatherRVModelArrayList.clear();

                try {
                    String temperature = response.getJSONObject("current").getString("temp_c");
                    temperatureTV.setText(temperature + "°c");
                    int isDay = response.getJSONObject("current").getInt("is_day");
                    String condition = response.getJSONObject("current").getJSONObject("condition").getString("text");
                    String conditionIcon = response.getJSONObject("current").getJSONObject("condition").getString("icon");
                    Picasso.get().load("https:".concat(conditionIcon)).into(iconIV); // loading image into iconIV
                    conditionTV.setText(condition);

                    if (isDay == 1) { // day
                        Picasso.get().load(R.drawable.day).into(backIV);
                    } else { // night
                        Picasso.get().load(R.drawable.night).into(backIV);
                    }

                    JSONObject forecastObj = response.getJSONObject("forecast");
                    JSONObject forecastFirstObject = forecastObj.getJSONArray("forecastday").getJSONObject(0);
                    JSONArray hourArray = forecastFirstObject.getJSONArray("hour");

                    for (int i = 0; i < hourArray.length(); i++) {
                        JSONObject hourObj = hourArray.getJSONObject(i);
                        String timeH = hourObj.getString("time");
                        String temperatureH = hourObj.getString("temp_c");
                        String conditionImgH = hourObj.getJSONObject("condition").getString("icon");
                        String windH = hourObj.getString("wind_kph");

                        weatherRVModelArrayList.add(new WeatherRVModel(timeH, temperatureH, conditionImgH, windH));
                    }
                    weatherRVAdapter.notifyDataSetChanged();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(MainActivity.this, "Please enter valid city name", Toast.LENGTH_SHORT).show();
            }
        });

        requestQueue.add(jsonObjectRequest);
    }

    protected void onStop() {
        super.onStop();
        // Remove location updates to conserve battery
        locationManager.removeUpdates(locationListener);
    }

}