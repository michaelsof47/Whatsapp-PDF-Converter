package com.example.whatsappchatconverter

import android.Manifest
import android.app.ComponentCaller
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.whatsappchatconverter.databinding.ActivityMainBinding
import com.example.whatsappchatconverter.event.ConverterStatusEvent
import com.example.whatsappchatconverter.model.DataFileModel
import com.example.whatsappchatconverter.service.WhatsappConverterService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var selectedZipUri: Uri
    private lateinit var filename: String

    private val selectFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri.let {
                selectedZipUri = it!!
                val _filename = getFileNameFromUri(this, it)
                filename = _filename
                binding.tietUrlFile.setText(_filename)
            }
        }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if(intent != null) {
            startAppsWithData(intent)
        }

        binding.btnSearch.setOnClickListener {
            selectFile.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed"
                )
            )
        }

        binding.btnConvert.setOnClickListener {
            if (binding.tietUrlFile.text!!.isNotEmpty()) {
                val intent = Intent(this, WhatsappConverterService::class.java).apply {
                    putExtra("file_zip", selectedZipUri)
                    putExtra("filename_url", filename)
                    addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                }

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(intent)
                else
                    startService(intent)
            } else {
                Toast.makeText(this, "Please select a file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var fileName = ""
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: ConverterStatusEvent) {
        if (event.isCompleted) {
            binding.btnShare.visibility = View.VISIBLE
        } else {
            binding.btnShare.visibility = View.GONE
        }

        binding.btnShare.setOnClickListener {
            if(event.uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, event.uri)
                    addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share PDF Via"))
            } else {
                Toast.makeText(this, "PDF is Empty. It can't shared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startAppsWithData(intent: Intent) {
        if(intent.action == Intent.ACTION_SEND) {
            val uri = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            selectedZipUri = uri!!
            filename = getFileNameFromUri(this@MainActivity, uri)
            binding.tietUrlFile.setText(filename)
        } else {
            if(intent.getIntExtra("progress", 0) == 100) {
                binding.btnShare.visibility = View.VISIBLE
            } else {
                binding.btnShare.visibility = View.GONE
            }

            binding.tietUrlFile.setText(intent.getStringExtra("filename_uri"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }
}