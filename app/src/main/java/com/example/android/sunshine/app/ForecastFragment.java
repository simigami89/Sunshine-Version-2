package com.example.android.sunshine.app;


import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by  on 09.02.2016.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;
    ArrayList<String> weakForecast;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String syncConnPref = sharedPref.getString(getString(R.string.location_key), getString(R.string.location_value));
            new FetchWeatherTask().execute(syncConnPref);
            return true;
        }
        else if (id == R.id.action_settings){
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        weakForecast = new ArrayList<>();
        weakForecast.add("Today- cold");
        weakForecast.add("Tomorrow - warm");
        weakForecast.add("Monday- hot");
        weakForecast.add("Thursday- cold");
        mForecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, weakForecast);

        mForecastAdapter.setNotifyOnChange(true);
        ListView forecastListview = (ListView) rootView.findViewById(R.id.listview_forecast);

        forecastListview.setAdapter(mForecastAdapter);
        forecastListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                String forecast = mForecastAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
                Toast.makeText(getActivity(), "click", Toast.LENGTH_SHORT).show();
            }
        });


        return rootView;
    }

    private String dateToString(long dateMs){
        String resultString = null;

        Date date = new Date(dateMs);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy hh:mm");
        resultString = simpleDateFormat.format(date);
        return resultString;
    }

    private String [] getWeatherDataFromJSON(String jsonString, int numDays)throws JSONException{
        String [] result = new String[numDays];

        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";
        final String OWM_WEATHER = "weather";
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";
        final String OWM_DESCRIPTION = "main";
        final String OWN_DATE = "dt";

        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray weatherArray = jsonObject.getJSONArray(OWM_LIST);
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int startDay = calendar.get(Calendar.DAY_OF_MONTH);
        weakForecast.clear();
        for (int i = 0; i < numDays; i++){
            calendar.set(Calendar.DAY_OF_MONTH, startDay+i);

            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            JSONObject jsonObjectDay = weatherArray.getJSONObject(i);
            String date = String.valueOf(day)+":"+ String.valueOf(month)+":"+String.valueOf(year);
            result[i] = date + "\n";
            JSONArray jsonObjectWeather = jsonObjectDay.getJSONArray(OWM_WEATHER);
            JSONObject jsonObject1 = jsonObjectWeather.getJSONObject(0);
            result[i]+= jsonObject1.optString(OWM_DESCRIPTION);

            JSONObject jsonObjectTemperatureMax = jsonObjectDay.getJSONObject(OWM_TEMPERATURE);
            result[i]+=" T_max: "+jsonObjectTemperatureMax.optString(OWM_MAX);

            JSONObject jsonObjectTemperatureMin = jsonObjectDay.getJSONObject(OWM_TEMPERATURE);
            result[i]+=" T_min: "+jsonObjectTemperatureMin.optString(OWM_MIN);

            weakForecast.add(result[i]);
        }

        return result;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String []>{

        private final String LOG_TAG = FetchWeatherTask.class.getName();

        @Override
        protected void onPostExecute(String[] result) {
            if(result != null){
                mForecastAdapter.clear();
                for(String itmResult: result){
                    mForecastAdapter.add(itmResult);
                }
            }
        }

        protected String[] doInBackground(String... params) {

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;
            String format = "json";
            String units = "metric";
            int numDays = 7;
            String apiKey = "ae19ee360b61d2654d531161c7a97f56";

            String[] result = new String[0];
            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERRY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UINTS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String API_KEY = "APPID";


                Uri uriBuild = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERRY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UINTS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(API_KEY, apiKey)
                        .build();

                URL url = new URL(uriBuild.toString());//&APPID=ae19ee360b61d2654d531161c7a97f56

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();

                Log.d(LOG_TAG, forecastJsonStr);
            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJSON(forecastJsonStr, 3);
            }catch (JSONException e){
                e.printStackTrace();
            }
            return null;
        }
    }
}
