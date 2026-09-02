package com.example.whatsappchatconverter.service

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.text.TextPaint
import com.example.whatsappchatconverter.event.ConverterStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class WhatsappConverterService : Service() {

    private var durationTime = SimpleDateFormat("dd-MM-yyyy HH-mm-ss", Locale.getDefault()).format(
        System.currentTimeMillis()
    )
    private var serviceJob = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pdfDocument = PdfDocument()
    private var isClosed = false

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("Do Start Convert at %s", durationTime)
        serviceJob.launch {
            doExtractZipAndConvert(intent!!.getParcelableExtra<Uri>("file_zip")!!)
        }
        return START_NOT_STICKY
    }

    private suspend fun doExtractZipAndConvert(uri: Uri) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val lineHeight = 18f

        withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri).use { stream ->
                    ZipInputStream(stream).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {

                                var currentY = margin
                                var pageNumber = 1

                                var pageInfo =
                                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber)
                                        .create()
                                var page = pdfDocument.startPage(pageInfo)
                                var canvas = page.canvas

                                val entrySize = entry.size
                                var bytesRead = 0L
                                val milestones = listOf(0, 25, 50, 75, 100)
                                var milestonesIndex = 0

                                Timber.i("Get Bytes of File at %s bytes", entrySize)

                                BufferedReader(InputStreamReader(zis)).useLines { lines ->
                                    lines.forEach { line ->
                                        // Convert bytes into canvas
                                        if (currentY + lineHeight > pageHeight + margin) {
                                            pdfDocument.finishPage(page)
                                            pageNumber++
                                            pageInfo =
                                                PdfDocument.PageInfo.Builder(
                                                    pageWidth,
                                                    pageHeight,
                                                    pageNumber
                                                ).create()
                                            page = pdfDocument.startPage(pageInfo)
                                            canvas = page.canvas
                                            currentY = margin
                                        }

                                        canvas.drawText(
                                            line,
                                            margin,
                                            currentY,
                                            TextPaint().apply {
                                                color = Color.BLACK
                                                textSize = 12f
                                            })
                                        currentY += lineHeight

                                        bytesRead += line.toByteArray().size + 1
                                        if (entrySize > 0) {
                                            val progress =
                                                (bytesRead * 100 / entrySize).toInt()
                                                    .coerceIn(0, 100)

                                            while (milestonesIndex < milestones.size && progress >= milestones[milestonesIndex]) {
                                                Timber.i(
                                                    "Convert Progress %s from 100",
                                                    milestones[milestonesIndex]
                                                )
                                                milestonesIndex++
                                            }
                                        }
                                    }
                                }

                                pdfDocument.finishPage(page)
                                break
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            "Counseling Result ${durationTime}.pdf"
                        )
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/neurokarsa folder"
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    val outputDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "neurokarsa folder"
                    ).apply { mkdirs() }
                    val destFile = File(outputDir, "Counseling Result ${durationTime}.pdf")
                    Uri.fromFile(destFile)
                }

                contentResolver.openOutputStream(uri!!).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                    outputStream!!.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    contentResolver.update(uri, contentValues, null, null)
                }

                Timber.i("PDF has successfully created. You have to see at %s", getPath(uri))

                withContext(Dispatchers.Main) {
                    EventBus.getDefault().post(ConverterStatusEvent(true, uri))
                }
                pdfDocument.close()
                isClosed = true
            } catch (e: Exception) {
                Timber.e("PDF can't created %s", e)
                withContext(Dispatchers.Main) {
                    if (!isClosed)
                        EventBus.getDefault().post(ConverterStatusEvent(false, null))
                }
            } finally {
                pdfDocument.close()
                isClosed = true
            }
        }
    }

    private fun getPath(uri: Uri): String {
        val projection =
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null).use { cursor ->
            if (cursor!!.moveToFirst()) {
                val path = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                ).trimEnd('/')
                val displayName = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                )
                return "/$path/$displayName"
            }
        }
        return ""
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}