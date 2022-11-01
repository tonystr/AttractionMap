package com.oslomet.attractionmap

import android.content.DialogInterface
import android.location.Address
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.NetworkOnMainThreadException
import android.util.Log
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
import java.io.IOException
import java.lang.RuntimeException
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var geocoder: Geocoder

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

        // Add a marker in Sydney and move the camera
        val oslomet = LatLng(59.91958244312204, 10.735418584314667)
        mMap.addMarker(MarkerOptions().position(oslomet).title("Oslomet marker"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(oslomet, 14.0f))



        // Move to method called on php response so all markers are loaded first
        mMap.setOnMapClickListener { latLng: LatLng ->
            var addresses: List<Address>? = null

            try {
                addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show()
            }

            if (addresses != null) {
                val address = addresses[0]
                val shortName = address.getAddressLine(0)
                println(address.toString())

                mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                val marker = mMap.addMarker(MarkerOptions().position(latLng).title(shortName))

                val alert = AlertDialog.Builder(this)
                    .setTitle("Register attraction here?")
                    .setMessage(shortName)
                    .create()

                val input = EditText(this)
                alert.setView(input)
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Ok") { _, _ ->
                    val title = input.text.toString();
                    marker.title = title
                    Toast.makeText(this, "Added marker $title", Toast.LENGTH_SHORT).show()
                }

                alert.window?.setGravity(Gravity.BOTTOM)
                alert.show()

                // Toast.makeText(this, "Added marker at $shortName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show()
            }
        }
    }
}