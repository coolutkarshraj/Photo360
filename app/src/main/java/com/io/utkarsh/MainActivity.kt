package com.io.utkarsh

import android.Manifest
import android.R.attr
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast

import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso

import org.bytedeco.javacpp.opencv_stitching.Stitcher

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.io.utkarsh.StitcherOutput.Failure
import com.io.utkarsh.StitcherOutput.Success
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.io.File
import id.zelory.compressor.Compressor
import kotlinx.coroutines.Dispatchers
import java.io.FileInputStream
import java.io.FileOutputStream
import android.graphics.BitmapFactory

import android.graphics.Bitmap
import android.os.Environment
import id.zelory.compressor.saveBitmap
import java.lang.Exception
import java.util.*
import android.R.attr.bitmap
import java.io.FileNotFoundException


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var imageStitcher: ImageStitcher
    private lateinit var disposable: Disposable

    private val stitcherInputRelay = PublishSubject.create<StitcherInput>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE)
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED)&&(ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,  permission, 1);
        }
        setUpViews()
        setUpStitcher()
    }

    private fun setUpViews() {
        imageView = findViewById(R.id.image)
        findViewById<View>(R.id.button).setOnClickListener { chooseImages() }
    }

    @Suppress("DEPRECATION")
    private  fun setUpStitcher() {
        imageStitcher = ImageStitcher(FileUtil(applicationContext))
        val dialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.processing_images))
            setCancelable(false)
        }

        disposable = stitcherInputRelay.switchMapSingle {
            imageStitcher.stitchImages(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { dialog.show() }
                    .doOnSuccess { dialog.dismiss() }
        }
                .subscribe({ processResult(it) }, { processError(it) })
    }

    private fun chooseImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
                .setType(INTENT_IMAGE_TYPE)
                .putExtra(EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, CHOOSE_IMAGES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHOOSE_IMAGES && resultCode == Activity.RESULT_OK && data != null) {
            val clipData = data.clipData
            val images = if (clipData != null) {
                List(clipData.itemCount) { clipData.getItemAt(it).uri }
            } else {
                listOf(data.data!!)
            }


               processImages(images)

        }
    }

     private fun processImages(uris: List<Uri>) {
        imageView.setImageDrawable(null) // reset preview
        val stitchMode = Stitcher.PANORAMA
        stitcherInputRelay.onNext(StitcherInput(uris, stitchMode))
    }


    private fun processError(e: Throwable) {
        Log.e(TAG, "", e)
        Toast.makeText(this, e.message + "", Toast.LENGTH_LONG).show()
    }

    private fun processResult(output: StitcherOutput) {
        when (output) {
            is Success -> showImage(output.file)
            is Failure -> processError(output.e)
        }
    }

    private fun showImage(file: File) {

        Picasso.with(this).load(file)
                .memoryPolicy(MemoryPolicy.NO_STORE, MemoryPolicy.NO_CACHE)
                .into(imageView)
        var bitmap: Bitmap? = null
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        try {
            bitmap = BitmapFactory.decodeStream(FileInputStream(file), null, options)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        if(bitmap != null){
            SaveImage(bitmap!!)
        }
       }

    private fun SaveImage(finalBitmap: Bitmap) {
        val root = Environment.getExternalStorageDirectory().toString()
        val myDir = File("$root/saved_images")
        if (!myDir.exists()) {
            myDir.mkdirs()
        }
        val generator = Random()
        var n = 10000
        n = generator.nextInt(n)
        val fname = "Image-$n.jpg"
        val file = File(myDir, fname)
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
    }

    companion object {
        private const val TAG = "TAG"
        private const val EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE"
        private const val INTENT_IMAGE_TYPE = "image/*"
        private const val CHOOSE_IMAGES = 777
    }
}
