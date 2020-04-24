package com.journear.app.map;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.EventLogTags;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.Context;

import androidx.core.app.ActivityCompat;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.Trip;
import com.journear.app.R;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.PointList;
import com.graphhopper.util.ProgressListener;
import com.graphhopper.util.StopWatch;

import org.oscim.android.MapView;
import org.oscim.android.canvas.AndroidGraphics;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.core.GeoPoint;
import org.oscim.event.Gesture;
import org.oscim.event.GestureListener;
import org.oscim.event.MotionEvent;
import org.oscim.layers.Layer;
import org.oscim.layers.marker.ItemizedLayer;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.layers.vector.PathLayer;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.theme.VtmThemes;
import org.oscim.tiling.source.mapfile.MapFileTileSource;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


public class MapActivity extends Activity {
    private static final int NEW_MENU_ID = Menu.FIRST + 1;
    private static final String LOGTAG = "Journear Map";
    private MapView mapView;
    private GraphHopper hopper;
    private GeoPoint start;
    private GeoPoint end;
    private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;
    private String currentArea = "berlin";
    private String fileListURL = "http://download2.graphhopper.com/public/maps/" + Constants.getMajorVersion() + "/";
    private String prefixURL = fileListURL;
    private String downloadURL;
    private File mapsFolder;
    private ItemizedLayer<MarkerItem> itemizedLayer;
    private PathLayer pathLayer;
    public final static String incomingIntentName = "SOURCE-DESTINATION-CURRENT";
    private LocationManager locationManager = null;
    private MyLocationListener locationListener = null;
    private GeoPoint onLoadMarker;

    private class MyLocationListener implements LocationListener {
        private static final long min_distance_forupdate = 10;
        private static final long min_time_to_update = 2 * 60 * 1000;
        Location location;

        public Location getLocation(String provider) {
            if (locationManager.isProviderEnabled(provider)) {

                try {

                    locationManager.requestLocationUpdates(provider, min_time_to_update, min_distance_forupdate, this);
                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(provider);
                        return location;

                    }
                } catch (SecurityException r) {

                    Log.d("loc", r.getMessage());
                }
                return location;
            }

            return location;
        }

        @Override
        public void onLocationChanged(Location loc) {
            Toast.makeText(
                    getBaseContext(),
                    "Location changed: Lat: " + loc.getLatitude() + " Lng: "
                            + loc.getLongitude(), Toast.LENGTH_SHORT).show();
            System.out.println("Location printed");
            System.out.println(loc.getLongitude());
            onLoadMarker = new GeoPoint((int) (loc.getLatitude() * 1E6), (int) (loc.getLongitude() * 1E6));
            itemizedLayer.addItem(createMarkerItem(onLoadMarker, R.drawable.gps));
            mapView.map().updateMap(true);
            /*------- To get city name from coordinates -------- */
            String cityName = null;
            Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());

            List<Address> addresses;
            try {
                addresses = gcd.getFromLocation(loc.getLatitude(),
                        loc.getLongitude(), 1);
                if (addresses.size() > 0) {
                    System.out.println(addresses.get(0).getLocality());
                    cityName = addresses.get(0).getLocality();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }

    public void clearRoute() {
        mapView.map().layers().remove(pathLayer);
        itemizedLayer.removeAllItems();


    }

    public boolean showRoute(GeoPoint p1, GeoPoint p2) {
        Log.i(LOGTAG, "Showing route...");
        if (!isReady())
            return false;

        if (shortestPathRunning) {
            logUser("Calculation still in progress");
            return false;
        }

        if (p1 != null && p2 != null) {
//            mapView.map().setMapPosition((p1.getLatitude() + p2.getLatitude()) / 2, (p1.getLongitude() + p2.getLongitude())/ 2, 1 << 10);
            start = p1;
            end = p2;
            shortestPathRunning = true;
            itemizedLayer.addItem(createMarkerItem(p1, R.drawable.marker_icon_green));
            itemizedLayer.addItem(createMarkerItem(p2, R.drawable.marker_icon_red));
            mapView.map().updateMap(true);

            calcPath(start.getLatitude(), start.getLongitude(), end.getLatitude(),
                    end.getLongitude());
        }
        return true;
    }

    protected boolean onLongPress(GeoPoint p) {
        if (!isReady())
            return false;

        if (shortestPathRunning) {
            logUser("Calculation still in progress");
            return false;
        }

        if (start != null && end == null) {
            end = p;
            shortestPathRunning = true;
            itemizedLayer.addItem(createMarkerItem(p, R.drawable.marker_icon_red));
            mapView.map().updateMap(true);

            calcPath(start.getLatitude(), start.getLongitude(), end.getLatitude(),
                    end.getLongitude());
        } else {
            start = p;
            end = null;
            // remove routing layers
            mapView.map().layers().remove(pathLayer);
            itemizedLayer.removeAllItems();
            itemizedLayer.addItem(createMarkerItem(start, R.drawable.marker_icon_green));
            mapView.map().updateMap(true);
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mapView = new MapView(this);

        final EditText input = new EditText(this);
        input.setText(currentArea);
        boolean greaterOrEqKitkat = Build.VERSION.SDK_INT >= 19;
        if (greaterOrEqKitkat) {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                logUser("GraphHopper is not usable without an external storage!");
                return;
            }
            mapsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "/graphhopper/maps/");
        } else
            mapsFolder = new File(Environment.getExternalStorageDirectory(), "/graphhopper/maps/");

        if (!mapsFolder.exists())
            mapsFolder.mkdirs();

//        Intent intent = getIntent();
//        double[] dddddd = intent.getDoubleArrayExtra(incomingIntentName);
//        GeoPoint p1, p2, p3;
//        p1 = new GeoPoint(dddddd[0], dddddd[1]);
//        p2 = new GeoPoint(dddddd[2], dddddd[3]);
//        p3 = new GeoPoint(dddddd[4], dddddd[5]);

        initFiles("ireland-and-northern-ireland-latest");

    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hopper != null)
            hopper.close();

