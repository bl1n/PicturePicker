package com.example.picturepicker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.Uri.fromFile
import android.net.Uri.fromParts
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

const val REQUEST_TAKE_PHOTO = 111
const val REQUEST_GALLERY_PHOTO = 222


class MainActivity : AppCompatActivity() {

    private lateinit var mPhotoFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestStoragePermissions(true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_TAKE_PHOTO) {
                Glide.with(this)
                    .load(mPhotoFile)
                    .into(imageView)
                rec_button.setOnClickListener {
                    val image = FirebaseVisionImage.fromFilePath(this, fromFile(mPhotoFile))
                    val recognizer = FirebaseVision.getInstance().onDeviceTextRecognizer
                    textView.visibility=View.GONE
                    progressBar.visibility= View.VISIBLE
                    recognizer.processImage(image)
                        .addOnSuccessListener {
                            progressBar.visibility = View.GONE
                            textView.text = it.text
                            textView.visibility=View.VISIBLE
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                            textView.text = it.message
                            textView.visibility=View.VISIBLE

                        }

                }
            } else if (requestCode == REQUEST_GALLERY_PHOTO && data != null) {
                val selectedImage: Uri? = data.data
                Glide.with(this)
                    .load(selectedImage)
                    .into(imageView)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun requestStoragePermissions(isCamera: Boolean) {
        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            )
            .withListener(
                object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()) {
                            if (isCamera) pic_pic_button.setOnClickListener { dispatchTakePictureIntent() }
                            else dispatchGalleryIntent() // не используется, но оставил на будущее
                        }
                        if (report.isAnyPermissionPermanentlyDenied) showSettingsDialog()
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken
                    ) {
                        token.continuePermissionRequest()
                    }
                }
            )
            .withErrorListener {
                Toast.makeText(applicationContext, "Error occurred! ", Toast.LENGTH_SHORT)
                    .show()
            }
            .onSameThread()
            .check()
    }

    private fun showSettingsDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Need permissions")
        builder.setMessage(
            "This app needs permissions! You can grand them in app settings."
        )
        builder.setPositiveButton("GOTO SETTINGS") { dialog, _ ->
            run {
                dialog.cancel()
                openSettings()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, 101)
    }

    private fun dispatchTakePictureIntent() {

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(this, ex.message, Toast.LENGTH_SHORT).show()
            }
            if (photoFile != null) {
                val photoURI: Uri? = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    photoFile
                )
                mPhotoFile = photoFile
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
            }
        }
    }

    private fun dispatchGalleryIntent() {
        val pickPhotoIntent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickPhotoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(intent, REQUEST_GALLERY_PHOTO)
    }

    /**
     * Create file with current timestamp name
     */
    @SuppressLint("SimpleDateFormat")
    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
        val mFileName = "JPEG_" + timestamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(mFileName, ".jpg", storageDir)
    }


}
