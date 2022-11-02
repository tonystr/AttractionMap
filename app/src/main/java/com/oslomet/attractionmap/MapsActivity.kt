package com.oslomet.attractionmap

import android.annotation.SuppressLint
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
    private val IDEAL_ZOOM = 15.0f

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

        // INSERT INTO attractions (title, description, address, latitude, longitude) VALUES ('coliseum', 'a big sphere in which people fight', 'oslo 6787', 34.123443, -12.789834); '
    }

    @SuppressLint("InflateParams") // https://stackoverflow.com/a/27662832/8715267
    private fun onReady() {

        // Map click - create marker
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
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, IDEAL_ZOOM))
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
                        val title = alertView.findViewById<EditText>(R.id.input_title).text.toString()
                        val desc = alertView.findViewById<EditText>(R.id.input_desc).text.toString()

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
                // Notify about unknown location
                Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show()
            }
        }

        // Marker click - edit marker
        mMap.setOnMarkerClickListener {
            // Find attraction for clicked marker
            val attraction = attractions.find { a -> a.title == it.title }
                // Cancel if attraction doesn't exist
                ?: return@setOnMarkerClickListener true

            // Move camera to marker
            mMap.animateCamera(CameraUpdateFactory.newLatLng(attraction.latLng))

            // Create popup dialog for editing or deleting marker
            with (AlertDialog.Builder(this, R.style.AlertDialogEditAttraction).create()) {
                // setMessage("${att.address}\n(${att.latLng.latitude}, ${att.latLng.longitude})")
                setTitle("Edit attraction")

                // Set custom view for alert dialog
                val view = layoutInflater.inflate(R.layout.attraction_edit_dialog, null)
                setView(view)

                // Save references to all input fields
                val inputName = view.findViewById<EditText>(R.id.input_title)
                val inputDesc = view.findViewById<EditText>(R.id.input_desc)
                val inputAddr = view.findViewById<EditText>(R.id.input_address)
                val inputLat  = view.findViewById<EditText>(R.id.input_latitude)
                val inputLong = view.findViewById<EditText>(R.id.input_longitude)

                // Put existing data into input fields
                inputName.setText(attraction.title)
                inputDesc.setText(attraction.description)
                inputAddr.setText(attraction.address)
                inputLat.setText("%.6f".format(attraction.latLng.latitude))
                inputLong.setText("%.6f".format(attraction.latLng.longitude))

                // DELETE button - delete existing attraction
                // TODO: IMPLEMENT THIS AND ON WEB SERVER
                setButton(DialogInterface.BUTTON_NEGATIVE, "Delete") { _, _ ->
                    // Remove attraction locally
                    attractions.remove(attraction)
                    it.remove()

                    val path = "http://data1500.cs.oslomet.no/~s354366/deleteAttraction.php?title=${it.title}"
                    // Run on a different thread to not block the UI
                    thread {
                        // Send http request
                        try {
                            URL(path).readText()
                        } catch (e: Exception) {
                            return@thread
                        }
                    }
                }

                // SAVE button - save changes to existing attraction
                // TODO: IMPLEMENT ON WEB SERVER
                setButton(DialogInterface.BUTTON_POSITIVE, "Save") { _, _ ->
                    // Recreate attraction based on inputs
                    val newAttr = Attraction(
                        inputName.text.toString(),
                        inputDesc.text.toString(),
                        inputAddr.text.toString(),
                        LatLng(
                            inputLat.text.toString().toDouble(),
                            inputLong.text.toString().toDouble(),
                        )
                    )

                    // Construct query based on new attraction values
                    val path = "http://data1500.cs.oslomet.no/~s354366/updateAttraction.php?${
                        arrayListOf(
                            "old_title=${it.title}",
                            "new_title=${newAttr.title}",
                            "description=${newAttr.description}",
                            "address=${newAttr.address}",
                            "latitude=${newAttr.latLng.latitude}",
                            "longitude=${newAttr.latLng.longitude}"
                        ).joinToString("&")
                    }"

                    // Run on a different thread to not block the UI
                    thread {
                        // Send http request
                        try {
                            URL(path).readText()
                        } catch (e: Exception) {
                            return@thread
                        }
                    }

                    // Replace old attraction with new
                    attractions.remove(attraction)
                    attractions.add(newAttr)

                    // Move and rename existing marker
                    it.title = newAttr.title
                    it.position = newAttr.latLng
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newAttr.latLng, IDEAL_ZOOM))
                }

                // Align alertdialog to bottom so marker can be seen while editing
                window?.setGravity(Gravity.BOTTOM)
                show()
            }

            true
        }
    }

    private fun createAttraction(attraction: Attraction) {
        // Cancel if attraction name is taken
        val att = attractions.find { a -> a.title == attraction.title }
        if (att != null) {
            Toast.makeText(
                this,
                "An attraction called ${attraction.title} already exists",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Construct GET query url
        val path = "http://data1500.cs.oslomet.no/~s354366/createAttraction.php" +
                "?title=${attraction.title}" +
                "&description=${attraction.description}" +
                "&address=${attraction.address}" +
                "&latitude=${attraction.latLng.latitude}" +
                "&longitude=${attraction.latLng.longitude}"

        // Update local list of attractions
        attractions.add(attraction)

        // Run on a different thread to not block the UI
        thread {
            // Send http request
            try {
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
                val url = URL(path)
                // Throw exception if connection times out
                with (url.openConnection()) {
                    connectTimeout = 2000
                    readTimeout = 2000
                    connect()
                }
                // Read response into jsonRes
                url.readText()
            } catch (e: Exception) {
                // Exception is thrown if not on same network as web server
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed to connect to database. You must be connected to EduVPN",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, IDEAL_ZOOM))
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