        hopper = null;
        // necessary?
        System.gc();

        // Cleanup VTM
        mapView.map().destroy();
    }

    boolean isReady() {
        // only return true if already loaded
        if (hopper != null)
            return true;

        if (prepareInProgress) {
            logUser("Preparation still in progress");
            return false;
        }
        logUser("Prepare finished but GraphHopper not ready. This happens when there was an error while loading the files");
        return false;
    }

    private void initFiles(String area) {
        prepareInProgress = true;
        currentArea = area;
        downloadingFiles();
    }

//    private void chooseAreaFromRemote() {
//        new GHAsyncTask<Void, Void, List<String>>() {
//            protected List<String> saveDoInBackground(Void... params)
//                    throws Exception {
//                String[] lines = new AndroidDownloader().downloadAsString(fileListURL, false).split("\n");
//                List<String> res = new ArrayList<>();
//                for (String str : lines) {
//                    int index = str.indexOf("href=\"");
//                    if (index >= 0) {
//                        index += 6;
//                        int lastIndex = str.indexOf(".ghz", index);
//                        if (lastIndex >= 0)
//                            res.add(prefixURL + str.substring(index, lastIndex)
//                                    + ".ghz");
//                    }
//                }
//
//                return res;
//            }
//
//            @Override
//            protected void onPostExecute(List<String> nameList) {
//                if (hasError()) {
//                    getError().printStackTrace();
//                    logUser("Are you connected to the internet? Problem while fetching remote area list: "
//                            + getErrorMessage());
//                    return;
//                } else if (nameList == null || nameList.isEmpty()) {
//                    logUser("No maps created for your version!? " + fileListURL);
//                    return;
//                }
//
//                MySpinnerListener spinnerListener = new MySpinnerListener() {
//                    @Override
//                    public void onSelect(String selectedArea, String selectedFile) {
//                        if (selectedFile == null
//                                || new File(mapsFolder, selectedArea + ".ghz").exists()
//                                || new File(mapsFolder, selectedArea + "-gh").exists()) {
//                            downloadURL = null;
//                        } else {
//                            downloadURL = selectedFile;
//                        }
//                        initFiles(selectedArea);
//                    }
//                };
//                chooseArea(remoteButton, remoteSpinner, nameList,
//                        spinnerListener);
//            }
//        }.execute();
//    }

    private void chooseArea(Button button, final Spinner spinner,
                            List<String> nameList, final MySpinnerListener myListener) {
        final Map<String, String> nameToFullName = new TreeMap<>();
        for (String fullName : nameList) {
            String tmp = Helper.pruneFileEnd(fullName);
            if (tmp.endsWith("-gh"))
                tmp = tmp.substring(0, tmp.length() - 3);

            tmp = AndroidHelper.getFileName(tmp);
            nameToFullName.put(tmp, fullName);
        }
        nameList.clear();
        nameList.addAll(nameToFullName.keySet());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, nameList);
        spinner.setAdapter(spinnerArrayAdapter);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Object o = spinner.getSelectedItem();
                if (o != null && o.toString().length() > 0 && !nameToFullName.isEmpty()) {
                    String area = o.toString();
                    myListener.onSelect(area, nameToFullName.get(area));
                } else {
                    myListener.onSelect(null, null);
                }
            }
        });
    }

    void downloadingFiles() {
        final File areaFolder = new File(mapsFolder, currentArea + "-gh");
        if (downloadURL == null || areaFolder.exists()) {
            loadMap(areaFolder);
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Downloading and uncompressing " + downloadURL);
        dialog.setIndeterminate(false);
        dialog.setMax(100);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.show();

        new GHAsyncTask<Void, Integer, Object>() {
            protected Object saveDoInBackground(Void... _ignore)
                    throws Exception {
                String localFolder = Helper.pruneFileEnd(AndroidHelper.getFileName(downloadURL));
                localFolder = new File(mapsFolder, localFolder + "-gh").getAbsolutePath();
                log("downloading & unzipping " + downloadURL + " to " + localFolder);
                AndroidDownloader downloader = new AndroidDownloader();
                downloader.setTimeout(30000);
                downloader.downloadAndUnzip(downloadURL, localFolder,
                        new ProgressListener() {
                            @Override
                            public void update(long val) {
                                publishProgress((int) val);
                            }
                        });
                return null;
            }

            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                dialog.setProgress(values[0]);
            }

            protected void onPostExecute(Object _ignore) {
                dialog.dismiss();
                if (hasError()) {
                    String str = "An error happened while retrieving maps:" + getErrorMessage();
                    log(str, getError());
                    logUser(str);
                } else {
                    loadMap(areaFolder);
                }
            }
        }.execute();
    }

    void loadMap(File areaFolder) {
        logUser("loading map");

        // Map events receiver
        mapView.map().layers().add(new MapEventsReceiver(mapView.map()));

        // Map file source
        MapFileTileSource tileSource = new MapFileTileSource();
        tileSource.setMapFile(new File(areaFolder, currentArea + ".map").getAbsolutePath());
        VectorTileLayer l = mapView.map().setBaseMap(tileSource);
        mapView.map().setTheme(VtmThemes.DEFAULT);
        mapView.map().layers().add(new BuildingLayer(mapView.map(), l));
        mapView.map().layers().add(new LabelLayer(mapView.map(), l));

        // Markers layer
        itemizedLayer = new ItemizedLayer<>(mapView.map(), (MarkerSymbol) null);
        mapView.map().layers().add(itemizedLayer);

        // Map position
        GeoPoint mapCenter = tileSource.getMapInfo().boundingBox.getCenterPoint();

        mapView.map().setMapPosition(mapCenter.getLatitude(), mapCenter.getLongitude(), 1 << 15);


        setContentView(mapView);
        loadGraphStorage();
        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        locationListener = new MyLocationListener();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager
                .GPS_PROVIDER, 5000, 10, locationListener);
        Location loc = locationListener.getLocation(LocationManager.NETWORK_PROVIDER);

        if (loc != null) {
            onLoadMarker = new GeoPoint((int) (loc.getLatitude() * 1E6), (int) (loc.getLongitude() * 1E6));
            itemizedLayer.addItem(createMarkerItem(onLoadMarker, R.drawable.black_gps_marker));
            mapView.map().updateMap(true);
        }
    }

    void loadGraphStorage() {
        logUser("loading graph (" + Constants.VERSION + ") ... ");
        new GHAsyncTask<Void, Void, Path>() {
            protected Path saveDoInBackground(Void... v) throws Exception {
                GraphHopper tmpHopp = new GraphHopper().forMobile();

                ProfileConfig carProfileConfig = new ProfileConfig("car");
                carProfileConfig.setWeighting("fastest");
                carProfileConfig.setVehicle("car");
                ProfileConfig footProfileConfig = new ProfileConfig("foot");
                footProfileConfig.setWeighting("fastest");
                tmpHopp.setProfiles(carProfileConfig, footProfileConfig);

                tmpHopp.load(new File(mapsFolder, currentArea).getAbsolutePath() + "-gh");
                log("found graph " + tmpHopp.getGraphHopperStorage().toString() + ", nodes:" + tmpHopp.getGraphHopperStorage().getNodes());
                hopper = tmpHopp;
                return null;
            }

            protected void onPostExecute(Path o) {
                if (hasError()) {
                    logUser("An error happened while creating graph:"
                            + getErrorMessage());
                } else {
                    logUser("Finished loading graph. Long press to define where to start and end the route.");
                }

                finishPrepare();
                showRoute();
            }
        }.execute();
    }

    private void showRoute() {
        Log.i(LOGTAG, "Showing route from intent");
        Intent intent = getIntent();
        double[] dddddd = intent.getDoubleArrayExtra(incomingIntentName);
         intent.getBooleanExtra("Start", false);
        GeoPoint p1, p2;
        p1 = new GeoPoint(dddddd[0], dddddd[1]);
        p2 = new GeoPoint(dddddd[2], dddddd[3]);

        showRoute(p1, p2);
    }

    private void finishPrepare() {
        prepareInProgress = false;
    }

    private PathLayer createPathLayer(PathWrapper response) {
        Style style = Style.builder()
                .fixed(true)
                .generalization(Style.GENERALIZATION_SMALL)
                .strokeColor(0x9900cc33)
                .strokeWidth(4 * getResources().getDisplayMetrics().density)
                .build();
        PathLayer pathLayer = new PathLayer(mapView.map(), style);
        List<GeoPoint> geoPoints = new ArrayList<>();
        PointList pointList = response.getPoints();
        for (int i = 0; i < pointList.getSize(); i++)
            geoPoints.add(new GeoPoint(pointList.getLatitude(i), pointList.getLongitude(i)));
        pathLayer.setPoints(geoPoints);
        return pathLayer;
    }

    @SuppressWarnings("deprecation")
    private MarkerItem createMarkerItem(GeoPoint p, int resource) {
        Drawable drawable = getResources().getDrawable(resource);
        Bitmap bitmap = AndroidGraphics.drawableToBitmap(drawable);
        MarkerSymbol markerSymbol = new MarkerSymbol(bitmap, 0.5f, 1);
        MarkerItem markerItem = new MarkerItem("", "", p);
        markerItem.setMarker(markerSymbol);
        return markerItem;
    }

    public void calcPath(final double fromLat, final double fromLon,
                         final double toLat, final double toLon) {

        log("calculating path ...");
        new AsyncTask<Void, Void, PathWrapper>() {
            float time;

            protected PathWrapper doInBackground(Void... v) {
                StopWatch sw = new StopWatch().start();
                String profile = "foot";
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).setProfile(profile).
                        setAlgorithm(Algorithms.DIJKSTRA_BI);
                req.getHints().
                        put(Routing.INSTRUCTIONS, "false");
                GHResponse resp = hopper.route(req);
                System.out.println(resp);
                time = sw.stop().getSeconds();
                return resp.getBest();
            }

            protected void onPostExecute(PathWrapper resp) {
                if (!resp.hasErrors()) {
                    log("from:" + fromLat + "," + fromLon + " to:" + toLat + ","
                            + toLon + " found path with distance:" + resp.getDistance()
                            / 1000f + ", nodes:" + resp.getPoints().getSize() + ", time:"
                            + time + " " + resp.getDebugInfo());
                    logUser("the route is " + (int) (resp.getDistance() / 100) / 10f
                            + "km long, time:" + resp.getTime() / 60000f + "min, debug:" + time);

//                    double  zoomFactor = 1 <<  (int)(resp.getDistance() * 0.15);
                    mapView.map().setMapPosition((toLat + fromLat) / 2, (toLon + fromLon) / 2, 1 << 10);


                    pathLayer = createPathLayer(resp);
                    mapView.map().layers().add(pathLayer);
                    mapView.map().updateMap(true);
                } else {
                    logUser("Error:" + resp.getErrors());
                }
                shortestPathRunning = false;
            }
        }.execute();
    }

    private void log(String str) {
        Log.i("GH", str);
    }

    private void log(String str, Throwable t) {
        Log.i("GH", str, t);
    }

    private void logUser(String str) {
        log(str);
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, NEW_MENU_ID, 0, "Google");
        // menu.add(0, NEW_MENU_ID + 1, 0, "Other");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case NEW_MENU_ID:
                if (start == null || end == null) {
                    logUser("tap screen to set start and end of route");
                    break;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                // get rid of the dialog
                intent.setClassName("com.google.android.apps.maps",
                        "com.google.android.maps.MapsActivity");
                intent.setData(Uri.parse("http://maps.google.com/maps?saddr="
                        + start.getLatitude() + "," + start.getLongitude() + "&daddr="
                        + end.getLatitude() + "," + end.getLongitude()));
                startActivity(intent);
                break;
        }
        return true;
    }

    public interface MySpinnerListener {
        void onSelect(String selectedArea, String selectedFile);
    }

    class MapEventsReceiver extends Layer implements GestureListener {

        MapEventsReceiver(org.oscim.map.Map map) {
            super(map);
        }

        @Override
        public boolean onGesture(Gesture g, MotionEvent e) {
            if (g instanceof Gesture.LongPress) {
                GeoPoint p = mMap.viewport().fromScreenPoint(e.getX(), e.getY());
                locationListener = new MyLocationListener();
                return onLongPress(p);
            }
            return false;
        }
    }
}
