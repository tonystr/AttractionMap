package com.oslomet.attractionmap

import android.content.DialogInterface
import android.location.Address
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.oslomet.attractionmap.databinding.ActivityMapsBinding
import org.json.JSONArray
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var geocoder: Geocoder
    private var attractions = arrayListOf<Attraction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geocoder = Geocoder(this, Locale.getDefault())

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker at OsloMet
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Fetches existing markers from server and populates map with them
        // This calls onReady() once it's done
        fetchAttractions()

        // INSERT INTO attractions (title, description, address, latitude, longitude) VALUES ('colluseum', 'a big sphere in which people fight', 'oslo 6787', 34.123443, -12.789834); '
    }

    private fun onReady() {
        mMap.setOnMapClickListener { latLng: LatLng ->
            // Find address for given location if it exists, null if not
            val addresses: List<Address>? = try {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            } catch (e: Exception) {
                null
            }

            if (addresses != null) {
                val address = addresses[0].getAddressLine(0)

                // Move camera and create marker
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                val marker = mMap.addMarker(MarkerOptions().position(latLng).title(address))

                // Stringify latitude and longitude
                val lat = "%.6f".format(latLng.latitude)
                val lng = "%.6f".format(latLng.longitude)

                // Create popup alert dialog for registering attraction
                with (AlertDialog.Builder(this).create()) {
                    setMessage("$address\n(${lat}, ${lng})")
                    setTitle("Register attraction here?")

                    val alertView = layoutInflater.inflate(R.layout.attraction_dialog, null)
                    setView(alertView)

                    // OK button - register attraction here
                    // TODO: Handle empty strings
                    setButton(DialogInterface.BUTTON_POSITIVE, "Ok") { _, _ ->
                        // Retrieve data from text inputs
                        val title = alertView.findViewById<EditText>(R.id.input_title).text.toString();
                        val desc = alertView.findViewById<EditText>(R.id.input_desc).text.toString();

                        val attraction = Attraction(title, desc, address, latLng)
                        createAttraction(attraction)

                        marker?.title = title
                        Toast.makeText(context, "Added marker $title", Toast.LENGTH_SHORT).show()
                    }

                    // CANCEL button - do not register attraction
                    setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel") { _, _ ->
                        marker?.remove()
                    }

                    // Click outside dialog
                    setOnCancelListener {
                        marker?.remove()
                    }

                    // Align alertdialog to bottom so marker can be seen while editing
                    window?.setGravity(Gravity.BOTTOM)
                    show()
                }
            } else {
                Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createAttraction(attraction: Attraction) {
        val title = attraction.title
        val desc = attraction.description
        val address = attraction.address
        val latitude = attraction.latLng.latitude
        val longitude = attraction.latLng.longitude
        val path = "http://data1500.cs.oslomet.no/~s354366/createAttraction.php" +
                "?title=$title" +
                "&description=$desc" +
                "&address=$address" +
                "&latitude=$latitude" +
                "&longitude=$longitude"

        // Run on a different thread to not block the UI
        thread {
            // Send http request
            val res = try {
                URL(path).readText()
            } catch (e: Exception) {
                return@thread
            }
        }
    }

    private fun fetchAttractions() {
        val path = "http://data1500.cs.oslomet.no/~s354366/"

        // Run on a different thread to not block the UI
        thread {
            // Send http request
            val jsonRes = try {
                URL(path).readText()
            } catch (e: Exception) {
                return@thread
            }

            // Back to UI thread
            runOnUiThread {
                // Loop over JSON array and retrieve objects
                val array = JSONArray(jsonRes)
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    val latLng = LatLng(
                        json.getDouble("latitude"),
                        json.getDouble("longitude")
                    )
                    // Create attraction object
                    val attraction = Attraction(
                        json.getString("title"),
                        json.getString("description"),
                        json.getString("address"),
                        latLng
                    )
                    attractions.add(attraction)

                    // Add attraction marker to map
                    mMap.addMarker(MarkerOptions().position(latLng).title(attraction.title))
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f))
                }

                // Set up map functionality
                onReady()
            }
        }
    }
}

data class Attraction (
    val title: String,
    val description: String,
    val address: String,
    val latLng: LatLng,
)