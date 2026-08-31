package com.example.whatsappchatconverter

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.whatsappchatconverter.databinding.ActivityMainBinding
import com.example.whatsappchatconverter.event.ConverterStatusEvent
import com.example.whatsappchatconverter.service.WhatsappConverterService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var selectedZipUri: Uri

    private val selectFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri.let {
                selectedZipUri = it!!
                val fileName = getFileNameFromUri(this, it)
                binding.tietUrlFile.setText(fileName)
            }
        }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                val intent = Intent(this, WhatsappConverterService::class.java)
                intent.putExtra("file_zip", selectedZipUri)
                intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }
}